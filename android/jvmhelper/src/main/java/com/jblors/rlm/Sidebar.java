package com.jblors.rlm;

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import javax.swing.JTabbedPane;

/**
 * RuneLite's sidebar is a JTabbedPane with tabs on the right (ClientUI); each tab's tooltip is
 * its navigation button's name ("Configuration" for the wrench). Selecting a tab fires
 * RuneLite's own change listener, which activates that panel just like a click.
 */
public final class Sidebar {
    private static volatile long lastOpenAttempt;

    private Sidebar() {}

    /**
     * Opens RuneLite's sidebar if it is closed. The mobile layout keeps it open (off screen,
     * shown by the slide-in panel) so the game canvas keeps the phone's shape; a closed sidebar
     * makes the canvas wider and the phone shows black bars. Uses RuneLite's own sidebar hotkey
     * (default Ctrl+F11) so ClientUI does its usual work; RuneLite only handles it while its
     * window is focused. Retries at most every 2 s.
     */
    public static void ensureOpen() {
        EventQueue.invokeLater(() -> {
            JTabbedPane tabs = find();
            if (tabs == null || tabs.isVisible()) return;
            long now = System.currentTimeMillis();
            if (now - lastOpenAttempt < 2000) return;
            lastOpenAttempt = now;
            java.awt.Window w = javax.swing.SwingUtilities.getWindowAncestor(tabs);
            if (w == null) return;
            if (!w.isFocused()) w.requestFocus();
            Component target = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (target == null) target = w;
            java.awt.EventQueue q = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue();
            int ctrl = java.awt.event.InputEvent.CTRL_DOWN_MASK;
            q.postEvent(new java.awt.event.KeyEvent(target, java.awt.event.KeyEvent.KEY_PRESSED, now, ctrl,
                    java.awt.event.KeyEvent.VK_F11, java.awt.event.KeyEvent.CHAR_UNDEFINED));
            q.postEvent(new java.awt.event.KeyEvent(target, java.awt.event.KeyEvent.KEY_RELEASED, now, ctrl,
                    java.awt.event.KeyEvent.VK_F11, java.awt.event.KeyEvent.CHAR_UNDEFINED));
            System.out.println("rlm-sidebar: was closed, sent the sidebar hotkey");
        });
    }

    /** Selects the sidebar tab with this tooltip (event thread); no-op if not found. */
    public static void selectTab(String tooltip) {
        EventQueue.invokeLater(() -> {
            JTabbedPane tabs = find();
            if (tabs == null) return;
            for (int i = 0; i < tabs.getTabCount(); i++) {
                if (tooltip.equals(tabs.getToolTipTextAt(i))) {
                    if (tabs.getSelectedIndex() != i) tabs.setSelectedIndex(i);
                    System.out.println("rlm-sidebar: selected '" + tooltip + "'");
                    return;
                }
            }
            System.out.println("rlm-sidebar: no tab '" + tooltip + "'");
        });
    }

    private static JTabbedPane find() {
        for (Frame f : Frame.getFrames()) {
            if (!f.isShowing()) continue;
            JTabbedPane t = find(f);
            if (t != null) return t;
        }
        return null;
    }

    private static JTabbedPane find(Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof JTabbedPane && ((JTabbedPane) k).getTabPlacement() == JTabbedPane.RIGHT) {
                return (JTabbedPane) k;
            }
            if (k instanceof Container) {
                JTabbedPane t = find((Container) k);
                if (t != null) return t;
            }
        }
        return null;
    }
}
