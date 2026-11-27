/*
 * libgb-hwc-waydroid.so — LD_PRELOAD guard for Waydroid's hwcomposer@2.1-service.
 *
 * Guards against known crashes in hwcomposer.waydroid.so, particularly under
 * persist.waydroid.multi_windows=true:
 *   1. create_dmabuf_wl_buffer(): rejects invalid/closed prime_fd, reused non-dmabuf
 *      FDs or gralloc-meta FDs, and undersized buffers before wl_proxy_marshal_flags()
 *      triggers EBADF or KWin EGL import error 7 (which disconnects Wayland and aborts
 *      hwc_wayland_thread()).
 *   2. create_shm_wl_buffer() & buffer::~buffer(): rejects 0x0/unsupported cursor SHM
 *      buffers and guards buffer::~buffer() against NULL wl_buffer dereference (+0x40).
 *   3. do_hotplug(): deduplicates redundant same-size display hotplugs in multi-window
 *      mode so KWin window configure events don't thrash Display 0 and invalidate
 *      ComposerResources caches.
 *   4. configureRpcThreadpool(): prevents hwcomposer's background thread (1 thread)
 *      from shrinking the HIDL threadpool after main() configures 4 threads.
 *   5. BpHwWaydroidTask::_hidl_{setFocusedTask,removeTask,removeAllVisibleRecentTasks}():
 *      dispatches HIDL calls asynchronously on a worker thread so hwc_wayland_thread
 *      never blocks on system_server while holding windowsMutex (breaks the 4-process
 *      system_server <-> SurfaceFlinger <-> composer <-> waydroid.task deadlock).
 *   6. wl_proxy_marshal_array_flags() + create_dmabuf_wl_buffer(): emits Planes 1..N
 *      for multi-plane CrOS gralloc buffers (e.g. NV12/YV12 video layers like App Cast)
 *      because hwcomposer.waydroid.so only forwards Plane 0 before create_immed().
 */

#define _GNU_SOURCE
#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <unistd.h>

#define LOG_TAG "gb-hwc-waydroid"
#define ALOGI(fmt, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, fmt, ##__VA_ARGS__)
#define ALOGW(fmt, ...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, fmt, ##__VA_ARGS__)
#define ALOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, ##__VA_ARGS__)

typedef struct {
    int version;
    int numFds;
    int numInts;
    int data[];
} native_handle_c_t;

typedef struct {
    uint32_t w0;
    uint32_t w1;
    uint32_t pitch;
    uint32_t format;
} buffer_metadata_c_t;

typedef void **(*create_dmabuf_fn_t)(
    void **sret, void *dpy, const buffer_metadata_c_t *meta,
    int prime_fd, int drm_format, int stride,
    int offset, uint64_t modifier, const native_handle_c_t *handle);

typedef void **(*create_shm_fn_t)(
    void **sret, void *dpy, const buffer_metadata_c_t *meta,
    const native_handle_c_t *handle);

typedef void (*buffer_dtor_fn_t)(void *buf);
typedef void (*do_hotplug_fn_t)(void *dpy);
typedef void (*config_rpc_fn_t)(size_t maxThreads, bool callerWillJoin);
typedef void *(*task_hidl_u32_fn_t)(void *sret, void *iface, void *instr, uint32_t task_id);
typedef void *(*task_hidl_void_fn_t)(void *sret, void *iface, void *instr);
typedef void (*hidl_ret_dtor_fn_t)(void *ret_status);

typedef union {
    int32_t i;
    uint32_t u;
    int32_t f;
    const char *s;
    void *o;
    uint32_t n;
    void *a;
    int32_t h;
} wl_arg_c_t;

typedef void *(*wl_marshal_array_flags_fn_t)(
    void *proxy, uint32_t opcode, const void *interface,
    uint32_t version, uint32_t flags, wl_arg_c_t *args);

typedef struct {
    bool active;
    uint32_t extra_count;
    int32_t fds[3];
    uint32_t plane_idx[3];
    uint32_t offsets[3];
    uint32_t strides[3];
    uint32_t mod_hi;
    uint32_t mod_lo;
    uint32_t drm_format;
    uint64_t modifier;
} gbos_dmabuf_tls_t;

