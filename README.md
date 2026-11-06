# RuneLite Mobile

Run the stock RuneLite client on Android (ARM64), PojavLauncher-style:

1. Bundle an ARM64 OpenJDK 17 built for Android (`jre-build/`).
2. Use Caciocavallo (`cacio-tta` / `cacio-shared` 1.18, from PojavLauncher) as the
   AWT backend, rendering to an offscreen buffer blitted to a `SurfaceView`.
3. Map Android touch and keyboard input to AWT events (cf. Pojav's
   `AWTInputBridge` / `awt_bridge.c`).
4. Launch the unmodified RuneLite launcher jar.
5. GPU plugin: desktop OpenGL through Mesa's Zink driver (OpenGL on Vulkan) via OSMesa.

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
| Milestone 4: stock RuneLite launcher in REFLECT mode, window maximized (client reaches its UI on desktop JDK 17) | done |
| GPU test: Zink gives OpenGL 4.6 on the Galaxy A56 (Xclipse 540, Vulkan 1.3) | done |
| GPU plugin: rlawt replacement + LWJGL 3.3.2 natives for Android (verified on desktop with Mesa llvmpipe) | awaiting device test |

## Trying the APK

1. Download `apk/runelite-mobile-0.17.1-debug.apk` on the phone (ARM64, Android 8+)
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

## Sound

The JRE has no Java Sound backend on Android: it would use ALSA, so the game found no audio
device and stayed silent. `com.jblors.rlm.sound` (in the helper jar, registered as a
`javax.sound.sampled.spi.MixerProvider`) adds an output `SourceDataLine` for the game. It is
backed by an AAudio stream in `android/app/src/main/cpp/rlm_sound.c` (`librlmsound.so`).

- 8/16-bit PCM in either byte order is converted to 16-bit little-endian. AAudio plays the
  line's own sample rate (the game uses 22050 Hz).
- Each line has a ring buffer of exactly the size the game asked for, and AAudio's data callback
  empties it (playing silence if it runs dry). `available()` and the frame position are counted
  from that ring, not from AAudio's frame counters. The game closes and reopens a line every few
  seconds unless it sees the queued amount drop (its `dm` player class), and with AAudio's
  counters on a blocking stream it didn't see that on a Honor phone.
- When the output device changes (headphones, Bluetooth), the stream is reopened automatically.
- The game is muted while the app is in the background.
- Not supported yet: `Clip` (RuneLite's custom notification sounds).

## Local plugins

RuneLite in developer mode loads every jar in `~/.runelite/sideloaded-plugins`. The app's
**Developer mode** (under More) starts RuneLite that way.

- **Built in:** the plugins in `plugins/` are compiled by `tools/build-plugins.sh` into
  `android/app/plugins/*.jar`, bundled with the APK, and installed before each Developer mode start.
- **Import:** More → **Plugins** → **Import .jar** copies a plugin jar from the phone (e.g. Downloads).
  Imported plugins can be removed from the same list.
- Plugins are ordinary desktop RuneLite plugin jars (Java 11 bytecode). They run unchanged,
  because the app runs a real JVM.

| Plugin | What it does |
| --- | --- |
| Mobile Controls (`plugins/rlm-mobile`) | Tells the app which tab is open and the game's keybinds for the inventory and prayer tabs, for the Pray/Inv button (system properties `rlm.tab`, `rlm.key.inv`, `rlm.key.prayer`). |
| Big Status Bars (`plugins/rlm-mobile`) | Large Hitpoints and Prayer bars, optionally special attack and run energy; HP turns green when poisoned and dark green when envenomed, and HP and Prayer flash below set thresholds. Movable (hold Alt and drag). |
| Big Menu (`plugins/rlm-mobile`) | Draws the right-click menu large (row height, text size and width can be set) over the game's own menu, with the game's colours. A RuneLite mouse listener moves taps and cursor movement inside it to the matching row of the game's menu (19 px header, 15 px rows, newest entry on top), like stretched mode scales clicks: one tap, one click. |
| Skilling Helper (`plugins/rlm-skilling`) | Remembers the last tree, rock or fishing spot you used. While you're idle, it outlines the nearest one of that kind (rocks by object ID, trees by name and their chop option, fishing spots by NPC ID). With a full inventory it outlines the nearest bank booth, bank chest, deposit box or banker instead. It only highlights. |
| Boss Helper (`plugins/rlm-bosses`) | Scurrius, TzTok-Jad / JalTok-Jad, Dagannoth Kings, Phantom Muspah, Sarachnis, Chaos Fanatic, Crazy and Deranged Archaeologists, Vorkath, Zulrah, Abyssal Sire, Royal Titans, Hespori, Amoxliatl, the Hueycoatl, Moons of Peril, Galvek and Brutus. Boxes the protection prayer against the boss's last attack in the prayer book (from its attack animation, projectiles aimed at you, or a single-style form). Marks hazards: warning graphics, landing tiles of ground-targeted projectiles (some stay marked after landing, e.g. Vorkath's acid), hazard objects (Zulrah's venom clouds) and hazard NPCs (Moons' threat circles, Royal Titans' area attacks); MOVE! when you stand on one. Each boss can be turned on or off. The IDs are from RuneLite's game data (see `Boss.java`); each boss is unconfirmed until tested in game. |

## GPU plugin (experimental)

Interface-only scaling: with GPU on, RuneLite's **Stretched Mode** plugin enlarges only the
interface. The GPU plugin draws the 3D scene at the full screen resolution and the interface
as a separate layer, scaled with its **UI scaling mode** setting (Nearest Neighbor or xBR for
crisp pixel art). Without GPU, the game draws the scene and the interface into one picture, so
stretching blurs both. An earlier attempt (v0.17.0) copied interface pixels from that picture
instead, and was withdrawn: it also enlarged the scene behind see-through parts of the
interface.

Turn **GPU** on in the app, start RuneLite, then enable the **GPU** plugin in RuneLite's plugin list.

How it works:

- `android/jvmhelper/.../net/runelite/rlawt/AWTContext.java` replaces RuneLite's rlawt, which needs
  JAWT and X11. It is on the boot class path, so it is found first.
- Its native side, `android/app/src/main/cpp/rlm_gl.c` (`librlmgl.so`), creates an OSMesa context
  with Mesa's Zink driver (PojavLauncher's Mesa 23.0.4 build, `jniLibs/arm64-v8a/libOSMesa.so`).
  It renders into a memory buffer the size of the game canvas.
