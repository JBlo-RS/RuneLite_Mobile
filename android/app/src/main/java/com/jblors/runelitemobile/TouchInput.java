package com.jblors.runelitemobile;

import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

/**
 * Touch (and physical mouse) -> AWT mouse events, tuned for RuneLite/OSRS:
 * <ul>
 *   <li>tap: left click</li>
 *   <li>long press: right click (OSRS context menu), with a haptic tick</li>
 *   <li>one-finger drag: left-button drag (inventory items, scrollbars)</li>
 *   <li>two-finger drag: middle-button drag (OSRS camera rotation)</li>
 *   <li>pinch: mouse wheel (zoom)</li>
 * </ul>
 */
final class TouchInput implements View.OnTouchListener, View.OnGenericMotionListener {
    /** Maps view coordinates to the virtual AWT screen. */
    interface Mapper {
        /** Virtual-screen point {x, y} for a view point, or null if it maps nowhere. */
        int[] toScreen(float viewX, float viewY);
    }

    interface Listener {
        void onGesture(String description);
    }

    private static final long LONG_PRESS_MS = 400;

    private final Mapper map;
    private final Listener listener;
    private final float slop;        // px of movement before a touch becomes a drag
    private final float pinchStep;   // px of span change per wheel click
    private final Handler handler = new Handler(Looper.getMainLooper());

    // single-finger state
    private float downX, downY;
    private boolean dragging, longPressed;
    private View view;
    private final Runnable longPress = this::fireLongPress;

    // two-finger state
    private boolean multi, rotating;
    private float startSpan, lastSpanStep, lastCx, lastCy, startCx, startCy;

    // physical mouse
    private int mouseButtons;

    /** Which button a tap clicks (left normally; right in the R button's right-click mode). */
    private java.util.function.IntSupplier tapButton = () -> AwtBridge.BUTTON_LEFT;

    void setTapButton(java.util.function.IntSupplier tapButton) {
        this.tapButton = tapButton;
    }

