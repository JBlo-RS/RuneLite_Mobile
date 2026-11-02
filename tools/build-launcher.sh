#!/bin/bash
# Builds the stock RuneLite launcher (github.com/runelite/launcher) at a pinned
# commit and copies the shaded jar to android/app/runelite/RuneLite.jar, which
# the APK bundles. The launcher's code is not modified; the app starts it with
# -Drunelite.launcher.reflect=true so the client runs in the same JVM.
#
# Usage: tools/build-launcher.sh [workdir]   (default: ./work)
set -euo pipefail

LAUNCHER_REV=f683f094736b35800b9a00fa75cef4e21333c176   # 2.8.1-SNAPSHOT, 2026-10-21

HERE="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mkdir -p "${1:-$PWD/work}" && cd "${1:-$PWD/work}" && pwd)"

cd "$WORK"
if [ ! -d launcher ]; then
  git init -q launcher
  git -C launcher fetch -q --depth 1 https://github.com/runelite/launcher.git "$LAUNCHER_REV"
  git -C launcher checkout -q FETCH_HEAD
fi
cd launcher

# The launcher's Lombok version does not support JDK 21's javac; build with 17.
export JAVA_HOME=${JAVA_HOME_17:-/usr/lib/jvm/java-17-openjdk-amd64}
# Maven Central sometimes answers 429 to parallel downloads; retry serially.
for i in $(seq 1 10); do
  if ./gradlew --no-daemon --max-workers=1 -I "$HERE/tools/offline-docs.init.gradle" \
       shadowJar -PfinalName=RuneLite -PRUNELITE_BUILD=runelite > build.log 2>&1; then
    break
  fi
  grep -q "status code 429" build.log || { tail -30 build.log; exit 1; }
  sleep 20
done

mkdir -p "$HERE/android/app/runelite"
cp build/libs/RuneLite.jar "$HERE/android/app/runelite/RuneLite.jar"
echo "$LAUNCHER_REV" > "$HERE/android/app/runelite/RuneLite.jar.rev"
ls -la "$HERE/android/app/runelite/RuneLite.jar"
