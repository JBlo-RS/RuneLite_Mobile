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
    private static final int REQ_IMPORT_CREDENTIALS = 1;
    private static final int[] UI_SIZES = {720, 630, 540};
    private static final String[] UI_SIZE_NAMES = {"Small", "Medium", "Large"};

    private TextView logView;
    private Button uiSizeButton, jagexButton;

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
        title.setText("RuneLite Mobile " + BuildConfig.VERSION_NAME);
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        root.addView(title);

        root.addView(bigButton("▶  Launch RuneLite", 0xFFB35C00, v -> launch(JvmActivity.MODE_RUNELITE)));

        LinearLayout settings = new LinearLayout(this);
        uiSizeButton = bigButton("", 0xFF37474F, v -> cycleUiSize());
        jagexButton = bigButton("", 0xFF37474F, v -> onJagexButton());
        settings.addView(uiSizeButton, new LinearLayout.LayoutParams(0, dp(56), 1f));
        View gap = new View(this);
        settings.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
        settings.addView(jagexButton, new LinearLayout.LayoutParams(0, dp(56), 1f));
        root.addView(settings);
        refreshSettings();

        LinearLayout tests = new LinearLayout(this);
        tests.addView(smallButton("Dev mode", 0xFF6D4C41, v -> launch(JvmActivity.MODE_RUNELITE_DEV)),
                new LinearLayout.LayoutParams(0, dp(52), 1f));
        View gap1 = new View(this);
        tests.addView(gap1, new LinearLayout.LayoutParams(dp(12), 1));
        tests.addView(smallButton("Swing demo", 0xFF4A148C, v -> launch(JvmActivity.MODE_SWING_DEMO)),
                new LinearLayout.LayoutParams(0, dp(52), 1f));
        View gap2 = new View(this);
        tests.addView(gap2, new LinearLayout.LayoutParams(dp(12), 1));
        tests.addView(smallButton("JVM probe", 0xFF1B5E20, v -> launch(JvmActivity.MODE_PROBE)),
                new LinearLayout.LayoutParams(0, dp(52), 1f));
        View gap3 = new View(this);
        tests.addView(gap3, new LinearLayout.LayoutParams(dp(12), 1));
        tests.addView(smallButton("Share log", 0xFF0D47A1, v -> shareLog()),
                new LinearLayout.LayoutParams(0, dp(52), 1f));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(12);
        root.addView(tests, tlp);

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

    // ------------------------------------------------------------ settings

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("settings", MODE_PRIVATE);
    }

    private int uiSizeIndex() {
        int h = prefs().getInt("screenHeight", JvmActivity.DEFAULT_SCREEN_HEIGHT);
        for (int i = 0; i < UI_SIZES.length; i++) if (UI_SIZES[i] == h) return i;
        return 1;
    }

    private void cycleUiSize() {
        int next = (uiSizeIndex() + 1) % UI_SIZES.length;
        prefs().edit().putInt("screenHeight", UI_SIZES[next]).apply();
        refreshSettings();
    }

    private void launch(String mode) {
        startActivity(JvmActivity.intent(this, mode)
                .putExtra(JvmActivity.EXTRA_SCREEN_HEIGHT, UI_SIZES[uiSizeIndex()]));
    }

    private void refreshSettings() {
        int i = uiSizeIndex();
        uiSizeButton.setText("UI size: " + UI_SIZE_NAMES[i] + "\n(" + UI_SIZES[i] + "p)");
        uiSizeButton.setTextSize(15);
        java.util.Properties creds = JagexCredentials.load(this);
        jagexButton.setText(creds == null ? "Jagex account:\nimport…"
                : "Jagex account:\n" + creds.getProperty("JX_DISPLAY_NAME", "(imported)"));
        jagexButton.setTextSize(15);
    }

    private void onJagexButton() {
        if (JagexCredentials.load(this) == null) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Import Jagex account")
                    .setMessage("Pick the credentials.properties file RuneLite wrote on your PC.\n\n"
                            + "On the PC: in the Jagex Launcher, open RuneLite's settings and add the "
                            + "client argument --insecure-write-credentials, then launch RuneLite once. "
                            + "It writes credentials.properties into the .runelite folder in your user "
                            + "folder. Copy that file to this phone.\n\n"
                            + "The file logs in to your account, so treat it like a password. It is "
                            + "stored only in this app's private storage.")
                    .setPositiveButton("Choose file", (d, w) -> startActivityForResult(
                            new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("*/*"), REQ_IMPORT_CREDENTIALS))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Jagex account")
                    .setMessage("RuneLite will log in as "
                            + JagexCredentials.load(this).getProperty("JX_DISPLAY_NAME", "the imported character")
                            + ".\n\nRemove the imported credentials from this phone?")
                    .setPositiveButton("Remove", (d, w) -> {
                        JagexCredentials.delete(this);
                        refreshSettings();
                    })
                    .setNegativeButton("Keep", null)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT_CREDENTIALS || resultCode != RESULT_OK || data == null) return;
        try (java.io.InputStream in = getContentResolver().openInputStream(data.getData())) {
            String error = JagexCredentials.importFrom(this, in);
            android.widget.Toast.makeText(this, error == null ? "Jagex account imported" : error,
                    android.widget.Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            android.widget.Toast.makeText(this, "Could not read file: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
        refreshSettings();
    }

    private Button smallButton(String label, int color, View.OnClickListener onClick) {
        Button b = bigButton(label, color, onClick);
        b.setTextSize(14);
        return b;
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
