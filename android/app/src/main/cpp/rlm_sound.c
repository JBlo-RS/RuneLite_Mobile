// Sound output for the JVM: natives of com.jblors.rlm.sound.AndroidSourceDataLine (android/jvmhelper),
// the javax.sound.sampled line the game writes its audio to. The JRE has no Java Sound backend
// on Android (it would use ALSA), so each line is an AAudio output stream of 16-bit PCM.
//
// The app (ART) loads this same file too, to mute sound while the app is in the background
// (Java_com_jblors_runelitemobile_Sound_setMuted0); both VMs share this library's globals.

#include <aaudio/AAudio.h>
#include <android/log.h>
#include <jni.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define TAG "rlm-sound"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static atomic_int muted;

struct line {
    AAudioStream *stream;
    int rate, channels, buffer_frames;
    int started;
    int64_t frames_base;  // frames played by earlier streams of this line (after a reconnect)
    int16_t *scratch;
    size_t scratch_cap;
};

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
    AAudioStreamBuilder_setBufferCapacityInFrames(b, l->buffer_frames);
    r = AAudioStreamBuilder_openStream(b, &l->stream);
    AAudioStreamBuilder_delete(b);
    if (r != AAUDIO_OK) {
        l->stream = NULL;
        return r;
    }
    // The game measures how much is queued against the buffer size it asked for
    AAudioStream_setBufferSizeInFrames(l->stream, l->buffer_frames);
    LOGI("stream %d Hz x%d, buffer %d/%d frames", AAudioStream_getSampleRate(l->stream),
         AAudioStream_getChannelCount(l->stream), AAudioStream_getBufferSizeInFrames(l->stream),
         AAudioStream_getBufferCapacityInFrames(l->stream));
    return AAUDIO_OK;
}

static void close_stream(struct line *l) {
    if (!l->stream) return;
    l->frames_base += AAudioStream_getFramesRead(l->stream);
    AAudioStream_close(l->stream);
    l->stream = NULL;
}

/** After the output device changed (headphones, Bluetooth), the stream must be reopened. */
static int reconnect(struct line *l) {
    LOGI("output device changed; reopening the stream");
    close_stream(l);
    if (open_stream(l) != AAUDIO_OK) return 0;
    if (l->started) AAudioStream_requestStart(l->stream);
    return 1;
}

static int64_t queued(struct line *l) {
    if (!l->stream) return 0;
    int64_t q = AAudioStream_getFramesWritten(l->stream) - AAudioStream_getFramesRead(l->stream);
    return q < 0 ? 0 : q;
}

static void wait_state(struct line *l, aaudio_stream_state_t from) {
    aaudio_stream_state_t next = AAUDIO_STREAM_STATE_UNINITIALIZED;
    AAudioStream_waitForStateChange(l->stream, from, &next, 200 * 1000000LL);
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
    aaudio_result_t r = open_stream(l);
    if (r != AAUDIO_OK) {
        LOGE("open %d Hz x%d failed: %s", rate, channels, AAudio_convertResultToText(r));
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
 * Writes frames of 16-bit native-endian PCM from data[off..]. Returns frames written (may be
 * fewer than asked if the timeout passed), or a negative error code.
 */
JNIEXPORT jint JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_write0(JNIEnv *env, jclass cls, jlong handle, jbyteArray data,
                                                       jint off, jint frames, jlong timeoutNanos) {
    struct line *l = LINE;
    if (!l->stream && !reconnect(l)) return AAUDIO_ERROR_DISCONNECTED;
    size_t bytes = (size_t) frames * l->channels * 2;
    if (l->scratch_cap < bytes) {
        int16_t *n = realloc(l->scratch, bytes);
        if (!n) return AAUDIO_ERROR_NO_MEMORY;
        l->scratch = n;
        l->scratch_cap = bytes;
    }
    if (atomic_load(&muted)) {
        memset(l->scratch, 0, bytes); // keep the game's timing, play silence
    } else {
        (*env)->GetByteArrayRegion(env, data, off, (jsize) bytes, (jbyte *) l->scratch);
    }
    aaudio_result_t r = AAudioStream_write(l->stream, l->scratch, frames, timeoutNanos);
    if (r == AAUDIO_ERROR_DISCONNECTED) {
        if (!reconnect(l)) return AAUDIO_ERROR_DISCONNECTED;
        r = AAudioStream_write(l->stream, l->scratch, frames, timeoutNanos);
    }
    return r;
}

JNIEXPORT jint JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_queued0(JNIEnv *env, jclass cls, jlong handle) {
    return (jint) queued(LINE);
}

JNIEXPORT jlong JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_framesPlayed0(JNIEnv *env, jclass cls, jlong handle) {
    struct line *l = LINE;
    return l->frames_base + (l->stream ? AAudioStream_getFramesRead(l->stream) : 0);
}

JNIEXPORT jint JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_bufferFrames0(JNIEnv *env, jclass cls, jlong handle) {
    struct line *l = LINE;
    return l->stream ? AAudioStream_getBufferSizeInFrames(l->stream) : l->buffer_frames;
}

JNIEXPORT void JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_start0(JNIEnv *env, jclass cls, jlong handle) {
    struct line *l = LINE;
    l->started = 1;
    if (!l->stream) return;
    if (AAudioStream_requestStart(l->stream) == AAUDIO_ERROR_DISCONNECTED) reconnect(l);
}

JNIEXPORT void JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_stop0(JNIEnv *env, jclass cls, jlong handle) {
    struct line *l = LINE;
    l->started = 0;
    if (!l->stream) return;
    AAudioStream_requestPause(l->stream);
    wait_state(l, AAUDIO_STREAM_STATE_PAUSING);
}

/** Discards queued audio (AAudio only flushes a paused stream). */
JNIEXPORT void JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_flush0(JNIEnv *env, jclass cls, jlong handle) {
    struct line *l = LINE;
    if (!l->stream) return;
    if (l->started) {
        AAudioStream_requestPause(l->stream);
        wait_state(l, AAUDIO_STREAM_STATE_PAUSING);
    }
    AAudioStream_requestFlush(l->stream);
    wait_state(l, AAUDIO_STREAM_STATE_FLUSHING);
    if (l->started) AAudioStream_requestStart(l->stream);
}

JNIEXPORT void JNICALL
Java_com_jblors_rlm_sound_AndroidSourceDataLine_close0(JNIEnv *env, jclass cls, jlong handle) {
    struct line *l = LINE;
    if (l->stream) {
        AAudioStream_requestStop(l->stream);
        AAudioStream_close(l->stream);
    }
    free(l->scratch);
    free(l);
}

// --- app side (ART): com.jblors.runelitemobile.Sound
JNIEXPORT void JNICALL
Java_com_jblors_runelitemobile_Sound_setMuted0(JNIEnv *env, jclass cls, jboolean m) {
    atomic_store(&muted, m ? 1 : 0);
}
