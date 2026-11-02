package com.jblors.rlm;

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces the screen image sent to Android: Caciocavallo's screen buffer with every secondary
 * window (dialogs, popups) drawn on top again.
 *
 * <p>Why: all windows share one screen buffer, and each Graphics Cacio hands out is clipped to
 * the windows that existed when it was created. The game client keeps one Graphics for its
 * Canvas and draws every frame through it, so once a dialog opens over the game (RuneLite's
 * colour picker, for example), every game frame paints over the dialog. Re-drawing secondary
 * windows from their own content restores the right stacking in what the user sees. Input is
 * unaffected: Cacio routes clicks by its real window order.
 *
 * <p>Window images are refreshed on the event thread about 30 times a second; {@link #screenRGB}
 * is called from the Android render thread.
 */
public final class ScreenCompositor {
    private static final class Overlay {
        final int x, y, w, h;
        final int[] argb;

        Overlay(int x, int y, int w, int h, int[] argb) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.argb = argb;
        }
    }

    private static volatile List<Overlay> overlays = new ArrayList<>();
    private static volatile boolean started;
    private static Method screenRgb;

    private ScreenCompositor() {}

    /** Starts refreshing window images. Call once AWT is up. */
    public static void start() {
        if (started) return;
        started = true;
        EventQueue.invokeLater(() -> new javax.swing.Timer(33, e -> refresh()).start());
    }

    /** The screen as ARGB ints, with secondary windows on top; null if not available yet. */
    public static int[] screenRGB() throws Exception {
        if (screenRgb == null) {
            screenRgb = Class.forName("com.github.caciocavallosilano.cacio.ctc.CTCScreen")
                    .getMethod("getCurrentScreenRGB");
        }
        int[] base = (int[]) screenRgb.invoke(null); // a fresh copy, safe to modify
        if (base == null) return null;
        List<Overlay> list = overlays;
        if (list.isEmpty()) return base;
        java.awt.Dimension size = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        int sw = size.width, sh = size.height;
        if (base.length != sw * sh) return base;
        for (Overlay o : list) {
            int x0 = Math.max(0, o.x), y0 = Math.max(0, o.y);
            int x1 = Math.min(sw, o.x + o.w), y1 = Math.min(sh, o.y + o.h);
            for (int y = y0; y < y1; y++) {
                int src = (y - o.y) * o.w - o.x, dst = y * sw;
                for (int x = x0; x < x1; x++) {
                    int p = o.argb[src + x];
                    if ((p >>> 24) != 0) base[dst + x] = p;
                }
            }
        }
        return base;
    }

    /** Event thread: render each showing secondary window into an image. */
    private static void refresh() {
        List<Overlay> next = new ArrayList<>();
        for (Window w : Window.getWindows()) {
            if (!w.isShowing() || w.getWidth() <= 0 || w.getHeight() <= 0) continue;
            if (w instanceof Frame && (((Frame) w).getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) continue;
            if (w.getClass().getName().endsWith("ProxyWindow")) continue; // Cacio's internal peer window
            // Window.printAll goes through Cacio's peer and draws nothing, so paint the Swing
            // root pane (content + any look-and-feel decorations) at its offset in the window.
            if (!(w instanceof javax.swing.RootPaneContainer)) continue;
            javax.swing.JRootPane root = ((javax.swing.RootPaneContainer) w).getRootPane();
            if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) continue;
            BufferedImage img = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                g.translate(root.getX(), root.getY());
                root.printAll(g);
            } catch (RuntimeException ignored) {
                continue;
            } finally {
                g.dispose();
            }
            int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
            next.add(new Overlay(w.getX(), w.getY(), img.getWidth(), img.getHeight(), px));
        }
        overlays = next;
    }
}
