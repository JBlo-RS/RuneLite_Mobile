#!/bin/bash
# Builds the RuneLite plugins in plugins/ into jars that the app installs as side-loaded plugins
# (developer mode loads every jar in ~/.runelite/sideloaded-plugins).
#
# Usage: tools/build-plugins.sh [jar-dir]
#   jar-dir: RuneLite's client jars to compile against (default: ~/.runelite/repository2, which
#            the RuneLite launcher fills on a PC; it needs client-*.jar, runelite-api-*.jar,
#            guice, javax.inject)
# Output: android/app/plugins/<name>.jar
set -euo pipefail

HERE="$(cd "$(dirname "$0")/.." && pwd)"
JARS="${1:-$HOME/.runelite/repository2}"
OUT="$HERE/android/app/plugins"
CP="$(ls "$JARS"/*.jar | tr '\n' ':')"
mkdir -p "$OUT"

for dir in "$HERE"/plugins/*/; do
  name="$(basename "$dir")"
  build="$(mktemp -d)"
  # Java 11 bytecode, like RuneLite's own plugins
  javac --release 11 -nowarn -cp "$CP" -d "$build" $(find "$dir/src/main/java" -name '*.java')
  [ -d "$dir/src/main/resources" ] && cp -r "$dir/src/main/resources/." "$build/"
  rm -f "$OUT/$name.jar"
  (cd "$build" && jar --create --file "$OUT/$name.jar" --date=2026-01-01T00:00:00Z .)
  rm -rf "$build"
  echo "built $OUT/$name.jar"
done
