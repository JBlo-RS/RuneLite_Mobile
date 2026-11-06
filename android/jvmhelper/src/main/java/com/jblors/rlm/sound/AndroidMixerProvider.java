package com.jblors.rlm.sound;

import javax.sound.sampled.Mixer;
import javax.sound.sampled.spi.MixerProvider;

/**
 * Java Sound provider for Android (registered in META-INF/services). The JRE has no Java Sound
 * backend on Android, so without this the game finds no audio device and stays silent.
 * Active when -Drlm.sound.lib names librlmsound.so (set by the app).
 */
public final class AndroidMixerProvider extends MixerProvider {
    private static final Mixer.Info[] NONE = new Mixer.Info[0];

    @Override
    public Mixer.Info[] getMixerInfo() {
        return AndroidSourceDataLine.nativeLoaded() ? new Mixer.Info[]{AndroidMixer.INFO} : NONE;
    }

    @Override
    public Mixer getMixer(Mixer.Info info) {
        if (AndroidSourceDataLine.nativeLoaded() && (info == null || info.equals(AndroidMixer.INFO))) {
            return AndroidMixer.INSTANCE;
        }
        throw new IllegalArgumentException("not supported: " + info);
    }
}
