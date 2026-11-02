package com.jblors.runelitemobile;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * Jagex Account credentials, as RuneLite writes them on a PC with
 * --insecure-write-credentials (credentials.properties). When the Jagex Launcher starts
 * RuneLite it passes these same values as JX_* environment variables, and the game client
 * reads them from the environment; we set them for the JVM process the same way.
 *
 * <p>Stored in app-private storage only.
 */
final class JagexCredentials {
    /** Keys the game client reads (seen in the injected client), in the order we set them. */
    static final String[] KEYS = {
            "JX_SESSION_ID", "JX_CHARACTER_ID", "JX_DISPLAY_NAME", "JX_REFRESH_TOKEN", "JX_ACCESS_TOKEN",
    };

    private JagexCredentials() {}

    private static File file(Context ctx) {
        return new File(new File(ctx.getFilesDir(), "jagex"), "credentials.properties");
    }

    /** The imported credentials, or null if none. */
    static Properties load(Context ctx) {
        File f = file(ctx);
        if (!f.isFile()) return null;
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(f)) {
            p.load(in);
            return p;
        } catch (IOException e) {
            return null;
        }
    }

    /** Validates and stores a credentials.properties stream; returns null or an error message. */
    static String importFrom(Context ctx, InputStream in) throws IOException {
        Properties src = new Properties();
        src.load(in);
        if (isBlank(src.getProperty("JX_SESSION_ID")) || isBlank(src.getProperty("JX_CHARACTER_ID"))) {
            return "That file has no JX_SESSION_ID / JX_CHARACTER_ID. Pick the credentials.properties "
                    + "RuneLite wrote with --insecure-write-credentials.";
        }
        Properties keep = new Properties();
        for (String k : KEYS) {
            String v = src.getProperty(k);
            if (!isBlank(v)) keep.setProperty(k, v.trim());
        }
        File f = file(ctx);
        f.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            keep.store(out, "Imported by RuneLite Mobile");
        }
        return null;
    }

    static void delete(Context ctx) {
        file(ctx).delete();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
