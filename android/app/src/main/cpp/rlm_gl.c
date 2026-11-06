// Native side of net.runelite.rlawt.AWTContext (android/jvmhelper): an OpenGL context for
// RuneLite's GPU plugin, made with Mesa's Zink driver (OpenGL on the phone's Vulkan driver)
// through OSMesa. OSMesa renders into a plain memory buffer; swapBuffers copies the finished
// frame to a front buffer, which the Android render thread copies into the virtual screen
// (blitFrame0, called from com.jblors.rlm.ScreenCompositor).
//
// Threads: create/makeCurrent/swap/destroy run on RuneLite's GL thread (the client thread),
// blitFrame0 on the Android render thread. The front buffer is shared under a mutex.

#include <android/log.h>
#include <dlfcn.h>
#include <inttypes.h>
#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define TAG "rlm-gl"
#define LOG(...) do { __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__); \
    printf("rlm-gl: " __VA_ARGS__); printf("\n"); fflush(stdout); } while (0)

// OSMesa (GL/osmesa.h)
typedef void *OSMesaContext;
typedef OSMesaContext (*PFN_OSMesaCreateContextAttribs)(const int *attribs, OSMesaContext share);
typedef unsigned char (*PFN_OSMesaMakeCurrent)(OSMesaContext, void *buf, unsigned type, int w, int h);
typedef void (*PFN_OSMesaDestroyContext)(OSMesaContext);
typedef void *(*PFN_OSMesaGetProcAddress)(const char *name);
#define OSMESA_FORMAT                0x22
#define OSMESA_DEPTH_BITS            0x30
#define OSMESA_STENCIL_BITS          0x31
#define OSMESA_PROFILE               0x33
#define OSMESA_CORE_PROFILE          0x34
#define OSMESA_COMPAT_PROFILE        0x35
#define OSMESA_CONTEXT_MAJOR_VERSION 0x36
#define OSMESA_CONTEXT_MINOR_VERSION 0x37

#define GL_RGBA          0x1908
#define GL_UNSIGNED_BYTE 0x1401
#define GL_VERSION       0x1F02
#define GL_RENDERER      0x1F01
typedef const unsigned char *(*PFN_glGetString)(unsigned);
typedef void (*PFN_glFinish)(void);
typedef void (*PFN_glGetIntegerv)(unsigned, int *);
#define GL_DRAW_BUFFER   0x0C01
#define GL_BACK          0x0405

static PFN_OSMesaCreateContextAttribs osm_create;
static PFN_OSMesaMakeCurrent osm_make_current;
static PFN_OSMesaDestroyContext osm_destroy;
static PFN_OSMesaGetProcAddress osm_proc;
static PFN_glFinish gl_finish;

struct ctx {
    OSMesaContext gl;
    // Render buffer (OSMesa draws here; RGBA bytes, bottom row first)
    uint8_t *buf;
    int w, h;
    // Last finished frame, read by blitFrame0
    pthread_mutex_t lock;
    uint8_t *front;
    int fw, fh;
    size_t front_cap;
    int has_frame;
    int draw_buffer; // GL_FRONT or GL_BACK, whichever OSMesa's default framebuffer has
};

static jstring err(JNIEnv *env, const char *fmt, const char *arg) {
    char msg[512];
    snprintf(msg, sizeof(msg), fmt, arg ? arg : "");
    LOG("%s", msg);
    return (*env)->NewStringUTF(env, msg);
}

/** Loads OSMesa once, pointing Pojav's Mesa build at the system Vulkan loader. */
static const char *load_osmesa(const char *path) {
    static int loaded;
    static char failure[512];
    if (loaded) return failure[0] ? failure : NULL;
    loaded = 1;
    void *vk = dlopen("libvulkan.so", RTLD_LAZY | RTLD_LOCAL);
    if (!vk) {
        snprintf(failure, sizeof(failure), "Vulkan not available: %s", dlerror());
        return failure;
    }
    char ptr[32];
    snprintf(ptr, sizeof(ptr), "%" PRIxPTR, (uintptr_t) vk);
    setenv("VULKAN_PTR", ptr, 1);
#ifndef RLM_GL_DESKTOP_TEST // desktop test: Mesa's default (software) driver
    setenv("GALLIUM_DRIVER", "zink", 1);
#endif
    setenv("MESA_SHADER_CACHE_DISABLE", "true", 1);
    void *lib = path ? dlopen(path, RTLD_LAZY | RTLD_LOCAL) : NULL;
    if (!lib) {
        snprintf(failure, sizeof(failure), "cannot load OSMesa %s: %s", path ? path : "(no path)", dlerror());
        return failure;
    }
    osm_create = (PFN_OSMesaCreateContextAttribs) dlsym(lib, "OSMesaCreateContextAttribs");
    osm_make_current = (PFN_OSMesaMakeCurrent) dlsym(lib, "OSMesaMakeCurrent");
    osm_destroy = (PFN_OSMesaDestroyContext) dlsym(lib, "OSMesaDestroyContext");
    osm_proc = (PFN_OSMesaGetProcAddress) dlsym(lib, "OSMesaGetProcAddress");
    if (!osm_create || !osm_make_current || !osm_destroy || !osm_proc) {
        snprintf(failure, sizeof(failure), "OSMesa is missing entry points");
        return failure;
    }
    gl_finish = (PFN_glFinish) osm_proc("glFinish");
    return NULL;
}

