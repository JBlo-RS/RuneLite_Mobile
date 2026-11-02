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
    // Where the virtual screen is drawn inside the SurfaceView (set by the render loop)
    private volatile int dstL, dstT, dstW = 1, dstH = 1;
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
        int availW = longSide - dp(STRIP_DP);
        // Smaller virtual height = everything drawn bigger on the phone
        screenH = getIntent().getIntExtra(EXTRA_SCREEN_HEIGHT, DEFAULT_SCREEN_HEIGHT);
        screenW = Math.round(screenH * (float) availW / shortSide) & ~1;
    }

    private void buildDemoUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Color.BLACK);
        root.addView(buildControlStrip(), new LinearLayout.LayoutParams(dp(STRIP_DP),
                LinearLayout.LayoutParams.MATCH_PARENT));

        FrameLayout game = new FrameLayout(this);
        SurfaceView sv = new SurfaceView(this);
        sv.getHolder().addCallback(this);
        TouchInput touch = new TouchInput(getResources().getDisplayMetrics().density,
                new TouchInput.Mapper() {
                    @Override
                    public int toScreenX(float x) {
                        return clamp(Math.round((x - dstL) * screenW / (float) dstW), screenW);
                    }

                    @Override
                    public int toScreenY(float y) {
                        return clamp(Math.round((y - dstT) * screenH / (float) dstH), screenH);
                    }
                }, g -> lastGesture = g);
        sv.setOnTouchListener(touch);
        sv.setOnGenericMotionListener(touch);
        game.addView(sv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        keyboard = new KeyboardSink(this, t -> lastGesture = t);
        game.addView(keyboard, new FrameLayout.LayoutParams(1, 1));
        root.addView(game, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        setContentView(root);
        hideSystemBars(root);
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
        Rect dst = new Rect();
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
                    // Fit the virtual screen to the surface, preserving aspect ratio
                    float scale = Math.min(cw / (float) screenW, ch / (float) screenH);
                    int w = Math.round(screenW * scale), h = Math.round(screenH * scale);
                    dst.set((cw - w) / 2, (ch - h) / 2, (cw - w) / 2 + w, (ch - h) / 2 + h);
                    dstL = dst.left;
                    dstT = dst.top;
                    dstW = Math.max(1, w);
                    dstH = Math.max(1, h);
                    c.drawColor(Color.BLACK);
                    c.drawBitmap(frame, null, dst, paint);
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
