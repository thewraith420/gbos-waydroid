/*
 * libgb-sf-waydroid.so — LD_PRELOAD shim for Googlebook Android 17 SurfaceFlinger.
 *
 * Googlebook's SurfaceFlinger does not call Waydroid's IWaydroidDisplay::setLayerName()
 * HIDL extension, so hwcomposer.waydroid.so receives blank layer names and cannot group
 * app layers into per-task host Wayland windows under persist.waydroid.multi_windows=true.
 *
 * When persist.waydroid.multi_windows (true/1) or persist.gbos.sf_layer_names (1) is set,
 * this shim installs inline trampolines at verified offsets of /system/bin/surfaceflinger
 * (sha256 78c2d53857b86f1d6b5018a85be1080e610ecea8e58c5a3e18e7486084b68b4e):
 *   1. Output::presentFrameAndReleaseLayers (+0x398420): walks Z-ordered OutputLayers for
 *      "Built-in Screen", maps Googlebook layer debug names ("Task=<id>#...",
 *      "VRI-<pkg>/<act>#...", "InputMethod...") to Waydroid's
 *      "TID:<taskId>#<pkg>/<act>#<id>" / "InputMethod" naming scheme, and pushes changes
 *      over IWaydroidDisplay::setLayerName(hwcIdx, name).
 *   2. Output::rebuildLayerStacks occlusion subtraction (+0x39060d): NOPs out
 *      visibleRegion.subtractSelf(aboveOpaqueLayers) when multi_windows is active so
 *      overlapping Android app windows keep rendering independently for host compositing.
 *
 * When neither property is set, the constructor returns immediately without patching.
 */

#define _GNU_SOURCE
#include <android/log.h>
#include <dlfcn.h>
#include <link.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/system_properties.h>
#include <unistd.h>

#define LOG_TAG "gb-sf-waydroid"
#define ALOGI(fmt, ...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, fmt, ##__VA_ARGS__)
#define ALOGW(fmt, ...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, fmt, ##__VA_ARGS__)
#define ALOGE(fmt, ...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, fmt, ##__VA_ARGS__)

/* Verified offsets for /system/bin/surfaceflinger (sha256 78c2d538...) */
#define SF_OFF_PRESENT_AND_RELEASE 0x398420ULL
#define SF_OFF_OCCLUSION_CHECK     0x3905ffULL
#define SF_OFF_OCCLUSION_CALL      0x39060dULL
#define SF_OFF_ROUNDED_CHECK       0x3c97f1ULL
#define SF_OFF_ROUNDED_CALL        0x3c97faULL

#define OUTPUT_NAME_OFFSET         0x20U
#define OUTPUT_VBASE_ADJ_OFF       (-0x280)
#define OUTPUT_VTBL_LAYER_COUNT    (0xf0U / sizeof(void *))
#define OUTPUT_VTBL_LAYER_AT       (0xf8U / sizeof(void *))
#define OL_VTBL_GET_HWC_LAYER      (0x70U / sizeof(void *))
#define OL_VBASE_ADJ_OFF           (-0xd0)
#define OL_VTBL_GET_LAYER_FE       (0x20U / sizeof(void *))
#define LAYER_FE_VTBL_DEBUG_NAME   (0x50U / sizeof(void *))

#define MAX_LAYERS                 128
#define MAX_NAME_LEN               256
#define RETRY_COOLDOWN_FRAMES      30

typedef struct {
    const char *mBuffer;
    uint32_t mSize;
    uint8_t mOwnsBuffer;
    uint8_t pad[3];
} HidlString;

typedef void (*present_fn_t)(void *output, uint8_t flush_even_when_disabled);
typedef void *(*try_get_service_fn)(void **out_sp, const void *std_string_sso, bool get_stub);
typedef void *(*set_layer_name_fn)(void *out_ret40, void *this_display,
                                   uint32_t layer_idx, const HidlString *name);
typedef void (*return_status_dtor_fn)(void *ret_status);

static uintptr_t g_sf_base = 0;
static present_fn_t g_orig_present = NULL;

