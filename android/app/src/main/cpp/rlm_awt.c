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
#include <pthread.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#define TAG "rlm-awt"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static JavaVM *art_vm;
static _Atomic(JavaVM *) jdk_vm;

static jclass cls_screen;      // com.jblors.rlm.ScreenCompositor (global ref, JDK side)
static jmethodID m_get_rgb;
static jclass cls_input;       // CTCAndroidInput (global ref, JDK side)
static jmethodID m_receive;
static jclass cls_helper;      // com.jblors.rlm.AndroidInput (our boot-classpath helper)
static jmethodID m_wheel;

// Android side of the clipboard: com.jblors.runelitemobile.AwtBridge (global ref, ART side)
static jclass art_bridge;
static jmethodID m_put_clipboard, m_read_clipboard;
// CTCClipboard (global ref, JDK side)
static jclass cls_clipboard;
static jmethodID m_clipboard_received;

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    if (art_vm == NULL) {
        art_vm = vm;
        LOGI("loaded by ART");
        // Called from AwtBridge's static initializer, so the app's classes are findable here
        // (not later, from threads attached by native code)
        JNIEnv *env;
        if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) == JNI_OK) {
            jclass c = (*env)->FindClass(env, "com/jblors/runelitemobile/AwtBridge");
            if (c) {
                art_bridge = (*env)->NewGlobalRef(env, c);
                m_put_clipboard = (*env)->GetStaticMethodID(env, c, "putClipboard", "(Ljava/lang/String;)V");
                m_read_clipboard = (*env)->GetStaticMethodID(env, c, "readClipboard", "()Ljava/lang/String;");
            }
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        }
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
        // ScreenCompositor.screenRGB() = CTCScreen.getCurrentScreenRGB() with dialogs and popups
        // re-drawn on top (the game's cached Graphics paints over them in the shared buffer)
        cls_screen = find_global(jenv, "com/jblors/rlm/ScreenCompositor");
        if (cls_screen == NULL) return JNI_FALSE;
        m_get_rgb = (*jenv)->GetStaticMethodID(jenv, cls_screen, "screenRGB", "()[I");
        if (check_clear(jenv, "screenRGB lookup") || m_get_rgb == NULL) return JNI_FALSE;
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

// ScreenCompositor.layout(): {canvasX, canvasY, canvasW, canvasH, winX, winY, winW, winH} or null
static jmethodID m_layout;
JNIEXPORT jintArray JNICALL
Java_com_jblors_runelitemobile_AwtBridge_layout(JNIEnv *aenv, jclass clazz) {
    JNIEnv *jenv = jdk_env();
    if (jenv == NULL || cls_screen == NULL) return NULL; // cls_screen is set by renderFrame
    if (m_layout == NULL) {
        m_layout = (*jenv)->GetStaticMethodID(jenv, cls_screen, "layout", "()[I");
        if (check_clear(jenv, "layout lookup") || m_layout == NULL) return NULL;
    }
    jintArray src = (jintArray) (*jenv)->CallStaticObjectMethod(jenv, cls_screen, m_layout);
    if (clear_quiet(jenv) || src == NULL) return NULL;
    jint buf[8];
    jsize n = (*jenv)->GetArrayLength(jenv, src);
    if (n > 8) n = 8;
    (*jenv)->GetIntArrayRegion(jenv, src, 0, n, buf);
    (*jenv)->DeleteLocalRef(jenv, src);
    jintArray out = (*aenv)->NewIntArray(aenv, n);
    if (out != NULL) (*aenv)->SetIntArrayRegion(aenv, out, 0, n, buf);
    return out;
}