static __thread gbos_dmabuf_tls_t g_dmabuf_tls;
static int g_multiplane_log_count = 0;

static int g_dmabuf_warn_count = 0;
static int g_shm_warn_count = 0;
static int g_dtor_warn_count = 0;
static int g_hotplug_skip_count = 0;
static bool g_logged_first_valid = false;

static pthread_mutex_t g_hotplug_mu = PTHREAD_MUTEX_INITIALIZER;
static int32_t g_last_hotplug_w = -1;
static int32_t g_last_hotplug_h = -1;

static pthread_mutex_t g_rpc_mu = PTHREAD_MUTEX_INITIALIZER;
static size_t g_rpc_threads = 0;

#define TASK_Q_CAP 64

typedef enum {
    TASK_OP_SET_FOCUSED = 1,
    TASK_OP_REMOVE_TASK = 2,
    TASK_OP_REMOVE_ALL_RECENT = 3,
} task_op_t;

typedef struct {
    task_op_t op;
    void *iface;
    void *instr;
    uint32_t task_id;
} task_req_t;

static pthread_mutex_t g_task_mu = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_task_cv = PTHREAD_COND_INITIALIZER;
static task_req_t g_task_q[TASK_Q_CAP];
static size_t g_task_head = 0;
static size_t g_task_tail = 0;
static size_t g_task_count = 0;
static bool g_task_worker_started = false;

__attribute__((constructor))
static void gbos_hwc_init(void) {
    ALOGI("loaded into composer pid=%d", (int)getpid());
}

static void *resolve_hwc_sym(const char *sym) {
    void *fn = NULL;
    void *hwc_lib = dlopen("/vendor/lib64/hw/hwcomposer.waydroid.so", RTLD_NOLOAD | RTLD_LOCAL);
    if (!hwc_lib) {
        hwc_lib = dlopen("hwcomposer.waydroid.so", RTLD_NOLOAD | RTLD_LOCAL);
    }
    if (hwc_lib) {
        fn = dlsym(hwc_lib, sym);
    }
    if (!fn) {
        fn = dlsym(RTLD_NEXT, sym);
    }
    if (!fn) {
        ALOGE("failed to resolve %s (hwc_lib=%p err=%s)", sym, hwc_lib, dlerror());
    }
    return fn;
}

static bool prop_is_true(const char *key) {
    char val[PROP_VALUE_MAX] = "";
    if (__system_property_get(key, val) <= 0) return false;
    return strcmp(val, "true") == 0 || strcmp(val, "1") == 0;
}

static bool is_multiplane_yuv_fourcc(uint32_t fmt) {
    return fmt == 0x3231564eU /* NV12 */ ||
           fmt == 0x32315659U /* YV12 */ ||
           fmt == 0x32315559U /* YU12 */ ||
           fmt == 0x30313050U /* P010 */ ||
           fmt == 0x3132564eU /* NV21 */;
}

static bool validate_dmabuf_fd(int fd, struct stat *st_out, char *linkbuf, size_t linkbuf_sz,
                               const char **reason_out, int *errno_out) {
    memset(st_out, 0, sizeof(*st_out));
    linkbuf[0] = '\0';
    if (fstat(fd, st_out) < 0) {
        *errno_out = errno;
        *reason_out = "fstat_failed";
        return false;
    }
    char fdpath[64];
    snprintf(fdpath, sizeof(fdpath), "/proc/self/fd/%d", fd);
    ssize_t n = readlink(fdpath, linkbuf, linkbuf_sz - 1);
    if (n < 0) {
        if (errno == ENOENT) {
            *errno_out = errno;
            *reason_out = "fd_enoent";
            return false;
        }
    } else {
        linkbuf[n] = '\0';
        if (strstr(linkbuf, "dmabuf") == NULL || strstr(linkbuf, "gralloc-meta") != NULL) {
            *reason_out = "non_pixel_dmabuf";
            return false;
        }
    }
    return true;
}

static uint32_t read_u32_unaligned(const void *p) {
    uint32_t v = 0;
    memcpy(&v, p, sizeof(v));
    return v;
}

