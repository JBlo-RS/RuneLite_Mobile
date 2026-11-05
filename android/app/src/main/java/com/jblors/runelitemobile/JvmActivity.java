package com.jblors.runelitemobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.system.Os;
import android.system.OsConstants;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs in the :jvm process. A JVM cannot be restarted in a process, so the process is killed
 * when the JVM ends or the activity is left.
 *
 * <ul>
 *   <li>{@link #MODE_PROBE}: headless smoke test, output shown as text.</li>
 *   <li>{@link #MODE_SWING_DEMO}: Swing on Caciocavallo, frames drawn to a SurfaceView.</li>
 *   <li>{@link #MODE_RUNELITE}: the stock RuneLite launcher on Caciocavallo, REFLECT mode.</li>
 * </ul>
 */
public class JvmActivity extends Activity implements SurfaceHolder.Callback {
    static final String EXTRA_MODE = "mode";
    static final String MODE_PROBE = "probe";
    static final String MODE_SWING_DEMO = "swing-demo";
    static final String MODE_RUNELITE = "runelite";
    /** RuneLite client started by our DevLaunch (no launcher) so --developer-mode applies. */
    static final String MODE_RUNELITE_DEV = "runelite-dev";
    /** No JVM: checks whether desktop OpenGL works on this phone through Zink. */
    static final String MODE_GPU_TEST = "gpu-test";

    /**
     * RuneLite's clientJvm17Arguments from bootstrap.json. In REFLECT mode the launcher runs
     * the client in its own JVM and cannot apply these, so we pass them up front.
     */
    private static final String[] RUNELITE_JVM_ARGS = {
            "-XX:+DisableAttachMechanism",
            "-Xss2m",
            "-XX:CompileThreshold=1500",
            "--add-opens=java.base/java.net=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            // launcherJvm17Arguments + run the client in this JVM (forking is impossible here)
            "-Drunelite.launcher.nojvm=true",
            "-Drunelite.launcher.reflect=true",
    };

    /** Intent extra: height of the virtual AWT screen; width follows the free display area. */
    static final String EXTRA_SCREEN_HEIGHT = "screenHeight";
    static final int DEFAULT_SCREEN_HEIGHT = 630;
    /** Intent extra: true = mobile layout (full-screen game, slide-in sidebar). */
    static final String EXTRA_MOBILE_LAYOUT = "mobileLayout";
    /** Intent extra: true = give RuneLite's GPU plugin an OpenGL context (Zink via OSMesa). */
    static final String EXTRA_GPU = "gpu";

    /** RuneLite's sidebar incl. its icon strip, and its title bar, in virtual px (measured). */
    private static final int RUNELITE_SIDEBAR_W = 274;
    private static final int RUNELITE_TITLE_H = 26;

    /** JVM properties for the GPU plugin: our rlawt (net.runelite.rlawt.AWTContext) and LWJGL. */
    private List<String> gpuArgs() {
        String dir = getApplicationInfo().nativeLibraryDir;
        append("gpu: on (Zink via OSMesa)");
        return Arrays.asList(
                "-Drlm.gl.lib=" + new File(dir, "librlmgl.so").getAbsolutePath(),
                "-Drlm.gl.osmesa=" + new File(dir, "libOSMesa.so").getAbsolutePath(),
                "-Dorg.lwjgl.librarypath=" + dir,
                "-Dorg.lwjgl.opengl.explicitInit=true");
    }

    static File logFile(Context ctx) {
        return new File(ctx.getFilesDir(), "jvm.log");
    }

    static Intent intent(Context ctx, String mode) {
        return new Intent(ctx, JvmActivity.class).putExtra(EXTRA_MODE, mode);
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private File log;
    private String mode;

    // probe mode
    private TextView text;
    private ScrollView scroll;

    // demo mode
    private TextView status;
    private volatile SurfaceHolder surface;
    private volatile boolean rendering;
    private int screenW, screenH;
    private ScreenLayout screenLayout;
    private View panelHandle;
    private volatile String lastGesture = "";
    private KeyboardSink keyboard;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_PROBE;
        log = logFile(this);
        log.delete();

        if (isGraphical()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            computeScreenSize();
            buildDemoUi();
        } else {
            buildProbeUi();
            ui.post(this::refreshLog);
        }
        new Thread(this::runJvm, "jvm-launcher").start();
    }

    // ---------------------------------------------------------------- UI

    private void buildProbeUi() {
        text = new TextView(this);
        text.setTextIsSelectable(true);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(11);
        text.setPadding(24, 24, 24, 24);
        text.setTextColor(0xFFE0E0E0);
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF121212);
        scroll.addView(text);
        // Edge-to-edge on Android 15+: keep the log clear of the system bars
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets b = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                v.setPadding(b.left, b.top, b.right, b.bottom);
                return WindowInsets.CONSUMED;
            }
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(scroll);
    }

    private void refreshLog() {
        try {
            String s = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            if (!s.contentEquals(text.getText())) {
                text.setText(s);
                scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
            }
        } catch (IOException ignored) {
        }
        ui.postDelayed(this::refreshLog, 400);
    }

    /** Width of the control strip on the left edge, in dp. */
    private static final int STRIP_DP = 60;

    private void computeScreenSize() {
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        int longSide = Math.max(m.widthPixels, m.heightPixels);
        int shortSide = Math.min(m.widthPixels, m.heightPixels);
        boolean mobile = getIntent().getBooleanExtra(EXTRA_MOBILE_LAYOUT, true);
        screenLayout = new ScreenLayout(mobile);
        // Smaller virtual height = everything drawn bigger on the phone
        screenH = getIntent().getIntExtra(EXTRA_SCREEN_HEIGHT, DEFAULT_SCREEN_HEIGHT);
        if (mobile) {
            // The game canvas fills the phone, so give it the phone's shape and leave room for
            // RuneLite's title bar and sidebar, which stay off screen until the panel opens
            int gameH = screenH - RUNELITE_TITLE_H;
            screenW = (Math.round(gameH * (float) longSide / shortSide) + RUNELITE_SIDEBAR_W) & ~1;
        } else {
            int availW = longSide - dp(STRIP_DP);
            screenW = Math.round(screenH * (float) availW / shortSide) & ~1;
        }
    }

    private void buildDemoUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        if (!screenLayout.isMobile()) {
            root.addView(buildControlStrip(), new LinearLayout.LayoutParams(dp(STRIP_DP),
                    LinearLayout.LayoutParams.MATCH_PARENT));
        }

        FrameLayout game = new FrameLayout(this);
        SurfaceView sv = new SurfaceView(this);
        sv.getHolder().addCallback(this);
        TouchInput touch = new TouchInput(getResources().getDisplayMetrics().density,
                (x, y) -> screenLayout.toScreen(x, y), g -> lastGesture = g);
        sv.setOnTouchListener(touch);
        sv.setOnGenericMotionListener(touch);
        game.addView(sv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        keyboard = new KeyboardSink(this, t -> lastGesture = t);
        game.addView(keyboard, new FrameLayout.LayoutParams(1, 1));
        if (screenLayout.isMobile()) addFloatingControls(game);
        root.addView(game, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        setContentView(root);
        hideSystemBars(root);
    }

    /** Mobile layout: a movable, collapsible button column and a movable drawer tab. */
    private void addFloatingControls(FrameLayout game) {
        android.content.SharedPreferences prefs = getSharedPreferences("overlay", MODE_PRIVATE);

        // --- button column, with a grip: tap = collapse/expand, drag = move
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView grip = new TextView(this);
        grip.setText("⠿");
        grip.setTextSize(18);
        grip.setTextColor(0xCCFFFFFF);
        grip.setGravity(Gravity.CENTER);
        grip.setBackground(roundRect(0x99222222));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(48), dp(28));
        glp.bottomMargin = dp(6);
        column.addView(grip, glp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.addView(floatingButton(stripButton("⌨", false, v -> keyboard.toggle())));
        buttons.addView(floatingButton(modifierButton("Alt", KeyMap.VK_ALT)));
        buttons.addView(floatingButton(modifierButton("Shift", KeyMap.VK_SHIFT)));
        buttons.addView(floatingButton(modifierButton("Ctrl", KeyMap.VK_CONTROL)));
        buttons.addView(floatingButton(stripButton("Esc", false, v -> {
            AwtBridge.tapKey(KeyMap.VK_ESCAPE);
            lastGesture = "Esc";
        })));
        column.addView(buttons);
        buttons.setVisibility(prefs.getBoolean("collapsed", false) ? View.GONE : View.VISIBLE);

        game.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));
        float colX = prefs.getFloat("columnX", -1f), colY = prefs.getFloat("columnY", -1f);
        game.post(() -> {
            // Default: left edge, vertically centred
            float x = colX >= 0 ? colX * game.getWidth() : dp(8);
            float y = colY >= 0 ? colY * game.getHeight() : (game.getHeight() - column.getHeight()) / 2f;
            column.setX(clampF(x, 0, game.getWidth() - column.getWidth()));
            column.setY(clampF(y, 0, game.getHeight() - column.getHeight()));
        });
        grip.setOnTouchListener(new Draggable(column, true, true,
                () -> {
                    boolean collapse = buttons.getVisibility() == View.VISIBLE;
                    buttons.setVisibility(collapse ? View.GONE : View.VISIBLE);
                    prefs.edit().putBoolean("collapsed", collapse).apply();
                    lastGesture = collapse ? "controls collapsed" : "controls expanded";
                },
                () -> prefs.edit()
                        .putFloat("columnX", column.getX() / Math.max(1, game.getWidth()))
                        .putFloat("columnY", column.getY() / Math.max(1, game.getHeight()))
                        .apply()));

        // --- drawer tab on the right edge: tap = slide the sidebar in/out, drag = move up/down
        TextView tab = new TextView(this);
        tab.setText("◀");
        tab.setTextSize(16);
        tab.setTextColor(Color.WHITE);
        tab.setGravity(Gravity.CENTER);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xAA1E1E1E);
        bg.setStroke(dp(1), 0x66DC8A00);
        float r = dp(12);
        bg.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
        tab.setBackground(bg);
        game.addView(tab, new FrameLayout.LayoutParams(dp(30), dp(76), Gravity.TOP | Gravity.END));
        float tabY = prefs.getFloat("tabY", 0.36f); // fraction of height for the tab's centre
        game.post(() -> tab.setY(clampF(tabY * game.getHeight() - tab.getHeight() / 2f,
                0, game.getHeight() - tab.getHeight())));
        tab.setOnTouchListener(new Draggable(tab, false, true, this::togglePanel,
                () -> prefs.edit().putFloat("tabY",
                        (tab.getY() + tab.getHeight() / 2f) / Math.max(1, game.getHeight())).apply()));
        panelHandle = tab;

        // Keep the panel's icons clear of rounded screen corners
        game.post(() -> screenLayout.setCornerInset(roundedCornerInset()));
    }

    private static float clampF(float v, float lo, float hi) {
        return Math.max(lo, Math.min(v, Math.max(lo, hi)));
    }

    /** Margin that keeps icons at the right edge clear of the screen's rounded corners. */
    private int roundedCornerInset() {
        if (Build.VERSION.SDK_INT < 31) return 0;
        WindowInsets in = getWindow().getDecorView().getRootWindowInsets();
        if (in == null) return 0;
        int radius = 0;
        for (int pos : new int[]{android.view.RoundedCorner.POSITION_TOP_RIGHT,
                android.view.RoundedCorner.POSITION_BOTTOM_RIGHT}) {
            android.view.RoundedCorner rc = in.getRoundedCorner(pos);
            if (rc != null) radius = Math.max(radius, rc.getRadius());
        }
        // An icon a few px from the edge is clear of the curve about half a radius in
        int inset = Math.round(radius * 0.55f);
        append("display: rounded corner radius " + radius + " px, panel inset " + inset + " px");
        return inset;
    }

    /**
     * Touch handler for a floating control: a tap runs onTap, a drag moves {@code target}
     * (horizontally and/or vertically, kept inside the parent) and then runs onMoved.
     */
    private final class Draggable implements View.OnTouchListener {
        private final View target;
        private final boolean horizontal, vertical;
        private final Runnable onTap, onMoved;
        private final float slop = dp(8);
        private float downRawX, downRawY, startX, startY;
        private boolean dragging;

        Draggable(View target, boolean horizontal, boolean vertical, Runnable onTap, Runnable onMoved) {
            this.target = target;
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.onTap = onTap;
            this.onMoved = onMoved;
        }

        @Override
        public boolean onTouch(View v, android.view.MotionEvent e) {
            View parent = (View) target.getParent();
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downRawX = e.getRawX();
                    downRawY = e.getRawY();
                    startX = target.getX();
                    startY = target.getY();
                    dragging = false;
                    v.setPressed(true);
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
                    if (!dragging && Math.hypot(dx, dy) > slop) dragging = true;
                    if (dragging) {
                        if (horizontal) target.setX(clampF(startX + dx, 0, parent.getWidth() - target.getWidth()));
                        if (vertical) target.setY(clampF(startY + dy, 0, parent.getHeight() - target.getHeight()));
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    v.setPressed(false);
                    if (dragging) onMoved.run();
                    else onTap.run();
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    if (dragging) onMoved.run();
                    return true;
            }
            return false;
        }
    }

    /** Wraps a strip button for the floating column: smaller, translucent. */
    private View floatingButton(TextView b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(40));
        lp.bottomMargin = dp(6);
        b.setLayoutParams(lp);
        b.setAlpha(0.85f);
        if (b.getTag() == null) b.setBackground(roundRect(0x99222222));
        return b;
    }

    /** First panel opening in this run shows RuneLite's Configuration (wrench) panel. */
    private boolean panelOpenedBefore;

    private void togglePanel() {
        boolean open = !screenLayout.isPanelOpen();
        if (open) {
            // RuneLite's sidebar itself is kept open by the render loop (ensureSidebarOpen)
            if (!panelOpenedBefore) {
                panelOpenedBefore = true;
                AwtBridge.selectSidebarTab("Configuration");
            }
        }
        screenLayout.setPanelOpen(open);
        if (panelHandle instanceof TextView) ((TextView) panelHandle).setText(open ? "▶" : "◀");
        lastGesture = open ? "panel opened" : "panel closed";
    }

    /** Moves the drawer tab along with the panel's left edge (UI thread). */
    private void placePanelHandle(int panelLeft, int surfaceW) {
        if (panelHandle == null) return;
        panelHandle.setTranslationX(panelLeft - surfaceW);
    }

    @Override
    public void onBackPressed() {
        if (screenLayout != null && screenLayout.isPanelOpen()) {
            togglePanel();
            return;
        }
        super.onBackPressed();
    }

    /** Left-edge controls: keyboard, latching Alt/Shift/Ctrl (like holding them on a PC), Esc. */
    private View buildControlStrip() {
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.VERTICAL);
        strip.setBackgroundColor(0xFF1A1A1A);
        strip.setPadding(dp(4), dp(6), dp(4), dp(6));

        strip.addView(stripButton("⌨", false, v -> keyboard.toggle()));
        strip.addView(modifierButton("Alt", KeyMap.VK_ALT));
        strip.addView(modifierButton("Shift", KeyMap.VK_SHIFT));
        strip.addView(modifierButton("Ctrl", KeyMap.VK_CONTROL));
        strip.addView(stripButton("Esc", false, v -> {
            AwtBridge.tapKey(KeyMap.VK_ESCAPE);
            lastGesture = "Esc";
        }));

        View spacer = new View(this);
        strip.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));
        status = new TextView(this);
        status.setTextColor(0xFFAAAAAA);
        status.setTextSize(9);
        status.setGravity(Gravity.CENTER);
        status.setText("starting");
        strip.addView(status);
        return strip;
    }

    private TextView stripButton(String label, boolean small, View.OnClickListener onClick) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(label.length() > 3 ? 12 : 16);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setBackground(roundRect(0xFF333333));
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        lp.bottomMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    /** Tap to hold the key down, tap again to release it; lit while held. */
    private TextView modifierButton(String label, int awtKey) {
        TextView b = stripButton(label, true, null);
        boolean[] held = {false};
        b.setOnClickListener(v -> {
            held[0] = !held[0];
            AwtBridge.holdModifier(awtKey, held[0]);
            b.setBackground(roundRect(held[0] ? 0xFFDC8A00 : 0xFF333333));
            b.setTextColor(held[0] ? Color.BLACK : Color.WHITE);
            lastGesture = label + (held[0] ? " held" : " released");
        });
        return b;
    }

    private android.graphics.drawable.GradientDrawable roundRect(int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(8));
        return d;
    }

    /** Sets JX_* like the Jagex Launcher does (values are never written to the log). */
    private void applyJagexCredentials() throws android.system.ErrnoException {
        java.util.Properties c = JagexCredentials.load(this);
        for (String k : JagexCredentials.KEYS) {
            String v = c == null ? null : c.getProperty(k);
            if (v != null) Os.setenv(k, v, true);
            else Os.unsetenv(k);
        }
        append(c == null ? "jagex: no Jagex account imported (normal login)"
                : "jagex: Jagex account credentials set for " + c.getProperty("JX_DISPLAY_NAME", "(no display name)"));
    }

    /**
     * The developer workflow reads ~/.runelite/credentials.properties; put the imported Jagex
     * credentials there (app-private storage), or remove a stale copy.
     */
    private void writeDevCredentialsFile() throws IOException {
        File f = new File(new File(new File(getFilesDir(), "home"), ".runelite"), "credentials.properties");
        java.util.Properties c = JagexCredentials.load(this);
        if (c == null) {
            f.delete();
            return;
        }
        f.getParentFile().mkdirs();
        try (java.io.OutputStream out = new java.io.FileOutputStream(f)) {
            c.store(out, null);
        }
        append("jagex: wrote credentials.properties for developer mode");
    }

    /** GPU test: runs in this (separate) process so a driver crash only ends this screen. */
    private void runGpuTest() {
        String err = NativeBridge.redirectStdio(log.getAbsolutePath());
        if (err != null) append("stdio redirect failed: " + err);
        append("== GPU test: desktop OpenGL through Mesa Zink (OpenGL on Vulkan)");
        String osmesa = new File(getApplicationInfo().nativeLibraryDir, "libOSMesa.so").getAbsolutePath();
        String report = GpuProbe.run(osmesa);
        append("== GPU test finished");
        ui.post(() -> {
            if (text != null) refreshLog();
            new android.app.AlertDialog.Builder(this)
                    .setTitle("GPU test result")
                    .setMessage(report.substring(report.lastIndexOf("RESULT:") >= 0 ? report.lastIndexOf("RESULT:") : 0))
                    .setPositiveButton("Close", (d, w) -> {
                        finish();
                        Process.killProcess(Process.myPid());
                    })
                    .setCancelable(false)
                    .show();
        });
    }

    private boolean isGraphical() {
        return MODE_SWING_DEMO.equals(mode) || MODE_RUNELITE.equals(mode) || MODE_RUNELITE_DEV.equals(mode);
    }

    private static int clamp(int v, int max) {
        return v < 0 ? 0 : Math.min(v, max - 1);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        int code = event.getKeyCode();
        boolean system = code == android.view.KeyEvent.KEYCODE_BACK
                || code == android.view.KeyEvent.KEYCODE_VOLUME_UP
                || code == android.view.KeyEvent.KEYCODE_VOLUME_DOWN;
        // Hardware keyboards; soft keyboard input arrives through KeyboardSink instead
        if (isGraphical() && !system && KeyboardSink.handleKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private void hideSystemBars(View v) {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            v.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private void setStatus(String s) {
        ui.post(() -> {
            if (status != null) status.setText(s);
        });
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surface = holder;
        if (!rendering) {
            rendering = true;
            new Thread(this::renderLoop, "awt-render").start();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surface = holder;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surface = null;
    }

    private void renderLoop() {
        Bitmap frame = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        Rect bandSrc = new Rect(), bandDst = new Rect();
        Paint shadow = new Paint();
        shadow.setColor(0x99000000);
        int[] awtLayout = null;
        long lastLayoutQuery = 0, lastSidebarCheck = 0, lastFrame = System.nanoTime();
        int lastPanelLeft = Integer.MIN_VALUE;
        long windowStart = System.nanoTime();
        int frames = 0;
        boolean announced = false;
        while (rendering) {
            long t0 = System.nanoTime();
            SurfaceHolder holder = surface;
            if (holder != null && AwtBridge.isReady() && AwtBridge.renderFrame(frame)) {
                if (!announced) {
                    announced = true;
                    append("== first AWT frame received (" + screenW + "x" + screenH + ")");
                    // AWT is initialized by now (the app did it), so it's safe to hook windows
                    AwtBridge.installWindowManager();
                }
                Canvas c = null;
                try {
                    c = holder.getSurface().lockHardwareCanvas();
                    int cw = c.getWidth(), ch = c.getHeight();
                    long nowNs = System.nanoTime();
                    if (screenLayout.isMobile() && nowNs - lastLayoutQuery > 250_000_000L) {
                        awtLayout = AwtBridge.layout(); // where the game canvas is right now
                        lastLayoutQuery = nowNs;
                        if (awtLayout != null && nowNs - lastSidebarCheck > 1_000_000_000L) {
                            lastSidebarCheck = nowNs;
                            AwtBridge.ensureSidebarOpen(); // a closed sidebar gives black bars
                        }
                    }
                    screenLayout.update(cw, ch, screenW, screenH, awtLayout, (nowNs - lastFrame) / 1e9f);
                    lastFrame = nowNs;
                    c.drawColor(Color.BLACK);
                    ScreenLayout.Mapping[] maps = screenLayout.mappings();
                    for (int i = 0; i < maps.length; i++) {
                        ScreenLayout.Mapping m = maps[i];
                        if (i > 0) {
                            c.drawRect(m.dst.left - dp(6), 0, m.dst.left, ch, shadow);
                            // Panel inset for rounded corners: fill the gaps above and below with
                            // the panel's top pixel row, stretched. The top row is the sidebar's
                            // plain background; the bottom row can be scrolling list content.
                            bandSrc.set(m.src.left, m.src.top, m.src.right, m.src.top + 1);
                            if (m.dst.top > 0) {
                                bandDst.set(m.dst.left, 0, m.dst.right, m.dst.top);
                                c.drawBitmap(frame, bandSrc, bandDst, null);
                            }
                            if (m.dst.bottom < ch) {
                                bandDst.set(m.dst.left, m.dst.bottom, m.dst.right, ch);
                                c.drawBitmap(frame, bandSrc, bandDst, null);
                            }
                        }
                        c.drawBitmap(frame, m.src, m.dst, paint);
                    }
                    int panelLeft = screenLayout.panelLeft(cw);
                    if (panelLeft != lastPanelLeft) {
                        lastPanelLeft = panelLeft;
                        ui.post(() -> placePanelHandle(panelLeft, cw));
                    }
                } catch (RuntimeException e) {
                    // Surface went away mid-frame
                } finally {
                    if (c != null) {
                        try {
                            holder.getSurface().unlockCanvasAndPost(c);
                        } catch (RuntimeException ignored) {
                        }
                    }
                }
                frames++;
            }
            long now = System.nanoTime();
            if (now - windowStart >= 1_000_000_000L) {
                float fps = frames * 1e9f / (now - windowStart);
                setStatus(AwtBridge.isReady()
                        ? String.format("%.0f fps\n%dx%d", fps, screenW, screenH)
                        : "waiting");
                frames = 0;
                windowStart = now;
            }
            long spent = (System.nanoTime() - t0) / 1_000_000;
            if (spent < 16) {
                try {
                    Thread.sleep(16 - spent);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    // ---------------------------------------------------------------- JVM

    private void append(String line) {
        synchronized (this) {
            try (Writer w = new FileWriter(log, true)) {
                w.write(line + "\n");
            } catch (IOException ignored) {
            }
        }
    }

    private void runJvm() {
        int rc;
        try {
            append("== RuneLite Mobile " + BuildConfig.VERSION_NAME + " (" + mode + ")");
            append("device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")");
            append("android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            append("abis: " + Arrays.toString(Build.SUPPORTED_ABIS));
            append("page size: " + Os.sysconf(OsConstants._SC_PAGESIZE));
            append("cpus: " + Runtime.getRuntime().availableProcessors());

            if (MODE_GPU_TEST.equals(mode)) {
                runGpuTest();
                return;
            }
            File jre = JreInstaller.ensureInstalled(this, this::append);
            File jars = new File(getFilesDir(), "jars");
            List<String> args = new ArrayList<>();
            if (isGraphical()) {
                AwtBridge.init(); // ART must load libpojavexec_awt.so before the JVM does
                File cacio = new File(getFilesDir(), "cacio");
                JreInstaller.deleteRecursively(cacio);
                for (String name : getAssets().list("cacio"))
                    JreInstaller.copyAsset(this, "cacio/" + name, new File(cacio, name));
                args.addAll(JvmLauncher.cacioArgs(cacio, screenW, screenH));
                if ((MODE_RUNELITE.equals(mode) || MODE_RUNELITE_DEV.equals(mode))
                        && getIntent().getBooleanExtra(EXTRA_GPU, false)) {
                    args.addAll(gpuArgs());
                }
                if (MODE_RUNELITE_DEV.equals(mode)) {
                    args.addAll(Arrays.asList(RUNELITE_JVM_ARGS));
                    args.add("-ea"); // RuneLite refuses developer mode without assertions
                    applyJagexCredentials();
                    writeDevCredentialsFile();
                    args.add("com.jblors.rlm.DevLaunch"); // on the boot class path (jvmhelper)
                } else {
                    String jarName = MODE_RUNELITE.equals(mode) ? "RuneLite.jar" : "awtdemo.jar";
                    if (MODE_RUNELITE.equals(mode)) {
                        args.addAll(Arrays.asList(RUNELITE_JVM_ARGS));
                        applyJagexCredentials();
                    }
                    File jar = JreInstaller.copyAsset(this, jarName, new File(jars, jarName));
                    args.add("-jar");
                    args.add(jar.getAbsolutePath());
                }
            } else {
                File probe = JreInstaller.copyAsset(this, "probe.jar", new File(jars, "probe.jar"));
                args.add("-Djava.awt.headless=true");
                args.add("-jar");
                args.add(probe.getAbsolutePath());
            }

            String err = NativeBridge.redirectStdio(log.getAbsolutePath());
            if (err != null) append("stdio redirect failed: " + err);

            append("== starting JVM");
            rc = JvmLauncher.launch(this, jre, args, 768, this::append);
            append("== JLI_Launch returned " + rc);
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            append("== launcher error\n" + sw);
            setStatus("Launcher error, see log");
            rc = -100;
        }
        final int code = rc;
        ui.postDelayed(() -> {
            if (text != null) refreshLog();
            setResult(code);
            finish();
            Process.killProcess(Process.myPid());
        }, 1500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        rendering = false;
        // Leaving the screen ends the JVM; it cannot be resumed or restarted in-process
        if (isFinishing()) Process.killProcess(Process.myPid());
    }
}