/** (Re)binds the context to a render buffer of w x h, reallocating it if the size changed. */
static int bind(struct ctx *c, int w, int h) {
    if (!c->buf || c->w != w || c->h != h) {
        uint8_t *nb = calloc((size_t) w * h, 4);
        if (!nb) return 0;
        free(c->buf);
        c->buf = nb;
        c->w = w;
        c->h = h;
    }
    return osm_make_current(c->gl, c->buf, GL_UNSIGNED_BYTE, w, h);
}

JNIEXPORT jlong JNICALL
Java_net_runelite_rlawt_AWTContext_create0(JNIEnv *env, jclass cls) {
    struct ctx *c = calloc(1, sizeof(*c));
    if (!c) return 0;
    pthread_mutex_init(&c->lock, NULL);
    return (jlong) (intptr_t) c;
}

JNIEXPORT void JNICALL
Java_net_runelite_rlawt_AWTContext_destroy0(JNIEnv *env, jclass cls, jlong instance) {
    struct ctx *c = (struct ctx *) (intptr_t) instance;
    if (c->gl) {
        osm_make_current(NULL, NULL, 0, 0, 0);
        osm_destroy(c->gl);
        c->gl = NULL;
    }
    free(c->buf);
    c->buf = NULL;
    // The struct itself is kept: the render thread may still be inside blitFrame0
    pthread_mutex_lock(&c->lock);
    free(c->front);
    c->front = NULL;
    c->front_cap = 0;
    c->has_frame = 0;
    pthread_mutex_unlock(&c->lock);
}

JNIEXPORT jstring JNICALL
Java_net_runelite_rlawt_AWTContext_createGLContext0(JNIEnv *env, jclass cls, jlong instance, jstring osmesaPath,
                                                    jint depth, jint stencil, jint w, jint h) {
    struct ctx *c = (struct ctx *) (intptr_t) instance;
    const char *path = osmesaPath ? (*env)->GetStringUTFChars(env, osmesaPath, NULL) : NULL;
    const char *failure = load_osmesa(path);
    if (path) (*env)->ReleaseStringUTFChars(env, osmesaPath, path);
    if (failure) return err(env, "%s", failure);
    if (c->gl) return err(env, "context already created", NULL);

    // Compatibility profile first (OpenGL 4.6 on Zink: everything the GPU plugin can use),
    // then a core 4.5 and core 3.3 context
    static const int versions[][3] = {{0, 0, 0}, {OSMESA_CORE_PROFILE, 4, 5}, {OSMESA_CORE_PROFILE, 3, 3}};
    for (size_t i = 0; i < sizeof(versions) / sizeof(versions[0]) && !c->gl; i++) {
        int a[16], n = 0;
        a[n++] = OSMESA_FORMAT; a[n++] = GL_RGBA;
        // No depth or stencil buffer on the window: RuneLite's GPU plugin draws into its own
        // framebuffers, and on Adreno Zink 23.0 aborts allocating a window depth buffer
        (void) depth;
        (void) stencil;
        a[n++] = OSMESA_DEPTH_BITS; a[n++] = 0;
        a[n++] = OSMESA_STENCIL_BITS; a[n++] = 0;
        if (versions[i][0]) {
            a[n++] = OSMESA_PROFILE; a[n++] = versions[i][0];
            a[n++] = OSMESA_CONTEXT_MAJOR_VERSION; a[n++] = versions[i][1];
            a[n++] = OSMESA_CONTEXT_MINOR_VERSION; a[n++] = versions[i][2];
        }
        a[n++] = 0;
        c->gl = osm_create(a, NULL);
    }
    if (!c->gl) return err(env, "OSMesaCreateContextAttribs failed", NULL);
    if (!bind(c, w, h)) {
        osm_destroy(c->gl);
        c->gl = NULL;
        return err(env, "OSMesaMakeCurrent failed", NULL);
    }
    PFN_glGetString gs = (PFN_glGetString) osm_proc("glGetString");
    PFN_glGetIntegerv gi = (PFN_glGetIntegerv) osm_proc("glGetIntegerv");
    c->draw_buffer = GL_BACK;
    if (gi) gi(GL_DRAW_BUFFER, &c->draw_buffer);
    if (gs) LOG("context: %s / %s, draw buffer 0x%x", gs(GL_VERSION), gs(GL_RENDERER), c->draw_buffer);
    return NULL;
}

