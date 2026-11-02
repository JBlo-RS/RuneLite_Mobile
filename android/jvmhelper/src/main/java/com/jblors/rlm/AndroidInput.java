package com.jblors.rlm;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.SwingUtilities;

/**
 * Input that Caciocavallo's robot cannot deliver, called from the Android side through JNI
 * (see app/src/main/cpp/rlm_awt.c). Lives on the boot class path next to the Cacio jars.
 */
public final class AndroidInput {
    private AndroidInput() {}

    /**
     * Mouse wheel at screen position (x, y). CTCRobotPeer.mouseWheel is a no-op, so build the
     * MouseWheelEvent ourselves and dispatch it to the deepest component under the point; AWT
     * bubbles it up to the nearest ancestor with a wheel listener (e.g. a JScrollPane).
     *
     * @param rotation wheel clicks; negative = away from the user (scroll up / zoom in)
     */
    public static void wheel(int x, int y, int rotation) {
        EventQueue.invokeLater(() -> {
            Window target = windowAt(x, y);
            if (target == null) return;
            int wx = x - target.getX(), wy = y - target.getY();
            Component c = SwingUtilities.getDeepestComponentAt(target, wx, wy);
            if (c == null) c = target;
            Point p = SwingUtilities.convertPoint(target, wx, wy, c);
            c.dispatchEvent(new MouseWheelEvent(c, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(),
                    0, p.x, p.y, x, y, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, rotation));
        });
    }

    private static Window windowAt(int x, int y) {
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active != null && active.isShowing() && active.getBounds().contains(x, y)) return active;
        Window found = null;
        for (Window w : Window.getWindows()) {
            if (w.isShowing() && w.getBounds().contains(x, y)) found = w; // later = opened later
        }
        return found;
    }
}
