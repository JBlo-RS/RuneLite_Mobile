package com.jblors.rlm;

import java.awt.Canvas;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.RepaintManager;
import javax.swing.RootPaneContainer;

/**
 * Produces the screen image sent to Android: Caciocavallo's screen buffer with the Swing parts
 * of every window drawn on top again, plus where the game canvas is (for the mobile layout).
 *
 * <p>Why: all windows share one screen buffer, and each Graphics Cacio hands out is clipped to
 * the windows and layout that existed when it was created. The game client keeps one Graphics
 * for its Canvas and draws every frame through it, so:
 * <ul>
 *   <li>a dialog opened later over the game (RuneLite's colour picker) is painted over, and</li>
 *   <li>if the canvas moved or shrank after that Graphics was made (window maximized, sidebar
 *   opened), game frames also wipe Swing UI next to it, such as the sidebar icons.</li>
 * </ul>
 * Re-drawing windows from their own Swing content restores what the user should see. For the
 * main (maximized) window everything except the game canvas's current area is re-drawn, only
 * when Swing has repainted something and at most 10 times a second (it is the costliest part);
 * dialogs and popups are re-drawn on every tick. Input is
 * unaffected: Cacio routes clicks by its real window order.
 *
 * <p>Images are refreshed on the event thread about 30 times a second; {@link #screenRGB} and
 * {@link #layout} are called from the Android render thread.
 */
public final class ScreenCompositor {
    private static final class Overlay {
        final int x, y, w, h;
        final int[] argb;
        final List<Rectangle> holes; // screen coordinates; not drawn (the live game canvas)

