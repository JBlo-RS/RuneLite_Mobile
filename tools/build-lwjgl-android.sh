#!/bin/bash
# Builds LWJGL 3.3.2's native libraries (liblwjgl.so, liblwjgl_opengl.so) for Android
# arm64, the LWJGL version RuneLite's client uses, for the GPU plugin. The generated C sources
# are committed in the LWJGL repository, so no code generation is needed. libffi (which LWJGL
# links statically and normally downloads prebuilt) is built from source.
#
# Usage: tools/build-lwjgl-android.sh [workdir]   (default: ./work)
# Output: android/app/src/main/jniLibs/arm64-v8a/liblwjgl.so, liblwjgl_opengl.so
#
# Needs: Android NDK r27b, autoconf, automake, libtool, libltdl-dev (for libffi's autogen).
set -euo pipefail

LWJGL_TAG=3.3.2
LIBFFI_TAG=v3.4.4

HERE="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mkdir -p "${1:-$PWD/work}" && cd "${1:-$PWD/work}" && pwd)"
NDK="${ANDROID_NDK_HOME:-${ANDROID_HOME:-/opt/android-sdk}/ndk/27.1.12297006}"
T="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
CC="$T/aarch64-linux-android26-clang"
OUT="$HERE/android/app/src/main/jniLibs/arm64-v8a"

cd "$WORK"
[ -d lwjgl3 ] || git clone -q --depth 1 -b "$LWJGL_TAG" https://github.com/LWJGL/lwjgl3.git lwjgl3
[ -d libffi ] || git clone -q --depth 1 -b "$LIBFFI_TAG" https://github.com/libffi/libffi.git libffi

# --- libffi (static). Static trampolines don't build on Android and LWJGL doesn't need them.
if [ ! -f ffi-out/lib/libffi.a ]; then
  (cd libffi && ./autogen.sh > ../ffi-autogen.log 2>&1)
  rm -rf libffi/build-android && mkdir libffi/build-android
  (cd libffi/build-android && CC="$CC" AR="$T/llvm-ar" RANLIB="$T/llvm-ranlib" CFLAGS="-O2 -fPIC" \
     ../configure --host=aarch64-linux-android --disable-shared --enable-static --with-pic \
       --disable-docs --disable-multi-os-directory --disable-exec-static-tramp \
       --prefix="$WORK/ffi-out" > ../../ffi-configure.log 2>&1 \
   && make -j"$(nproc)" > ../../ffi-make.log 2>&1 && make install > /dev/null)
fi

L="$WORK/lwjgl3/modules/lwjgl"
CFLAGS="-std=gnu11 -O3 -fPIC -DNDEBUG -DLWJGL_LINUX -DLWJGL_arm64 -U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=0
        -D_GNU_SOURCE -D_FILE_OFFSET_BITS=64 -I$L/core/src/main/c -I$L/core/src/main/c/linux"
LDFLAGS="-shared -O3 -fPIC -z noexecstack -Wl,--no-undefined
         -Wl,--version-script,$WORK/lwjgl3/config/linux/version.script -Wl,-z,max-page-size=16384"

compile() { # objdir sources...
  local dir="$1"; shift
  rm -rf "$dir" && mkdir -p "$dir"
  for f in "$@"; do
    # shellcheck disable=SC2086
    "$CC" -c $CFLAGS $EXTRA -o "$dir/$(basename "$f" .c).o" "$f"
  done
}

# --- liblwjgl (core). io_uring bindings left out: unused by RuneLite, Linux-only syscalls.
EXTRA="-I$L/core/src/main/c/libffi -I$L/core/src/main/c/libffi/aarch64"
compile obj-core $(ls "$L"/core/src/main/c/*.c "$L"/core/src/generated/c/*.c \
                     "$L"/core/src/generated/c/linux/*.c | grep -v liburing)
mkdir -p "$OUT"
# shellcheck disable=SC2086
"$CC" $LDFLAGS -o "$OUT/liblwjgl.so" obj-core/*.o ffi-out/lib/libffi.a -ldl -llog

# --- liblwjgl_opengl (WGL is Windows-only)
EXTRA="-I$L/opengl/src/main/c"
compile obj-opengl $(ls "$L"/opengl/src/generated/c/*.c | grep -v org_lwjgl_opengl_WGL.c)
# shellcheck disable=SC2086
"$CC" $LDFLAGS -o "$OUT/liblwjgl_opengl.so" obj-opengl/*.o -ldl

ls -la "$OUT"/liblwjgl*.so
