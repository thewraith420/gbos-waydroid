#include <stdint.h>
#include <stddef.h>

extern const char *getprogname(void);
extern char *strstr(const char *haystack, const char *needle);
extern int strcmp(const char *s1, const char *s2);
extern void *dlopen(const char *filename, int flags);
extern void *dlsym(void *handle, const char *symbol);
extern int __android_log_print(int prio, const char *tag, const char *fmt, ...);
extern int __system_property_get(const char *name, char *value);
extern void *fopen(const char *filename, const char *mode);
extern char *fgets(char *s, int size, void *stream);
extern int fclose(void *stream);

#define RTLD_NOW   2
#define RTLD_LOCAL 0
#define ANDROID_LOG_INFO 4
#define ANDROID_LOG_WARN 5
#define ANDROID_LOG_ERROR 6

struct hw_module_t;
struct hw_device_t;

struct hw_module_methods_t {
    int (*open)(const struct hw_module_t *module, const char *id,
                struct hw_device_t **device);
};

struct hw_device_t {
    uint32_t tag;
    uint32_t version;
    const struct hw_module_t *module;
    uint64_t reserved[12];
    int (*close)(struct hw_device_t *device);
};

struct hw_module_t {
    uint32_t tag;
    uint16_t module_api_version;
    uint16_t hal_api_version;
    const char *id;
    const char *name;
    const char *author;
    struct hw_module_methods_t *methods;
    void *dso;
    void *reserved[32 - 7];
};

struct hwvulkan_module_t {
    struct hw_module_t common;
};

struct hwvulkan_device_t {
    struct hw_device_t common;
    int (*EnumerateInstanceExtensionProperties)(const char *pLayerName, uint32_t *pPropertyCount, void *pProperties);
    int (*CreateInstance)(const void *pCreateInfo, const void *pAllocator, void **pInstance);
    void *(*GetInstanceProcAddr)(void *instance, const char *pName);
};

typedef uint32_t VkFlags;
typedef uint32_t VkStructureType;

typedef struct VkBaseInStructure {
    VkStructureType sType;
    const struct VkBaseInStructure *pNext;
} VkBaseInStructure;

typedef struct VkExtensionProperties {
    char extensionName[256];
    uint32_t specVersion;
} VkExtensionProperties;

typedef struct VkDeviceCreateInfo {
    VkStructureType sType;
    const void *pNext;
    VkFlags flags;
    uint32_t queueCreateInfoCount;
    const void *pQueueCreateInfos;
    uint32_t enabledLayerCount;
    const char *const *ppEnabledLayerNames;
    uint32_t enabledExtensionCount;
    const char *const *ppEnabledExtensionNames;
    const void *pEnabledFeatures;
} VkDeviceCreateInfo;

typedef void *(*PFN_vkGetInstanceProcAddr)(void *instance, const char *pName);
typedef int (*PFN_vkCreateDevice)(void *physicalDevice, const VkDeviceCreateInfo *pCreateInfo, const void *pAllocator, void **pDevice);
typedef int (*PFN_vkEnumerateDeviceExtensionProperties)(void *physicalDevice, const char *pLayerName, uint32_t *pPropertyCount, VkExtensionProperties *pProperties);

/* Minimal memory-properties layout (ABI-identical to Vulkan headers). */
typedef struct { uint32_t propertyFlags; uint32_t heapIndex; } HMemType;
typedef struct { uint64_t size; uint32_t flags; } HMemHeap;
typedef struct {
    uint32_t memoryTypeCount;
    HMemType memoryTypes[32];
    uint32_t memoryHeapCount;
    HMemHeap memoryHeaps[16];
} HMemProps;
typedef struct { uint32_t sType; void *pNext; HMemProps memoryProperties; } HMemProps2;
typedef void (*PFN_vkGetPhysicalDeviceMemoryProperties)(void *pd, HMemProps *p);
typedef void (*PFN_vkGetPhysicalDeviceMemoryProperties2)(void *pd, HMemProps2 *p);

#define H_DEVICE_LOCAL 0x1u
#define H_HOST_VISIBLE 0x2u
#define H_HEAP_DEVICE_LOCAL 0x1u

