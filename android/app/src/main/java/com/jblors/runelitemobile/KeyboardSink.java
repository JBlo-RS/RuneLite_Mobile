package com.jblors.runelitemobile;

import android.content.Context;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

/**
 * Invisible view that receives soft-keyboard input and forwards it to AWT. There is no text
 * buffer: committed text becomes KEY_TYPED events, and in-progress ("composing") text is
 * diffed into backspaces and characters, so keyboards that compose whole words still work.
 */
final class KeyboardSink extends View {
    interface Listener {
        void onText(String description);
    }

    private final Listener listener;
    private String composing = "";

    KeyboardSink(Context ctx, Listener listener) {
        super(ctx);
        this.listener = listener;
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    void toggle() {
        InputMethodManager imm = getContext().getSystemService(InputMethodManager.class);
        if (isKeyboardVisible()) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
            return;
        }
        requestFocus();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController c = getWindowInsetsController();
            if (c != null) {
                c.show(android.view.WindowInsets.Type.ime());
                return;
            }
        }
        imm.showSoftInput(this, InputMethodManager.SHOW_FORCED);
    }

    private boolean isKeyboardVisible() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsets in = getRootWindowInsets();
            return in != null && in.isVisible(android.view.WindowInsets.Type.ime());
        }
        return false; // older: always "show"; the IME's own back/hide closes it
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo out) {
        out.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        out.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN
                | EditorInfo.IME_ACTION_NONE;
        composing = "";
        return new BaseInputConnection(this, false) {
            @Override
            public boolean commitText(CharSequence text, int newCursorPosition) {
                replaceComposing(text.toString());
                composing = "";
                return true;
            }

            @Override
            public boolean setComposingText(CharSequence text, int newCursorPosition) {
                replaceComposing(text.toString());
                return true;
            }

            @Override
            public boolean finishComposingText() {
                composing = "";
                return true;
            }

            @Override
            public boolean deleteSurroundingText(int beforeLength, int afterLength) {
                for (int i = 0; i < beforeLength; i++) AwtBridge.tapKey(KeyMap.VK_BACK_SPACE);
                if (beforeLength > 0) listener.onText("backspace x" + beforeLength);
                return true;
            }

            @Override
            public boolean sendKeyEvent(KeyEvent event) {
                return handleKey(event);
            }

            /**
             * The keyboard's own Paste (clipboard panel, edit panel), Select all, Copy and Cut:
             * keyboards ask the text box to do these, and there's no text box here, so paste
             * types the clipboard's text into whatever has focus in RuneLite (any field, even
             * a password field or the game's chat), and the others become Ctrl+A/C/X.
             */
            @Override
            public boolean performContextMenuAction(int id) {
                if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
                    String text = clipboardText();
                    if (text == null || text.isEmpty()) return true;
                    composing = "";
                    for (int i = 0; i < text.length(); i++) {
                        char c = text.charAt(i);
                        if (c == '\n') AwtBridge.tapKey(KeyMap.VK_ENTER);
                        else if (c != '\r') AwtBridge.typeChar(c);
                    }
                    listener.onText("pasted " + text.length() + " characters");
                    return true;
                }
                int letter = id == android.R.id.selectAll ? 'A' : id == android.R.id.copy ? 'C'
                        : id == android.R.id.cut ? 'X' : 0;
                if (letter == 0) return false;
                AwtBridge.key(KeyMap.VK_CONTROL, true);
                AwtBridge.tapKey(letter);
                AwtBridge.key(KeyMap.VK_CONTROL, false);
                return true;
            }

            @Override
            public boolean performEditorAction(int actionCode) {
                AwtBridge.tapKey(KeyMap.VK_ENTER);
                listener.onText("enter");
                return true;
            }
        };
    }

    /** Android's clipboard text (this runs on the main thread, where reading it is allowed). */
    private String clipboardText() {
        try {
            android.content.ClipboardManager cm = getContext().getSystemService(android.content.ClipboardManager.class);
            android.content.ClipData clip = cm == null ? null : cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence t = clip.getItemAt(0).coerceToText(getContext());
            return t == null ? null : t.toString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Turns the old composing text into the new one with backspaces + typed chars. */
    private void replaceComposing(String now) {
        int common = 0;
        while (common < composing.length() && common < now.length()
                && composing.charAt(common) == now.charAt(common)) common++;
        for (int i = composing.length(); i > common; i--) AwtBridge.tapKey(KeyMap.VK_BACK_SPACE);
        for (int i = common; i < now.length(); i++) {
            char c = now.charAt(i);
            if (c == '\n') AwtBridge.tapKey(KeyMap.VK_ENTER);
            else AwtBridge.typeChar(c);
        }
        if (now.length() > common) listener.onText("typed \"" + now.substring(common) + "\"");
        composing = now;
    }

    /** Key events from the IME (backspace, enter, ...) and from hardware keyboards. */
    static boolean handleKey(KeyEvent event) {
        int vk = KeyMap.toAwt(event.getKeyCode());
        if (vk == 0) {
            // Unmapped but printable (e.g. symbols on a hardware keyboard): type the char
            int ch = event.getUnicodeChar();
            if (ch != 0 && event.getAction() == KeyEvent.ACTION_DOWN) {
                AwtBridge.typeChar((char) ch);
                return true;
            }
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) AwtBridge.key(vk, true);
        else if (event.getAction() == KeyEvent.ACTION_UP) AwtBridge.key(vk, false);
        return true;
    }
}