static int32_t read_s32_unaligned(const void *p) {
    int32_t v = 0;
    memcpy(&v, p, sizeof(v));
    return v;
}

static uint64_t read_u64_unaligned(const void *p) {
    uint64_t v = 0;
    memcpy(&v, p, sizeof(v));
    return v;
}

__attribute__((visibility("default")))
void *wl_proxy_marshal_array_flags(void *proxy, uint32_t opcode, const void *interface,
                                   uint32_t version, uint32_t flags, wl_arg_c_t *args) {
    static wl_marshal_array_flags_fn_t s_real_fn = NULL;
    wl_marshal_array_flags_fn_t real_fn = __atomic_load_n(&s_real_fn, __ATOMIC_ACQUIRE);
    if (!real_fn) {
        real_fn = (wl_marshal_array_flags_fn_t)resolve_hwc_sym("wl_proxy_marshal_array_flags");
        if (!real_fn) {
            return NULL;
        }
        __atomic_store_n(&s_real_fn, real_fn, __ATOMIC_RELEASE);
    }

    if (__builtin_expect(!g_dmabuf_tls.active, 1)) {
        return real_fn(proxy, opcode, interface, version, flags, args);
    }

    if (opcode == 1U && interface == NULL && args != NULL && args[1].u == 0U) {
        int32_t p0_fd = args[0].h;
        uint32_t p0_off = args[2].u;
        uint32_t p0_stride = args[3].u;
        void *ret = real_fn(proxy, opcode, interface, version, flags, args);
        g_dmabuf_tls.active = false;

        for (uint32_t i = 0; i < g_dmabuf_tls.extra_count && i < 3U; i++) {
            wl_arg_c_t p_args[6];
            memset(p_args, 0, sizeof(p_args));
            p_args[0].h = g_dmabuf_tls.fds[i];
            p_args[1].u = g_dmabuf_tls.plane_idx[i];
            p_args[2].u = g_dmabuf_tls.offsets[i];
            p_args[3].u = g_dmabuf_tls.strides[i];
            p_args[4].u = g_dmabuf_tls.mod_hi;
            p_args[5].u = g_dmabuf_tls.mod_lo;
            real_fn(proxy, 1U, NULL, version, flags, p_args);
        }

        if (__atomic_fetch_add(&g_multiplane_log_count, 1, __ATOMIC_RELAXED) < 10) {
            ALOGI("added %u extra dmabuf plane(s): drm=0x%x mod=0x%llx "
                  "p0=(fd=%d off=%u stride=%u) p1=(fd=%d off=%u stride=%u)",
                  g_dmabuf_tls.extra_count, g_dmabuf_tls.drm_format,
                  (unsigned long long)g_dmabuf_tls.modifier,
                  p0_fd, p0_off, p0_stride,
                  g_dmabuf_tls.fds[0], g_dmabuf_tls.offsets[0], g_dmabuf_tls.strides[0]);
        }
        return ret;
    }

    return real_fn(proxy, opcode, interface, version, flags, args);
}

