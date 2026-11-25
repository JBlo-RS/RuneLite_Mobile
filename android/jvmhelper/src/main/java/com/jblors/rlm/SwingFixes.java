package com.jblors.rlm;

import javax.swing.UIManager;

/**
 * Swing settings for things Cacio's toolkit can't do.
 *
 * <p>Password fields: RuneLite's look and feel (FlatLaf) shows a Caps Lock warning in a focused
 * password field, asking Toolkit.getLockingKeyState(VK_CAPS_LOCK) on every paint. Cacio's toolkit
 * doesn't implement that (Java's default throws UnsupportedOperationException), so a focused
 * password field (a plugin's "secret" setting, such as an API key) failed to paint and nothing
 * typed into it showed. The warning is turned off; there's no Caps Lock on a phone keyboard to
 * warn about anyway.
 */
public final class SwingFixes {
    private static volatile boolean installed;

    private SwingFixes() {}

    public static void install() {
        if (installed) return;
        installed = true;
        apply();
        // RuneLite installs FlatLaf after we start: set it again whenever the look and feel changes
        UIManager.addPropertyChangeListener(e -> {
            if ("lookAndFeel".equals(e.getPropertyName())) apply();
        });
    }

    private static void apply() {
        UIManager.put("PasswordField.showCapsLock", Boolean.FALSE);
    }
}