static void *g_display_lib = NULL;
static void *g_display = NULL;
static try_get_service_fn g_try_get_service = NULL;
static set_layer_name_fn g_set_layer_name = NULL;
static return_status_dtor_fn g_return_status_dtor = NULL;
static int g_retry_cooldown = 0;

static char g_prev_names[MAX_LAYERS][MAX_NAME_LEN];
static size_t g_prev_count = 0;
static bool g_debug_logs = false;
static char g_extra_blacklist[256] = "";

static int ends_with(const char *str, const char *suffix) {
    if (!str || !suffix) return 0;
    size_t slen = strlen(str);
    size_t flen = strlen(suffix);
    if (slen < flen) return 0;
    return memcmp(str + slen - flen, suffix, flen) == 0;
}

static int dl_phdr_cb(struct dl_phdr_info *info, size_t size, void *data) {
    (void)size;
    (void)data;
    if (info->dlpi_name == NULL || info->dlpi_name[0] == '\0' ||
        ends_with(info->dlpi_name, "/surfaceflinger")) {
        g_sf_base = (uintptr_t)info->dlpi_addr;
        return 1;
    }
    return 0;
}

static bool prop_is_true(const char *key) {
    char val[PROP_VALUE_MAX] = "";
    if (__system_property_get(key, val) <= 0) return false;
    return strcmp(val, "true") == 0 || strcmp(val, "1") == 0;
}

static bool mprotect_rw(uintptr_t addr, size_t len, int prot) {
    long pgsz_l = sysconf(_SC_PAGESIZE);
    uintptr_t pgsz = (pgsz_l > 0) ? (uintptr_t)pgsz_l : 4096U;
    uintptr_t start = addr & ~(pgsz - 1U);
    uintptr_t end = (addr + len + pgsz - 1U) & ~(pgsz - 1U);
    return mprotect((void *)start, end - start, prot) == 0;
}

static void write_abs_jmp14(uint8_t *dst, uintptr_t target) {
    dst[0] = 0xff;
    dst[1] = 0x25;
    dst[2] = 0x00;
    dst[3] = 0x00;
    dst[4] = 0x00;
    dst[5] = 0x00;
    uint64_t addr64 = (uint64_t)target;
    memcpy(dst + 6, &addr64, sizeof(addr64));
}

static const char *read_libcxx_sso(const void *obj) {
    const uint8_t *p = (const uint8_t *)obj;
    if (p[0] & 1U) {
        return *(const char *const *)(p + 16);
    }
    return (const char *)(p + 1);
}

static uint32_t fnv1a_hash(const char *s, size_t len) {
    uint32_t h = 2166136261U;
    for (size_t i = 0; i < len; i++) {
        h ^= (uint8_t)s[i];
        h *= 16777619U;
    }
    return h;
}

static bool parse_task_prefix(const char *s, const char *prefix, int *out_tid) {
    size_t plen = strlen(prefix);
    if (strncmp(s, prefix, plen) != 0) return false;
    const char *p = s + plen;
    if (*p < '0' || *p > '9') return false;
    long v = 0;
    while (*p >= '0' && *p <= '9') {
        v = v * 10 + (*p - '0');
        if (v > 99999999L) return false;
        p++;
    }
    if (*p != '\0' && *p != '#') return false;
    *out_tid = (int)v;
    return true;
}

static bool pkg_in_colon_list(const char *pkg, size_t pkg_len, const char *list) {
    if (!list || !list[0]) return false;
    const char *p = list;
    while (*p) {
        const char *colon = strchr(p, ':');
        size_t item_len = colon ? (size_t)(colon - p) : strlen(p);
        if (item_len == pkg_len && memcmp(p, pkg, pkg_len) == 0) return true;
        if (!colon) break;
        p = colon + 1;
    }
    return false;
}

