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
        // If LD_LIBRARY_PATH names a dir holding libjvm.so, libjli re-execs the process to
        // apply it, which cannot work inside app_process. Keep it out of the environment and
        // set the linker's own search path instead.
        Os.unsetenv("LD_LIBRARY_PATH");
        String nativeDir = ctx.getApplicationInfo().nativeLibraryDir;
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

        log.line("argv: " + String.join(" ", argv));
        return NativeBridge.launchJvm(new File(lib, "libjli.so").getAbsolutePath(),
                argv.toArray(new String[0]));
    }
}
