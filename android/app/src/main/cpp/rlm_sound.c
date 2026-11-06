// Sound output for the JVM: natives of com.jblors.rlm.sound.AndroidSourceDataLine (android/jvmhelper),
// the javax.sound.sampled line the game writes its audio to. The JRE has no Java Sound backend
// on Android (it would use ALSA), so each line is an AAudio output stream of 16-bit PCM.
//
// Each line has its own ring buffer of exactly the size the game asked for. write0 fills it;
// AAudio's data callback empties it (playing silence if it runs dry). How much is queued, and
// how much has been played, are counted here rather than taken from AAudio's frame counters:
// the game measures playback progress from them, and closes and reopens the line every few
// seconds when it sees none.
//
// The app (ART) loads this same file too, to mute sound while the app is in the background
// (Java_com_jblors_runelitemobile_Sound_setMuted0); both VMs share this library's globals.

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <jni.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define TAG "rlm-sound"
// stdout goes to the app's log (see rlm_native.c); logcat gets it too
#define LOG(...) do { __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__); \
    printf("rlm-sound: " __VA_ARGS__); printf("\n"); fflush(stdout); } while (0)

static atomic_int muted;

struct line {
    AAudioStream *stream;
    int rate, channels, buffer_frames;
    atomic_int started;
    atomic_int disconnected;  // set by the error callback; the next call reopens the stream
    pthread_mutex_t lock;     // open/close/start/stop vs. each other (not the callback)

    // Ring buffer of buffer_frames frames. Producer: write0. Consumer: the data callback.
    int16_t *ring;
    _Atomic int64_t written;  // frames put into the ring, ever
    _Atomic int64_t played;   // frames taken out of the ring by AAudio, ever
    _Atomic int64_t flush_to; // >= 0: the callback should skip ahead to this many frames
    // Diagnostics
    _Atomic int64_t callbacks, underrun_frames;
    int64_t opened_ns, last_report_ns;
    int reports;
};

static int64_t now_ns(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return t.tv_sec * 1000000000LL + t.tv_nsec;
}

