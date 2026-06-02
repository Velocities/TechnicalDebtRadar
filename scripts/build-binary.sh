#!/usr/bin/env bash
# Builds a standalone Technical Debt Radar binary (macOS / Linux).
#
# Output: dist/radar/bin/radar  (no Scala or Java required to run it)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Building fat JAR (sbt assembly)..."
sbt -batch "assembly"

echo "==> Staging JAR..."
rm -rf stage dist
mkdir -p stage
cp target/scala-3.8.3/technical-debt-radar.jar stage/

echo "==> Packaging standalone binary (jpackage)..."
jpackage \
  --type app-image \
  --name radar \
  --input stage \
  --main-jar technical-debt-radar.jar \
  --main-class radar \
  --dest dist

echo ""
echo "Done. Standalone binary lives under dist/radar/"
echo "Try it:  ./dist/radar/bin/radar /path/to/some/repo   # macOS/Linux launcher"