    TouchInput(float density, Mapper map, Listener listener) {
        this.map = map;
        this.listener = listener;
        this.slop = 10 * density;
        this.pinchStep = 40 * density;
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        view = v;
        if (e.isFromSource(InputDevice.SOURCE_MOUSE)) return onMouse(e);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = longPressed = multi = rotating = false;
                move(downX, downY);
                handler.postDelayed(longPress, LONG_PRESS_MS);
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() == 2) startMulti(e);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (multi) {
                    if (e.getPointerCount() >= 2) moveMulti(e);
                    return true;
                }
                if (longPressed) return true;
                if (!dragging && Math.hypot(e.getX() - downX, e.getY() - downY) > slop) {
                    handler.removeCallbacks(longPress);
                    dragging = true;
                    move(downX, downY);
                    AwtBridge.mouseButton(AwtBridge.BUTTON_LEFT, true);
                    listener.onGesture("drag start");
                }
                if (dragging) move(e.getX(), e.getY());
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                if (multi && e.getPointerCount() == 2) endMulti();
                return true;

            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPress);
                if (multi) {
                    endMulti();
                } else if (dragging) {
                    move(e.getX(), e.getY());
                    AwtBridge.mouseButton(AwtBridge.BUTTON_LEFT, false);
                    listener.onGesture("drag end");
                } else if (!longPressed) {
                    int button = tapButton.getAsInt();
                    click(downX, downY, button);
                    listener.onGesture(button == AwtBridge.BUTTON_RIGHT ? "tap (right click)" : "tap");
                }
                multi = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPress);
                if (dragging) AwtBridge.mouseButton(AwtBridge.BUTTON_LEFT, false);
                if (rotating) AwtBridge.mouseButton(AwtBridge.BUTTON_MIDDLE, false);
                dragging = rotating = multi = false;
                return true;
        }
        return false;
    }

    private void fireLongPress() {
        if (dragging || multi) return;
        longPressed = true;
        click(downX, downY, AwtBridge.BUTTON_RIGHT);
        if (view != null) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        listener.onGesture("long press (right click)");
    }

    private void startMulti(MotionEvent e) {
        handler.removeCallbacks(longPress);
        if (dragging) {
            AwtBridge.mouseButton(AwtBridge.BUTTON_LEFT, false);
            dragging = false;
        }
        multi = true;
        rotating = false;
        startSpan = lastSpanStep = span(e);
        startCx = lastCx = (e.getX(0) + e.getX(1)) / 2;
        startCy = lastCy = (e.getY(0) + e.getY(1)) / 2;
    }

    private void moveMulti(MotionEvent e) {
        float cx = (e.getX(0) + e.getX(1)) / 2, cy = (e.getY(0) + e.getY(1)) / 2;
        float s = span(e);

        // Pinch -> wheel: one click per pinchStep of span change. Spreading = zoom in = negative.
        int steps = (int) ((s - lastSpanStep) / pinchStep);
        if (steps != 0) {
            wheel(cx, cy, -steps);
            lastSpanStep += steps * pinchStep;
            listener.onGesture("pinch (wheel " + (-steps) + ")");
        }

        // Two-finger pan -> middle-button drag (camera); only when not mostly pinching
        boolean pinching = Math.abs(s - startSpan) > Math.hypot(cx - startCx, cy - startCy);
        if (!rotating && !pinching && Math.hypot(cx - startCx, cy - startCy) > slop) {
            rotating = true;
            move(startCx, startCy);
            AwtBridge.mouseButton(AwtBridge.BUTTON_MIDDLE, true);
            listener.onGesture("two-finger drag (middle button)");
        }
        if (rotating && (cx != lastCx || cy != lastCy)) move(cx, cy);
        lastCx = cx;
        lastCy = cy;
    }

    private void endMulti() {
        if (rotating) {
            AwtBridge.mouseButton(AwtBridge.BUTTON_MIDDLE, false);
            listener.onGesture("two-finger drag end");
        }
        rotating = false;
        // stay in multi mode until all fingers lift, so the last finger doesn't click
    }

    private static float span(MotionEvent e) {
        return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
    }

    // ------------------------------------------------------------ physical mouse

    private boolean onMouse(MotionEvent e) {
        move(e.getX(), e.getY());
        int now = e.getButtonState();
        updateButton(now, MotionEvent.BUTTON_PRIMARY, AwtBridge.BUTTON_LEFT);
        updateButton(now, MotionEvent.BUTTON_SECONDARY, AwtBridge.BUTTON_RIGHT);
        updateButton(now, MotionEvent.BUTTON_TERTIARY, AwtBridge.BUTTON_MIDDLE);
        mouseButtons = now;
        return true;
    }

    private void updateButton(int now, int androidButton, int awtMask) {
        boolean was = (mouseButtons & androidButton) != 0, is = (now & androidButton) != 0;
        if (was != is) AwtBridge.mouseButton(awtMask, is);
    }

    @Override
    public boolean onGenericMotion(View v, MotionEvent e) {
        if (!e.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_HOVER_MOVE:
                move(e.getX(), e.getY());
                return true;
            case MotionEvent.ACTION_SCROLL:
                float vs = e.getAxisValue(MotionEvent.AXIS_VSCROLL);
                wheel(e.getX(), e.getY(), vs > 0 ? -1 : vs < 0 ? 1 : 0);
                return true;
            case MotionEvent.ACTION_BUTTON_PRESS:
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return onMouse(e);
        }
        return false;
    }

    private void move(float x, float y) {
        int[] p = map.toScreen(x, y);
        if (p != null) AwtBridge.mouseMove(p[0], p[1]);
    }

    private void click(float x, float y, int button) {
        int[] p = map.toScreen(x, y);
        if (p != null) AwtBridge.click(p[0], p[1], button);
    }

    private void wheel(float x, float y, int rotation) {
        int[] p = map.toScreen(x, y);
        if (p != null) AwtBridge.wheel(p[0], p[1], rotation);
    }
}
