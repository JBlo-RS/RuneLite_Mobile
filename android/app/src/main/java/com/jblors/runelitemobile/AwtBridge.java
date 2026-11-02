package com.jblors.runelitemobile;

import android.graphics.Bitmap;

/** Android side of the Caciocavallo bridge (src/main/cpp/rlm_awt.c). */
final class AwtBridge {
    // CTCAndroidInput event types
    static final int EVENT_CHAR = 1000;
    static final int EVENT_CURSOR_POS = 1003;
    static final int EVENT_KEY = 1005;
    static final int EVENT_MOUSE_BUTTON = 1006;

    // Button masks for CTCRobotPeer.mousePress. This Cacio build wants the legacy
    // InputEvent.BUTTONn_MASK values: the *_DOWN_MASK ones only work for button 1 and
    // otherwise produce events with button 0 (measured; see README "Input").
    static final int BUTTON_LEFT = 16;  // InputEvent.BUTTON1_MASK
    static final int BUTTON_MIDDLE = 8; // InputEvent.BUTTON2_MASK
    static final int BUTTON_RIGHT = 4;  // InputEvent.BUTTON3_MASK (also sets isPopupTrigger)

    static {
        // Must be loaded by ART before the JVM's CTCScreen loads the same file, so the
        // library sees ART's JavaVM first.
        System.loadLibrary("pojavexec_awt");
    }

    private AwtBridge() {}

    static void init() {
        // Forces the static initializer
    }

    /** True once the JVM has loaded the bridge (from CTCScreen). */
    static native boolean isReady();

    /** Copies the Cacio screen into an ARGB_8888 bitmap of the same size. */
    static native boolean renderFrame(Bitmap bitmap);

    static native void sendInput(int type, int a, int b, int c, int d);

    /** Mouse wheel at a screen position; negative rotation = scroll up / zoom in. */
    static native void sendScroll(int x, int y, int rotation);

    // ------------------------------------------------------------ convenience

    static void mouseMove(int x, int y) {
        if (isReady()) sendInput(EVENT_CURSOR_POS, x, y, 0, 0);
    }

    static void mouseButton(int buttonMask, boolean down) {
        if (isReady()) sendInput(EVENT_MOUSE_BUTTON, buttonMask, down ? 1 : 0, 0, 0);
    }

    static void click(int x, int y, int buttonMask) {
        mouseMove(x, y);
        mouseButton(buttonMask, true);
        mouseButton(buttonMask, false);
    }

    /** Key press/release by AWT VK code; a press also produces KEY_TYPED for printable keys. */
    static void key(int awtKeyCode, boolean down) {
        if (isReady()) sendInput(EVENT_KEY, 0, awtKeyCode, down ? 1 : 0, 0);
    }

    static void tapKey(int awtKeyCode) {
        key(awtKeyCode, true);
        key(awtKeyCode, false);
    }

    /** Types one character (KEY_TYPED only), for text from the soft keyboard. */
    static void typeChar(char c) {
        if (isReady()) sendInput(EVENT_CHAR, c, 0, 0, 0);
    }

    static void wheel(int x, int y, int rotation) {
        if (isReady() && rotation != 0) sendScroll(x, y, rotation);
    }
}
