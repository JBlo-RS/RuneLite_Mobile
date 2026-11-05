// GPU feasibility test: can this phone run desktop OpenGL (what RuneLite's GPU plugin needs,
// 3.3 core or newer) through Mesa's Zink driver (OpenGL on top of Vulkan)?
//
// Uses Mesa's OSMesa interface, which renders into a plain memory buffer. The Mesa build is
// PojavLauncher's (Mesa 23.0.4 with Zink); like Pojav, the app opens the system Vulkan loader
// itself and passes the handle in $VULKAN_PTR.
//
// Every step is printed (stdout goes to the app's log) before it runs, so a crash in a driver
// still shows how far the test got.

#include <android/api-level.h>
#include <android/log.h>
#include <dlfcn.h>
#include <inttypes.h>
#include <stdarg.h>
#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <vulkan/vulkan.h>

#define STEP(...) do { printf("gpu: " __VA_ARGS__); printf("\n"); fflush(stdout); } while (0)

// OSMesa (GL/osmesa.h)
typedef void *OSMesaContext;
typedef OSMesaContext (*PFN_OSMesaCreateContextAttribs)(const int *attribs, OSMesaContext share);
typedef unsigned char (*PFN_OSMesaMakeCurrent)(OSMesaContext, void *buf, unsigned type, int w, int h);
typedef void (*PFN_OSMesaDestroyContext)(OSMesaContext);
typedef void *(*PFN_OSMesaGetProcAddress)(const char *name);
#define OSMESA_FORMAT                0x22
#define OSMESA_DEPTH_BITS            0x30
#define OSMESA_PROFILE               0x33
#define OSMESA_CORE_PROFILE          0x34
#define OSMESA_COMPAT_PROFILE        0x35
#define OSMESA_CONTEXT_MAJOR_VERSION 0x36
#define OSMESA_CONTEXT_MINOR_VERSION 0x37

// The few GL entry points used (GL/gl.h values)
#define GL_RGBA                 0x1908
#define GL_UNSIGNED_BYTE        0x1401
#define GL_COLOR_BUFFER_BIT     0x4000
#define GL_VENDOR               0x1F00
#define GL_RENDERER             0x1F01
#define GL_VERSION              0x1F02
#define GL_SHADING_LANGUAGE_VERSION 0x8B8C
typedef const unsigned char *(*PFN_glGetString)(unsigned);
typedef void (*PFN_glClearColor)(float, float, float, float);
typedef void (*PFN_glClear)(unsigned);
typedef void (*PFN_glFinish)(void);

static char report[8192];

/** Adds a line to the report and prints it (stdout goes to the app's log). */
static void out(const char *fmt, ...) __attribute__((format(printf, 1, 2)));
static void out(const char *fmt, ...) {
    char line[512];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(line, sizeof(line), fmt, ap);
    va_end(ap);
    size_t used = strlen(report);
    snprintf(report + used, sizeof(report) - used, "%s\n", line);
    printf("gpu: %s\n", line);
    fflush(stdout);
}

static double now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1e6;
}

