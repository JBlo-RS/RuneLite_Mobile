package com.jblors.runelitemobile;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.system.Os;
import android.system.OsConstants;
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
import java.util.Arrays;

/**
 * Runs in the :jvm process. Milestone 1: start the JVM headless on the probe jar and show its
 * output live. The process is killed when the JVM finishes.
 */
public class JvmActivity extends Activity {
    static File logFile(android.content.Context ctx) {
        return new File(ctx.getFilesDir(), "jvm.log");
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView text;
    private ScrollView scroll;
    private File log;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        text = new TextView(this);
        text.setTextIsSelectable(true);
        text.setTypeface(android.graphics.Typeface.MONOSPACE);
        text.setTextSize(11);
        text.setPadding(24, 24, 24, 24);
        scroll = new ScrollView(this);
        scroll.addView(text);
        setContentView(scroll);

        log = logFile(this);
        log.delete();
        new Thread(this::run, "jvm-launcher").start();
        ui.post(this::refresh);
    }

    private void refresh() {
        try {
            String s = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
            if (!s.contentEquals(text.getText())) {
                text.setText(s);
                scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
            }
        } catch (IOException ignored) {
        }
        ui.postDelayed(this::refresh, 400);
    }

    private void append(String line) {
        try (Writer w = new FileWriter(log, true)) {
            w.write(line + "\n");
        } catch (IOException ignored) {
        }
    }

    private void run() {
        int rc;
        try {
            append("== RuneLite Mobile " + BuildConfig.VERSION_NAME);
            append("device: " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.DEVICE + ")");
            append("android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            append("abis: " + Arrays.toString(Build.SUPPORTED_ABIS));
            append("page size: " + Os.sysconf(OsConstants._SC_PAGESIZE));
            append("cpus: " + Runtime.getRuntime().availableProcessors());

            File jre = JreInstaller.ensureInstalled(this, this::append);
            File probe = JreInstaller.copyAsset(this, "probe.jar", new File(getFilesDir(), "probe.jar"));

            String err = NativeBridge.redirectStdio(log.getAbsolutePath());
            if (err != null) append("stdio redirect failed: " + err);

            append("== starting JVM");
            rc = JvmLauncher.launch(this, jre,
                    Arrays.asList("-Djava.awt.headless=true", "-jar", probe.getAbsolutePath()),
                    512, this::append);
            append("== JLI_Launch returned " + rc);
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            append("== launcher error\n" + sw);
            rc = -100;
        }
        final int code = rc;
        ui.postDelayed(() -> {
            refresh();
            setResult(code);
            finish();
            // A JVM cannot be restarted in the same process
            Process.killProcess(Process.myPid());
        }, 1500);
    }
}
