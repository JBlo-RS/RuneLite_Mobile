#!/bin/bash
# Cross-compile an ARM64 Android OpenJDK 17 JRE using PojavLauncher's
# android-openjdk-build-multiarch scripts, adapted for a sandbox that can reach
# GitHub (git only), Maven Central, Google and the Ubuntu apt mirror, but not
# SourceForge / GitHub release assets / api.github.com.
#
# Usage: jre-build/build-jre17-arm64.sh [workdir]   (default: ./work)
# Output: <workdir>/jdkbuild/jre17-arm64-<date>-release.tar.xz
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mkdir -p "${1:-$PWD/work}" && cd "${1:-$PWD/work}" && pwd)"
SDK="${ANDROID_HOME:-/opt/android-sdk}"

# Pinned sources (known-good as of 2026-11-02)
JDKBUILD_REV=f3936fba6fcf14f45202c5dd8d2d8179dde051b2   # branch buildjre17-21
JDK17U_REV=b84391a2df6cf9fff72c1580502dd7b2b1838dd6

# --- host dependencies -------------------------------------------------------
if command -v apt-get >/dev/null; then
  apt-get update -q
  DEBIAN_FRONTEND=noninteractive apt-get install -y -q openjdk-17-jdk-headless \
    autoconf automake libtool python3 python-is-python3 unzip zip cmake xz-utils \
    systemtap-sdt-dev libxtst-dev libasound2-dev libelf-dev libfontconfig1-dev \
    libx11-dev libxext-dev libxrandr-dev libxrender-dev libxt-dev file
fi
if [ ! -d "$SDK/ndk/27.1.12297006" ]; then
  # `yes` dies of SIGPIPE when sdkmanager exits; don't let pipefail treat that as failure
  { yes || :; } | "$SDK/cmdline-tools/latest/bin/sdkmanager" "ndk/27.1.12297006" >/dev/null
fi

# --- sources (git only) -------------------------------------------------------
cd "$WORK"
if [ ! -d jdkbuild ]; then
  git clone -b buildjre17-21 https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch.git jdkbuild
  git -C jdkbuild checkout -q "$JDKBUILD_REV"
fi
cd jdkbuild
# Upstream 1_fix_toolchain.diff no longer applies to current jdk17u; use ours.
cp "$HERE/patches/jre_17/android/1_fix_toolchain.diff" patches/jre_17/android/
# Align ELF LOAD segments to 16 KB so the libs load on 16 KB-page kernels
# (Android 15+ devices); this still works on 4 KB-page devices.
sed -i 's/^export LDFLAGS=""$/export LDFLAGS="-Wl,-z,max-page-size=16384"/' setdevkitpath.sh
grep -q 'max-page-size=16384' setdevkitpath.sh

if [ ! -d openjdk-17 ]; then
  git init -q openjdk-17
  git -C openjdk-17 fetch -q --depth 1 https://github.com/openjdk/jdk17u "$JDK17U_REV"
  git -C openjdk-17 checkout -q FETCH_HEAD
fi
[ -d freetype-2.10.0 ] || git clone -q --depth 1 -b VER-2-10-0 https://github.com/freetype/freetype.git freetype-2.10.0
[ -d cups-2.2.4 ]      || git clone -q --depth 1 -b v2.2.4 https://github.com/apple/cups.git cups-2.2.4

# --- build --------------------------------------------------------------------
# Pojav's scripts test unset variables with [[ -z ]]; nounset breaks them.
set +u
. "$HERE/jdkenv.sh"
# The proxy's JAVA_TOOL_OPTIONS banner confuses configure; the JDK build is offline.
unset JAVA_TOOL_OPTIONS
. ./setdevkitpath.sh
cp devkit.info.arm64 "$TOOLCHAIN/"

if [ ! -f freetype-2.10.0/build_android-arm64/lib/libfreetype.so ]; then
  (cd freetype-2.10.0 && ./autogen.sh)
  bash ./4_buildlibs.sh          # 3_getlibs.sh skipped: sources come from git above
fi
# 5_clonejdk.sh skipped: openjdk-17 pinned above
bash ./6_buildjdk.sh
bash ./7_removejdkdebuginfo.sh
bash ./8_tarjdk.sh
ls -la jre17-arm64-*-release.tar.xz
