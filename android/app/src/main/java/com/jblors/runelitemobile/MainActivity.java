package com.jblors.runelitemobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Main process: launch the JVM probe, then show and share its log. */
public class MainActivity extends Activity {
    private TextView logView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);
        // targetSdk 35 is edge-to-edge on Android 15+: keep content clear of the status,
        // navigation and cutout areas.
        final int pad = dp(16);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets b = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(b.left + pad, b.top + pad, b.right + pad, b.bottom + pad);
                return WindowInsets.CONSUMED;
            }
            v.setPadding(insets.getSystemWindowInsetLeft() + pad, insets.getSystemWindowInsetTop() + pad,
                    insets.getSystemWindowInsetRight() + pad, insets.getSystemWindowInsetBottom() + pad);
            return insets.consumeSystemWindowInsets();
        });

        TextView title = new TextView(this);
        title.setText("RuneLite Mobile " + BuildConfig.VERSION_NAME + "\nMilestone 2: Swing on screen");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        root.addView(title);

        root.addView(bigButton("▶  Run Swing demo", 0xFF6A1B9A,
                v -> startActivity(JvmActivity.intent(this, JvmActivity.MODE_SWING_DEMO))));
        root.addView(bigButton("▶  Run JVM probe", 0xFF2E7D32,
                v -> startActivity(JvmActivity.intent(this, JvmActivity.MODE_PROBE))));
        root.addView(bigButton("Share log", 0xFF1565C0, v -> shareLog()));

        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextColor(0xFFE0E0E0);
        logView.setTextSize(11);
        logView.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private Button bigButton(String label, int color, View.OnClickListener onClick) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(20);
        b.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        b.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        logView.setText(readLog());
    }

    private String readLog() {
        File f = JvmActivity.logFile(this);
        if (!f.isFile()) return "No log yet. Tap a \"Run\" button above.";
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Could not read log: " + e;
        }
    }

    private void shareLog() {
        String log = readLog();
        if (log.length() > 200_000) log = log.substring(log.length() - 200_000);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "RuneLite Mobile JVM log")
                .putExtra(Intent.EXTRA_TEXT, log);
        startActivity(Intent.createChooser(send, "Share log"));
    }
}
