package com.jblors.runelitemobile;

/**
 * App side of the JVM's sound output (src/main/cpp/rlm_sound.c). The JVM loads the same
 * librlmsound.so for com.jblors.rlm.sound.AndroidSourceDataLine; this side only mutes it.
 */
final class Sound {
    private static boolean loaded;

    private Sound() {}

    /** Mutes the game's sound (keeps its timing; plays silence), e.g. while in the background. */
    static synchronized void setMuted(boolean muted) {
        if (!loaded) {
            try {
                System.loadLibrary("rlmsound");
                loaded = true;
            } catch (UnsatisfiedLinkError e) {
                return;
            }
        }
        setMuted0(muted);
    }

    private static native void setMuted0(boolean muted);
}