static bool is_blacklisted_pkg(const char *pkg, size_t pkg_len) {
    static const char *const defaults[] = {
        "com.google.android.apps.nexuslauncher",
        "com.android.launcher3",
    };
    for (size_t i = 0; i < sizeof(defaults) / sizeof(defaults[0]); i++) {
        size_t dlen = strlen(defaults[i]);
        if (dlen == pkg_len && memcmp(defaults[i], pkg, pkg_len) == 0) return true;
    }
    return pkg_in_colon_list(pkg, pkg_len, g_extra_blacklist);
}

static bool init_hidl_symbols(void) {
    if (g_try_get_service && g_set_layer_name) return true;
    if (!g_display_lib) {
        g_display_lib = dlopen("/system/lib64/vendor.waydroid.display@1.0.so", RTLD_NOW);
        if (!g_display_lib) {
            g_display_lib = dlopen("vendor.waydroid.display@1.0.so", RTLD_NOW);
        }
        if (!g_display_lib) {
            ALOGW("dlopen(vendor.waydroid.display@1.0.so) failed: %s", dlerror());
            return false;
        }
        if (g_debug_logs) {
            ALOGI("loaded vendor.waydroid.display@1.0.so (%p)", g_display_lib);
        }
    }
    g_try_get_service = (try_get_service_fn)dlsym(
        g_display_lib,
        "_ZN6vendor8waydroid7display4V1_016IWaydroidDisplay13tryGetServiceE"
        "RKNSt3__112basic_stringIcNS4_11char_traitsIcEENS4_9allocatorIcEEEEb");
    g_set_layer_name = (set_layer_name_fn)dlsym(
        g_display_lib,
        "_ZN6vendor8waydroid7display4V1_019BpHwWaydroidDisplay12setLayerNameE"
        "jRKN7android8hardware11hidl_stringE");
    g_return_status_dtor = (return_status_dtor_fn)dlsym(
        RTLD_DEFAULT,
        "_ZN7android8hardware7details13return_statusD2Ev");
    if (!g_try_get_service || !g_set_layer_name) {
        ALOGE("missing HIDL symbols in vendor.waydroid.display@1.0.so (tgs=%p sln=%p)",
              (void *)g_try_get_service, (void *)g_set_layer_name);
        return false;
    }
    if (!g_return_status_dtor) {
        g_return_status_dtor = (return_status_dtor_fn)dlsym(
            g_display_lib, "_ZN7android8hardware7details13return_statusD2Ev");
    }
    return true;
}

static bool ensure_display_service(void) {
    if (g_display) return true;
    if (g_retry_cooldown > 0) {
        g_retry_cooldown--;
        return false;
    }
    if (!init_hidl_symbols()) {
        g_retry_cooldown = RETRY_COOLDOWN_FRAMES;
        return false;
    }
    /* libc++ SSO std::string("default"): length 7 -> size byte (7 << 1) = 14 */
    uint8_t sso_default[24] = {14, 'd', 'e', 'f', 'a', 'u', 'l', 't', 0};
    void *sp = NULL;
    g_try_get_service(&sp, sso_default, false);
    if (!sp) {
        g_retry_cooldown = RETRY_COOLDOWN_FRAMES;
        ALOGW("IWaydroidDisplay/default unavailable yet (retry in %d frames)",
              RETRY_COOLDOWN_FRAMES);
        return false;
    }
    g_display = sp;
    memset(g_prev_names, 0, sizeof(g_prev_names));
    g_prev_count = 0;
    ALOGI("connected to IWaydroidDisplay/default (%p)", g_display);
    return true;
}

static bool call_set_layer_name(uint32_t idx, const char *name) {
    if (!g_display || !g_set_layer_name) return false;
    HidlString hs;
    hs.mBuffer = name;
    hs.mSize = (uint32_t)strlen(name);
    hs.mOwnsBuffer = 0;
    memset(hs.pad, 0, sizeof(hs.pad));

    uint8_t ret[48];
    memset(ret, 0, sizeof(ret));
    g_set_layer_name(ret, g_display, idx, &hs);

    int32_t ex = 0;
    memcpy(&ex, ret, sizeof(ex));
    /* Mark return_status checked (mCheckedStatus at +0x20) before destructor runs. */
    ret[0x20] = 1;
    if (g_return_status_dtor) {
        g_return_status_dtor(ret);
    }
    if (ex != 0) {
        ALOGW("setLayerName(%u, '%s') transport error %d; resetting display proxy",
              idx, name, ex);
        g_display = NULL;
        g_retry_cooldown = RETRY_COOLDOWN_FRAMES;
        memset(g_prev_names, 0, sizeof(g_prev_names));
        g_prev_count = 0;
        return false;
    }
    return true;
}

