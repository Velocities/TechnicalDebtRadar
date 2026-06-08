#!/usr/bin/env bash
# Builds a standalone Technical Debt Radar binary (macOS / Linux).
#
# Requires JDK 21+ (virtual threads + jpackage). Set JAVA_HOME to a JDK 21+
# install, or make sure `java` on PATH is 21+.
#
# Output: dist/radar/bin/radar  (no Scala or Java required to run it)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/}java"
MAJOR="$("$JAVA_BIN" -version 2>&1 | head -n1 | sed -E 's/.*version "([0-9]+).*/\1/')"
if [ "${MAJOR:-0}" -lt 21 ]; then
  echo "Need JDK 21+ (found ${MAJOR:-none}). Install one and/or set JAVA_HOME." >&2
  exit 1
fi
JPACKAGE="${JAVA_HOME:+$JAVA_HOME/bin/}jpackage"

echo "==> Using Java $MAJOR"

echo "==> Building fat JAR (sbt assembly)..."
sbt -batch "assembly"

echo "==> Staging JAR..."
rm -rf stage dist
mkdir -p stage
cp target/scala-3.8.3/technical-debt-radar.jar stage/

echo "==> Packaging standalone binary (jpackage)..."
"$JPACKAGE" \
  --type app-image \
  --name radar \
  --input stage \
  --main-jar technical-debt-radar.jar \
  --main-class radar \
  --dest dist

echo ""
echo "Done. Standalone binary lives under dist/radar/"
echo "Try it:  ./dist/radar/bin/radar /path/to/some/repo   # macOS/Linux launcher"
