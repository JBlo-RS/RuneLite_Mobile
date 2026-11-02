// Native glue for running an OpenJDK 17 (built for Android) inside an app process.
//
// The JVM is started in-process through libjli's JLI_Launch, the same entry point
// the `java` executable uses. Approach after PojavLauncher's jre_launcher.c.

#include <android/log.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define TAG "rlm"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef jint JLI_Launch_t(int argc, char **argv,
                          int jargc, const char **jargv,
                          int appclassc, const char **appclassv,
                          const char *fullversion, const char *dotversion,
                          const char *pname, const char *lname,
                          jboolean javaargs, jboolean cpwildcard,
                          jboolean javaw, jint ergo);

typedef void android_update_LD_LIBRARY_PATH_t(const char *);

// ---------------------------------------------------------------------------
// Linker search path. Bionic exposes this privately; dlopen() of a bare soname
// (e.g. libjvm.so loading libjava.so's dependencies) resolves against it.

JNIEXPORT void JNICALL
Java_com_jblors_runelitemobile_NativeBridge_setLdLibraryPath(JNIEnv *env, jclass clazz, jstring path) {
    void *libdl = dlopen("libdl.so", RTLD_LAZY);
    void *fn = libdl ? dlsym(libdl, "android_update_LD_LIBRARY_PATH") : NULL;
    if (!fn && libdl) fn = dlsym(libdl, "__loader_android_update_LD_LIBRARY_PATH");
    if (!fn) {
        LOGE("android_update_LD_LIBRARY_PATH not found: %s", dlerror());
        return;
    }
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    ((android_update_LD_LIBRARY_PATH_t *) fn)(p);
    LOGI("linker LD_LIBRARY_PATH = %s", p);
    (*env)->ReleaseStringUTFChars(env, path, p);
}

// Returns null on success, or the dlerror() text.
JNIEXPORT jstring JNICALL
Java_com_jblors_runelitemobile_NativeBridge_dlopen(JNIEnv *env, jclass clazz, jstring path) {
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    void *h = dlopen(p, RTLD_GLOBAL | RTLD_LAZY);
    jstring err = NULL;
    if (!h) {
        const char *e = dlerror();
        LOGE("dlopen %s failed: %s", p, e);
        err = (*env)->NewStringUTF(env, e ? e : "unknown dlopen error");
    }
    (*env)->ReleaseStringUTFChars(env, path, p);
    return err;
}

JNIEXPORT jint JNICALL
Java_com_jblors_runelitemobile_NativeBridge_chdir(JNIEnv *env, jclass clazz, jstring path) {
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    int r = chdir(p);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return r == 0 ? 0 : errno;
}

// ---------------------------------------------------------------------------
// stdout/stderr -> log file + logcat. The JVM writes to fds 1 and 2 directly.

static int log_pipe[2] = {-1, -1};
static int log_fd = -1;

static void *log_pump(void *unused) {
    char buf[4096];
    ssize_t n;
    while ((n = read(log_pipe[0], buf, sizeof(buf) - 1)) > 0) {
        if (log_fd >= 0) write(log_fd, buf, (size_t) n);
        buf[n] = 0;
        // logcat wants lines; split so each shows up separately
        char *save = NULL;
        for (char *line = strtok_r(buf, "\n", &save); line; line = strtok_r(NULL, "\n", &save))
            __android_log_write(ANDROID_LOG_INFO, "rlm-jvm", line);
    }
    return NULL;
}

// Returns null on success, or an error message.
JNIEXPORT jstring JNICALL
Java_com_jblors_runelitemobile_NativeBridge_redirectStdio(JNIEnv *env, jclass clazz, jstring logPath) {
    const char *p = (*env)->GetStringUTFChars(env, logPath, NULL);
    log_fd = open(p, O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, 0644);
    (*env)->ReleaseStringUTFChars(env, logPath, p);
    if (log_fd < 0) return (*env)->NewStringUTF(env, strerror(errno));

    if (pipe(log_pipe) != 0) return (*env)->NewStringUTF(env, strerror(errno));
    setvbuf(stdout, NULL, _IOLBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    if (dup2(log_pipe[1], STDOUT_FILENO) < 0 || dup2(log_pipe[1], STDERR_FILENO) < 0)
        return (*env)->NewStringUTF(env, strerror(errno));

    pthread_t t;
    int r = pthread_create(&t, NULL, log_pump, NULL);
    if (r != 0) return (*env)->NewStringUTF(env, strerror(r));
    pthread_detach(t);
    return NULL;
}

// ---------------------------------------------------------------------------
// Launch. argv[0] must be "java" (no slash): the JRE's Android patch then takes
// the Java home from $JAVA_HOME instead of /proc/self/exe (app_process).

JNIEXPORT jint JNICALL
Java_com_jblors_runelitemobile_NativeBridge_launchJvm(JNIEnv *env, jclass clazz,
                                                     jstring jliPath, jobjectArray args) {
    const char *jli = (*env)->GetStringUTFChars(env, jliPath, NULL);
    void *h = dlopen(jli, RTLD_LAZY | RTLD_GLOBAL);
    if (!h) {
        LOGE("dlopen %s failed: %s", jli, dlerror());
        (*env)->ReleaseStringUTFChars(env, jliPath, jli);
        return -1;
    }
    (*env)->ReleaseStringUTFChars(env, jliPath, jli);

    JLI_Launch_t *launch = (JLI_Launch_t *) dlsym(h, "JLI_Launch");
    if (!launch) {
        LOGE("JLI_Launch not found: %s", dlerror());
        return -2;
    }

    int argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc((size_t) argc + 1, sizeof(char *));
    for (int i = 0; i < argc; i++) {
        jstring s = (jstring) (*env)->GetObjectArrayElement(env, args, i);
        const char *c = (*env)->GetStringUTFChars(env, s, NULL);
        argv[i] = strdup(c);
        (*env)->ReleaseStringUTFChars(env, s, c);
        (*env)->DeleteLocalRef(env, s);
    }

    LOGI("calling JLI_Launch with %d args", argc);
    jint rc = launch(argc, argv, 0, NULL, 0, NULL,
                     "17-rlm", "17", argv[0], argv[0],
                     JNI_FALSE, JNI_TRUE, JNI_FALSE, 0);
    LOGI("JLI_Launch returned %d", rc);
    // argv intentionally not freed: the JVM may keep pointers into it
    return rc;
}
