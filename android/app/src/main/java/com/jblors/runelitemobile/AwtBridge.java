package com.jblors.runelitemobile;

import android.graphics.Bitmap;

/** Android side of the Caciocavallo bridge (src/main/cpp/rlm_awt.c). */
final class AwtBridge {
    // CTCAndroidInput event types
    static final int EVENT_CHAR = 1000;
    static final int EVENT_CURSOR_POS = 1003;
    static final int EVENT_KEY = 1005;
    static final int EVENT_MOUSE_BUTTON = 1006;
    static final int EVENT_SCROLL = 1007;

    static {
        // Must be loaded by ART before the JVM's CTCScreen loads the same file, so the
        // library sees ART's JavaVM first.
        System.loadLibrary("pojavexec_awt");
    }

    private AwtBridge() {}

    static void init() {
        // Forces the static initializer
    }

    /** True once the JVM has created the Cacio screen. */
    static native boolean isReady();

    /** Copies the Cacio screen into an ARGB_8888 bitmap of the same size. */
    static native boolean renderFrame(Bitmap bitmap);

    static native void sendInput(int type, int a, int b, int c, int d);
}
