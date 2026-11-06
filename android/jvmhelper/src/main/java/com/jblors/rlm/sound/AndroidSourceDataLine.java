package com.jblors.rlm.sound;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * A SourceDataLine that plays through an AAudio stream (android/app/src/main/cpp/rlm_sound.c).
 * Audio is converted to 16-bit native-endian PCM, which AAudio plays at the line's sample rate.
 *
 * <p>The game measures how far ahead it is as (buffer size - available()), so available() is
 * the free space in a buffer of exactly the size the line was opened with.
 */
public final class AndroidSourceDataLine implements SourceDataLine {
    private static final long WRITE_TIMEOUT_NS = 100_000_000L;
    private static Boolean loaded;

    private final AndroidMixer mixer;
    private final AudioFormat defaultFormat;
    private final int defaultBufferSize;
    private final List<LineListener> listeners = new CopyOnWriteArrayList<>();

    private AudioFormat format;
    private int bufferSize; // bytes, in the line's format
    private int bufferFrames;
    private volatile long handle;
    private volatile boolean running;
    private byte[] converted = new byte[0];

    /** True if librlmsound.so is available (the app sets -Drlm.sound.lib). */
    static synchronized boolean nativeLoaded() {
        if (loaded == null) {
            String lib = System.getProperty("rlm.sound.lib");
            loaded = Boolean.FALSE;
            if (lib != null) {
                try {
                    System.load(lib);
                    loaded = Boolean.TRUE;
                } catch (Throwable t) {
                    System.out.println("rlm-sound: cannot load " + lib + ": " + t);
                }
            }
        }
        return loaded;
    }

    AndroidSourceDataLine(AndroidMixer mixer, AudioFormat format, int bufferSize) {
        this.mixer = mixer;
        this.defaultFormat = format != null && format.getSampleRate() != AudioSystem.NOT_SPECIFIED
                ? format : new AudioFormat(44100f, 16, 2, true, false);
        this.defaultBufferSize = bufferSize;
        this.format = defaultFormat;
    }

    // ------------------------------------------------------------ open / close

    @Override
    public void open() throws LineUnavailableException {
        open(defaultFormat, defaultBufferSize);
    }

    @Override
    public void open(AudioFormat format) throws LineUnavailableException {
        open(format, AudioSystem.NOT_SPECIFIED);
    }

    @Override
    public synchronized void open(AudioFormat format, int bufferSize) throws LineUnavailableException {
        if (handle != 0) return;
        if (!AndroidMixer.supports(format) || format.getSampleRate() <= 0) {
            throw new IllegalArgumentException("unsupported format: " + format);
        }
        int frameSize = format.getFrameSize();
        int rate = Math.round(format.getSampleRate());
        if (bufferSize <= 0) bufferSize = rate / 2 * frameSize; // half a second
        int frames = Math.max(256, bufferSize / frameSize);
        long h = open0(rate, format.getChannels(), frames);
        if (h <= 0) {
            throw new LineUnavailableException("Android audio: " + errorText0((int) h));
        }
        this.format = format;
        this.bufferFrames = frames;
        this.bufferSize = frames * frameSize;
        this.handle = h;
        mixer.opened(this);
        System.out.println("rlm-sound: line open: " + format + ", buffer " + frames + " frames");
        fire(LineEvent.Type.OPEN);
    }

    @Override
    public synchronized void close() {
        long h = handle;
        if (h == 0) return;
        running = false;
        handle = 0;
        close0(h);
        mixer.closed(this);
        fire(LineEvent.Type.CLOSE);
    }

    @Override
    public boolean isOpen() {
        return handle != 0;
    }

    // ------------------------------------------------------------ playback

    @Override
    public int write(byte[] b, int off, int len) {
        int frameSize = format.getFrameSize();
        len -= len % frameSize;
        if (len <= 0 || handle == 0) return 0;
        if (off < 0 || off + len > b.length) throw new ArrayIndexOutOfBoundsException();
        int frames = len / frameSize;
        byte[] pcm = convert(b, off, frames);
        int pcmOff = isNative() ? off : 0;
        int outFrameSize = format.getChannels() * 2;
        int done = 0;
        while (done < frames) {
            long h = handle;
            if (h == 0) break;
            int r = write0(h, pcm, pcmOff + done * outFrameSize, frames - done, WRITE_TIMEOUT_NS);
            if (r < 0) {
                System.out.println("rlm-sound: write failed: " + errorText0(r));
                break;
            }
            done += r;
            // A stopped line doesn't play, so a full buffer would never drain
            if (r == 0 && !running) break;
        }
        return done * frameSize;
    }