__attribute__((visibility("default")))
void **_Z23create_dmabuf_wl_bufferP7displayRK15buffer_metadataiiiimPK13native_handle(
    void **sret, void *dpy, const buffer_metadata_c_t *meta,
    int prime_fd, int drm_format, int stride,
    int offset, uint64_t modifier, const native_handle_c_t *handle) {
    static create_dmabuf_fn_t real_fn = NULL;

    bool bad = false;
    const char *reason = "";
    int saved_errno = 0;
    struct stat st;
    memset(&st, 0, sizeof(st));
    char linkbuf[256] = "";
    uint64_t need = 0;

    g_dmabuf_tls.active = false;

    if (!handle || handle->numFds <= 0 || prime_fd < 0 ||
        !meta || meta->w0 == 0 || meta->w1 == 0 || stride <= 0 || offset < 0) {
        bad = true;
        reason = "invalid_args";
    } else if (!validate_dmabuf_fd(prime_fd, &st, linkbuf, sizeof(linkbuf), &reason, &saved_errno)) {
        bad = true;
    } else {
        need = (uint64_t)offset + (uint64_t)stride * (uint64_t)meta->w0;
        if (st.st_size > 0 && (uint64_t)st.st_size < need) {
            bad = true;
            reason = "undersized_fd";
        }
    }

    if (!bad) {
        bool is_cros = false;
        uint32_t num_planes = 0;
        if (handle->numFds + handle->numInts == 36) {
            const uint8_t *raw = (const uint8_t *)handle;
            uint32_t magic = read_u32_unaligned(raw + 0x74);
            num_planes = read_u32_unaligned(raw + 0x88);
            uint64_t reserved_sz = read_u64_unaligned(raw + 0x8c);
            uint32_t expected_fds = num_planes + (reserved_sz > 0 ? 1U : 0U);
            if (magic == 0xabcddcbaU && num_planes >= 1U && num_planes <= 4U &&
                handle->numFds == (int)expected_fds) {
                is_cros = true;
            }
        }

        if (is_cros && num_planes > 1U) {
            const uint8_t *raw = (const uint8_t *)handle;
            uint32_t extra = num_planes - 1U;
            gbos_dmabuf_tls_t pending;
            memset(&pending, 0, sizeof(pending));
            pending.extra_count = extra;
            pending.mod_hi = (uint32_t)(modifier >> 32);
            pending.mod_lo = (uint32_t)(modifier & 0xffffffffU);
            pending.drm_format = (uint32_t)drm_format;
            pending.modifier = modifier;

            for (uint32_t i = 0; i < extra; i++) {
                uint32_t p = i + 1U;
                int32_t fd_p = read_s32_unaligned(raw + 0x0c + p * 4U);
                uint32_t stride_p = read_u32_unaligned(raw + 0x20 + p * 4U);
                uint32_t offset_p = read_u32_unaligned(raw + 0x30 + p * 4U);
                uint32_t size_p = read_u32_unaligned(raw + 0x40 + p * 4U);

                struct stat pst;
                char plink[256] = "";
                if (fd_p < 0 || stride_p == 0U) {
                    bad = true;
                    reason = "invalid_plane_args";
                    break;
                }
                if (!validate_dmabuf_fd(fd_p, &pst, plink, sizeof(plink), &reason, &saved_errno)) {
                    bad = true;
                    break;
                }
                if (pst.st_size > 0 && size_p > 0 &&
                    (uint64_t)offset_p + (uint64_t)size_p > (uint64_t)pst.st_size) {
                    bad = true;
                    reason = "undersized_plane_fd";
                    break;
                }
                pending.fds[i] = fd_p;
                pending.plane_idx[i] = p;
                pending.offsets[i] = offset_p;
                pending.strides[i] = stride_p;
            }
            if (!bad) {
                pending.active = true;
                g_dmabuf_tls = pending;
            }
        } else if (is_multiplane_yuv_fourcc((uint32_t)drm_format)) {
            bad = true;
            reason = "missing_yuv_planes";
        }
    }

    if (bad) {
        if (g_dmabuf_warn_count < 40) {
            g_dmabuf_warn_count++;
            ALOGW("skipping bad dmabuf (%s): prime_fd=%d errno=%d link='%s' size=%lld need=%llu "
                  "handle=%p numFds=%d meta=(%u,%u,%u,0x%x) drm=0x%x stride=%d off=%d mod=0x%llx",
                  reason, prime_fd, saved_errno, linkbuf, (long long)st.st_size,
                  (unsigned long long)need, (const void *)handle,
                  handle ? handle->numFds : -1, meta ? meta->w0 : 0U, meta ? meta->w1 : 0U,
                  meta ? meta->pitch : 0U, meta ? meta->format : 0U,
                  (unsigned)drm_format, stride, offset, (unsigned long long)modifier);
        }
        g_dmabuf_tls.active = false;
        *sret = NULL;
        return sret;
    }

    if (!g_logged_first_valid) {
        g_logged_first_valid = true;
        ALOGI("intercepted create_dmabuf_wl_buffer (first fd=%d link='%s')", prime_fd, linkbuf);
    }

    if (!real_fn) {
        real_fn = (create_dmabuf_fn_t)resolve_hwc_sym(
            "_Z23create_dmabuf_wl_bufferP7displayRK15buffer_metadataiiiimPK13native_handle");
        if (!real_fn) {
            g_dmabuf_tls.active = false;
            *sret = NULL;
            return sret;
        }
    }

    void **res = real_fn(sret, dpy, meta, prime_fd, drm_format, stride, offset, modifier, handle);
    g_dmabuf_tls.active = false;
    return res;
}