static PFN_vkGetPhysicalDeviceMemoryProperties g_real_memprops = NULL;
static PFN_vkGetPhysicalDeviceMemoryProperties2 g_real_memprops2 = NULL;
static int g_truemem = 0;

/* The Venus guest stamps every HOST_VISIBLE type (incl. PCIe system RAM) as
 * DEVICE_LOCAL so Android gralloc/AHB clients work. Compute apps that pick
 * DEVICE_LOCAL|HOST_VISIBLE first-fit then land in sysmem and run PCIe-bound.
 * Undo the stamp on heaps that are not real VRAM (a real VRAM heap contains
 * a DEVICE_LOCAL type that is NOT host visible). */
static void undo_uma_stamp(HMemProps *p) {
    uint32_t real_vram_heap = 0; /* bitmask */
    for (uint32_t t = 0; t < p->memoryTypeCount; t++) {
        uint32_t f = p->memoryTypes[t].propertyFlags;
        if ((f & H_DEVICE_LOCAL) && !(f & H_HOST_VISIBLE))
            real_vram_heap |= 1u << p->memoryTypes[t].heapIndex;
    }
    if (!real_vram_heap) return; /* unknown layout (e.g. true UMA): leave alone */
    for (uint32_t t = 0; t < p->memoryTypeCount; t++) {
        if (!(real_vram_heap & (1u << p->memoryTypes[t].heapIndex)))
            p->memoryTypes[t].propertyFlags &= ~H_DEVICE_LOCAL;
    }
    for (uint32_t h = 0; h < p->memoryHeapCount; h++) {
        if (!(real_vram_heap & (1u << h)))
            p->memoryHeaps[h].flags &= ~H_HEAP_DEVICE_LOCAL;
    }
}

static void hybrid_vkGetPhysicalDeviceMemoryProperties(void *pd, HMemProps *p) {
    g_real_memprops(pd, p);
    if (g_truemem) undo_uma_stamp(p);
}

static void hybrid_vkGetPhysicalDeviceMemoryProperties2(void *pd, HMemProps2 *p) {
    g_real_memprops2(pd, p);
    if (g_truemem) undo_uma_stamp(&p->memoryProperties);
}

static PFN_vkGetInstanceProcAddr g_real_gipa = NULL;
static PFN_vkCreateDevice g_real_create_device = NULL;
static PFN_vkEnumerateDeviceExtensionProperties g_real_enum_dev_ext = NULL;
static struct hwvulkan_device_t g_wrapped_dev;

static int is_flutter_app(void) {
    void *f = fopen("/proc/self/maps", "r");
    if (!f) return 0;
    char line[512];
    int found = 0;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, "libflutter.so")) {
            found = 1;
            break;
        }
    }
    fclose(f);
    return found;
}

static int should_use_intel(const char *prog) {
    char prop[92] = {0};
    if (__system_property_get("persist.waydroid.vulkan.override", prop) > 0) {
        if (strcmp(prop, "intel") == 0) return 1;
        if (strcmp(prop, "nvidia") == 0) return 0;
    }
    if (__system_property_get("persist.waydroid.vulkan.apps.intel", prop) > 0 && prog) {
        if (strstr(prop, prog)) return 1;
    }
    if (!prog) return 0;
    if (strstr(prog, "chrome")) return 1;
    if (strstr(prog, "webview")) return 1;
    if (strstr(prog, "SystemUI")) return 1;
    if (strstr(prog, "nexuslauncher")) return 1;
    if (strstr(prog, "vending")) return 1;
    if (strstr(prog, "gms")) return 1;
    if (strstr(prog, "bible")) return 1;
    if (is_flutter_app()) return 1;
    return 0;
}

static int is_dangerous_extension(const char *name) {
    if (!name) return 0;
    if (strstr(name, "cooperative_matrix")) return 1;
    if (strstr(name, "deferred_host_operations")) return 1;
    if (strstr(name, "ray_tracing")) return 1;
    if (strstr(name, "ray_query")) return 1;
    return 0;
}

