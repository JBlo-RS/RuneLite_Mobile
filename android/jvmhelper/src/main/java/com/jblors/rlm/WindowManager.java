package com.jblors.rlm;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowEvent;

/**
 * Minimal window management for a phone screen: every resizable frame (RuneLite's client
 * window, not the fixed-size launcher splash) is maximized to fill the virtual screen.
 *
 * <p>Installed from the Android side once Caciocavallo's screen exists (AWT is up by then). It
 * must not run earlier: initializing the toolkit before the RuneLite launcher sets its
 * display system properties would make those settings ineffective.
 */
public final class WindowManager {
    private static volatile boolean installed;

    private WindowManager() {}

    public static void install() {
        if (installed) return;
        installed = true;
        EventQueue.invokeLater(() -> {
            Toolkit.getDefaultToolkit().addAWTEventListener(e -> {
                if (e.getID() == WindowEvent.WINDOW_OPENED && e.getSource() instanceof Frame) {
                    // Let the app finish its own sizing/positioning first
                    Frame f = (Frame) e.getSource();
                    EventQueue.invokeLater(() -> fill(f));
                }
            }, AWTEvent.WINDOW_EVENT_MASK);
            for (Window w : Window.getWindows()) {
                if (w instanceof Frame && w.isShowing()) fill((Frame) w);
            }
        });
    }

    private static void fill(Frame f) {
        if (!f.isResizable() || !f.isShowing()) return;
        Rectangle screen = f.getGraphicsConfiguration().getBounds();
        if ((f.getExtendedState() & Frame.MAXIMIZED_BOTH) != Frame.MAXIMIZED_BOTH) {
            f.setExtendedState(f.getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
        if (!f.getBounds().equals(screen)) {
            f.setBounds(screen);
            f.validate();
        }
    }
}
