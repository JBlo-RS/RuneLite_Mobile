// AWT bridge between the Android runtime (ART) and the in-process OpenJDK.
//
// The file must be named libpojavexec_awt.so: Caciocavallo's CTCScreen looks for
// that name on $LD_LIBRARY_PATH and System.load()s it. The app loads it first
// through System.loadLibrary, so one copy of this library holds pointers to
// both VMs:
//   - JNI_OnLoad called by ART       -> art_vm
//   - JNI_OnLoad called by HotSpot   -> jdk_vm  (happens inside CTCScreen.<clinit>,
//     so jdk_vm != NULL means the Cacio screen exists)
//
// Frames are pulled from the Android side: CTCScreen.getCurrentScreenRGB()
// returns the screen as ARGB ints, which we swizzle straight into an Android
// ARGB_8888 Bitmap (RGBA byte order in memory).

#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>
#include <stdatomic.h>
#include <stdint.h>

#define TAG "rlm-awt"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM *art_vm;
static _Atomic(JavaVM *) jdk_vm;

static jclass cls_screen;      // CTCScreen (global ref, JDK side)
static jmethodID m_get_rgb;
static jclass cls_input;       // CTCAndroidInput (global ref, JDK side)
static jmethodID m_receive;
static jclass cls_helper;      // com.jblors.rlm.AndroidInput (our boot-classpath helper)
static jmethodID m_wheel;

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    if (art_vm == NULL) {
        art_vm = vm;
        LOGI("loaded by ART");
    } else if (vm != art_vm) {
        atomic_store(&jdk_vm, vm);
        LOGI("loaded by OpenJDK");
    }
    return JNI_VERSION_1_6;
}

// Attach the calling (Android) thread to the JDK VM as a daemon, so it never
// holds up JVM shutdown.
static JNIEnv *jdk_env(void) {
    JavaVM *vm = atomic_load(&jdk_vm);
    if (vm == NULL) return NULL;
    JNIEnv *env = NULL;
    jint r = (*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6);
    if (r == JNI_EDETACHED) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "android-bridge", NULL};
        if ((*vm)->AttachCurrentThreadAsDaemon(vm, &env, &args) != JNI_OK) return NULL;
    } else if (r != JNI_OK) {
        return NULL;
    }
    return env;
}