static int hybrid_vkEnumerateDeviceExtensionProperties(void *physicalDevice, const char *pLayerName,
                                                       uint32_t *pPropertyCount, VkExtensionProperties *pProperties) {
    if (!g_real_enum_dev_ext) return -1;

    if (!pProperties) {
        int res = g_real_enum_dev_ext(physicalDevice, pLayerName, pPropertyCount, NULL);
        return res;
    }

    uint32_t req_count = *pPropertyCount;
    int res = g_real_enum_dev_ext(physicalDevice, pLayerName, pPropertyCount, pProperties);
    if (res >= 0 && pPropertyCount) {
        uint32_t out_count = 0;
        for (uint32_t i = 0; i < *pPropertyCount; i++) {
            const char *name = pProperties[i].extensionName;
            if (is_dangerous_extension(name)) {
                __android_log_print(ANDROID_LOG_WARN, "VulkanHybrid", "Filtered dangerous dev ext: %s", name);
                continue;
            }
            if (out_count != i) {
                pProperties[out_count] = pProperties[i];
            }
            out_count++;
        }
        __android_log_print(ANDROID_LOG_INFO, "VulkanHybrid", "EnumerateDeviceExtensions: filtered %u -> %u",
                            *pPropertyCount, out_count);
        *pPropertyCount = out_count;
    }
    return res;
}

static int hybrid_vkCreateDevice(void *physicalDevice, const VkDeviceCreateInfo *pCreateInfo,
                                const void *pAllocator, void **pDevice) {
    if (!g_real_create_device) return -1;

    VkDeviceCreateInfo safe_dci;
    const char *safe_exts[128];
    uint32_t safe_ext_count = 0;

    if (pCreateInfo) {
        safe_dci = *pCreateInfo;

        __android_log_print(ANDROID_LOG_INFO, "VulkanHybrid", ">>> vkCreateDevice: requesting %u extensions",
                            pCreateInfo->enabledExtensionCount);

        for (uint32_t i = 0; i < pCreateInfo->enabledExtensionCount && safe_ext_count < 128; i++) {
            const char *ext = pCreateInfo->ppEnabledExtensionNames[i];
            if (is_dangerous_extension(ext)) {
                __android_log_print(ANDROID_LOG_WARN, "VulkanHybrid", "Stripping dangerous extension from vkCreateDevice: %s", ext);
                continue;
            }
            __android_log_print(ANDROID_LOG_INFO, "VulkanHybrid", "    enabledExt[%u]: %s", safe_ext_count, ext);
            safe_exts[safe_ext_count++] = ext;
        }

        safe_dci.enabledExtensionCount = safe_ext_count;
        safe_dci.ppEnabledExtensionNames = safe_exts;

        // Log pNext chain
        const VkBaseInStructure *p = (const VkBaseInStructure *)pCreateInfo->pNext;
        while (p) {
            __android_log_print(ANDROID_LOG_INFO, "VulkanHybrid", "    pNext sType=%u", p->sType);
            p = p->pNext;
        }

        pCreateInfo = &safe_dci;
    }

    __android_log_print(ANDROID_LOG_INFO, "VulkanHybrid", "Calling real vkCreateDevice...");
    int res = g_real_create_device(physicalDevice, pCreateInfo, pAllocator, pDevice);
    __android_log_print(ANDROID_LOG_INFO, "VulkanHybrid", "real vkCreateDevice completed with result %d!", res);
    return res;
}

static void *hybrid_GetInstanceProcAddr(void *instance, const char *pName) {
    if (!pName || !g_real_gipa) return NULL;

    if (strcmp(pName, "vkCreateDevice") == 0) {
        if (!g_real_create_device) {
            g_real_create_device = (PFN_vkCreateDevice)g_real_gipa(instance, "vkCreateDevice");
        }
        return (void *)hybrid_vkCreateDevice;
    }

    if (strcmp(pName, "vkGetPhysicalDeviceMemoryProperties") == 0) {
        if (!g_real_memprops)
            g_real_memprops = (PFN_vkGetPhysicalDeviceMemoryProperties)g_real_gipa(instance, pName);
        return g_real_memprops ? (void *)hybrid_vkGetPhysicalDeviceMemoryProperties : NULL;
    }
    if (strcmp(pName, "vkGetPhysicalDeviceMemoryProperties2") == 0 ||
        strcmp(pName, "vkGetPhysicalDeviceMemoryProperties2KHR") == 0) {
        if (!g_real_memprops2)
            g_real_memprops2 = (PFN_vkGetPhysicalDeviceMemoryProperties2)g_real_gipa(instance, "vkGetPhysicalDeviceMemoryProperties2");
        return g_real_memprops2 ? (void *)hybrid_vkGetPhysicalDeviceMemoryProperties2 : NULL;
    }

    if (strcmp(pName, "vkEnumerateDeviceExtensionProperties") == 0) {
        if (!g_real_enum_dev_ext) {
            g_real_enum_dev_ext = (PFN_vkEnumerateDeviceExtensionProperties)g_real_gipa(instance, "vkEnumerateDeviceExtensionProperties");
        }
        return (void *)hybrid_vkEnumerateDeviceExtensionProperties;
    }

    return g_real_gipa(instance, pName);
}

