package com.jblors.runelitemobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;

/** Launch screen: play, account, settings, and the developer/test tools under "More". */
public class MainActivity extends Activity {
    private static final int REQ_IMPORT_CREDENTIALS = 1;
    private static final int REQ_IMPORT_PLUGIN = 2;
    private static final int[] UI_SIZES = {720, 630, 540};
    private static final String[] UI_SIZE_NAMES = {"Small", "Medium", "Large"};

    // RuneLite-ish palette
    private static final int BG = 0xFF171717;
    private static final int CARD = 0xFF232323;
    private static final int CARD_LINE = 0xFF2E2E2E;
    private static final int ORANGE = 0xFFDC8A00;
    private static final int TEXT = 0xFFE6E6E6;
    private static final int MUTED = 0xFF8C8C8C;

    private TextView accountName, accountHint, accountAction;
    private LinearLayout layoutChoice, sizeChoice, gpuChoice, more;
    private TextView moreToggle;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(20), dp(20), dp(24));

        col.addView(header());
        col.addView(playButton(), marginTop(dp(24)));
        col.addView(accountCard(), marginTop(dp(16)));
        col.addView(settingsCard(), marginTop(dp(12)));
        col.addView(moreCard(), marginTop(dp(12)));

        TextView footer = text("Unofficial port. Not affiliated with RuneLite or Jagex.", 12, MUTED);
        footer.setGravity(Gravity.CENTER);
        col.addView(footer, marginTop(dp(20)));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        scroll.addView(col);
        // targetSdk 35 is edge-to-edge on Android 15+: keep content clear of the system bars
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets b = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(b.left, b.top, b.right, b.bottom);
                return WindowInsets.CONSUMED;
            }
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(scroll);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkGpuCrash();
        refresh();
    }

    /**
     * If the last run crashed in the OpenGL driver (Mesa/Zink aborts with an assertion on some
     * phones' Vulkan drivers), turn the GPU setting off and say so, once per crash.
     */
    private void checkGpuCrash() {
        if (!gpu()) return;
        File log = JvmActivity.logFile(this);
        if (!log.isFile() || log.lastModified() == prefs().getLong("gpuCrashSeen", 0)) return;
        String text = readLog();
        int tail = Math.max(0, text.length() - 20000);
        String end = text.substring(tail);
        boolean mesaAbort = end.contains("src/gallium/") || end.contains("src/vulkan/") || end.contains("zink_")
                && end.contains("assertion");
        if (!mesaAbort) return;
        prefs().edit().putBoolean("gpu", false).putLong("gpuCrashSeen", log.lastModified()).apply();
        String line = "";
        for (String l : end.split("\n")) if (l.contains("assertion") || l.contains("src/gallium/")) line = l.trim();
        new AlertDialog.Builder(this)
                .setTitle("GPU turned off")
                .setMessage("The last start crashed in the GPU driver (Zink, OpenGL on this device's Vulkan "
                        + "driver), so GPU has been turned off. RuneLite works as before without it.\n\n"
                        + "This build of Zink doesn't work with some GPUs yet, e.g. Qualcomm Adreno.\n\n" + line)
                .setPositiveButton("OK", null)
                .show();
    }

    // ------------------------------------------------------------ sections

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.runelite_logo);
        row.addView(logo, new LinearLayout.LayoutParams(dp(64), dp(64)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(14), 0, 0, 0);
        TextView name = text("RuneLite", 28, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(name);
        TextView sub = text("Mobile  ·  " + BuildConfig.VERSION_NAME, 14, ORANGE);
        titles.addView(sub);
        row.addView(titles);
        return row;
    }

    private View playButton() {
        TextView play = text("▶   Play", 22, 0xFF111111);
        play.setTypeface(Typeface.DEFAULT_BOLD);
        play.setGravity(Gravity.CENTER);
        play.setBackground(round(ORANGE, dp(16), 0));
        play.setOnClickListener(v -> launch(JvmActivity.MODE_RUNELITE));
        play.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(72)));
        play.setElevation(dp(4));
        return play;
    }

    private View accountCard() {
        LinearLayout card = card();
        card.addView(label("ACCOUNT"));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        accountName = text("", 17, TEXT);
        accountHint = text("", 13, MUTED);
        texts.addView(accountName);
        texts.addView(accountHint);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        accountAction = chip("", false);
        accountAction.setOnClickListener(v -> onAccountAction());
        row.addView(accountAction);
        card.addView(row, marginTop(dp(8)));
        return card;
    }

    private View settingsCard() {
        LinearLayout card = card();
        card.addView(label("LAYOUT"));
        layoutChoice = segmented(new String[]{"Mobile", "Classic"}, i -> {
            prefs().edit().putBoolean("mobileLayout", i == 0).apply();
            refresh();
        });
        card.addView(layoutChoice, marginTop(dp(8)));
        TextView layoutHint = text("Mobile: the game fills the screen and the ◀ tab slides in the "
                + "RuneLite sidebar. Works best with the game set to Resizable mode.", 12, MUTED);
        card.addView(layoutHint, marginTop(dp(6)));

        card.addView(label("UI SIZE"), marginTop(dp(16)));
        sizeChoice = segmented(UI_SIZE_NAMES, i -> {
            prefs().edit().putInt("screenHeight", UI_SIZES[i]).apply();
            refresh();
        });
        card.addView(sizeChoice, marginTop(dp(8)));

        card.addView(label("GPU (EXPERIMENTAL)"), marginTop(dp(16)));
        gpuChoice = segmented(new String[]{"Off", "On"}, i -> {
            prefs().edit().putBoolean("gpu", i == 1).apply();
            refresh();
        });
        card.addView(gpuChoice, marginTop(dp(8)));
        card.addView(text("On: RuneLite's GPU plugin can draw the game with the phone's GPU "
                + "(OpenGL through Zink on Vulkan). Turn the GPU plugin on in RuneLite's "
                + "plugin list too.", 12, MUTED), marginTop(dp(6)));
        return card;
    }

    private View moreCard() {
        LinearLayout card = card();
        moreToggle = text("More  ▾", 15, TEXT);
        moreToggle.setPadding(0, dp(2), 0, dp(2));
        moreToggle.setOnClickListener(v -> {
            boolean show = more.getVisibility() != View.VISIBLE;
            more.setVisibility(show ? View.VISIBLE : View.GONE);
            moreToggle.setText(show ? "More  ▴" : "More  ▾");
        });
        card.addView(moreToggle);

        more = new LinearLayout(this);
        more.setOrientation(LinearLayout.VERTICAL);
        more.setVisibility(View.GONE);
        more.addView(moreItem("Developer mode", "Start the client without the launcher, with "
                + "--developer-mode. Loads your local plugins.", v -> launch(JvmActivity.MODE_RUNELITE_DEV)));
        more.addView(moreItem("Plugins", "Local plugins for Developer mode: import a .jar or remove one",
                v -> showPlugins()));
        more.addView(moreItem("View log", "Output of the last run", v -> showLog()));
        more.addView(moreItem("Share log", "Send the last run's log", v -> shareLog()));
        more.addView(moreItem("Swing demo", "Test touch, keyboard and drawing", v -> launch(JvmActivity.MODE_SWING_DEMO)));
        more.addView(moreItem("JVM probe", "Test the Java runtime", v -> launch(JvmActivity.MODE_PROBE)));
        more.addView(moreItem("GPU test", "Check whether the GPU plugin could work on this phone",
                v -> launch(JvmActivity.MODE_GPU_TEST)));
        card.addView(more);
        return card;
    }

    private View moreItem(String title, String hint, View.OnClickListener onClick) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, dp(12), 0, dp(12));
        item.addView(text(title, 16, TEXT));
        item.addView(text(hint, 12, MUTED));
        item.setOnClickListener(onClick);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        View line = new View(this);
        line.setBackgroundColor(CARD_LINE);
        wrap.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        wrap.addView(item);
        return wrap;
    }

    // ------------------------------------------------------------ state

    private SharedPreferences prefs() {
        return getSharedPreferences("settings", MODE_PRIVATE);
    }

    private int uiSizeIndex() {
        int h = prefs().getInt("screenHeight", JvmActivity.DEFAULT_SCREEN_HEIGHT);
        for (int i = 0; i < UI_SIZES.length; i++) if (UI_SIZES[i] == h) return i;
        return 1;
    }

    private boolean gpu() {
        return prefs().getBoolean("gpu", false);
    }

    private boolean mobileLayout() {
        return prefs().getBoolean("mobileLayout", true);
    }

    private void refresh() {
        if (layoutChoice == null) return;
        select(layoutChoice, mobileLayout() ? 0 : 1);
        select(sizeChoice, uiSizeIndex());
        select(gpuChoice, gpu() ? 1 : 0);
        Properties creds = JagexCredentials.load(this);
        if (creds == null) {
            accountName.setText("No Jagex account");
            accountHint.setText("Log in with username and password in the game");
            accountAction.setText("Import");
        } else {
            accountName.setText(creds.getProperty("JX_DISPLAY_NAME", "Jagex account"));
            accountHint.setText("Jagex account, logs in automatically");
            accountAction.setText("Remove");
        }
    }

    private void launch(String mode) {
        startActivity(JvmActivity.intent(this, mode)
                .putExtra(JvmActivity.EXTRA_SCREEN_HEIGHT, UI_SIZES[uiSizeIndex()])
                .putExtra(JvmActivity.EXTRA_MOBILE_LAYOUT, mobileLayout())
                .putExtra(JvmActivity.EXTRA_GPU, gpu()));
    }

    private void onAccountAction() {
        Properties creds = JagexCredentials.load(this);
        if (creds == null) {
            new AlertDialog.Builder(this)
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
            new AlertDialog.Builder(this)
                    .setTitle("Remove Jagex account?")
                    .setMessage("RuneLite currently logs in as "
                            + creds.getProperty("JX_DISPLAY_NAME", "the imported character")
                            + ". Remove the imported credentials from this phone?")
                    .setPositiveButton("Remove", (d, w) -> {
                        JagexCredentials.delete(this);
                        refresh();
                    })
                    .setNegativeButton("Keep", null)
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQ_IMPORT_PLUGIN) {
            String error = LocalPlugins.importJar(this, data.getData());
            Toast.makeText(this, error == null ? "Plugin imported. Start Developer mode to use it." : error,
                    Toast.LENGTH_LONG).show();
            if (error == null) showPlugins();
            return;
        }
        if (requestCode != REQ_IMPORT_CREDENTIALS) return;
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
            String error = JagexCredentials.importFrom(this, in);
            Toast.makeText(this, error == null ? "Jagex account imported" : error, Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Could not read file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        refresh();
    }

    // ------------------------------------------------------------ plugins

    private void showPlugins() {
        try {
            LocalPlugins.installBuiltIn(this);
        } catch (IOException e) {
            Toast.makeText(this, "Could not install built-in plugins: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        java.util.List<String> builtIn = LocalPlugins.builtIn(this);
        java.util.List<String> names = LocalPlugins.installed(this);
        String[] rows = new String[names.size()];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = names.get(i) + (builtIn.contains(names.get(i)) ? "  (built in)" : "");
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("Local plugins")
                .setPositiveButton("Import .jar", (d, w) -> startActivityForResult(
                        new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE)
                                .setType("*/*"), REQ_IMPORT_PLUGIN))
                .setNegativeButton("Close", null);
        if (rows.length == 0) {
            b.setMessage("No local plugins yet.");
        } else {
            b.setItems(rows, (d, i) -> {
                String name = names.get(i);
                if (builtIn.contains(name)) {
                    new AlertDialog.Builder(this).setTitle(name)
                            .setMessage("This plugin comes with the app. Turn it off in RuneLite's plugin "
                                    + "list if you don't want it.")
                            .setPositiveButton("OK", (d2, w2) -> showPlugins())
                            .show();
                    return;
                }
                new AlertDialog.Builder(this).setTitle("Remove " + name + "?")
                        .setPositiveButton("Remove", (d2, w2) -> {
                            LocalPlugins.remove(this, name);
                            showPlugins();
                        })
                        .setNegativeButton("Keep", (d2, w2) -> showPlugins())
                        .show();
            });
        }
        b.show();
    }

    // ------------------------------------------------------------ log

    private String readLog() {
        File f = JvmActivity.logFile(this);
        if (!f.isFile()) return "No log yet. Press Play first.";
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Could not read log: " + e;
        }
    }

    private void showLog() {
        TextView t = text(readLog(), 11, TEXT);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        ScrollView s = new ScrollView(this);
        s.addView(t);
        s.post(() -> s.fullScroll(View.FOCUS_DOWN));
        new AlertDialog.Builder(this).setTitle("Last run").setView(s)
                .setPositiveButton("Close", null)
                .setNeutralButton("Share", (d, w) -> shareLog())
                .show();
    }

    private void shareLog() {
        String log = readLog();
        if (log.length() > 200_000) log = log.substring(log.length() - 200_000);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "RuneLite Mobile log")
                .putExtra(Intent.EXTRA_TEXT, log);
        startActivity(Intent.createChooser(send, "Share log"));
    }

    // ------------------------------------------------------------ widgets

    private interface Choice {
        void chosen(int index);
    }

    /** Row of chips where exactly one is selected. */
    private LinearLayout segmented(String[] labels, Choice onChoose) {
        LinearLayout row = new LinearLayout(this);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView c = chip(labels[i], false);
            c.setOnClickListener(v -> onChoose.chosen(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (i > 0) lp.leftMargin = dp(8);
            row.addView(c, lp);
        }
        return row;
    }

    private void select(LinearLayout segmented, int index) {
        for (int i = 0; i < segmented.getChildCount(); i++) {
            TextView c = (TextView) segmented.getChildAt(i);
            boolean on = i == index;
            c.setBackground(round(on ? 0x33DC8A00 : 0xFF2B2B2B, dp(10), on ? ORANGE : 0));
            c.setTextColor(on ? ORANGE : TEXT);
        }
    }

    private TextView chip(String label, boolean on) {
        TextView c = text(label, 14, on ? ORANGE : TEXT);
        c.setGravity(Gravity.CENTER);
        c.setPadding(dp(16), dp(8), dp(16), dp(8));
        c.setBackground(round(0xFF2B2B2B, dp(10), 0));
        return c;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(round(CARD, dp(16), 0));
        return c;
    }

    private TextView label(String s) {
        TextView t = text(s, 11, MUTED);
        t.setLetterSpacing(0.12f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams marginTop(int px) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = px;
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
