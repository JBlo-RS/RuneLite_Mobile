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
| ARM64 Android JRE 17 (17.0.21, 27 MB `.tar.xz`, 16 KB page aligned) | done |
| Milestone 1: in-process JVM launcher + probe, all checks pass on Galaxy A56 (Android 16) | done |
| Milestone 2: Swing via Caciocavallo on a `SurfaceView`, smooth at ~60 repaints/s on device | done |
| Milestone 3: touch / keyboard input (tap, drag, Samsung keyboard verified on device) | done |
| Milestone 4: stock RuneLite launcher in REFLECT mode, window maximized (client reaches its UI on desktop JDK 17) | awaiting device test |

## Trying the APK

1. Download `apk/runelite-mobile-0.5.1-debug.apk` on the phone (ARM64, Android 8+)
   and install it. Allow installs from your browser/files app when asked.
2. Open **RuneLite Mobile** and tap **Run JVM probe**. The first run unpacks the JRE,
   which takes a few seconds.
3. When it finishes it drops back to the main screen. Tap **Share log** and send
   the text.
4. **Run Swing demo** opens a full-screen landscape Swing window drawn through
   Caciocavallo. Press back to leave it, then use **Share log**.

The probe tests JIT, threads, zip, font rendering (freetype) and HTTPS
(`static.runelite.net`). A good run ends with `ALL CHECKS PASSED`.

Every APK is signed with `android/debug.keystore`, so new builds install over old ones.

## Input

| Gesture | AWT |
| --- | --- |
| Tap | left click |
| Long press (400 ms, haptic) | right click (OSRS context menu) |
| One-finger drag | left-button drag |
| Two-finger drag | middle-button drag (OSRS camera) |
| Pinch | mouse wheel (zoom) |
| ⌨ (left strip) | soft keyboard; text arrives as `KEY_TYPED`, Backspace/Enter as key presses |
| Alt / Shift / Ctrl (left strip) | tap to hold the key down, tap again to release (lit while held) |
| Esc (left strip) | Escape |
| Hardware keyboard / mouse | mapped directly |

Implementation notes:

- This Cacio build's `CTCRobotPeer.mousePress` wants the legacy masks
  `BUTTON1_MASK`=16, `BUTTON2_MASK`=8 and `BUTTON3_MASK`=4. The `*_DOWN_MASK`
  values only work for button 1; for the others they produce events with
  button 0, which crash AWT's `LightweightDispatcher`.
- A held Alt reaches Java as key events, which the OSRS client and RuneLite hotkeys
  use. It does not show in `MouseEvent.isAltDown()`, because Cacio does not track Alt
  in its robot. Forcing the modifier bit makes clicks come out as button 0, because
  the legacy `ALT_MASK` equals `BUTTON2_MASK`. Shift and Ctrl do show on mouse events.
- Cacio gives no component keyboard focus on click, so nothing owned focus and key input
  never reached the game's `Canvas`. The jvmhelper `WindowManager` adds click-to-focus for
  heavyweight components and focuses new windows.
- Cacio does not support `MAXIMIZED_BOTH`, so RuneLite widened the window when the sidebar
  opened, pushing it off screen. `WindowManager` records the maximized state in `Frame`
  itself and keeps maximized frames at screen size.
- `CTCRobotPeer.mouseWheel` does nothing, so wheel events come from
  `com.jblors.rlm.AndroidInput` (`android/jvmhelper`) on the boot class path.

## Jagex accounts

RuneLite's built-in Jagex login runs a native helper, which Android cannot execute,
and the helper is not shipped for Linux anyway. Instead, the app does what the Jagex
Launcher does: it sets `JX_SESSION_ID`, `JX_CHARACTER_ID` and `JX_DISPLAY_NAME` in the
JVM's environment, and the game client reads them.

1. On a PC, in the Jagex Launcher, add `--insecure-write-credentials` to RuneLite's
   client arguments, then launch RuneLite once. It writes
   `credentials.properties` to the `.runelite` folder in your user folder.
2. Copy that file to the phone. Tap **Jagex account** and pick it.

The file is a login token, so treat it like a password. The app keeps only the
`JX_*` keys, in its private storage, and never logs their values.

## Developer mode

RuneLite turns developer mode off whenever a launcher version is set, and the launcher
always sets one. **Dev mode** on the main screen therefore skips the launcher and uses
`com.jblors.rlm.DevLaunch` (in `android/jvmhelper`), which does the launcher's last step
itself:

- reads `bootstrap.json` and picks this platform's artifacts with the launcher's rules;
- checks the jars the launcher already downloaded against bootstrap's SHA-256 hashes;
- starts the client with `--developer-mode` and `-ea`.

Run the normal launch once first so the jars exist. The launcher ships a trimmed
`runelite-api-*-runtime.jar` without the `gameval` classes the Developer Tools plugin
needs. DevLaunch therefore downloads the full `runelite-api-*.jar` from repo.runelite.net,
checks it against the repository's SHA-1, and uses it instead.

DevLaunch does not check the signature on `bootstrap.json`; it relies on HTTPS. Imported
Jagex credentials are also written to `~/.runelite/credentials.properties` in this mode.

## Building the APK

```bash
cd android && ./gradlew :app:assembleDebug
```

This needs the Android SDK with NDK r27b and CMake 3.31.6. It bundles the newest
`assets/jre17-arm64-*-release.tar.xz`.

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
