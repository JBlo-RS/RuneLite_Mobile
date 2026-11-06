# Prebuilt native libraries

`arm64-v8a/libOSMesa.so` is Mesa 23.0.4 (git-b6b4c99605) with the Zink driver (OpenGL
on Vulkan), built as OSMesa. It is copied unchanged from PojavLauncher
(`app_pojavlauncher/src/main/jniLibs/arm64-v8a`, commit
b12ad048157b3aa255d078c235dd4571e1900309). Mesa is MIT licensed.

It is only used by the "GPU test" (`src/main/cpp/gpu_probe.c`), which checks whether
desktop OpenGL 3.3+ (what RuneLite's GPU plugin needs) works on the phone through Zink.
If the GPU plugin goes ahead, this should be replaced with a Mesa build made by this
project.

`arm64-v8a/liblwjgl.so` and `arm64-v8a/liblwjgl_opengl.so` are LWJGL 3.3.2's native
libraries (the version RuneLite's client uses) built for Android by
`tools/build-lwjgl-android.sh`, with libffi 3.4.4 linked statically. LWJGL is BSD-3-Clause
licensed and libffi MIT. Every native method in RuneLite's lwjgl-3.3.2 and
lwjgl-opengl-3.3.2 jars has a matching symbol, except the Windows, macOS and Linux io_uring
bindings, which RuneLite does not use on this platform.

`arm64-v8a/libvulkan_freedreno.so` is Turnip, Mesa 25.2.1's open-source Vulkan driver for
Qualcomm Adreno GPUs, built as an Android Vulkan HAL. It is copied unchanged from
Amethyst-Android, a maintained PojavLauncher fork
(`app_pojavlauncher/src/main/jniLibs/arm64-v8a`, commit 28d6e12d68121b7dd75f2e841058a6b7cdfe4a29,
sha256 4535934c66a312c6139d94a5a398431fa711f2b81ddc296db0a213e9dda32dfd). Mesa is MIT licensed.
It is used instead of the phone's own Vulkan driver on Qualcomm GPUs, where Zink 23.0.4
aborts (`zink_resource.c:991`, seen on an Adreno 735). See `src/main/cpp/vk_turnip.c`.
It should be replaced with a Turnip build made by this project.
