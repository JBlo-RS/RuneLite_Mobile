# RuneLite launcher

`RuneLite.jar` is the unmodified RuneLite launcher (github.com/runelite/launcher),
built by `tools/build-launcher.sh` at the commit in `RuneLite.jar.rev`. It is
BSD-2-Clause licensed; see the launcher repository's LICENSE.

The app runs it with `-Drunelite.launcher.reflect=true`, so the launcher starts
the client in the same JVM with its `ReflectionLauncher` instead of forking a
new `java` process, which Android does not allow. Because nothing is forked, the
client JVM arguments from bootstrap.json (`clientJvm17Arguments`) are passed up
front by `JvmActivity`.