/*
 * Format one raw Googlebook debug layer name into Waydroid's layer string:
 *   "InputMethod" or "TID:<taskId>#<pkg>/<act>#<id>" or "" (ignored/system layer).
 */
static void format_layer_name(const char *raw, int task_id, bool keep_caption,
                              char *out, size_t out_sz) {
    out[0] = '\0';
    if (!raw || !raw[0]) return;

    int parsed_tid = -1;
    if (parse_task_prefix(raw, "Task=", &parsed_tid)) {
        return; /* buffer-less task container */
    }
    if (parse_task_prefix(raw, "Caption of Task=", &parsed_tid)) {
        if (keep_caption && parsed_tid >= 0) {
            snprintf(out, out_sz, "TID:%d#caption/task#%d", parsed_tid, parsed_tid);
        }
        return;
    }
    if (strstr(raw, "InputMethod") != NULL) {
        snprintf(out, out_sz, "InputMethod");
        return;
    }
    if (strncmp(raw, "VRI-NotificationShade", 21) == 0 ||
        strncmp(raw, "NotificationShade", 17) == 0) {
        snprintf(out, out_sz, "TID:999001#com.android.systemui/NotificationShade#1");
        return;
    }

    char normalized[MAX_NAME_LEN];
    const char *s = raw;
    const char *sv = NULL;
    if (strncmp(s, "Background for ", 15) == 0) return;
    if (strncmp(s, "VRI-", 4) == 0) {
        s += 4;
        snprintf(normalized, sizeof(normalized), "%s", s);
    } else if ((sv = strstr(s, "SurfaceView[")) != NULL) {
        s = sv + 12;
        const char *rb = strchr(s, ']');
        if (!rb) return;
        size_t inner = (size_t)(rb - s);
        if (inner == 0 || inner >= sizeof(normalized) - 32) return;
        memcpy(normalized, s, inner);
        normalized[inner] = '\0';
        snprintf(normalized + inner, sizeof(normalized) - inner, "%s", rb + 1);
    } else {
        snprintf(normalized, sizeof(normalized), "%s", s);
    }

    const char *slash = strchr(normalized, '/');
    if (!slash || slash == normalized) {
        if (task_id >= 0 && strncmp(normalized, "PopupWindow:", 12) == 0) {
            snprintf(out, out_sz, "TID:%d#android.popup/%s", task_id, normalized);
        }
        return;
    }

    size_t pkg_len = (size_t)(slash - normalized);
    bool has_dot = false;
    for (size_t i = 0; i < pkg_len; i++) {
        char c = normalized[i];
        if (c == ' ' || c == '[' || c == ']' || c == '=') return;
        if (c == '.') has_dot = true;
    }
    if (!has_dot) return;
    if (is_blacklisted_pkg(normalized, pkg_len)) return;

    int tid = (task_id >= 0)
                  ? task_id
                  : (int)(900000U + (fnv1a_hash(normalized, pkg_len) % 90000U));
    snprintf(out, out_sz, "TID:%d#%s", tid, normalized);
}

static char g_last_skipped[64] = "";
static bool g_logged_hook_entry = false;
static bool g_logged_first_frame = false;