// com.jblors.rlm.Sidebar.ensureOpen()
JNIEXPORT void JNICALL
Java_com_jblors_runelitemobile_AwtBridge_ensureSidebarOpen(JNIEnv *aenv, jclass clazz) {
    JNIEnv *jenv = jdk_env();
    if (jenv == NULL) return;
    jclass sb = (*jenv)->FindClass(jenv, "com/jblors/rlm/Sidebar");
    if (check_clear(jenv, "Sidebar lookup") || sb == NULL) return;
    jmethodID m = (*jenv)->GetStaticMethodID(jenv, sb, "ensureOpen", "()V");
    if (!check_clear(jenv, "Sidebar.ensureOpen lookup") && m != NULL) {
        (*jenv)->CallStaticVoidMethod(jenv, sb, m);
        check_clear(jenv, "Sidebar.ensureOpen");
    }
    (*jenv)->DeleteLocalRef(jenv, sb);
}

// com.jblors.rlm.Sidebar.selectTab(tooltip): show a RuneLite sidebar panel by its name
JNIEXPORT void JNICALL
Java_com_jblors_runelitemobile_AwtBridge_selectSidebarTab(JNIEnv *aenv, jclass clazz, jstring tooltip) {
    JNIEnv *jenv = jdk_env();
    if (jenv == NULL) return;
    jclass sb = (*jenv)->FindClass(jenv, "com/jblors/rlm/Sidebar");
    if (check_clear(jenv, "Sidebar lookup") || sb == NULL) return;
    jmethodID sel = (*jenv)->GetStaticMethodID(jenv, sb, "selectTab", "(Ljava/lang/String;)V");
    if (!check_clear(jenv, "Sidebar.selectTab lookup") && sel != NULL) {
        const char *utf = (*aenv)->GetStringUTFChars(aenv, tooltip, NULL);
        jstring jt = (*jenv)->NewStringUTF(jenv, utf); // strings don't cross VMs; copy
        (*aenv)->ReleaseStringUTFChars(aenv, tooltip, utf);
        (*jenv)->CallStaticVoidMethod(jenv, sb, sel, jt);
        check_clear(jenv, "Sidebar.selectTab");
        (*jenv)->DeleteLocalRef(jenv, jt);
    }
    (*jenv)->DeleteLocalRef(jenv, sb);
}

// Installs com.jblors.rlm.WindowManager (maximizes resizable frames). Call once AWT is up.
JNIEXPORT void JNICALL
Java_com_jblors_runelitemobile_AwtBridge_installWindowManager(JNIEnv *aenv, jclass clazz) {
    JNIEnv *jenv = jdk_env();
    if (jenv == NULL) return;
    jclass wm = (*jenv)->FindClass(jenv, "com/jblors/rlm/WindowManager");
    if (check_clear(jenv, "WindowManager lookup") || wm == NULL) return;
    jmethodID install = (*jenv)->GetStaticMethodID(jenv, wm, "install", "()V");
    if (!check_clear(jenv, "WindowManager.install lookup") && install != NULL) {
        (*jenv)->CallStaticVoidMethod(jenv, wm, install);
        check_clear(jenv, "WindowManager.install");
    }
    jclass comp = (*jenv)->FindClass(jenv, "com/jblors/rlm/ScreenCompositor");
    if (!check_clear(jenv, "ScreenCompositor lookup") && comp != NULL) {
        jmethodID start = (*jenv)->GetStaticMethodID(jenv, comp, "start", "()V");
        if (!check_clear(jenv, "ScreenCompositor.start lookup") && start != NULL) {
            (*jenv)->CallStaticVoidMethod(jenv, comp, start);
            check_clear(jenv, "ScreenCompositor.start");
        }
        (*jenv)->DeleteLocalRef(jenv, comp);
    }
    (*jenv)->DeleteLocalRef(jenv, wm);
}

// CTCClipboard natives: the JVM's clipboard <-> Android's (text only).