static int hybrid_hal_open(const struct hw_module_t *module, const char *id,
                           struct hw_device_t **out_device) {
    (void)module;
    const char *prog = getprogname();
    int use_intel = should_use_intel(prog);

    const char *intel_so  = "/vendor/lib64/hw/vulkan.intel.so";
    const char *nvidia_so = "/vendor/lib64/hw/vulkan.nvidia.so";
    const char *pastel_so = "/vendor/lib64/hw/vulkan.pastel.so";

    void *handle = NULL;
    const char *chosen_so = NULL;

    if (use_intel) {
        chosen_so = intel_so;
        handle = dlopen(chosen_so, RTLD_NOW | RTLD_LOCAL);
    } else {
        chosen_so = nvidia_so;
        handle = dlopen(chosen_so, RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            chosen_so = pastel_so;
            handle = dlopen(chosen_so, RTLD_NOW | RTLD_LOCAL);
        }
        if (!handle) {
            chosen_so = intel_so;
            handle = dlopen(chosen_so, RTLD_NOW | RTLD_LOCAL);
        }
    }

    if (!handle) {
        __android_log_print(ANDROID_LOG_ERROR, "VulkanHybrid",
                            "dlopen(%s) failed for %s", chosen_so, prog ? prog : "?");
        return -1;
    }

    struct hw_module_t *backend_hmi = (struct hw_module_t *)dlsym(handle, "HMI");
    if (!backend_hmi || !backend_hmi->methods || !backend_hmi->methods->open) {
        __android_log_print(ANDROID_LOG_ERROR, "VulkanHybrid",
                            "dlsym(HMI) failed in %s", chosen_so);
        return -1;
    }

    __android_log_print(ANDROID_LOG_INFO, "VulkanHybrid",
                        "Routing %s -> %s", prog ? prog : "?", chosen_so);
    int res = backend_hmi->methods->open(backend_hmi, id, out_device);
    if (res != 0 || !out_device || !*out_device) {
        return res;
    }

    // Only hook NVIDIA / Venus backend to intercept and filter dangerous extensions
    if (!use_intel) {
        struct hwvulkan_device_t *backend_dev = (struct hwvulkan_device_t *)*out_device;
        {
            char tm[92] = {0};
            g_truemem = (__system_property_get("persist.waydroid.vulkan.truemem", tm) > 0 && tm[0] == '1');
            __android_log_print(ANDROID_LOG_INFO, "VulkanHybrid", "truemem=%d", g_truemem);
        }
        g_real_gipa = backend_dev->GetInstanceProcAddr;
        g_wrapped_dev = *backend_dev;
        g_wrapped_dev.GetInstanceProcAddr = hybrid_GetInstanceProcAddr;
        *out_device = &g_wrapped_dev.common;
        __android_log_print(ANDROID_LOG_INFO, "VulkanHybrid", "Installed Vulkan device interceptor on %s", prog ? prog : "?");
    }

    return res;
}

static struct hw_module_methods_t hybrid_methods = {
    .open = hybrid_hal_open,
};

__attribute__((visibility("default")))
struct hwvulkan_module_t HMI = {
    .common = {
        .tag = 0x48574d54u, // 'HWMT'
        .module_api_version = 0x0001,
        .hal_api_version = 0x0100,
        .id = "vulkan",
        .name = "Waydroid Hybrid Vulkan Router (Intel UI + NVIDIA RTX)",
        .author = "Googlebook",
        .methods = &hybrid_methods,
        .dso = NULL,
        .reserved = {0},
    },
};
