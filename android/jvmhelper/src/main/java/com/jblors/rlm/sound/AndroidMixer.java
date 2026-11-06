package com.jblors.rlm.sound;

import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

/** The phone's audio output: any number of SourceDataLines, each an AAudio stream. */
final class AndroidMixer implements Mixer {
    static final Mixer.Info INFO = new Mixer.Info("Android", "RuneLite Mobile",
            "Android audio output (AAudio)", "1.0") {};
    static final AndroidMixer INSTANCE = new AndroidMixer();

    private static final int ANY = AudioSystem.NOT_SPECIFIED;
    private static final DataLine.Info SOURCE_INFO = new DataLine.Info(SourceDataLine.class, formats(), ANY, ANY);

    private final List<AndroidSourceDataLine> open = new ArrayList<>();

    private AndroidMixer() {}

    /** The PCM formats AndroidSourceDataLine converts: 8/16-bit, signed or not, either endianness. */
    private static AudioFormat[] formats() {
        List<AudioFormat> out = new ArrayList<>();
        for (int ch = 1; ch <= 2; ch++) {
            for (boolean big : new boolean[]{false, true}) {
                out.add(new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, ANY, 16, ch, ch * 2, ANY, big));
                out.add(new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, ANY, 16, ch, ch * 2, ANY, big));
            }
            out.add(new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, ANY, 8, ch, ch, ANY, false));
            out.add(new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED, ANY, 8, ch, ch, ANY, false));
        }
        return out.toArray(new AudioFormat[0]);
    }

    static boolean supports(AudioFormat f) {
        for (AudioFormat s : SOURCE_INFO.getFormats()) {
            if (f.matches(s)) return true;
        }
        return false;
    }

    synchronized void opened(AndroidSourceDataLine l) {
        open.add(l);
    }

    synchronized void closed(AndroidSourceDataLine l) {
        open.remove(l);
    }

    @Override
    public Mixer.Info getMixerInfo() {
        return INFO;
    }

    @Override
    public Line.Info[] getSourceLineInfo() {
        return new Line.Info[]{SOURCE_INFO};
    }

    @Override
    public Line.Info[] getTargetLineInfo() {
        return new Line.Info[0];
    }

    @Override
    public Line.Info[] getSourceLineInfo(Line.Info info) {
        return isLineSupported(info) ? getSourceLineInfo() : new Line.Info[0];
    }

    @Override
    public Line.Info[] getTargetLineInfo(Line.Info info) {
        return new Line.Info[0];
    }

    @Override
    public boolean isLineSupported(Line.Info info) {
        if (info == null || !info.getLineClass().isAssignableFrom(SourceDataLine.class)) return false;
        if (info instanceof DataLine.Info) {
            for (AudioFormat f : ((DataLine.Info) info).getFormats()) {
                if (!supports(f)) return false;
            }
        }
        return true;
    }

    @Override
    public Line getLine(Line.Info info) throws LineUnavailableException {
        if (!isLineSupported(info)) throw new IllegalArgumentException("not supported: " + info);
        AudioFormat format = null;
        int bufferSize = ANY;
        if (info instanceof DataLine.Info) {
            DataLine.Info d = (DataLine.Info) info;
            AudioFormat[] fs = d.getFormats();
            if (fs.length > 0) format = fs[0];
            bufferSize = d.getMaxBufferSize();
        }
        return new AndroidSourceDataLine(this, format, bufferSize);
    }

    @Override
    public int getMaxLines(Line.Info info) {
        return isLineSupported(info) ? ANY : 0;
    }

    @Override
    public synchronized Line[] getSourceLines() {
        return open.toArray(new Line[0]);
    }

    @Override
    public Line[] getTargetLines() {
        return new Line[0];
    }

    @Override
    public void synchronize(Line[] lines, boolean maintainSync) {
        throw new IllegalArgumentException("synchronization not supported");
    }

    @Override
    public void unsynchronize(Line[] lines) {
        throw new IllegalArgumentException("synchronization not supported");
    }

    @Override
    public boolean isSynchronizationSupported(Line[] lines, boolean maintainSync) {
        return false;
    }

    // --- Line (the mixer itself is always open)

    @Override
    public Line.Info getLineInfo() {
        return new Line.Info(Mixer.class);
    }

    @Override
    public void open() {}

    @Override
    public void close() {}

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public Control[] getControls() {
        return new Control[0];
    }

    @Override
    public boolean isControlSupported(Control.Type control) {
        return false;
    }

    @Override
    public Control getControl(Control.Type control) {
        throw new IllegalArgumentException("not supported: " + control);
    }

    @Override
    public void addLineListener(LineListener listener) {}

    @Override
    public void removeLineListener(LineListener listener) {}
}
