package com.jblors.runelitemobile;

/** JNI entry points implemented in src/main/cpp/rlm_native.c. */
final class NativeBridge {
    static {
        System.loadLibrary("rlm");
    }

    private NativeBridge() {}

    /** Sets the dynamic linker's search path (not the environment variable). */
    static native void setLdLibraryPath(String path);

    /** dlopen(path, RTLD_GLOBAL); returns null on success, else the dlerror() text. */
    static native String dlopen(String path);

    /** chdir(path); returns 0 or errno. */
    static native int chdir(String path);

    /** Sends fds 1 and 2 to the given file (appending) and to logcat; null on success. */
    static native String redirectStdio(String logPath);

    /** Runs JLI_Launch from the given libjli.so; blocks until the Java main thread ends. */
    static native int launchJvm(String libjliPath, String[] argv);
}