static void sync_layer_names(void *output) {
    if (!output) return;
    const char *out_name = read_libcxx_sso((const uint8_t *)output + OUTPUT_NAME_OFFSET);
    if (!g_logged_hook_entry) {
        g_logged_hook_entry = true;
        ALOGI("first hook entry on output '%s'", out_name ? out_name : "(null)");
    }
    if (!out_name || !out_name[0] ||
        strstr(out_name, "Virtual") != NULL ||
        strstr(out_name, "Cast") != NULL ||
        strstr(out_name, "Recorder") != NULL) {
        const char *safe = (out_name && out_name[0]) ? out_name : "(null)";
        if (strcmp(safe, g_last_skipped) != 0) {
            snprintf(g_last_skipped, sizeof(g_last_skipped), "%s", safe);
            ALOGI("ignoring output '%s'", g_last_skipped);
        }
        return;
    }
    if (!ensure_display_service()) return;

    int64_t vbase_adj = *(const int64_t *)(*(const char *const *)output + OUTPUT_VBASE_ADJ_OFF);
    void *output_base = (char *)output + vbase_adj;
    void **out_vtbl = *(void ***)output_base;
    size_t count = ((size_t (*)(void *))out_vtbl[OUTPUT_VTBL_LAYER_COUNT])(output_base);

    const char *raw_names[MAX_LAYERS];
    int task_ids[MAX_LAYERS];
    size_t num_hwc = 0;

    for (size_t i = 0; i < count && num_hwc < MAX_LAYERS; i++) {
        void *ol = ((void *(*)(void *, size_t))out_vtbl[OUTPUT_VTBL_LAYER_AT])(output_base, i);
        if (!ol) continue;
        void *hwc_layer = ((void *(*)(void *))(*(void ***)ol)[OL_VTBL_GET_HWC_LAYER])(ol);
        if (!hwc_layer) continue;

        int64_t ol_adj = *(const int64_t *)(*(const char *const *)ol + OL_VBASE_ADJ_OFF);
        void *ol_base = (char *)ol + ol_adj;
        void *layer_fe = ((void *(*)(void *))(*(void ***)ol_base)[OL_VTBL_GET_LAYER_FE])(ol_base);
        const char *raw =
            layer_fe ? ((const char *(*)(void *))(*(void ***)layer_fe)[LAYER_FE_VTBL_DEBUG_NAME])(layer_fe)
                     : "";
        raw_names[num_hwc] = raw ? raw : "";
        task_ids[num_hwc] = -1;
        num_hwc++;
    }
    if (!g_logged_first_frame) {
        g_logged_first_frame = true;
        ALOGI("first frame on output '%s' (count=%zu, hwc=%zu)", out_name, count, num_hwc);
    }

    int cur_tid = -1;
    for (size_t i = 0; i < num_hwc; i++) {
        int tid = -1;
        if (parse_task_prefix(raw_names[i], "Task=", &tid)) {
            cur_tid = tid;
            task_ids[i] = tid;
        } else if (parse_task_prefix(raw_names[i], "Caption of Task=", &tid)) {
            task_ids[i] = tid;
            for (size_t k = i; k > 0; k--) {
                size_t j = k - 1;
                if (task_ids[j] != -1) break;
                if (strchr(raw_names[j], '/') == NULL &&
                    strstr(raw_names[j], "PopupWindow:") == NULL) {
                    break;
                }
                task_ids[j] = tid;
            }
            cur_tid = -1;
        } else {
            task_ids[i] = cur_tid;
        }
    }

    char cap_prop[PROP_VALUE_MAX] = "";
    __system_property_get("persist.gbos.multi_window_caption", cap_prop);
    bool keep_caption = (strcmp(cap_prop, "0") != 0 && strcmp(cap_prop, "false") != 0);
    for (size_t idx = 0; idx < num_hwc; idx++) {
        char mapped[MAX_NAME_LEN];
        format_layer_name(raw_names[idx], task_ids[idx], keep_caption, mapped, sizeof(mapped));
        if (strcmp(mapped, g_prev_names[idx]) != 0) {
            if (!call_set_layer_name((uint32_t)idx, mapped)) return;
            snprintf(g_prev_names[idx], MAX_NAME_LEN, "%s", mapped);
            if (g_debug_logs || mapped[0] != '\0') {
                ALOGI("hwc[%zu] '%s' -> '%s'", idx, raw_names[idx], mapped);
            }
        }
    }

    for (size_t idx = num_hwc; idx < g_prev_count && idx < MAX_LAYERS; idx++) {
        if (g_prev_names[idx][0] != '\0') {
            if (!call_set_layer_name((uint32_t)idx, "")) return;
            g_prev_names[idx][0] = '\0';
        }
    }
    g_prev_count = num_hwc;
}

