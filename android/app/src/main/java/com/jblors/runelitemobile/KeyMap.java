package com.jblors.runelitemobile;

import android.util.SparseIntArray;
import android.view.KeyEvent;

/** Android KeyEvent key codes -> java.awt.event.KeyEvent VK codes. */
final class KeyMap {
    static final int VK_BACK_SPACE = 8;
    static final int VK_TAB = 9;
    static final int VK_ENTER = 10;
    static final int VK_SHIFT = 16;
    static final int VK_CONTROL = 17;
    static final int VK_ALT = 18;
    static final int VK_ESCAPE = 27;
    static final int VK_SPACE = 32;
    static final int VK_LEFT = 37;
    static final int VK_UP = 38;
    static final int VK_RIGHT = 39;
    static final int VK_DOWN = 40;
    static final int VK_F1 = 112;

    private static final SparseIntArray MAP = new SparseIntArray();

    static {
        for (int i = 0; i < 26; i++) MAP.put(KeyEvent.KEYCODE_A + i, 'A' + i);   // VK_A..VK_Z
        for (int i = 0; i < 10; i++) MAP.put(KeyEvent.KEYCODE_0 + i, '0' + i);   // VK_0..VK_9
        for (int i = 0; i < 12; i++) MAP.put(KeyEvent.KEYCODE_F1 + i, VK_F1 + i); // VK_F1..VK_F12
        MAP.put(KeyEvent.KEYCODE_DEL, VK_BACK_SPACE);
        MAP.put(KeyEvent.KEYCODE_FORWARD_DEL, 127);   // VK_DELETE
        MAP.put(KeyEvent.KEYCODE_TAB, VK_TAB);
        MAP.put(KeyEvent.KEYCODE_ENTER, VK_ENTER);
        MAP.put(KeyEvent.KEYCODE_NUMPAD_ENTER, VK_ENTER);
        MAP.put(KeyEvent.KEYCODE_SHIFT_LEFT, VK_SHIFT);
        MAP.put(KeyEvent.KEYCODE_SHIFT_RIGHT, VK_SHIFT);
        MAP.put(KeyEvent.KEYCODE_CTRL_LEFT, VK_CONTROL);
        MAP.put(KeyEvent.KEYCODE_CTRL_RIGHT, VK_CONTROL);
        MAP.put(KeyEvent.KEYCODE_ALT_LEFT, VK_ALT);
        MAP.put(KeyEvent.KEYCODE_ALT_RIGHT, VK_ALT);
        MAP.put(KeyEvent.KEYCODE_ESCAPE, VK_ESCAPE);
        MAP.put(KeyEvent.KEYCODE_SPACE, VK_SPACE);
        MAP.put(KeyEvent.KEYCODE_PAGE_UP, 33);
        MAP.put(KeyEvent.KEYCODE_PAGE_DOWN, 34);
        MAP.put(KeyEvent.KEYCODE_MOVE_END, 35);
        MAP.put(KeyEvent.KEYCODE_MOVE_HOME, 36);
        MAP.put(KeyEvent.KEYCODE_DPAD_LEFT, VK_LEFT);
        MAP.put(KeyEvent.KEYCODE_DPAD_UP, VK_UP);
        MAP.put(KeyEvent.KEYCODE_DPAD_RIGHT, VK_RIGHT);
        MAP.put(KeyEvent.KEYCODE_DPAD_DOWN, VK_DOWN);
        MAP.put(KeyEvent.KEYCODE_INSERT, 155);
        MAP.put(KeyEvent.KEYCODE_COMMA, 44);
        MAP.put(KeyEvent.KEYCODE_MINUS, 45);
        MAP.put(KeyEvent.KEYCODE_PERIOD, 46);
        MAP.put(KeyEvent.KEYCODE_SLASH, 47);
        MAP.put(KeyEvent.KEYCODE_SEMICOLON, 59);
        MAP.put(KeyEvent.KEYCODE_EQUALS, 61);
        MAP.put(KeyEvent.KEYCODE_LEFT_BRACKET, 91);
        MAP.put(KeyEvent.KEYCODE_BACKSLASH, 92);
        MAP.put(KeyEvent.KEYCODE_RIGHT_BRACKET, 93);
        MAP.put(KeyEvent.KEYCODE_GRAVE, 192);
        MAP.put(KeyEvent.KEYCODE_APOSTROPHE, 222);
    }

    private KeyMap() {}

    /** AWT VK code for an Android key code, or 0 if unmapped. */
    static int toAwt(int androidKeyCode) {
        return MAP.get(androidKeyCode, 0);
    }
}