- `swapBuffers` publishes each frame. `ScreenCompositor` copies it into the canvas area of the
  screen sent to Android, under the RuneLite UI and dialogs.
- LWJGL's own natives (`liblwjgl.so`, `liblwjgl_opengl.so`) are built for Android by
  `tools/build-lwjgl-android.sh`.
- LWJGL gets its GL functions from `OSMesaGetProcAddress`, because OSMesa doesn't export all of
  them by name.
- With GPU off, `AWTContext.loadNatives()` throws, and RuneLite turns the GPU plugin off as it
  does on a PC without GPU support.

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
| Pray / Inv | opens the prayer tab, or the inventory if the prayer tab is open, by pressing the game's own keybinds for them (read from the game by the Mobile Controls plugin; otherwise long-press to enter them) |
| R | right-click mode for one-handed play: tap once = the next tap right-clicks, again = every tap right-clicks, again = off |
| Hardware keyboard / mouse | mapped directly |
| Copy / paste | shared with Android's clipboard (text): select text, then Ctrl (hold) + C / V |

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
- All windows share Cacio's single screen buffer, and every `Graphics` Cacio hands out is
  clipped to the windows that existed when it was created. The game keeps one `Graphics`
  for its `Canvas`, so every game frame painted over dialogs opened later (for example
  RuneLite's colour picker). `com.jblors.rlm.ScreenCompositor` redraws each dialog and
  popup from its own Swing root pane about 30 times a second and draws them on top of the
  frame sent to Android. Input already follows Cacio's real window order.
- The same stale clip let game frames wipe Swing UI next to the canvas after the layout
  changed (sidebar icons disappearing until touched). `ScreenCompositor` also keeps a UI
  layer for the main window: its Swing content is re-printed when Swing repaints, at most
  10 times a second, and drawn everywhere except the game canvas's current area.
- `CTCRobotPeer.mouseWheel` does nothing, so wheel events come from
  `com.jblors.rlm.AndroidInput` (`android/jvmhelper`) on the boot class path.

## Layouts

- **Mobile** (default): the game canvas fills the phone screen. The ◀ tab on the right
  edge slides RuneLite's sidebar in as an overlay (and opens it with Ctrl+F11 if it is
  closed).
  - The first opening in a run shows the Configuration (wrench) panel; later openings
    show whichever panel RuneLite was last on.
  - The panel is inset by the display's rounded-corner radius, so the top and bottom
    icons are not cut off.
  - Keyboard, Alt/Shift/Ctrl and Esc are floating buttons. Tap the ⠿ grip to collapse
    them; drag it to move them.
  - Drag the ◀ tab up or down the edge to move it.
  - Positions are remembered, and Back closes the panel. The virtual screen is sized so the canvas has the phone's aspect ratio;
  set the game to Resizable mode so the canvas fills it. `ScreenCompositor.layout()`
  reports where the canvas is.
- **Classic**: the whole RuneLite window, fitted beside a fixed control strip.

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