__attribute__((visibility("default")))
void **_Z20create_shm_wl_bufferP7displayRK15buffer_metadataPK13native_handle(
    void **sret, void *dpy, const buffer_metadata_c_t *meta,
    const native_handle_c_t *handle) {
    static create_shm_fn_t real_fn = NULL;

    bool valid_fmt = meta && (meta->format == 1U || meta->format == 2U || meta->format == 5U);
    if (!handle || !meta || meta->w0 == 0 || meta->w1 == 0 || !valid_fmt) {
        if (g_shm_warn_count < 25) {
            g_shm_warn_count++;
            ALOGW("skipping bad shm buffer: handle=%p meta=%p (%u,%u,%u,0x%x)",
                  (const void *)handle, (const void *)meta,
                  meta ? meta->w0 : 0U, meta ? meta->w1 : 0U,
                  meta ? meta->pitch : 0U, meta ? meta->format : 0U);
        }
        *sret = NULL;
        return sret;
    }

    if (!real_fn) {
        real_fn = (create_shm_fn_t)resolve_hwc_sym(
            "_Z20create_shm_wl_bufferP7displayRK15buffer_metadataPK13native_handle");
        if (!real_fn) {
            *sret = NULL;
            return sret;
        }
    }

    return real_fn(sret, dpy, meta, handle);
}

__attribute__((visibility("default")))
void _ZN6bufferD2Ev(void *buf) {
    static buffer_dtor_fn_t real_dtor = NULL;
    if (!buf) return;

    void *wl_buf = *(void **)buf;
    if (wl_buf == NULL) {
        if (g_dtor_warn_count < 25) {
            g_dtor_warn_count++;
            ALOGW("guarded buffer::~buffer with NULL wl_buffer (buf=%p)", buf);
        }
        uint8_t is_shm = *((const uint8_t *)buf + 0x20);
        void *shm_data = *(void **)((char *)buf + 0x28);
        int32_t shm_size = *(const int32_t *)((const char *)buf + 0x30);
        if (is_shm == 1 && shm_data != NULL && shm_data != MAP_FAILED && shm_size > 0) {
            munmap(shm_data, (size_t)shm_size);
        }
        return;
    }

    if (!real_dtor) {
        real_dtor = (buffer_dtor_fn_t)resolve_hwc_sym("_ZN6bufferD2Ev");
        if (!real_dtor) return;
    }
    real_dtor(buf);
}

__attribute__((visibility("default")))
void _ZN6bufferD1Ev(void *buf) {
    _ZN6bufferD2Ev(buf);
}

__attribute__((visibility("default")))
void _Z10do_hotplugP7display(void *dpy) {
    static do_hotplug_fn_t real_fn = NULL;

    if (dpy && prop_is_true("persist.waydroid.multi_windows")) {
        int32_t w = *(const int32_t *)((const char *)dpy + 0x2b0);
        int32_t h = *(const int32_t *)((const char *)dpy + 0x2b4);
        pthread_mutex_lock(&g_hotplug_mu);
        if (g_last_hotplug_w == w && g_last_hotplug_h == h && w > 0 && h > 0) {
            int cnt = ++g_hotplug_skip_count;
            pthread_mutex_unlock(&g_hotplug_mu);
            if (cnt <= 25) {
                ALOGI("skipping redundant do_hotplug(%dx%d) in multi_windows mode", w, h);
            }
            return;
        }
        g_last_hotplug_w = w;
        g_last_hotplug_h = h;
        pthread_mutex_unlock(&g_hotplug_mu);
    }

    if (!real_fn) {
        real_fn = (do_hotplug_fn_t)resolve_hwc_sym("_Z10do_hotplugP7display");
        if (!real_fn) return;
    }
    real_fn(dpy);
}

