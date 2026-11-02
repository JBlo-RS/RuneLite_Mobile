# RuneLite Mobile

Run the stock RuneLite client on Android (ARM64), PojavLauncher-style:

1. Bundle an ARM64 OpenJDK 17 built for Android (`jre-build/`).
2. Use Caciocavallo (`cacio-tta` / `cacio-shared` 1.18, from PojavLauncher) as the
   AWT backend, rendering to an offscreen buffer blitted to a `SurfaceView`.
3. Map Android touch and keyboard input to AWT events (cf. Pojav's
   `AWTInputBridge` / `awt_bridge.c`).
4. Launch the unmodified RuneLite launcher jar.
5. Later: GPU plugin via GL4ES or Zink.

## Status

| Step | State |
| --- | --- |
| Android SDK (platform 36, build-tools 36.1.0), NDK r27b + r30, CMake 3.31.6 | done |
| RuneLite builds and tests pass (723 tests, 0 failures) | done |
| RuneLite client starts on desktop under Xvfb | done |
| ARM64 Android JRE 17 | building |
| Android app (JVM launcher, Cacio surface, input) | not started |

## Building RuneLite in a restricted sandbox

`javadoc` and `checkstyle` fetch from `docs.oracle.com` / `checkstyle.org`. When
those are blocked, disable them across all included builds:

```bash
./gradlew --max-workers=1 -I tools/offline-docs.init.gradle :buildAll
```

`--max-workers=1` avoids HTTP 429s from Maven Central; rerun if one still appears.

## Building the JRE

```bash
jre-build/build-jre17-arm64.sh /path/to/workdir
```

This wraps PojavLauncherTeam/android-openjdk-build-multiarch (branch
`buildjre17-21`) with these changes:

- freetype 2.10.0 and cups 2.2.4 come from git, not SourceForge or GitHub release
  assets. freetype needs `autogen.sh`.
- `patches/jre_17/android/1_fix_toolchain.diff` is regenerated for current jdk17u.
  The `config.sub` and `jdwp` hunks had drifted. The upstream original is kept
  alongside as `.orig-upstream`.
- The boot JDK is apt's `openjdk-17-jdk-headless`, and `JAVA_TOOL_OPTIONS` is unset
  for the build.

## Pinned upstream commits

| Repo | Commit |
| --- | --- |
| runelite/runelite | c653d7dbffc674b1120435a0bffe464bfbbc448a |
| runelite/launcher | f683f094736b35800b9a00fa75cef4e21333c176 |
| PojavLauncherTeam/PojavLauncher | b12ad048157b3aa255d078c235dd4571e1900309 |
| PojavLauncherTeam/android-openjdk-build-multiarch | f3936fba6fcf14f45202c5dd8d2d8179dde051b2 |
| openjdk/jdk17u | b84391a2df6cf9fff72c1580502dd7b2b1838dd6 |
