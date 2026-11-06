package com.jblors.runelitemobile;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Local (side-loaded) RuneLite plugins. RuneLite in developer mode loads every jar in
 * ~/.runelite/sideloaded-plugins; the JVM's home is files/home. Plugins built into the app
 * (assets/plugins, from the repo's plugins/ folder) are copied there before each start.
 */
final class LocalPlugins {
    private LocalPlugins() {}

    static File dir(Context ctx) {
        return new File(ctx.getFilesDir(), "home/.runelite/sideloaded-plugins");
    }

    /** Jar names bundled with the app. */
    static List<String> builtIn(Context ctx) {
        try {
            String[] names = ctx.getAssets().list("plugins");
            List<String> out = new ArrayList<>();
            if (names != null) for (String n : names) if (n.endsWith(".jar")) out.add(n);
            return out;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /** Copies (updates) the built-in plugins into the side-load folder. */
    static void installBuiltIn(Context ctx) throws IOException {
        File d = dir(ctx);
        d.mkdirs();
        for (String n : builtIn(ctx)) JreInstaller.copyAsset(ctx, "plugins/" + n, new File(d, n));
    }

    /** Installed jar names, sorted. */
    static List<String> installed(Context ctx) {
        String[] names = dir(ctx).list((f, n) -> n.endsWith(".jar"));
        List<String> out = new ArrayList<>();
        if (names != null) out.addAll(Arrays.asList(names));
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    static boolean remove(Context ctx, String name) {
        return new File(dir(ctx), name).delete();
    }

    /** Copies a picked jar into the side-load folder. Returns an error message, or null. */
    static String importJar(Context ctx, Uri uri) {
        ContentResolver cr = ctx.getContentResolver();
        String name = displayName(cr, uri);
        if (name == null || !name.toLowerCase().endsWith(".jar")) {
            return "That isn't a .jar file" + (name == null ? "" : " (" + name + ")");
        }
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        if (builtIn(ctx).contains(name)) return name + " is already built into the app";
        File d = dir(ctx);
        d.mkdirs();
        File tmp = new File(d, name + ".part");
        try {
            try (InputStream in = cr.openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
                if (in == null) return "Could not open the file";
                byte[] buf = new byte[64 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
            if (!hasClasses(tmp)) {
                tmp.delete();
                return name + " has no Java classes in it, so it isn't a plugin";
            }
            File dest = new File(d, name);
            if (!tmp.renameTo(dest)) {
                tmp.delete();
                return "Could not save " + name;
            }
            return null;
        } catch (IOException e) {
            tmp.delete();
            return "Could not read the file: " + e.getMessage();
        }
    }

    private static boolean hasClasses(File jar) {
        try (ZipInputStream z = new ZipInputStream(new java.io.FileInputStream(jar))) {
            for (ZipEntry e; (e = z.getNextEntry()) != null; ) {
                if (e.getName().endsWith(".class")) return true;
            }
        } catch (IOException ignored) {
            // not a zip
        }
        return false;
    }

    private static String displayName(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (RuntimeException ignored) {
            // fall back to the path
        }
        String p = uri.getLastPathSegment();
        return p == null ? null : p.substring(p.lastIndexOf('/') + 1);
    }
}