/** The calling thread's ART env, attaching it if needed (*attached = 1 then). */
static JNIEnv *art_env(int *attached) {
    JNIEnv *env = NULL;
    *attached = 0;
    if (!art_vm) return NULL;
    jint r = (*art_vm)->GetEnv(art_vm, (void **) &env, JNI_VERSION_1_6);
    if (r == JNI_EDETACHED) {
        JavaVMAttachArgs args = {JNI_VERSION_1_6, "rlm-clipboard", NULL};
        if ((*art_vm)->AttachCurrentThread(art_vm, &env, &args) != JNI_OK) return NULL;
        *attached = 1;
    } else if (r != JNI_OK) {
        return NULL;
    }
    return env;
}

/** Copy: JVM text -> Android clipboard. */
JNIEXPORT void JNICALL
Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_nPutClipboardData(JNIEnv *env, jclass clazz,
                                                                          jstring data, jstring mime) {
    if (!data || !art_bridge || !m_put_clipboard) return;
    const char *utf = (*env)->GetStringUTFChars(env, data, NULL);
    if (!utf) return;
    int attached;
    JNIEnv *aenv = art_env(&attached);
    if (aenv) {
        jstring s = (*aenv)->NewStringUTF(aenv, utf);
        if (s) (*aenv)->CallStaticVoidMethod(aenv, art_bridge, m_put_clipboard, s);
        if ((*aenv)->ExceptionCheck(aenv)) (*aenv)->ExceptionClear(aenv);
        if (attached) (*art_vm)->DetachCurrentThread(art_vm);
    }
    (*env)->ReleaseStringUTFChars(env, data, utf);
}

/**
 * Paste. CTCClipboard calls nQuerySystemClipboard() holding its lock, then waits on it until
 * systemClipboardDataReceived() is called, so the answer must come from another thread, and
 * must always come (null if there is no text) or the asking thread waits forever.
 */
static void *clipboard_query_thread(void *arg) {
    char *text = NULL;
    int attached;
    JNIEnv *aenv = art_env(&attached);
    if (aenv && art_bridge && m_read_clipboard) {
        jstring s = (*aenv)->CallStaticObjectMethod(aenv, art_bridge, m_read_clipboard);
        if ((*aenv)->ExceptionCheck(aenv)) {
            (*aenv)->ExceptionClear(aenv);
            s = NULL;
        }
        if (s) {
            const char *utf = (*aenv)->GetStringUTFChars(aenv, s, NULL);
            if (utf) {
                text = strdup(utf);
                (*aenv)->ReleaseStringUTFChars(aenv, s, utf);
            }
        }
    }
    if (attached) (*art_vm)->DetachCurrentThread(art_vm);

    JavaVM *vm = atomic_load(&jdk_vm);
    JNIEnv *env = NULL;
    JavaVMAttachArgs args = {JNI_VERSION_1_6, "rlm-clipboard", NULL};
    if (vm && (*vm)->AttachCurrentThreadAsDaemon(vm, &env, &args) == JNI_OK) {
        jstring data = text ? (*env)->NewStringUTF(env, text) : NULL;
        jstring mime = text ? (*env)->NewStringUTF(env, "plain") : NULL;
        (*env)->CallStaticVoidMethod(env, cls_clipboard, m_clipboard_received, data, mime);
        check_clear(env, "systemClipboardDataReceived");
        (*vm)->DetachCurrentThread(vm);
    }
    free(text);
    return NULL;
}

JNIEXPORT void JNICALL
Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_nQuerySystemClipboard(JNIEnv *env, jclass clazz) {
    if (!cls_clipboard) {
        m_clipboard_received = (*env)->GetStaticMethodID(env, clazz, "systemClipboardDataReceived",
                                                         "(Ljava/lang/String;Ljava/lang/String;)V");
        if (!m_clipboard_received) {
            check_clear(env, "CTCClipboard lookup");
            return;
        }
        cls_clipboard = (*env)->NewGlobalRef(env, clazz);
    }
    pthread_t t;
    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
    if (pthread_create(&t, &attr, clipboard_query_thread, NULL) != 0) {
        LOGE("clipboard: cannot start thread");
    }
    pthread_attr_destroy(&attr);
}