__attribute__((visibility("default")))
void _ZN7android8hardware22configureRpcThreadpoolEmb(size_t maxThreads, bool callerWillJoin) {
    static config_rpc_fn_t real_fn = NULL;

    pthread_mutex_lock(&g_rpc_mu);
    if (g_rpc_threads > 0 && maxThreads <= g_rpc_threads) {
        size_t cur = g_rpc_threads;
        pthread_mutex_unlock(&g_rpc_mu);
        ALOGI("skipping duplicate configureRpcThreadpool(%zu) (already >= %zu)",
              maxThreads, cur);
        return;
    }
    if (g_rpc_threads == 0 && maxThreads < 4) {
        maxThreads = 4;
    }
    g_rpc_threads = maxThreads;
    pthread_mutex_unlock(&g_rpc_mu);

    if (!real_fn) {
        real_fn = (config_rpc_fn_t)dlsym(RTLD_NEXT, "_ZN7android8hardware22configureRpcThreadpoolEmb");
        if (!real_fn) {
            void *lib = dlopen("libhidlbase.so", RTLD_NOLOAD | RTLD_LOCAL);
            if (lib) {
                real_fn = (config_rpc_fn_t)dlsym(lib, "_ZN7android8hardware22configureRpcThreadpoolEmb");
            }
        }
        if (!real_fn) {
            ALOGE("failed to resolve configureRpcThreadpool: %s", dlerror());
            return;
        }
    }

    real_fn(maxThreads, callerWillJoin);
}

static void *resolve_task_sym(const char *sym) {
    void *fn = NULL;
    void *task_lib = dlopen("/vendor/lib64/vendor.waydroid.task@1.0.so", RTLD_NOLOAD | RTLD_LOCAL);
    if (!task_lib) {
        task_lib = dlopen("vendor.waydroid.task@1.0.so", RTLD_NOLOAD | RTLD_LOCAL);
    }
    if (task_lib) {
        fn = dlsym(task_lib, sym);
    }
    if (!fn) {
        fn = dlsym(RTLD_NEXT, sym);
    }
    if (!fn) {
        ALOGE("failed to resolve task symbol %s (task_lib=%p err=%s)", sym, task_lib, dlerror());
    }
    return fn;
}

static void *gbos_task_worker(void *arg) {
    (void)arg;
    static const char sym_focus[] =
        "_ZN6vendor8waydroid4task4V1_016BpHwWaydroidTask20_hidl_setFocusedTaskE"
        "PN7android8hardware10IInterfaceEPNS5_7details16HidlInstrumentorEj";
    static const char sym_remove[] =
        "_ZN6vendor8waydroid4task4V1_016BpHwWaydroidTask16_hidl_removeTaskE"
        "PN7android8hardware10IInterfaceEPNS5_7details16HidlInstrumentorEj";
    static const char sym_remove_all[] =
        "_ZN6vendor8waydroid4task4V1_016BpHwWaydroidTask33_hidl_removeAllVisibleRecentTasksE"
        "PN7android8hardware10IInterfaceEPNS5_7details16HidlInstrumentorE";
    static const char sym_ret_dtor[] = "_ZN7android8hardware7details13return_statusD2Ev";

    task_hidl_u32_fn_t real_focus = NULL;
    task_hidl_u32_fn_t real_remove = NULL;
    task_hidl_void_fn_t real_remove_all = NULL;
    hidl_ret_dtor_fn_t ret_dtor = (hidl_ret_dtor_fn_t)dlsym(RTLD_DEFAULT, sym_ret_dtor);
    if (!ret_dtor) {
        void *hidl_lib = dlopen("libhidlbase.so", RTLD_NOLOAD | RTLD_LOCAL);
        if (hidl_lib) {
            ret_dtor = (hidl_ret_dtor_fn_t)dlsym(hidl_lib, sym_ret_dtor);
        }
    }

    for (;;) {
        task_req_t req;
        pthread_mutex_lock(&g_task_mu);
        while (g_task_count == 0) {
            pthread_cond_wait(&g_task_cv, &g_task_mu);
        }
        req = g_task_q[g_task_head];
        g_task_head = (g_task_head + 1U) % TASK_Q_CAP;
        g_task_count--;
        pthread_mutex_unlock(&g_task_mu);

        uint8_t ret[48];
        memset(ret, 0, sizeof(ret));
        bool called = false;

        if (req.op == TASK_OP_SET_FOCUSED) {
            if (!real_focus) real_focus = (task_hidl_u32_fn_t)resolve_task_sym(sym_focus);
            if (real_focus) {
                real_focus(ret, req.iface, req.instr, req.task_id);
                called = true;
            }
        } else if (req.op == TASK_OP_REMOVE_TASK) {
            if (!real_remove) real_remove = (task_hidl_u32_fn_t)resolve_task_sym(sym_remove);
            if (real_remove) {
                real_remove(ret, req.iface, req.instr, req.task_id);
                called = true;
            }
        } else if (req.op == TASK_OP_REMOVE_ALL_RECENT) {
            if (!real_remove_all) real_remove_all = (task_hidl_void_fn_t)resolve_task_sym(sym_remove_all);
            if (real_remove_all) {
                real_remove_all(ret, req.iface, req.instr);
                called = true;
            }
        }

        if (called) {
            ret[0x20] = 1;
            if (ret_dtor) {
                ret_dtor(ret);
            }
        }
    }
    return NULL;
}

