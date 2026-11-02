# Source before running Pojav's android-openjdk-build steps.
# CI=1 stops setdevkitpath.sh from calling update-java-alternatives.
export CI=1
export TARGET=aarch64-linux-android TARGET_JDK=aarch64 TARGET_VERSION=17
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64   # boot JDK must be 16 or 17
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_NDK_HOME=${ANDROID_HOME:-/opt/android-sdk}/ndk/27.1.12297006  # r27b, what Pojav's patches target
