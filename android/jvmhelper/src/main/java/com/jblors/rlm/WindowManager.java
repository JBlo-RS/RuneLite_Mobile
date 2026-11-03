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
 * <ul>
 *   <li>Dialogs and other owned windows are kept on screen and above their owner: when the
 *   owner is activated, its showing owned windows are raised again (without taking focus),
 *   as desktop window managers do for transient windows.</li>
 *   <li>Window lifecycle events are logged (prefix "rlm-wm") so problems show up in the
 *   app's shared log.</li>
 * </ul>
 *
 * <p>Installed from the Android side once Caciocavallo's screen exists (AWT is up by then).
 * It must not run earlier: initializing the toolkit before the RuneLite launcher sets its
 * display system properties would make those settings ineffective.
 */
public final class WindowManager {
    private static final boolean DEBUG = Boolean.getBoolean("rlm.wm.debug");
    private static volatile boolean installed;
    private static Field frameState;

    private WindowManager() {}

    public static void install() {
        if (installed) return;
        installed = true;
        EventQueue.invokeLater(() -> {
            Toolkit.getDefaultToolkit().addAWTEventListener(WindowManager::onEvent,
                    AWTEvent.WINDOW_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK);
            // Windows already open when installed (the launcher splash usually is)
            for (Window w : Window.getWindows()) {
                if (!(w instanceof Frame) || !w.isShowing()) continue;
                if (maximizable((Frame) w)) fill((Frame) w);
                else center(w);
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
                    log("opened", w);
                    // Let the app finish its own sizing/positioning first
                    EventQueue.invokeLater(() -> {
                        if (w instanceof Frame && maximizable((Frame) w)) fill((Frame) w);
                        else if (w instanceof Frame) center(w); // e.g. the launcher/client splash
                        else if (w instanceof java.awt.Dialog) centerOverGame(w); // e.g. colour picker
                        else keepOnScreen(w); // popups, tooltips: stay where they opened
                        w.toFront();
                        w.requestFocus();
                    });
                }
                break;
            case WindowEvent.WINDOW_ACTIVATED:
                if (e.getSource() instanceof Window) {
                    Window w = (Window) e.getSource();
                    log("activated", w);
                    raiseOwnedWindows(w);
                }
                break;
            case WindowEvent.WINDOW_DEACTIVATED:
            case WindowEvent.WINDOW_CLOSING:
            case WindowEvent.WINDOW_CLOSED:
                if (e.getSource() instanceof Window) {
                    log(e.getID() == WindowEvent.WINDOW_DEACTIVATED ? "deactivated"
                            : e.getID() == WindowEvent.WINDOW_CLOSING ? "closing" : "closed", (Window) e.getSource());
                }
                break;
            case java.awt.event.ComponentEvent.COMPONENT_HIDDEN:
                if (e.getSource() instanceof Window) log("hidden", (Window) e.getSource());
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

    /** Re-raise the owner's showing dialogs above it without moving focus. */
    private static void raiseOwnedWindows(Window owner) {
        for (Window w : owner.getOwnedWindows()) {
            if (!w.isShowing()) continue;
            boolean auto = w.isAutoRequestFocus();
            w.setAutoRequestFocus(false);
            w.toFront();
            w.setAutoRequestFocus(auto);
            log("raised above owner", w);
        }
    }

    /**
     * Centre a dialog over the game canvas, which is the part of the screen the mobile layout
     * shows, so its title bar and close button are visible. Falls back to the whole screen.
     */
    private static void centerOverGame(Window w) {
        if (!w.isShowing()) return;
        int[] l = ScreenCompositor.layout();
        Rectangle area = l != null ? new Rectangle(l[0], l[1], l[2], l[3]) : w.getGraphicsConfiguration().getBounds();
        int x = area.x + (area.width - w.getWidth()) / 2, y = area.y + (area.height - w.getHeight()) / 2;
        // Keep the top-left (title bar, close button) inside the visible area even if it's big
        x = Math.max(area.x, x);
        y = Math.max(area.y, y);
        if (x != w.getX() || y != w.getY()) {
            w.setLocation(x, y);
            log("centred over game", w);
        }
    }

    /** Centre a window on the virtual screen. */
    private static void center(Window w) {
        if (!w.isShowing()) return;
        Rectangle screen = w.getGraphicsConfiguration().getBounds();
        int x = screen.x + (screen.width - w.getWidth()) / 2, y = screen.y + (screen.height - w.getHeight()) / 2;
        if (x != w.getX() || y != w.getY()) {
            w.setLocation(Math.max(screen.x, x), Math.max(screen.y, y));
            log("centred", w);
        }
    }

    /** Move a (non-maximized) window so it is entirely on the virtual screen. */
    private static void keepOnScreen(Window w) {
        if (!w.isShowing()) return;
        Rectangle screen = w.getGraphicsConfiguration().getBounds();
        Rectangle b = w.getBounds();
        int x = Math.max(screen.x, Math.min(b.x, screen.x + screen.width - b.width));
        int y = Math.max(screen.y, Math.min(b.y, screen.y + screen.height - b.height));
        if (x != b.x || y != b.y) {
            w.setLocation(x, y);
            log("moved on screen", w);
        }
    }

    private static void log(String what, Window w) {
        String title = w instanceof Frame ? ((Frame) w).getTitle()
                : w instanceof java.awt.Dialog ? ((java.awt.Dialog) w).getTitle() : w.getClass().getSimpleName();
        System.out.println("rlm-wm: " + what + " " + w.getClass().getSimpleName() + " '" + title + "' "
                + w.getBounds().x + "," + w.getBounds().y + " " + w.getWidth() + "x" + w.getHeight());
    }

    /**
     * Frames to fill the screen: resizable ones, except splash screens. RuneLite's launcher and
     * client splash screens (net.runelite.*.SplashScreen) are resizable 200x274 frames;
     * maximizing them leaves their content in a corner of a full-screen window.
     */
    private static boolean maximizable(Frame f) {
        return f.isResizable() && !f.getClass().getSimpleName().endsWith("SplashScreen");
    }

    private static void fill(Frame f) {
        if (!maximizable(f) || !f.isShowing()) return;
        Rectangle screen = screenOf(f);
        if (!isMaximized(f)) {
            f.setExtendedState(f.getExtendedState() | Frame.MAXIMIZED_BOTH);
            if (!isMaximized(f)) forceMaximizedState(f);
        }
        if (!f.getBounds().equals(screen)) {
            if (DEBUG) System.out.println("rlm-wm: refit '" + f.getTitle() + "' " + f.getBounds() + " -> " + screen);
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
