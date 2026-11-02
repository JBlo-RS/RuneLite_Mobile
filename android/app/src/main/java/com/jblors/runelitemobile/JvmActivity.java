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
 * </ul>
 */
public class JvmActivity extends Activity implements SurfaceHolder.Callback {
    static final String EXTRA_MODE = "mode";
    static final String MODE_PROBE = "probe";
    static final String MODE_SWING_DEMO = "swing-demo";

    /** Height of the virtual AWT screen; width follows the display's aspect ratio. */
    private static final int SCREEN_HEIGHT = 720;

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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_PROBE;
        log = logFile(this);
        log.delete();

        if (MODE_SWING_DEMO.equals(mode)) {
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

    private void computeScreenSize() {
        DisplayMetrics m = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(m);
        int longSide = Math.max(m.widthPixels, m.heightPixels);
        int shortSide = Math.min(m.widthPixels, m.heightPixels);
        screenH = SCREEN_HEIGHT;
        screenW = Math.round(SCREEN_HEIGHT * (float) longSide / shortSide) & ~1;
    }

    private void buildDemoUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        SurfaceView sv = new SurfaceView(this);
        sv.getHolder().addCallback(this);
        root.addView(sv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        status = new TextView(this);
        status.setTextColor(0xFFFFFF00);
        status.setBackgroundColor(0x80000000);
        status.setTextSize(12);
        status.setPadding(12, 6, 12, 6);
        status.setText("Starting JVM…");
        root.addView(status, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));
        setContentView(root);
        hideSystemBars(root);
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
                }
                Canvas c = null;
                try {
                    c = holder.getSurface().lockHardwareCanvas();
                    int cw = c.getWidth(), ch = c.getHeight();
                    // Fit the virtual screen to the surface, preserving aspect ratio
                    float scale = Math.min(cw / (float) screenW, ch / (float) screenH);
                    int w = Math.round(screenW * scale), h = Math.round(screenH * scale);
                    dst.set((cw - w) / 2, (ch - h) / 2, (cw - w) / 2 + w, (ch - h) / 2 + h);
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
                        ? String.format("%dx%d  %.0f fps (bridge)", screenW, screenH, fps)
                        : "Waiting for AWT…");
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
            if (MODE_SWING_DEMO.equals(mode)) {
                AwtBridge.init(); // ART must load libpojavexec_awt.so before the JVM does
                File cacio = new File(getFilesDir(), "cacio");
                JreInstaller.deleteRecursively(cacio);
                for (String name : getAssets().list("cacio"))
                    JreInstaller.copyAsset(this, "cacio/" + name, new File(cacio, name));
                File demo = JreInstaller.copyAsset(this, "awtdemo.jar", new File(jars, "awtdemo.jar"));
                args.addAll(JvmLauncher.cacioArgs(cacio, screenW, screenH));
                args.add("-jar");
                args.add(demo.getAbsolutePath());
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
