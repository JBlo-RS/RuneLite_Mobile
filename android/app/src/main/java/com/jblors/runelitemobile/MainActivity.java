package com.jblors.runelitemobile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
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
        root.setPadding(32, 48, 32, 32);

        TextView title = new TextView(this);
        title.setText("RuneLite Mobile " + BuildConfig.VERSION_NAME + "\nMilestone 1: JVM smoke test");
        title.setTextSize(18);
        root.addView(title);

        LinearLayout buttons = new LinearLayout(this);
        Button run = new Button(this);
        run.setText("Run JVM probe");
        run.setOnClickListener(v -> startActivity(new Intent(this, JvmActivity.class)));
        Button share = new Button(this);
        share.setText("Share log");
        share.setOnClickListener(v -> shareLog());
        buttons.addView(run);
        buttons.addView(share);
        root.addView(buttons);

        logView = new TextView(this);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(11);
        logView.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        logView.setText(readLog());
    }

    private String readLog() {
        File f = JvmActivity.logFile(this);
        if (!f.isFile()) return "No log yet. Tap \"Run JVM probe\".";
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