static aaudio_data_callback_result_t on_audio(AAudioStream *s, void *user, void *data, int32_t frames) {
    struct line *l = user;
    int16_t *out = data;
    int ch = l->channels;
    atomic_fetch_add(&l->callbacks, 1);

    int64_t skip = atomic_exchange(&l->flush_to, -1);
    if (skip >= 0) atomic_store(&l->played, skip);

    int64_t played = atomic_load(&l->played);
    int64_t avail = atomic_load(&l->written) - played;
    int32_t n = avail < frames ? (int32_t) avail : frames;
    if (n < 0) n = 0;
    int mute = atomic_load(&muted);
    for (int32_t i = 0; i < n; i++) {
        int64_t pos = (played + i) % l->buffer_frames;
        if (mute) {
            memset(out + (size_t) i * ch, 0, (size_t) ch * 2);
        } else {
            memcpy(out + (size_t) i * ch, l->ring + pos * ch, (size_t) ch * 2);
        }
    }
    if (n < frames) {
        memset(out + (size_t) n * ch, 0, (size_t) (frames - n) * ch * 2);
        atomic_fetch_add(&l->underrun_frames, frames - n);
    }
    atomic_store(&l->played, played + n);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

static void on_error(AAudioStream *s, void *user, aaudio_result_t error) {
    // Usually AAUDIO_ERROR_DISCONNECTED: the output device changed (headphones, Bluetooth).
    // The stream can't be reopened from this callback; the next write does it.
    atomic_store(&((struct line *) user)->disconnected, 1);
}

static aaudio_result_t open_stream(struct line *l) {
    AAudioStreamBuilder *b;
    aaudio_result_t r = AAudio_createStreamBuilder(&b);
    if (r != AAUDIO_OK) return r;
    AAudioStreamBuilder_setDirection(b, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setSharingMode(b, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setPerformanceMode(b, AAUDIO_PERFORMANCE_MODE_NONE);
    AAudioStreamBuilder_setFormat(b, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setSampleRate(b, l->rate);
    AAudioStreamBuilder_setChannelCount(b, l->channels);
    AAudioStreamBuilder_setDataCallback(b, on_audio, l);
    AAudioStreamBuilder_setErrorCallback(b, on_error, l);
    r = AAudioStreamBuilder_openStream(b, &l->stream);
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK) {
        l->stream = NULL;
        return r;
    }
    atomic_store(&l->disconnected, 0);
    LOG("stream open: %d Hz x%d (asked %d Hz x%d), burst %d, buffer %d/%d frames, device %d",
        AAudioStream_getSampleRate(l->stream), AAudioStream_getChannelCount(l->stream), l->rate, l->channels,
        AAudioStream_getFramesPerBurst(l->stream), AAudioStream_getBufferSizeInFrames(l->stream),
        AAudioStream_getBufferCapacityInFrames(l->stream), AAudioStream_getDeviceId(l->stream));
    return AAUDIO_OK;
}

static void close_stream(struct line *l) {
    if (!l->stream) return;
    AAudioStream_requestStop(l->stream);
    AAudioStream_close(l->stream);
    l->stream = NULL;
}

/** Reopens the stream after the output device changed. Call with l->lock held. */
static void reconnect_locked(struct line *l) {
    LOG("output device changed; reopening the stream");
    close_stream(l);
    if (open_stream(l) == AAUDIO_OK && atomic_load(&l->started)) {
        aaudio_result_t r = AAudioStream_requestStart(l->stream);
        if (r != AAUDIO_OK) LOG("restart failed: %s", AAudio_convertResultToText(r));
    }
}

static void check_reconnect(struct line *l) {
    if (!atomic_load(&l->disconnected) && l->stream) return;
    pthread_mutex_lock(&l->lock);
    if (atomic_load(&l->disconnected) || !l->stream) reconnect_locked(l);
    pthread_mutex_unlock(&l->lock);
}

static int64_t queued(struct line *l) {
    int64_t skip = atomic_load(&l->flush_to);
    int64_t q = atomic_load(&l->written) - (skip >= 0 ? skip : atomic_load(&l->played));
    return q < 0 ? 0 : q;
}

/** Logs how the line is doing: often at first, then rarely. */
static void report(struct line *l) {
    int64_t t = now_ns();
    int64_t every = l->reports < 5 ? 2000000000LL : 60000000000LL;
    if (t - l->last_report_ns < every) return;
    l->last_report_ns = t;
    l->reports++;
    LOG("line %p after %lld ms: written %lld, played %lld, queued %lld, callbacks %lld, silence %lld frames, "
        "state %s%s", (void *) l, (long long) ((t - l->opened_ns) / 1000000), (long long) atomic_load(&l->written),
        (long long) atomic_load(&l->played), (long long) queued(l), (long long) atomic_load(&l->callbacks),
        (long long) atomic_load(&l->underrun_frames),
        l->stream ? AAudio_convertStreamStateToText(AAudioStream_getState(l->stream)) : "closed",
        atomic_load(&muted) ? " (muted)" : "");
}

#define LINE ((struct line *) (intptr_t) handle)

/** Returns a handle, or a negative AAudio error code. */
JNIEXPORT jlong JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_open0(JNIEnv *env, jclass cls, jint rate, jint channels,
                                                      jint bufferFrames) {
    struct line *l = calloc(1, sizeof(*l));
    if (!l) return AAUDIO_ERROR_NO_MEMORY;
    l->rate = rate;
    l->channels = channels;
    l->buffer_frames = bufferFrames;
    l->ring = calloc((size_t) bufferFrames * channels, sizeof(int16_t));
    atomic_store(&l->flush_to, -1);
    pthread_mutex_init(&l->lock, NULL);
    l->opened_ns = l->last_report_ns = now_ns();
    aaudio_result_t r = l->ring ? open_stream(l) : AAUDIO_ERROR_NO_MEMORY;
    if (r != AAUDIO_OK) {
        LOG("open %d Hz x%d failed: %s", rate, channels, AAudio_convertResultToText(r));
        free(l->ring);
        free(l);
        return r < 0 ? r : AAUDIO_ERROR_INTERNAL;
    }
    return (jlong) (intptr_t) l;
}

JNIEXPORT jstring JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_errorText0(JNIEnv *env, jclass cls, jint result) {
    return (*env)->NewStringUTF(env, AAudio_convertResultToText(result));
}

/**
 * Copies frames of 16-bit native-endian PCM from data[off..] into the ring, waiting up to
 * timeoutNanos for room. Returns frames written (fewer than asked if the time ran out).
 */
JNIEXPORT jint JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_write0(JNIEnv *env, jclass cls, jlong handle, jbyteArray data,
                                                       jint off, jint frames, jlong timeoutNanos) {
    struct line *l = LINE;
    check_reconnect(l);
    report(l);
    int ch = l->channels;
    int64_t deadline = now_ns() + timeoutNanos;
    jint done = 0;
    while (done < frames) {
        int64_t room = l->buffer_frames - queued(l);
        if (room <= 0) {
            // Full. Only a playing stream empties it.
            if (!atomic_load(&l->started) || now_ns() >= deadline) break;
            struct timespec ts = {0, 1000000}; // 1 ms
            nanosleep(&ts, NULL);
            check_reconnect(l);
            continue;
        }
        int32_t n = room < frames - done ? (int32_t) room : frames - done;
        int64_t w = atomic_load(&l->written);
        // Copy in at most two pieces (the ring wraps)
        int32_t copied = 0;
        while (copied < n) {
            int64_t pos = (w + copied) % l->buffer_frames;
            int32_t piece = n - copied;
            if (pos + piece > l->buffer_frames) piece = (int32_t) (l->buffer_frames - pos);
            (*env)->GetByteArrayRegion(env, data, off + (done + copied) * ch * 2, piece * ch * 2,
                                       (jbyte *) (l->ring + pos * ch));
            copied += piece;
        }
        atomic_store(&l->written, w + n);
        done += n;
    }
    return done;
}

JNIEXPORT jint JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_queued0(JNIEnv *env, jclass cls, jlong handle) {
    return (jint) queued(LINE);
}

JNIEXPORT jlong JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_framesPlayed0(JNIEnv *env, jclass cls, jlong handle) {
    return atomic_load(&LINE->played);
}

JNIEXPORT jint JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_bufferFrames0(JNIEnv *env, jclass cls, jlong handle) {
    return LINE->buffer_frames;
}

JNIEXPORT void JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_start0(JNIEnv *env, jclass cls, jlong handle) {
    struct line *l = LINE;
    pthread_mutex_lock(&l->lock);
    atomic_store(&l->started, 1);
    if (!l->stream || atomic_load(&l->disconnected)) {
        reconnect_locked(l);
    } else {
        aaudio_result_t r = AAudioStream_requestStart(l->stream);
        if (r == AAUDIO_ERROR_DISCONNECTED) {
            reconnect_locked(l);
        } else if (r != AAUDIO_OK) {
            LOG("start failed: %s", AAudio_convertResultToText(r));
        }
    }
    pthread_mutex_unlock(&l->lock);
}

JNIEXPORT void JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_stop0(JNIEnv *env, jclass cls, jlong handle) {
    struct line *l = LINE;
    pthread_mutex_lock(&l->lock);
    atomic_store(&l->started, 0);
    if (l->stream) AAudioStream_requestPause(l->stream);
    pthread_mutex_unlock(&l->lock);
}

/** Discards queued audio: the callback skips ahead to what has been written so far. */
JNIEXPORT void JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_flush0(JNIEnv *env, jclass cls, jlong handle) {
    struct line *l = LINE;
    atomic_store(&l->flush_to, atomic_load(&l->written));
    if (!atomic_load(&l->started)) {
        // No callbacks while stopped: apply it now
        int64_t skip = atomic_exchange(&l->flush_to, -1);
        if (skip >= 0) atomic_store(&l->played, skip);
    }
}

JNIEXPORT void JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_close0(JNIEnv *env, jclass cls, jlong handle) {
    struct line *l = LINE;
    pthread_mutex_lock(&l->lock);
    atomic_store(&l->started, 0);
    l->last_report_ns = 0;
    report(l); // one last line about how it went
    close_stream(l); // stops callbacks before the ring goes away
    pthread_mutex_unlock(&l->lock);
    pthread_mutex_destroy(&l->lock);
    free(l->ring);
    free(l);
}

// --- app side (ART): com.jblors.runelitemobile.Sound
JNIEXPORT void JNICALL
Java_com_jblors_runelitemobile_Sound_setMuted0(JNIEnv *env, jclass cls, jboolean m) {
    atomic_store(&muted, m ? 1 : 0);
}
