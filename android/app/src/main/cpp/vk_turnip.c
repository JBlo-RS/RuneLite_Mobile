// Turnip as Zink's Vulkan driver.
//
// Turnip is built as an Android Vulkan HAL: it exports only HMI, a hw_module_t whose open()
// gives a hwvulkan_device_t with the driver's vkGetInstanceProcAddr. Android's Vulkan loader
// normally does that; here it's done directly, and this library exports vkGetInstanceProcAddr
// (and the other loader-level functions Zink looks up by name) leading to Turnip. Zink
// (Pojav's Mesa) takes "the Vulkan library" as a dlopen handle in $VULKAN_PTR: it gets this one.
//
// Turnip's non-public dependencies (libcutils, libhardware) are stand-ins in the app's native
// library directory (cutils_stub.c, hardware_stub.c), so it loads in the app's own namespace.
//
// The Turnip binary is Amethyst-Android's prebuilt (Mesa 25.2.1); see jniLibs/README.md.

#include "vk_turnip.h"

#include <dlfcn.h>
#include <stdio.h>
#include <string.h>

// AOSP hardware/hardware.h and hardware/hwvulkan.h (LP64 layout)
struct hw_module_t;
struct hw_device_t;
typedef struct hw_module_methods_t {
    int (*open)(const struct hw_module_t *module, const char *id, struct hw_device_t **device);
} hw_module_methods_t;
typedef struct hw_module_t {
    uint32_t tag;
    uint16_t module_api_version;
    uint16_t hal_api_version;
    const char *id;
    const char *name;
    const char *author;
    hw_module_methods_t *methods;
    void *dso;
    uint64_t reserved[32 - 7];
} hw_module_t;
typedef struct hw_device_t {
    uint32_t tag;
    uint32_t version;
    struct hw_module_t *module;
    uint64_t reserved[12];
    int (*close)(struct hw_device_t *device);
} hw_device_t;
typedef struct hwvulkan_device_t {
    hw_device_t common;
    PFN_vkEnumerateInstanceExtensionProperties EnumerateInstanceExtensionProperties;
    PFN_vkCreateInstance CreateInstance;
    PFN_vkGetInstanceProcAddr GetInstanceProcAddr;
} hwvulkan_device_t;

static PFN_vkGetInstanceProcAddr driver_gipa;
static VkInstance last_instance;

const char *turnip_load(const char *path) {
    static char err[512];
    if (driver_gipa) return NULL;
    void *h = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (!h) {
        snprintf(err, sizeof(err), "cannot load Turnip: %s", dlerror());
        return err;
    }
    hw_module_t *hmi = (hw_module_t *) dlsym(h, "HMI");
    if (!hmi || !hmi->methods || !hmi->methods->open) return "Turnip has no HMI module";
    hw_device_t *dev = NULL;
    int r = hmi->methods->open(hmi, "vk0", &dev); // HWVULKAN_DEVICE_0
    if (r != 0 || !dev) {
        snprintf(err, sizeof(err), "Turnip HAL open failed (%d)", r);
        return err;
    }
    driver_gipa = ((hwvulkan_device_t *) dev)->GetInstanceProcAddr;
    return driver_gipa ? NULL : "Turnip HAL has no vkGetInstanceProcAddr";
}

void *turnip_handle(void) {
    Dl_info info;
    if (!dladdr((void *) &turnip_handle, &info) || !info.dli_fname) return NULL;
    return dlopen(info.dli_fname, RTLD_NOW | RTLD_NOLOAD);
}

// --- what a Vulkan loader exports, leading to Turnip

static VkResult VKAPI_CALL create_instance(const VkInstanceCreateInfo *ci, const VkAllocationCallbacks *a,
                                           VkInstance *out) {
    PFN_vkCreateInstance real = (PFN_vkCreateInstance) driver_gipa(NULL, "vkCreateInstance");
    VkResult r = real(ci, a, out);
    if (r == VK_SUCCESS) last_instance = *out;
    return r;
}

