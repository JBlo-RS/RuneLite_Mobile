package com.jblors.runelitemobile;

import android.content.Context;
import android.system.ErrnoException;
import android.system.Os;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Prepares the process and starts the bundled JVM in-process via JLI_Launch. */
final class JvmLauncher {
    private JvmLauncher() {}

    interface Log {
        void line(String s);
    }

    /**
     * Blocks until the Java main thread returns. Note that System.exit() in Java ends this whole
     * process, which is why the caller runs in the dedicated :jvm process.
     */
    static int launch(Context ctx, File jreHome, List<String> appArgs, int maxHeapMb, Log log)
            throws ErrnoException {
        File lib = new File(jreHome, "lib");
        File server = new File(lib, "server");
        File home = new File(ctx.getFilesDir(), "home");
        home.mkdirs();
        File tmp = ctx.getCacheDir();

        Os.setenv("JAVA_HOME", jreHome.getAbsolutePath(), true);
        Os.setenv("HOME", home.getAbsolutePath(), true);
        Os.setenv("TMPDIR", tmp.getAbsolutePath(), true);
        String nativeDir = ctx.getApplicationInfo().nativeLibraryDir;
        installXawtStub(new File(nativeDir, "libawt_xawt.so"), new File(lib, "libawt_xawt.so"), log);
        // libjli re-execs the process (impossible inside app_process) when LD_LIBRARY_PATH
        // contains a libjvm.so dir, unless the value *starts* with libjvm's own dir.
        // Cacio's CTCScreen also searches this variable for libpojavexec_awt.so (nativeDir).
        Os.setenv("LD_LIBRARY_PATH", server + ":" + lib + ":" + nativeDir, true);
        // The environment variable does not affect the already-running linker; set its path too
        NativeBridge.setLdLibraryPath(server + ":" + lib + ":/system/lib64:/vendor/lib64:" + nativeDir);

        // Preload in dependency order with absolute paths so later bare-soname lookups hit.
        String[] preload = {
                "libjli.so", "server/libjvm.so", "libverify.so", "libjava.so", "libzip.so",
                "libnet.so", "libnio.so", "libawt.so", "libawt_headless.so", "libfreetype.so",
                "libfontmanager.so",
        };
        for (String so : preload) {
            String err = NativeBridge.dlopen(new File(lib, so).getAbsolutePath());
            log.line(err == null ? "dlopen " + so + ": ok" : "dlopen " + so + ": FAILED " + err);
        }

        NativeBridge.chdir(home.getAbsolutePath());

        List<String> argv = new ArrayList<>();
        argv.add("java"); // no slash: the JRE's Android patch then uses $JAVA_HOME
        argv.add("-Djava.home=" + jreHome.getAbsolutePath());
        argv.add("-Duser.home=" + home.getAbsolutePath());
        argv.add("-Djava.io.tmpdir=" + tmp.getAbsolutePath());
        argv.add("-Xms64m");
        argv.add("-Xmx" + maxHeapMb + "m");
        argv.add("-XX:ActiveProcessorCount=" + Runtime.getRuntime().availableProcessors());
        // POSIX_SPAWN would exec jspawnhelper, which Android forbids from app storage
        argv.add("-Djdk.lang.Process.launchMechanism=FORK");
        argv.add("-Dos.version=Android-" + android.os.Build.VERSION.RELEASE);
        argv.add("-Duser.timezone=" + java.util.TimeZone.getDefault().getID());
        argv.addAll(appArgs);

        log.line("argv: " + String.join("\n    ", argv));
        return NativeBridge.launchJvm(new File(lib, "libjli.so").getAbsolutePath(),
                argv.toArray(new String[0]));
    }

    /**
     * libawt loads lib/libawt_xawt.so next to itself whenever the JVM is not headless; our JRE
     * is headless-only, so put the app's stand-in there (see src/main/cpp/xawt_stub.c).
     */
    private static void installXawtStub(File src, File dst, Log log) {
        try {
            if (dst.length() != src.length()) {
                java.nio.file.Files.copy(src.toPath(), dst.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                log.line("installed libawt_xawt.so stub");
            }
        } catch (java.io.IOException e) {
            log.line("could not install libawt_xawt.so stub: " + e);
        }
    }

    /**
     * JVM options that put AWT/Swing on Caciocavallo's managed toolkit with a virtual screen of
     * the given size. After PojavLauncher's Tools.getCacioJavaArgs (Java 17 branch).
     */
    static List<String> cacioArgs(File cacioDir, int width, int height) {
        List<String> a = new ArrayList<>();
        a.add("-Djava.awt.headless=false");
        a.add("-Dcacio.managed.screensize=" + width + "x" + height);
        a.add("-Dcacio.font.fontmanager=sun.awt.X11FontManager");
        a.add("-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler");
        a.add("-Dswing.defaultlaf=javax.swing.plaf.metal.MetalLookAndFeel");
        a.add("-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit");
        a.add("-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment");
        a.add("-Djava.system.class.loader=com.github.caciocavallosilano.cacio.ctc.CTCPreloadClassLoader");
        for (String pkg : new String[]{
                "java.desktop/java.awt", "java.desktop/java.awt.peer", "java.desktop/sun.awt.image",
                "java.desktop/sun.java2d", "java.desktop/java.awt.dnd.peer", "java.desktop/sun.awt",
                "java.desktop/sun.awt.event", "java.desktop/sun.awt.datatransfer",
                "java.desktop/sun.font", "java.base/sun.security.action"}) {
            a.add("--add-exports=" + pkg + "=ALL-UNNAMED");
        }
        for (String pkg : new String[]{
                "java.base/java.util", "java.desktop/java.awt", "java.desktop/sun.font",
                "java.desktop/sun.java2d", "java.base/java.lang.reflect", "java.base/java.net"}) {
            a.add("--add-opens=" + pkg + "=ALL-UNNAMED");
        }
        StringBuilder boot = new StringBuilder("-Xbootclasspath/a");
        File[] jars = cacioDir.listFiles((d, n) -> n.endsWith(".jar"));
        if (jars != null) {
            java.util.Arrays.sort(jars);
            for (File j : jars) boot.append(':').append(j.getAbsolutePath());
        }
        a.add(boot.toString());
        return a;
    }
}