static void *enqueue_task_op(void *sret, task_op_t op, void *iface, void *instr, uint32_t task_id) {
    memset(sret, 0, 0x28);
    ((uint8_t *)sret)[0x20] = 1;

    pthread_mutex_lock(&g_task_mu);
    if (!g_task_worker_started) {
        pthread_t tid;
        pthread_attr_t attr;
        pthread_attr_init(&attr);
        pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
        if (pthread_create(&tid, &attr, gbos_task_worker, NULL) == 0) {
            g_task_worker_started = true;
            ALOGI("started async IWaydroidTask worker thread");
        } else {
            ALOGE("failed to start async IWaydroidTask worker thread");
        }
        pthread_attr_destroy(&attr);
    }

    if (g_task_worker_started) {
        if (g_task_count < TASK_Q_CAP) {
            g_task_q[g_task_tail].op = op;
            g_task_q[g_task_tail].iface = iface;
            g_task_q[g_task_tail].instr = instr;
            g_task_q[g_task_tail].task_id = task_id;
            g_task_tail = (g_task_tail + 1U) % TASK_Q_CAP;
            g_task_count++;
            pthread_cond_signal(&g_task_cv);
        } else if (op == TASK_OP_SET_FOCUSED) {
            for (size_t i = g_task_count; i > 0; i--) {
                size_t idx = (g_task_head + i - 1U) % TASK_Q_CAP;
                if (g_task_q[idx].op == TASK_OP_SET_FOCUSED) {
                    g_task_q[idx].iface = iface;
                    g_task_q[idx].instr = instr;
                    g_task_q[idx].task_id = task_id;
                    break;
                }
            }
        }
    }
    pthread_mutex_unlock(&g_task_mu);
    return sret;
}

__attribute__((visibility("default")))
void *_ZN6vendor8waydroid4task4V1_016BpHwWaydroidTask20_hidl_setFocusedTaskEPN7android8hardware10IInterfaceEPNS5_7details16HidlInstrumentorEj(
    void *sret, void *iface, void *instr, uint32_t task_id) {
    return enqueue_task_op(sret, TASK_OP_SET_FOCUSED, iface, instr, task_id);
}

__attribute__((visibility("default")))
void *_ZN6vendor8waydroid4task4V1_016BpHwWaydroidTask16_hidl_removeTaskEPN7android8hardware10IInterfaceEPNS5_7details16HidlInstrumentorEj(
    void *sret, void *iface, void *instr, uint32_t task_id) {
    return enqueue_task_op(sret, TASK_OP_REMOVE_TASK, iface, instr, task_id);
}

__attribute__((visibility("default")))
void *_ZN6vendor8waydroid4task4V1_016BpHwWaydroidTask33_hidl_removeAllVisibleRecentTasksEPN7android8hardware10IInterfaceEPNS5_7details16HidlInstrumentorE(
    void *sret, void *iface, void *instr) {
    return enqueue_task_op(sret, TASK_OP_REMOVE_ALL_RECENT, iface, instr, 0U);
}
