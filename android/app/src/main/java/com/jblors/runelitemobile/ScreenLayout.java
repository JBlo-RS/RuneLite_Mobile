package com.jblors.runelitemobile;

import android.graphics.Rect;

/**
 * How the virtual AWT screen is shown on the phone, as a stack of source -> destination
 * mappings (drawn bottom to top; touches go to the topmost one under the finger).
 *
 * <ul>
 *   <li>Classic: the whole virtual screen, fitted to the view.</li>
 *   <li>Mobile: just the game canvas, scaled to fill the view (RuneLite's title bar and sidebar
 *   are off screen); when the panel is open, RuneLite's sidebar slides in from the right at the
 *   same scale.</li>
 * </ul>
 * Updated by the render thread, read by the UI thread for touch mapping.
 */
final class ScreenLayout {
    static final class Mapping {
        final Rect src = new Rect(), dst = new Rect();
    }

    /** Sidebar narrower than this (virtual px) means RuneLite's sidebar is closed. */
    static final int MIN_PANEL_WIDTH = 100;

    private final boolean mobile;
    private volatile Mapping[] mappings = new Mapping[0];
    private volatile int panelWidthPx; // on the phone, when open
    private volatile int panelSrcWidth;

    /** 0 = closed, 1 = open; animated by the render thread toward the target. */
    private float panelProgress;
    private volatile boolean panelOpen;
    /** Vertical margin for the panel so its icons clear rounded screen corners (px). */
    private volatile int cornerInset;

    ScreenLayout(boolean mobile) {
        this.mobile = mobile;
    }

    boolean isMobile() {
        return mobile;
    }

    boolean isPanelOpen() {
        return panelOpen;
    }

    void setCornerInset(int px) {
        cornerInset = Math.max(0, px);
    }

    void setPanelOpen(boolean open) {
        panelOpen = open;
    }

    /** Width of RuneLite's sidebar region on the virtual screen (0 if unknown). */
    int panelSourceWidth() {
        return panelSrcWidth;
    }

    int panelWidthPx() {
        return panelWidthPx;
    }

    Mapping[] mappings() {
        return mappings;
    }

    /**
     * Recompute for the current surface size; {@code awt} is AwtBridge.layout() or null.
     * @return true while the panel animation is running
     */
    boolean update(int surfaceW, int surfaceH, int screenW, int screenH, int[] awt, float dtSeconds) {
        if (!mobile || awt == null || awt.length < 8 || awt[2] < 50 || awt[3] < 50) {
            Mapping m = new Mapping();
            m.src.set(0, 0, screenW, screenH);
            fit(m.dst, screenW, screenH, surfaceW, surfaceH);
            mappings = new Mapping[]{m};
            panelSrcWidth = 0;
            return false;
        }
        int cx = awt[0], cy = awt[1], cw = awt[2], ch = awt[3];
        int winRight = awt[4] + awt[6];

        Mapping game = new Mapping();
        game.src.set(cx, cy, cx + cw, cy + ch);
        fit(game.dst, cw, ch, surfaceW, surfaceH);

        // RuneLite's sidebar: everything right of the game canvas, over the canvas's height
        int pw = Math.max(0, winRight - (cx + cw));
        panelSrcWidth = pw;
        int inset = Math.min(cornerInset, surfaceH / 6);
        int panelH = surfaceH - 2 * inset;
        float scale = panelH / (float) ch;
        panelWidthPx = Math.min(surfaceW, Math.round(pw * scale));

        float target = panelOpen ? 1f : 0f;
        float step = dtSeconds / 0.18f; // ~180 ms slide
        panelProgress = target > panelProgress ? Math.min(target, panelProgress + step)
                : Math.max(target, panelProgress - step);

        if (panelProgress <= 0f || pw <= 0) {
            mappings = new Mapping[]{game};
        } else {
            Mapping panel = new Mapping();
            panel.src.set(cx + cw, cy, winRight, cy + ch);
            int left = surfaceW - Math.round(panelWidthPx * panelProgress);
            panel.dst.set(left, inset, left + panelWidthPx, inset + panelH);
            mappings = new Mapping[]{game, panel};
        }
        return panelProgress != target;
    }

    /** Left edge of the panel on the phone (surfaceW when closed). */
    int panelLeft(int surfaceW) {
        Mapping[] ms = mappings;
        return ms.length > 1 ? ms[ms.length - 1].dst.left : surfaceW;
    }

    /** Virtual-screen point for a view point, or null if outside every mapping. */
    int[] toScreen(float x, float y) {
        Mapping[] ms = mappings;
        for (int i = ms.length - 1; i >= 0; i--) {
            Mapping m = ms[i];
            if (x < m.dst.left || x >= m.dst.right || y < m.dst.top || y >= m.dst.bottom) continue;
            int sx = m.src.left + Math.round((x - m.dst.left) * m.src.width() / (float) m.dst.width());
            int sy = m.src.top + Math.round((y - m.dst.top) * m.src.height() / (float) m.dst.height());
            return new int[]{Math.min(sx, m.src.right - 1), Math.min(sy, m.src.bottom - 1)};
        }
        // Outside (letterbox): clamp into the bottom mapping so drags past the edge still work
        if (ms.length == 0) return null;
        Mapping m = ms[0];
        float cx = Math.max(m.dst.left, Math.min(x, m.dst.right - 1));
        float cy = Math.max(m.dst.top, Math.min(y, m.dst.bottom - 1));
        return new int[]{
                m.src.left + Math.round((cx - m.dst.left) * m.src.width() / (float) m.dst.width()),
                m.src.top + Math.round((cy - m.dst.top) * m.src.height() / (float) m.dst.height())};
    }

    /** Aspect-preserving fit of a w x h source, centered in the surface. */
    private static void fit(Rect dst, int w, int h, int surfaceW, int surfaceH) {
        float s = Math.min(surfaceW / (float) w, surfaceH / (float) h);
        int dw = Math.round(w * s), dh = Math.round(h * s);
        int l = (surfaceW - dw) / 2, t = (surfaceH - dh) / 2;
        dst.set(l, t, l + dw, t + dh);
    }
}
