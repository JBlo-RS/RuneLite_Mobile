package com.jblors.runelitemobile;

import android.content.Context;
import android.content.res.AssetManager;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Unpacks the bundled JRE (assets/jre.tar.xz) into app storage when it changes. */
final class JreInstaller {
    private static final String MARKER = ".installed-sha256";

    private JreInstaller() {}

    interface Progress {
        void onProgress(String message);
    }

    static File jreHome(Context ctx) {
        return new File(ctx.getFilesDir(), "jre");
    }

    static File ensureInstalled(Context ctx, Progress progress) throws IOException {
        AssetManager assets = ctx.getAssets();
        String wanted = readAsset(assets, "jre.sha256").trim();
        File home = jreHome(ctx);
        File marker = new File(home, MARKER);
        if (marker.isFile() && readFile(marker).trim().equals(wanted)) {
            progress.onProgress("JRE already installed (" + wanted.substring(0, 12) + ")");
            return home;
        }

        progress.onProgress("Extracting JRE…");
        deleteRecursively(home);
        File staging = new File(ctx.getFilesDir(), "jre.partial");
        deleteRecursively(staging);
        if (!staging.mkdirs()) throw new IOException("mkdir " + staging);

        String root = staging.getCanonicalPath() + File.separator;
        int files = 0;
        try (InputStream raw = new BufferedInputStream(assets.open("jre.tar.xz"), 1 << 16);
             TarArchiveInputStream tar = new TarArchiveInputStream(new XZCompressorInputStream(raw))) {
            TarArchiveEntry e;
            byte[] buf = new byte[1 << 16];
            while ((e = tar.getNextEntry()) != null) {
                File out = new File(staging, e.getName());
                // Refuse entries that would escape the target directory
                if (!out.getCanonicalPath().startsWith(root) && !out.getCanonicalPath().equals(staging.getCanonicalPath()))
                    throw new IOException("Bad tar entry: " + e.getName());
                if (e.isDirectory()) {
                    out.mkdirs();
                } else if (e.isSymbolicLink()) {
                    out.getParentFile().mkdirs();
                    Files.createSymbolicLink(out.toPath(), new File(e.getLinkName()).toPath());
                } else {
                    out.getParentFile().mkdirs();
                    try (OutputStream os = new FileOutputStream(out)) {
                        int n;
                        while ((n = tar.read(buf)) > 0) os.write(buf, 0, n);
                    }
                    if ((e.getMode() & 0100) != 0) out.setExecutable(true, true);
                    if (++files % 50 == 0) progress.onProgress("Extracting JRE… " + files + " files");
                }
            }
        }
        try (OutputStream os = new FileOutputStream(new File(staging, MARKER))) {
            os.write(wanted.getBytes(StandardCharsets.UTF_8));
        }
        if (!staging.renameTo(home)) throw new IOException("rename " + staging + " -> " + home);
        progress.onProgress("JRE extracted: " + files + " files");
        return home;
    }

    /** Copies an asset to a file, replacing it. */
    static File copyAsset(Context ctx, String name, File dest) throws IOException {
        dest.getParentFile().mkdirs();
        try (InputStream in = ctx.getAssets().open(name); OutputStream os = new FileOutputStream(dest)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        return dest;
    }

    private static String readAsset(AssetManager assets, String name) throws IOException {
        try (InputStream in = assets.open(name)) {
            return readAll(in);
        }
    }

    private static String readFile(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toString("UTF-8");
    }

    static void deleteRecursively(File f) {
        if (!f.exists() && !Files.isSymbolicLink(f.toPath())) return;
        if (f.isDirectory() && !Files.isSymbolicLink(f.toPath())) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursively(k);
        }
        f.delete();
    }
}