    /** 16-bit signed little-endian (what Android plays) already: no conversion needed. */
    private boolean isNative() {
        return format.getSampleSizeInBits() == 16 && !format.isBigEndian()
                && AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding());
    }

    /** The data as 16-bit signed little-endian PCM (b itself if it already is). */
    private byte[] convert(byte[] b, int off, int frames) {
        if (isNative()) return b;
        int samples = frames * format.getChannels();
        if (converted.length < samples * 2) converted = new byte[samples * 2];
        byte[] o = converted;
        boolean unsigned = AudioFormat.Encoding.PCM_UNSIGNED.equals(format.getEncoding());
        if (format.getSampleSizeInBits() == 8) {
            for (int i = 0; i < samples; i++) {
                int v = unsigned ? (b[off + i] & 0xff) - 128 : b[off + i];
                o[2 * i] = 0;
                o[2 * i + 1] = (byte) v;
            }
        } else {
            boolean big = format.isBigEndian();
            for (int i = 0; i < samples; i++) {
                int lo = b[off + 2 * i + (big ? 1 : 0)], hi = b[off + 2 * i + (big ? 0 : 1)];
                if (unsigned) hi ^= 0x80;
                o[2 * i] = (byte) lo;
                o[2 * i + 1] = (byte) hi;
            }
        }
        return o;
    }

    @Override
    public int available() {
        long h = handle;
        if (h == 0) return 0;
        int free = bufferFrames - queued0(h);
        return Math.max(0, free) * format.getFrameSize();
    }

    @Override
    public void start() {
        long h = handle;
        if (h == 0 || running) return;
        running = true;
        start0(h);
        fire(LineEvent.Type.START);
    }

    @Override
    public void stop() {
        long h = handle;
        if (h == 0 || !running) return;
        running = false;
        stop0(h);
        fire(LineEvent.Type.STOP);
    }

    @Override
    public void flush() {
        long h = handle;
        if (h != 0) flush0(h);
    }

    @Override
    public void drain() {
        long h = handle;
        if (h == 0 || !running) return;
        long deadline = System.currentTimeMillis() + 1000
                + (long) (bufferFrames * 1000L / Math.max(1f, format.getSampleRate()));
        while (handle != 0 && running && queued0(h) > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isActive() {
        long h = handle;
        return running && h != 0 && queued0(h) > 0;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public int getBufferSize() {
        return handle != 0 ? bufferSize : defaultBufferSize;
    }

    @Override
    public int getFramePosition() {
        return (int) getLongFramePosition();
    }

    @Override
    public long getLongFramePosition() {
        long h = handle;
        return h == 0 ? 0 : framesPlayed0(h);
    }

    @Override
    public long getMicrosecondPosition() {
        return (long) (getLongFramePosition() * 1_000_000.0 / format.getSampleRate());
    }

    @Override
    public float getLevel() {
        return AudioSystem.NOT_SPECIFIED;
    }

    // ------------------------------------------------------------ Line

    @Override
    public Line.Info getLineInfo() {
        return new DataLine.Info(SourceDataLine.class, format, getBufferSize());
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
    public void addLineListener(LineListener listener) {
        if (listener != null) listeners.add(listener);
    }

    @Override
    public void removeLineListener(LineListener listener) {
        listeners.remove(listener);
    }

    private void fire(LineEvent.Type type) {
        if (listeners.isEmpty()) return;
        LineEvent e = new LineEvent(this, type, getLongFramePosition());
        for (LineListener l : listeners) {
            try {
                l.update(e);
            } catch (RuntimeException ignored) {
                // a listener's problem
            }
        }
    }

    // ------------------------------------------------------------ native (librlmsound.so)

    private static native long open0(int rate, int channels, int bufferFrames);

    private static native String errorText0(int result);

    private static native int write0(long handle, byte[] data, int off, int frames, long timeoutNanos);

    private static native int queued0(long handle);

    private static native long framesPlayed0(long handle);

    private static native int bufferFrames0(long handle);

    private static native void start0(long handle);

    private static native void stop0(long handle);

    private static native void flush0(long handle);

    private static native void close0(long handle);
}
