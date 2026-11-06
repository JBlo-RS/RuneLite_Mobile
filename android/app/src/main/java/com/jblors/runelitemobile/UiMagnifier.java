package com.jblors.runelitemobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * "Interface scale": draws the game's minimap, side panel (inventory) and chatbox enlarged
 * with crisp (nearest-neighbour) pixels, anchored to their corners, while the 3D scene stays
 * at the full resolution. Taps on an enlarged panel go to the same spot on the real one.
 *
 * <p>Where the panels are comes from the Mobile Controls plugin (JVM system property
 * rlm.ui.regions, "x,y,w,h,ANCHOR;..." in game-canvas pixels). Without it nothing is enlarged.
 */
final class UiMagnifier {
    private static final class Region {
        final Rect src = new Rect();     // virtual screen pixels
        final RectF dst = new RectF();   // view pixels
    }

    private final float scale;
    private final Paint crisp = new Paint(); // no FILTER_BITMAP_FLAG: nearest neighbour
    private volatile Region[] regions = new Region[0];

    UiMagnifier(float scale) {
        this.scale = scale;
    }

    boolean enabled() {
        return scale > 1.01f;
    }

    /**
     * Recomputes the enlarged panels (render thread).
     *
     * @param spec      rlm.ui.regions, or null
     * @param canvasX   where the game canvas is on the virtual screen
     * @param game      the mapping that shows the game (virtual src -> view dst)
     */
    void update(String spec, int canvasX, int canvasY, ScreenLayout.Mapping game) {
        if (!enabled() || spec == null || spec.isEmpty() || game == null) {
            regions = new Region[0];
            return;
        }
        float sx = game.dst.width() / (float) game.src.width();
        float sy = game.dst.height() / (float) game.src.height();
        String[] parts = spec.split(";");
        Region[] out = new Region[parts.length];
        int n = 0;
        for (String part : parts) {
            String[] f = part.split(",");
            if (f.length != 5) continue;
            try {
                int x = Integer.parseInt(f[0]) + canvasX, y = Integer.parseInt(f[1]) + canvasY;
                int w = Integer.parseInt(f[2]), h = Integer.parseInt(f[3]);
                Region r = new Region();
                r.src.set(x, y, x + w, y + h);
                if (!r.src.intersect(game.src)) continue;
                // Where it is on screen now, then enlarged from its anchor corner
                float left = game.dst.left + (r.src.left - game.src.left) * sx;
                float top = game.dst.top + (r.src.top - game.src.top) * sy;
                float vw = r.src.width() * sx, vh = r.src.height() * sy;
                float k = Math.min(scale, Math.min(game.dst.width() / vw, game.dst.height() / vh));
                float ew = vw * k, eh = vh * k;
                String anchor = f[4];
                float dl = anchor.endsWith("L") ? left : left + vw - ew;
                float dt = anchor.startsWith("T") ? top : top + vh - eh;
                dl = Math.max(game.dst.left, Math.min(dl, game.dst.right - ew));
                dt = Math.max(game.dst.top, Math.min(dt, game.dst.bottom - eh));
                r.dst.set(dl, dt, dl + ew, dt + eh);
                out[n++] = r;
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        Region[] done = new Region[n];
        System.arraycopy(out, 0, done, 0, n);
        regions = done;
    }

    /** Draws the enlarged panels over the game (render thread). */
    void draw(Canvas c, Bitmap frame) {
        for (Region r : regions) {
            c.drawBitmap(frame, r.src, r.dst, crisp);
        }
    }

    /** The virtual screen point under an enlarged panel, or null if the point isn't on one. */
    int[] toScreen(float x, float y) {
        Region[] rs = regions;
        // Later regions are drawn on top
        for (int i = rs.length - 1; i >= 0; i--) {
            Region r = rs[i];
            if (r.dst.contains(x, y)) {
                float fx = (x - r.dst.left) / r.dst.width(), fy = (y - r.dst.top) / r.dst.height();
                return new int[]{
                        r.src.left + Math.min(r.src.width() - 1, (int) (fx * r.src.width())),
                        r.src.top + Math.min(r.src.height() - 1, (int) (fy * r.src.height()))};
            }
        }
        return null;
    }
}