        Overlay(int x, int y, int w, int h, int[] argb, List<Rectangle> holes) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.argb = argb;
            this.holes = holes;
        }
    }

    /** Notes that Swing repainted something, so the main window's UI layer needs refreshing. */
    private static final class TrackingRepaintManager extends RepaintManager {
        @Override
        public void addDirtyRegion(JComponent c, int x, int y, int w, int h) {
            super.addDirtyRegion(c, x, y, w, h);
            uiDirty = true;
        }

        @Override
        public void addDirtyRegion(Window window, int x, int y, int w, int h) {
            super.addDirtyRegion(window, x, y, w, h);
            uiDirty = true;
        }

        @Override
        public void markCompletelyDirty(JComponent c) {
            super.markCompletelyDirty(c);
            uiDirty = true;
        }
    }

    private static volatile List<Overlay> overlays = new ArrayList<>();
    private static volatile int[] layout; // see layout()
    private static volatile boolean uiDirty = true;
    private static volatile boolean started;
    private static Overlay mainLayer;
    private static List<Rectangle> mainLayerKey;
    private static long lastFullRefresh, lastPrint;
    /** Printing RuneLite's whole window costs ~4-12 ms on desktop, more on a phone: cap it. */
    private static final long MIN_PRINT_INTERVAL_MS = 100;
    private static Method screenRgb;

    private ScreenCompositor() {}

    /** Starts refreshing window images. Call once AWT is up. */
    public static void start() {
        if (started) return;
        started = true;
        EventQueue.invokeLater(() -> {
            RepaintManager.setCurrentManager(new TrackingRepaintManager());
            new javax.swing.Timer(33, e -> refresh()).start();
        });
    }

    /**
     * Where things are on the virtual screen, or null before the main window exists:
     * {canvasX, canvasY, canvasW, canvasH, windowX, windowY, windowW, windowH}. The canvas is the
     * largest heavyweight Canvas (the game) in the maximized window.
     */
    public static int[] layout() {
        return layout;
    }

    /** The screen as ARGB ints, with window UI on top; null if not available yet. */
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
        for (Overlay o : list) blit(o, base, sw, sh);
        return base;
    }

    private static void blit(Overlay o, int[] base, int sw, int sh) {
        int x0 = Math.max(0, o.x), y0 = Math.max(0, o.y);
        int x1 = Math.min(sw, o.x + o.w), y1 = Math.min(sh, o.y + o.h);
        for (int y = y0; y < y1; y++) {
            int src = (y - o.y) * o.w - o.x, dst = y * sw;
            // Split the row around holes (at most a couple; the game canvas)
            int x = x0;
            while (x < x1) {
                int end = x1;
                int skipTo = -1;
                for (Rectangle r : o.holes) {
                    if (y < r.y || y >= r.y + r.height) continue;
                    if (x >= r.x && x < r.x + r.width) {
                        skipTo = Math.max(skipTo, r.x + r.width);
                    } else if (r.x > x && r.x < end) {
                        end = r.x;
                    }
                }
                if (skipTo >= 0) {
                    x = Math.min(x1, skipTo);
                    continue;
                }
                for (; x < end; x++) {
                    int p = o.argb[src + x];
                    if ((p >>> 24) != 0) base[dst + x] = p;
                }
            }
        }
    }

    /** Event thread. */
    private static void refresh() {
        List<Overlay> next = new ArrayList<>();
        int[] newLayout = null;
        for (Window w : Window.getWindows()) {
            if (!w.isShowing() || w.getWidth() <= 0 || w.getHeight() <= 0) continue;
            if (w.getClass().getName().endsWith("ProxyWindow")) continue; // Cacio's internal peer window
            if (!(w instanceof RootPaneContainer)) continue;
            boolean main = w instanceof Frame && (((Frame) w).getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
            if (main) {
                List<Rectangle> canvases = heavyweightCanvases(w);
                Rectangle game = largest(canvases);
                if (game != null) {
                    newLayout = new int[]{game.x, game.y, game.width, game.height,
                            w.getX(), w.getY(), w.getWidth(), w.getHeight()};
                }
                Overlay layer = mainLayer(w, canvases);
                if (layer != null) next.add(0, layer); // under dialogs
            } else {
                Overlay o = print(w, new ArrayList<>());
                if (o != null) next.add(o);
            }
        }
        layout = newLayout;
        overlays = next;
    }

    /** The main window's Swing UI, re-printed only when Swing repainted or the layout changed. */
    private static Overlay mainLayer(Window w, List<Rectangle> canvases) {
        List<Rectangle> key = new ArrayList<>(canvases);
        key.add(w.getBounds());
        long now = System.currentTimeMillis();
        boolean stale = mainLayer == null || !key.equals(mainLayerKey) || now - lastFullRefresh > 2000;
        if (!uiDirty && !stale) return mainLayer;
        // Layout changes are shown at once; ordinary repaints at most every 100 ms (stays dirty)
        if (!stale && now - lastPrint < MIN_PRINT_INTERVAL_MS) return mainLayer;
        uiDirty = false;
        lastPrint = now;
        Overlay o = print(w, canvases);
        if (o != null) {
            mainLayer = o;
            mainLayerKey = key;
            lastFullRefresh = now;
        }
        return mainLayer;
    }

    /** Swing content of a window as an overlay; Window.printAll draws nothing under Cacio. */
    private static Overlay print(Window w, List<Rectangle> holes) {
        JRootPane root = ((RootPaneContainer) w).getRootPane();
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return null;
        BufferedImage img = new BufferedImage(w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.translate(root.getX(), root.getY());
            root.printAll(g);
        } catch (RuntimeException e) {
            return null;
        } finally {
            g.dispose();
        }
        int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        return new Overlay(w.getX(), w.getY(), img.getWidth(), img.getHeight(), px, holes);
    }

    /** Screen rectangles of showing heavyweight Canvases (the game) inside a window. */
    private static List<Rectangle> heavyweightCanvases(Container c) {
        List<Rectangle> out = new ArrayList<>();
        collect(c, out);
        return out;
    }

    private static void collect(Container parent, List<Rectangle> out) {
        for (Component k : parent.getComponents()) {
            if (!k.isShowing()) continue;
            if (k instanceof Canvas && k.getWidth() > 0 && k.getHeight() > 0) {
                Point p = k.getLocationOnScreen();
                out.add(new Rectangle(p.x, p.y, k.getWidth(), k.getHeight()));
            } else if (k instanceof Container) {
                collect((Container) k, out);
            }
        }
    }

    private static Rectangle largest(List<Rectangle> rs) {
        Rectangle best = null;
        for (Rectangle r : rs) {
            if (best == null || (long) r.width * r.height > (long) best.width * best.height) best = r;
        }
        return best;
    }
}