__attribute__((visibility("default")))
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance instance, const char *name) {
    if (!driver_gipa || !name) return NULL;
    if (!strcmp(name, "vkGetInstanceProcAddr")) return (PFN_vkVoidFunction) vkGetInstanceProcAddr;
    if (!strcmp(name, "vkCreateInstance")) return (PFN_vkVoidFunction) create_instance;
    return driver_gipa(instance, name);
}

PFN_vkGetInstanceProcAddr turnip_gipa(void) {
    return vkGetInstanceProcAddr;
}

__attribute__((visibility("default")))
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetDeviceProcAddr(VkDevice device, const char *name) {
    static PFN_vkGetDeviceProcAddr real;
    if (!real && driver_gipa && last_instance) {
        real = (PFN_vkGetDeviceProcAddr) driver_gipa(last_instance, "vkGetDeviceProcAddr");
    }
    return real ? real(device, name) : NULL;
}

__attribute__((visibility("default")))
VKAPI_ATTR VkResult VKAPI_CALL vkCreateInstance(const VkInstanceCreateInfo *ci, const VkAllocationCallbacks *a,
                                                VkInstance *out) {
    return driver_gipa ? create_instance(ci, a, out) : VK_ERROR_INITIALIZATION_FAILED;
}

__attribute__((visibility("default")))
VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateInstanceExtensionProperties(const char *layer, uint32_t *n,
                                                                      VkExtensionProperties *props) {
    PFN_vkEnumerateInstanceExtensionProperties f = driver_gipa ?
            (PFN_vkEnumerateInstanceExtensionProperties) driver_gipa(NULL, "vkEnumerateInstanceExtensionProperties") : NULL;
    return f ? f(layer, n, props) : VK_ERROR_INITIALIZATION_FAILED;
}

__attribute__((visibility("default")))
VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateInstanceLayerProperties(uint32_t *n, VkLayerProperties *props) {
    (void) props;
    *n = 0; // no layers without a loader
    return VK_SUCCESS;
}

__attribute__((visibility("default")))
VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateInstanceVersion(uint32_t *version) {
    PFN_vkEnumerateInstanceVersion f = driver_gipa ?
            (PFN_vkEnumerateInstanceVersion) driver_gipa(NULL, "vkEnumerateInstanceVersion") : NULL;
    if (f) return f(version);
    *version = VK_API_VERSION_1_1;
    return VK_SUCCESS;
}

uint32_t vk_first_device(PFN_vkGetInstanceProcAddr gipa, char *name, size_t nameLen, uint32_t *apiVersion,
                         char *err, size_t errLen) {
    if (name && nameLen) name[0] = 0;
    PFN_vkCreateInstance create = (PFN_vkCreateInstance) gipa(NULL, "vkCreateInstance");
    if (!create) {
        if (err) snprintf(err, errLen, "no vkCreateInstance");
        return 0;
    }
    VkApplicationInfo app = {VK_STRUCTURE_TYPE_APPLICATION_INFO, NULL, "rlm", 1, NULL, 0, VK_API_VERSION_1_1};
    VkInstanceCreateInfo ci = {VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, NULL, 0, &app, 0, NULL, 0, NULL};
    VkInstance inst;
    VkResult r = create(&ci, NULL, &inst);
    if (r != VK_SUCCESS) {
        if (err) snprintf(err, errLen, "vkCreateInstance failed (%d)", r);
        return 0;
    }
    PFN_vkEnumeratePhysicalDevices enumerate = (PFN_vkEnumeratePhysicalDevices) gipa(inst, "vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceProperties props = (PFN_vkGetPhysicalDeviceProperties) gipa(inst, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroy = (PFN_vkDestroyInstance) gipa(inst, "vkDestroyInstance");
    uint32_t n = 1, vendor = 0;
    VkPhysicalDevice dev;
    r = enumerate(inst, &n, &dev);
    if ((r == VK_SUCCESS || r == VK_INCOMPLETE) && n > 0) {
        VkPhysicalDeviceProperties p;
        props(dev, &p);
        vendor = p.vendorID;
        if (name) snprintf(name, nameLen, "%s", p.deviceName);
        if (apiVersion) *apiVersion = p.apiVersion;
    } else if (err) {
        snprintf(err, errLen, "no Vulkan devices (%d)", r);
    }
    destroy(inst, NULL);
    return vendor;
}
