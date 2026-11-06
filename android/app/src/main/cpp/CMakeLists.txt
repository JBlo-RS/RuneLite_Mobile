cmake_minimum_required(VERSION 3.22)
project(rlm C)

# JVM launch glue (System.loadLibrary("rlm"))
add_library(rlm SHARED rlm_native.c)
target_link_libraries(rlm log dl)

# AWT bridge; the name is fixed by Caciocavallo's CTCScreen
add_library(pojavexec_awt SHARED rlm_awt.c)
target_link_libraries(pojavexec_awt log jnigraphics)

# libawt_xawt.so stand-in (copied into <jre>/lib at launch). Linked against an
# empty placeholder so it records DT_NEEDED libawt_headless.so; the placeholder
# itself is excluded from the APK (see packaging in build.gradle.kts).
add_library(awt_headless SHARED awt_headless_placeholder.c)
add_library(awt_xawt SHARED xawt_stub.c)
# --no-as-needed: nothing here calls into libawt_headless, but the DT_NEEDED entry is the point
target_link_libraries(awt_xawt "-Wl,--no-as-needed" awt_headless)

# GPU feasibility test (Zink via OSMesa); see gpu_probe.c
add_library(rlmgpu SHARED gpu_probe.c)
target_link_libraries(rlmgpu log dl)

# OpenGL for RuneLite's GPU plugin (natives of net.runelite.rlawt.AWTContext); see rlm_gl.c
add_library(rlmgl SHARED rlm_gl.c)
target_link_libraries(rlmgl log dl)

# Sound output for the JVM (com.jblors.rlm.sound, AAudio); see rlm_sound.c
add_library(rlmsound SHARED rlm_sound.c)
target_link_libraries(rlmsound aaudio log)

foreach(t rlm pojavexec_awt awt_xawt rlmgpu rlmgl rlmsound)
    target_compile_options(${t} PRIVATE -Wall -Wextra -Wno-unused-parameter)
    # 16 KB page alignment (also set via ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES)
    target_link_options(${t} PRIVATE -Wl,-z,max-page-size=16384)
endforeach()