JNIEXPORT jstring JNICALL
Java_net_runelite_rlawt_AWTContext_makeCurrent0(JNIEnv *env, jclass cls, jlong instance, jint w, jint h) {
    struct ctx *c = (struct ctx *) (intptr_t) instance;
    if (!c->gl) return err(env, "no context", NULL);
    // Keep the current size; swapBuffers follows resizes between frames
    if (!bind(c, c->buf ? c->w : w, c->buf ? c->h : h)) return err(env, "OSMesaMakeCurrent failed", NULL);
    return NULL;
}

JNIEXPORT void JNICALL
Java_net_runelite_rlawt_AWTContext_detachCurrent0(JNIEnv *env, jclass cls, jlong instance) {
    osm_make_current(NULL, NULL, 0, 0, 0);
}

JNIEXPORT jstring JNICALL
Java_net_runelite_rlawt_AWTContext_swapBuffers0(JNIEnv *env, jclass cls, jlong instance, jint w, jint h) {
    struct ctx *c = (struct ctx *) (intptr_t) instance;
    if (!c->gl || !c->buf) return err(env, "no context", NULL);
    gl_finish(); // with OSMesa the frame is in c->buf once rendering finished

    size_t bytes = (size_t) c->w * c->h * 4;
    pthread_mutex_lock(&c->lock);
    if (c->front_cap < bytes) {
        uint8_t *nf = realloc(c->front, bytes);
        if (nf) {
            c->front = nf;
            c->front_cap = bytes;
        }
    }
    if (c->front_cap >= bytes) {
        memcpy(c->front, c->buf, bytes);
        c->fw = c->w;
        c->fh = c->h;
        c->has_frame = 1;
    }
    pthread_mutex_unlock(&c->lock);

    if (w != c->w || h != c->h) {
        if (!bind(c, w, h)) return err(env, "OSMesaMakeCurrent failed after resize", NULL);
    }
    return NULL;
}

JNIEXPORT jlong JNICALL
Java_net_runelite_rlawt_AWTContext_getGLContext0(JNIEnv *env, jclass cls, jlong instance) {
    return (jlong) (intptr_t) ((struct ctx *) (intptr_t) instance)->gl;
}

JNIEXPORT jint JNICALL
Java_net_runelite_rlawt_AWTContext_getBufferMode0(JNIEnv *env, jclass cls, jlong instance) {
    return ((struct ctx *) (intptr_t) instance)->draw_buffer;
}

JNIEXPORT jlong JNICALL
Java_net_runelite_rlawt_AWTContext_getProcAddress0(JNIEnv *env, jclass cls, jstring name) {
    if (!osm_proc || !name) return 0;
    const char *n = (*env)->GetStringUTFChars(env, name, NULL);
    void *p = osm_proc(n);
    (*env)->ReleaseStringUTFChars(env, name, n);
    return (jlong) (intptr_t) p;
}

JNIEXPORT jboolean JNICALL
Java_net_runelite_rlawt_AWTContext_blitFrame0(JNIEnv *env, jclass cls, jlong instance, jintArray screen,
                                              jint sw, jint sh, jint x, jint y, jint w, jint h) {
    struct ctx *c = (struct ctx *) (intptr_t) instance;
    if ((*env)->GetArrayLength(env, screen) < sw * sh) return JNI_FALSE;
    pthread_mutex_lock(&c->lock);
    if (!c->has_frame) {
        pthread_mutex_unlock(&c->lock);
        return JNI_FALSE;
    }
    uint32_t *dst = (*env)->GetPrimitiveArrayCritical(env, screen, NULL);
    if (!dst) {
        pthread_mutex_unlock(&c->lock);
        return JNI_FALSE;
    }
    int cw = w < c->fw ? w : c->fw, ch = h < c->fh ? h : c->fh;
    int x0 = x < 0 ? -x : 0, y0 = y < 0 ? -y : 0;
    int x1 = x + cw > sw ? sw - x : cw, y1 = y + ch > sh ? sh - y : ch;
    for (int row = y0; row < y1; row++) {
        // OpenGL's bottom row comes first in the buffer
        const uint32_t *s = (const uint32_t *) c->front + (size_t) (c->fh - 1 - row) * c->fw;
        uint32_t *d = dst + (size_t) (y + row) * sw + x;
        for (int col = x0; col < x1; col++) {
            uint32_t p = s[col]; // bytes R,G,B,A -> 0xAABBGGRR
            d[col] = 0xFF000000u | ((p & 0xFFu) << 16) | (p & 0xFF00u) | ((p >> 16) & 0xFFu);
        }
    }
    (*env)->ReleasePrimitiveArrayCritical(env, screen, dst, 0);
    pthread_mutex_unlock(&c->lock);
    return JNI_TRUE;
}
