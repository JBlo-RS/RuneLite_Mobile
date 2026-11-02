// Stand-in for libawt_xawt.so, which our headless-only JRE does not have.
//
// With java.awt.headless=false, libawt's AWT_OnLoad loads "<jre>/lib/libawt_xawt.so".
// Caciocavallo replaces the X11 toolkit, so all that has to exist are the
// native methods java.awt.* and sun.awt.UNIXToolkit register from that library.
// This library lists libawt_headless.so as a dependency (DT_NEEDED), so JNI
// lookups through its handle also find the real headless implementations
// (fonts, Java2D loops, ...). Same approach as PojavLauncher's xawt_fake.c.
//
// The app copies this file into <jre>/lib before starting the JVM.

#include <jni.h>

#define NOOP_INITIDS(cls) \
    JNIEXPORT void JNICALL Java_##cls##_initIDs(JNIEnv *env, jclass c) {}

NOOP_INITIDS(java_awt_AWTEvent)
NOOP_INITIDS(java_awt_Button)
NOOP_INITIDS(java_awt_Checkbox)
NOOP_INITIDS(java_awt_Component)
NOOP_INITIDS(java_awt_Container)
NOOP_INITIDS(java_awt_Cursor)
NOOP_INITIDS(java_awt_Dialog)
NOOP_INITIDS(java_awt_Event)
NOOP_INITIDS(java_awt_FileDialog)
NOOP_INITIDS(java_awt_Frame)
NOOP_INITIDS(java_awt_Insets)
NOOP_INITIDS(java_awt_KeyboardFocusManager)
NOOP_INITIDS(java_awt_Menu)
NOOP_INITIDS(java_awt_MenuComponent)
NOOP_INITIDS(java_awt_MenuItem)
NOOP_INITIDS(java_awt_ScrollPane)
NOOP_INITIDS(java_awt_Scrollbar)
NOOP_INITIDS(java_awt_TextArea)
NOOP_INITIDS(java_awt_TextField)
NOOP_INITIDS(java_awt_TrayIcon)
NOOP_INITIDS(java_awt_Window)
NOOP_INITIDS(java_awt_event_InputEvent)
NOOP_INITIDS(java_awt_event_KeyEvent)

JNIEXPORT void JNICALL
Java_java_awt_Cursor_finalizeImpl(JNIEnv *env, jclass c, jlong pData) {}

JNIEXPORT void JNICALL
Java_java_awt_AWTEvent_nativeSetSource(JNIEnv *env, jobject self, jobject peer) {}

JNIEXPORT void JNICALL
Java_sun_awt_SunToolkit_closeSplashScreen(JNIEnv *env, jclass c) {}

// sun.awt.UNIXToolkit: report that GTK is unavailable
JNIEXPORT jboolean JNICALL
Java_sun_awt_UNIXToolkit_check_1gtk(JNIEnv *env, jclass c, jint version) { return JNI_FALSE; }

JNIEXPORT jboolean JNICALL
Java_sun_awt_UNIXToolkit_load_1gtk(JNIEnv *env, jclass c, jint version, jboolean verbose) { return JNI_FALSE; }

JNIEXPORT jboolean JNICALL
Java_sun_awt_UNIXToolkit_unload_1gtk(JNIEnv *env, jclass c) { return JNI_FALSE; }

JNIEXPORT jint JNICALL
Java_sun_awt_UNIXToolkit_get_1gtk_1version(JNIEnv *env, jclass c) { return 0; }

JNIEXPORT jboolean JNICALL
Java_sun_awt_UNIXToolkit_gtkCheckVersionImpl(JNIEnv *env, jobject self,
                                             jint major, jint minor, jint micro) { return JNI_FALSE; }

JNIEXPORT jboolean JNICALL
Java_sun_awt_UNIXToolkit_load_1gtk_1icon(JNIEnv *env, jobject self, jstring filename) { return JNI_FALSE; }

JNIEXPORT jboolean JNICALL
Java_sun_awt_UNIXToolkit_load_1stock_1icon(JNIEnv *env, jobject self, jint widget_type,
                                           jstring stock_id, jint icon_size,
                                           jint text_direction, jstring detail) { return JNI_FALSE; }

JNIEXPORT void JNICALL
Java_sun_awt_UNIXToolkit_nativeSync(JNIEnv *env, jobject self) {}
