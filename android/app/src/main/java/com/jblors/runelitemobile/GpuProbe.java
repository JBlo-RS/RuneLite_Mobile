package com.jblors.runelitemobile;

/** GPU feasibility test (src/main/cpp/gpu_probe.c): desktop OpenGL through Mesa's Zink. */
final class GpuProbe {
    static {
        System.loadLibrary("rlmgpu");
    }

    private GpuProbe() {}

    /** Runs the test and returns a readable report. Each step is also printed to stdout. */
    static native String run(String osmesaPath);
}
