package com.jblors.rlm;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import javax.swing.JComponent;

/**
 * Minimal window management for a phone screen, filling in what a desktop window system
 * would do and Caciocavallo does not:
 *
 * <ul>
 *   <li>Every resizable frame (RuneLite's client window, not the fixed-size launcher splash)
 *   fills the virtual screen and reports itself as maximized. Cacio doesn't support the
 *   MAXIMIZED_BOTH state, and RuneLite widens a non-maximized window when its sidebar
 *   opens, pushing the sidebar off screen. A maximized frame that something moves or resizes
 *   is put back (checked on a timer: Cacio does not post component moved/resized events).</li>
 *   <li>Click-to-focus: a mouse press on a heavyweight component (such as the game's Canvas)
 *   gives it keyboard focus, as native toolkits do. Without it nothing owns focus and key
 *   input never reaches the game. (Swing components already request focus themselves.)</li>
 * </ul>
 *
 * <p>Installed from the Android side once Caciocavallo's screen exists (AWT is up by then).
 * It must not run earlier: initializing the toolkit before the RuneLite launcher sets its
 * display system properties would make those settings ineffective.
 */
public final class WindowManager {
    private static volatile boolean installed;
    private static Field frameState;

    private WindowManager() {}

    public static void install() {
        if (installed) return;
        installed = true;
        EventQueue.invokeLater(() -> {
            Toolkit.getDefaultToolkit().addAWTEventListener(WindowManager::onEvent,
                    AWTEvent.WINDOW_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
            for (Window w : Window.getWindows()) {
                if (w instanceof Frame && w.isShowing()) fill((Frame) w);
            }
            new javax.swing.Timer(500, e -> {
                for (Window w : Window.getWindows()) {
                    if (w instanceof Frame && w.isShowing() && isMaximized((Frame) w)
                            && !w.getBounds().equals(screenOf((Frame) w))) {
                        fill((Frame) w);
                    }
                }
            }).start();
        });
    }

    private static void onEvent(AWTEvent e) {
        switch (e.getID()) {
            case WindowEvent.WINDOW_OPENED:
                if (e.getSource() instanceof Window) {
                    Window w = (Window) e.getSource();
                    // Let the app finish its own sizing/positioning first
                    EventQueue.invokeLater(() -> {
                        if (w instanceof Frame) fill((Frame) w);
                        w.toFront();
                        w.requestFocus();
                    });
                }
                break;
            case MouseEvent.MOUSE_PRESSED:
                Component c = ((MouseEvent) e).getComponent();
                if (c != null && !(c instanceof JComponent) && !(c instanceof Window)
                        && c.isFocusable() && c.isShowing()
                        && KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() != c) {
                    c.requestFocus();
                }
                break;
            default:
                break;
        }
    }

    private static void fill(Frame f) {
        if (!f.isResizable() || !f.isShowing()) return;
        Rectangle screen = screenOf(f);
        if (!isMaximized(f)) {
            f.setExtendedState(f.getExtendedState() | Frame.MAXIMIZED_BOTH);
            if (!isMaximized(f)) forceMaximizedState(f);
        }
        if (!f.getBounds().equals(screen)) {
            f.setBounds(screen);
            f.validate();
        }
    }

    private static Rectangle screenOf(Frame f) {
        return f.getGraphicsConfiguration().getBounds();
    }

    private static boolean isMaximized(Frame f) {
        return (f.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }

    /** Cacio rejects MAXIMIZED_BOTH, so record it in Frame's own state field. */
    private static void forceMaximizedState(Frame f) {
        try {
            if (frameState == null) {
                frameState = Frame.class.getDeclaredField("state");
                frameState.setAccessible(true); // needs --add-opens java.desktop/java.awt
            }
            int old = frameState.getInt(f);
            frameState.setInt(f, old | Frame.MAXIMIZED_BOTH);
            // Same notification setExtendedState would have sent
            Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new WindowEvent(
                    f, WindowEvent.WINDOW_STATE_CHANGED, old, old | Frame.MAXIMIZED_BOTH));
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.err.println("rlm: cannot mark frame maximized: " + e);
        }
    }
}