static void hook_present_frame_and_release_layers(void *output, uint8_t flush_even_when_disabled) {
    static __thread int in_hook = 0;
    if (!in_hook) {
        in_hook = 1;
        sync_layer_names(output);
        in_hook = 0;
    }
    g_orig_present(output, flush_even_when_disabled);
}

static bool install_present_hook(void) {
    static const uint8_t expected[20] = {
        0x55, 0x48, 0x89, 0xe5, 0x41, 0x57, 0x41, 0x56, 0x41, 0x55,
        0x41, 0x54, 0x53, 0x48, 0x81, 0xec, 0x58, 0x01, 0x00, 0x00
    };
    uintptr_t target = g_sf_base + SF_OFF_PRESENT_AND_RELEASE;
    uint8_t *code = (uint8_t *)target;
    if (memcmp(code, expected, sizeof(expected)) != 0) {
        ALOGE("unexpected prologue at %p (+0x%llx); skipping presentFrame hook",
              (void *)target, (unsigned long long)SF_OFF_PRESENT_AND_RELEASE);
        return false;
    }

    uint8_t *tramp = mmap(NULL, 4096, PROT_READ | PROT_WRITE | PROT_EXEC,
                          MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (tramp == MAP_FAILED) {
        ALOGE("mmap trampoline failed");
        return false;
    }
    memcpy(tramp, expected, sizeof(expected));
    write_abs_jmp14(tramp + sizeof(expected), target + sizeof(expected));
    g_orig_present = (present_fn_t)(void *)tramp;

    if (!mprotect_rw(target, sizeof(expected), PROT_READ | PROT_WRITE | PROT_EXEC)) {
        ALOGE("mprotect RWX failed for presentFrame hook");
        g_orig_present = NULL;
        return false;
    }
    write_abs_jmp14(code, (uintptr_t)&hook_present_frame_and_release_layers);
    memset(code + 14, 0x90, sizeof(expected) - 14);
    __builtin___clear_cache((char *)code, (char *)(code + sizeof(expected)));
    mprotect_rw(target, sizeof(expected), PROT_READ | PROT_EXEC);

    ALOGI("hooked Output::presentFrameAndReleaseLayers at %p (trampoline %p)",
          (void *)target, (void *)tramp);
    return true;
}

static bool install_occlusion_bypass(void) {
    static const uint8_t expected[19] = {
        0x49, 0x8d, 0x5f, 0x70, 0x48, 0x8d, 0xbd, 0xf0, 0xfe, 0xff,
        0xff, 0x48, 0x89, 0xde, 0xe8, 0x2e, 0x2c, 0x55, 0x00
    };
    uintptr_t check_addr = g_sf_base + SF_OFF_OCCLUSION_CHECK;
    uintptr_t call_addr = g_sf_base + SF_OFF_OCCLUSION_CALL;
    if (memcmp((const void *)check_addr, expected, sizeof(expected)) != 0) {
        ALOGE("unexpected bytes at occlusion check %p (+0x%llx); skipping occlusion bypass",
              (void *)check_addr, (unsigned long long)SF_OFF_OCCLUSION_CHECK);
        return false;
    }
    if (!mprotect_rw(call_addr, 5, PROT_READ | PROT_WRITE | PROT_EXEC)) {
        ALOGE("mprotect RWX failed for occlusion bypass");
        return false;
    }
    memset((void *)call_addr, 0x90, 5);
    __builtin___clear_cache((char *)call_addr, (char *)(call_addr + 5));
    mprotect_rw(call_addr, 5, PROT_READ | PROT_EXEC);
    ALOGI("disabled SurfaceFlinger occlusion culling at %p (+0x%llx)",
          (void *)call_addr, (unsigned long long)SF_OFF_OCCLUSION_CALL);
    return true;
}

static bool install_rounded_corners_bypass(void) {
    /*
     * In OutputLayer::writeOutputIndependentPerFrameState (+0x3c97f1..0x3c9803),
     * SurfaceFlinger forces HWC2::Composition::Client (1) whenever
     * layerFE->hasRoundedCorners() (vtable +0x60 at +0x3c97fa) returns true.
     * Googlebook Desktop Windowing gives every freeform task rounded corners,
     * which forces all freeform app windows to CLIENT (HWC_SKIP_LAYER) so
     * hwcomposer.waydroid.so skips them in multi_window_mode.
     * Replace `call *0x60(%rcx)` (ff 51 60) with `xor %eax,%eax; nop` (31 c0 90).
     */
    static const uint8_t expected[19] = {
        0xff, 0x50, 0x20, 0x48, 0x8b, 0x08, 0x48, 0x89, 0xc7,
        0xff, 0x51, 0x60, 0x84, 0xc0, 0xb8, 0x01, 0x00, 0x00, 0x00
    };
    static const uint8_t patch[3] = {0x31, 0xc0, 0x90};
    uintptr_t check_addr = g_sf_base + SF_OFF_ROUNDED_CHECK;
    uintptr_t call_addr = g_sf_base + SF_OFF_ROUNDED_CALL;
    if (memcmp((const void *)check_addr, expected, sizeof(expected)) != 0) {
        ALOGE("unexpected bytes at rounded-corner check %p (+0x%llx); skipping bypass",
              (void *)check_addr, (unsigned long long)SF_OFF_ROUNDED_CHECK);
        return false;
    }
    if (!mprotect_rw(call_addr, sizeof(patch), PROT_READ | PROT_WRITE | PROT_EXEC)) {
        ALOGE("mprotect RWX failed for rounded-corner bypass");
        return false;
    }
    memcpy((void *)call_addr, patch, sizeof(patch));
    __builtin___clear_cache((char *)call_addr, (char *)(call_addr + sizeof(patch)));
    mprotect_rw(call_addr, sizeof(patch), PROT_READ | PROT_EXEC);
    ALOGI("disabled rounded-corner CLIENT override at %p (+0x%llx)",
          (void *)call_addr, (unsigned long long)SF_OFF_ROUNDED_CALL);
    return true;
}

__attribute__((constructor))
static void gbos_sf_init(void) {
    bool multi_windows = prop_is_true("persist.waydroid.multi_windows");
    bool sf_layer_names = prop_is_true("persist.gbos.sf_layer_names");
    if (!multi_windows && !sf_layer_names) {
        ALOGI("dormant (persist.waydroid.multi_windows and persist.gbos.sf_layer_names unset)");
        return;
    }

    g_debug_logs = prop_is_true("persist.gbos.sf_shim_debug");
    __system_property_get("persist.gbos.multi_window_blacklist", g_extra_blacklist);

    dl_iterate_phdr(dl_phdr_cb, NULL);
    if (!g_sf_base) {
        ALOGE("failed to locate /system/bin/surfaceflinger PIE base");
        return;
    }
    ALOGI("active (multi_windows=%d layer_names=%d sf_base=%p)",
          multi_windows ? 1 : 0, sf_layer_names ? 1 : 0, (void *)g_sf_base);

    if (!install_present_hook()) {
        return;
    }

    char no_occ[PROP_VALUE_MAX] = "";
    __system_property_get("persist.gbos.sf_no_occlusion", no_occ);
    if (multi_windows && strcmp(no_occ, "0") != 0) {
        install_occlusion_bypass();
    }

    char no_rc[PROP_VALUE_MAX] = "";
    __system_property_get("persist.gbos.sf_no_rounded_client", no_rc);
    if (multi_windows && strcmp(no_rc, "0") != 0) {
        install_rounded_corners_bypass();
    }
}