static jboolean check_clear(JNIEnv *env, const char *what) {
    if ((*env)->ExceptionCheck(env)) {
        LOGE("Java exception in %s", what);
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

// Like check_clear, but silent: for calls expected to fail briefly during startup
static jboolean clear_quiet(JNIEnv *env) {
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

static jclass find_global(JNIEnv *env, const char *name) {
    jclass c = (*env)->FindClass(env, name);
    if (check_clear(env, name) || c == NULL) return NULL;
    jclass g = (*env)->NewGlobalRef(env, c);
    (*env)->DeleteLocalRef(env, c);
    return g;
}

JNIEXPORT jboolean JNICALL
Java_com_jblors_runelitemobile_AwtBridge_isReady(JNIEnv *env, jclass clazz) {
    return atomic_load(&jdk_vm) != NULL;
}

// Copies the current Cacio screen into `bitmap` (must match the screen size).
// Returns false if the screen is not available yet.
JNIEXPORT jboolean JNICALL
Java_com_jblors_runelitemobile_AwtBridge_renderFrame(JNIEnv *aenv, jclass clazz, jobject bitmap) {
    JNIEnv *jenv = jdk_env();
    if (jenv == NULL) return JNI_FALSE;

    if (m_get_rgb == NULL) {
        cls_screen = find_global(jenv, "com/github/caciocavallosilano/cacio/ctc/CTCScreen");
        if (cls_screen == NULL) return JNI_FALSE;
        m_get_rgb = (*jenv)->GetStaticMethodID(jenv, cls_screen, "getCurrentScreenRGB", "()[I");
        if (check_clear(jenv, "getCurrentScreenRGB lookup") || m_get_rgb == NULL) return JNI_FALSE;
    }

    // jdk_vm is set while CTCScreen.<clinit> is still running, so until its instance exists
    // this throws NullPointerException; that just means "no frame yet".
    jintArray arr = (jintArray) (*jenv)->CallStaticObjectMethod(jenv, cls_screen, m_get_rgb);
    if (clear_quiet(jenv) || arr == NULL) return JNI_FALSE;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(aenv, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        (*jenv)->DeleteLocalRef(jenv, arr);
        return JNI_FALSE;
    }
    jsize len = (*jenv)->GetArrayLength(jenv, arr);
    if ((uint32_t) len != info.width * info.height) {
        LOGE("screen has %d px, bitmap is %ux%u", len, info.width, info.height);
        (*jenv)->DeleteLocalRef(jenv, arr);
        return JNI_FALSE;
    }

    void *dst_px;
    if (AndroidBitmap_lockPixels(aenv, bitmap, &dst_px) != ANDROID_BITMAP_RESULT_SUCCESS) {
        (*jenv)->DeleteLocalRef(jenv, arr);
        return JNI_FALSE;
    }
    const uint32_t *src = (*jenv)->GetPrimitiveArrayCritical(jenv, arr, NULL);
    if (src != NULL) {
        for (uint32_t y = 0; y < info.height; y++) {
            uint32_t *d = (uint32_t *) ((uint8_t *) dst_px + (size_t) y * info.stride);
            const uint32_t *s = src + (size_t) y * info.width;
            for (uint32_t x = 0; x < info.width; x++) {
                uint32_t p = s[x];  // 0xAARRGGBB -> memory R,G,B,A == 0xAABBGGRR; force opaque
                d[x] = 0xFF000000u | (p & 0x0000FF00u) | ((p >> 16) & 0xFFu) | ((p & 0xFFu) << 16);
            }
        }
        (*jenv)->ReleasePrimitiveArrayCritical(jenv, arr, (void *) src, JNI_ABORT);
    }
    AndroidBitmap_unlockPixels(aenv, bitmap);
    (*jenv)->DeleteLocalRef(jenv, arr);
    return src != NULL;
}

// Forwards an input event to CTCAndroidInput.receiveData(type, a, b, c, d).
JNIEXPORT void JNICALL
Java_com_jblors_runelitemobile_AwtBridge_sendInput(JNIEnv *aenv, jclass clazz,
                                                  jint type, jint a, jint b, jint c, jint d) {
    JNIEnv *jenv = jdk_env();
    if (jenv == NULL) return;
    if (m_receive == NULL) {
        cls_input = find_global(jenv, "com/github/caciocavallosilano/cacio/ctc/CTCAndroidInput");
        if (cls_input == NULL) return;
        m_receive = (*jenv)->GetStaticMethodID(jenv, cls_input, "receiveData", "(IIIII)V");
        if (check_clear(jenv, "receiveData lookup") || m_receive == NULL) return;
    }
    (*jenv)->CallStaticVoidMethod(jenv, cls_input, m_receive, type, a, b, c, d);
    check_clear(jenv, "receiveData");
}

// Mouse wheel via com.jblors.rlm.AndroidInput.wheel (Cacio's robot ignores the wheel).
JNIEXPORT void JNICALL
Java_com_jblors_runelitemobile_AwtBridge_sendScroll(JNIEnv *aenv, jclass clazz, jint x, jint y, jint rotation) {
    JNIEnv *jenv = jdk_env();
    if (jenv == NULL) return;
    if (m_wheel == NULL) {
        cls_helper = find_global(jenv, "com/jblors/rlm/AndroidInput");
        if (cls_helper == NULL) return;
        m_wheel = (*jenv)->GetStaticMethodID(jenv, cls_helper, "wheel", "(III)V");
        if (check_clear(jenv, "wheel lookup") || m_wheel == NULL) return;
    }
    (*jenv)->CallStaticVoidMethod(jenv, cls_helper, m_wheel, x, y, rotation);
    check_clear(jenv, "wheel");
}

// CTCClipboard natives. Clipboard sharing with Android comes later; until then
// the Java clipboard simply stays local to the JVM.
JNIEXPORT void JNICALL
Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_nQuerySystemClipboard(JNIEnv *env, jclass clazz) {}

JNIEXPORT void JNICALL
Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_nPutClipboardData(JNIEnv *env, jclass clazz,
                                                                          jstring data, jstring mime) {}