/** Lists the Vulkan devices straight from the system driver (what Zink will run on). */
static void probe_vulkan(void *libvulkan) {
    PFN_vkGetInstanceProcAddr gipa = (PFN_vkGetInstanceProcAddr) dlsym(libvulkan, "vkGetInstanceProcAddr");
    if (!gipa) { out("Vulkan: vkGetInstanceProcAddr missing"); return; }
    PFN_vkCreateInstance create = (PFN_vkCreateInstance) gipa(NULL, "vkCreateInstance");
    VkApplicationInfo app = {VK_STRUCTURE_TYPE_APPLICATION_INFO, NULL, "rlm-gpu-test", 1, NULL, 0, VK_API_VERSION_1_1};
    VkInstanceCreateInfo ci = {VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO, NULL, 0, &app, 0, NULL, 0, NULL};
    VkInstance inst;
    STEP("vkCreateInstance");
    VkResult r = create(&ci, NULL, &inst);
    if (r != VK_SUCCESS) { out("Vulkan: vkCreateInstance failed (%d)", r); return; }
    PFN_vkEnumeratePhysicalDevices enumerate = (PFN_vkEnumeratePhysicalDevices) gipa(inst, "vkEnumeratePhysicalDevices");
    PFN_vkGetPhysicalDeviceProperties props = (PFN_vkGetPhysicalDeviceProperties) gipa(inst, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroy = (PFN_vkDestroyInstance) gipa(inst, "vkDestroyInstance");
    uint32_t n = 0;
    enumerate(inst, &n, NULL);
    VkPhysicalDevice devs[4];
    if (n > 4) n = 4;
    enumerate(inst, &n, devs);
    for (uint32_t i = 0; i < n; i++) {
        VkPhysicalDeviceProperties p;
        props(devs[i], &p);
        out("Vulkan device %u: %s, Vulkan %u.%u.%u, driver 0x%x, vendor 0x%x", i, p.deviceName,
            VK_VERSION_MAJOR(p.apiVersion), VK_VERSION_MINOR(p.apiVersion), VK_VERSION_PATCH(p.apiVersion),
            p.driverVersion, p.vendorID);
    }
    if (n == 0) out("Vulkan: no devices");
    destroy(inst, NULL);
}

struct osm {
    PFN_OSMesaCreateContextAttribs create;
    PFN_OSMesaMakeCurrent make_current;
    PFN_OSMesaDestroyContext destroy;
    PFN_OSMesaGetProcAddress proc;
};

/** One OpenGL context: report what it is, render a frame, time 120 frames. Returns 1 if OK. */
static int probe_context(struct osm *o, const char *label, int profile, int major, int minor) {
    int attribs[16], n = 0;
    attribs[n++] = OSMESA_FORMAT; attribs[n++] = GL_RGBA;
    attribs[n++] = OSMESA_DEPTH_BITS; attribs[n++] = 24;
    if (profile) {
        attribs[n++] = OSMESA_PROFILE; attribs[n++] = profile;
        attribs[n++] = OSMESA_CONTEXT_MAJOR_VERSION; attribs[n++] = major;
        attribs[n++] = OSMESA_CONTEXT_MINOR_VERSION; attribs[n++] = minor;
    }
    attribs[n++] = 0;

    STEP("create context: %s", label);
    OSMesaContext ctx = o->create(attribs, NULL);
    if (!ctx) { out("[%s] context: FAILED (not supported)", label); return 0; }

    const int w = 1280, h = 720;
    unsigned char *buf = calloc((size_t) w * h, 4);
    STEP("make current: %s", label);
    if (!o->make_current(ctx, buf, GL_UNSIGNED_BYTE, w, h)) {
        out("[%s] make current: FAILED", label);
        o->destroy(ctx);
        free(buf);
        return 0;
    }
    PFN_glGetString gs = (PFN_glGetString) o->proc("glGetString");
    PFN_glClearColor cc = (PFN_glClearColor) o->proc("glClearColor");
    PFN_glClear clear = (PFN_glClear) o->proc("glClear");
    PFN_glFinish finish = (PFN_glFinish) o->proc("glFinish");
    out("[%s] GL_VERSION  = %s", label, gs(GL_VERSION));
    out("[%s] GL_RENDERER = %s", label, gs(GL_RENDERER));
    out("[%s] GL_VENDOR   = %s", label, gs(GL_VENDOR));
    out("[%s] GLSL        = %s", label, gs(GL_SHADING_LANGUAGE_VERSION));

    STEP("render: %s", label);
    cc(1.0f, 0.5f, 0.0f, 1.0f);
    clear(GL_COLOR_BUFFER_BIT);
    finish();
    unsigned char *p = buf + ((size_t) (h / 2) * w + w / 2) * 4;
    int ok = p[0] > 200 && p[1] > 100 && p[1] < 160 && p[2] < 30;
    out("[%s] rendered pixel = %u,%u,%u,%u %s", label, p[0], p[1], p[2], p[3],
        ok ? "(correct: GPU frame reached memory)" : "(WRONG)");

    double t0 = now_ms();
    for (int i = 0; i < 120; i++) {
        cc((i % 2) ? 0.2f : 0.8f, 0.3f, 0.6f, 1.0f);
        clear(GL_COLOR_BUFFER_BIT);
        finish();
    }
    double ms = (now_ms() - t0) / 120;
    out("[%s] 1280x720 clear + read back: %.2f ms per frame (%.0f fps max)", label, ms, ms > 0 ? 1000 / ms : 0);

    o->make_current(NULL, NULL, 0, 0, 0);
    o->destroy(ctx);
    free(buf);
    return ok;
}

JNIEXPORT jstring JNICALL
Java_com_jblors_runelitemobile_GpuProbe_run(JNIEnv *env, jclass clazz, jstring osmesaPath) {
    report[0] = 0;
    out("Android API %d", android_get_device_api_level());

    STEP("dlopen libvulkan.so");
    void *libvulkan = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    if (!libvulkan) {
        out("Vulkan: not available (%s)", dlerror());
        return (*env)->NewStringUTF(env, report);
    }
    probe_vulkan(libvulkan);

    // Pojav's Mesa build takes the Vulkan loader handle from $VULKAN_PTR
    char ptr[32];
    snprintf(ptr, sizeof(ptr), "%" PRIxPTR, (uintptr_t) libvulkan);
    setenv("VULKAN_PTR", ptr, 1);
    setenv("GALLIUM_DRIVER", "zink", 1);
    setenv("MESA_GLSL_CACHE_DISABLE", "true", 1);

    const char *path = (*env)->GetStringUTFChars(env, osmesaPath, NULL);
    STEP("dlopen %s", path);
    void *osm = dlopen(path, RTLD_LAZY | RTLD_LOCAL);
    (*env)->ReleaseStringUTFChars(env, osmesaPath, path);
    if (!osm) {
        out("OSMesa: load failed (%s)", dlerror());
        return (*env)->NewStringUTF(env, report);
    }
    struct osm o = {
        (PFN_OSMesaCreateContextAttribs) dlsym(osm, "OSMesaCreateContextAttribs"),
        (PFN_OSMesaMakeCurrent) dlsym(osm, "OSMesaMakeCurrent"),
        (PFN_OSMesaDestroyContext) dlsym(osm, "OSMesaDestroyContext"),
        (PFN_OSMesaGetProcAddress) dlsym(osm, "OSMesaGetProcAddress"),
    };
    if (!o.create || !o.make_current || !o.destroy || !o.proc) {
        out("OSMesa: missing entry points");
        return (*env)->NewStringUTF(env, report);
    }

    int compat = probe_context(&o, "default", 0, 0, 0);
    int core33 = probe_context(&o, "core 3.3", OSMESA_CORE_PROFILE, 3, 3);
    int core45 = probe_context(&o, "core 4.5", OSMESA_CORE_PROFILE, 4, 5);

    out("RESULT: %s", core33 ? "OpenGL 3.3 core works: the GPU plugin is feasible on this phone"
            : compat ? "OpenGL works, but not 3.3 core: the GPU plugin would not start as-is"
            : "Zink did not give a working OpenGL context on this phone");
    if (core45) out("        OpenGL 4.5 core also works (RuneLite uses some 4.5 features when present)");
    return (*env)->NewStringUTF(env, report);
}
