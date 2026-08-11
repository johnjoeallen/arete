#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

mvn --no-transfer-progress clean package -DskipTests

JAR=$(ls "$DIR"/target/openapi-viewer-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "Build succeeded but no JAR found in target/" >&2
  exit 1
fi

cp "$JAR" "$DIR/scripts/speculate.jar"
echo "Built: scripts/speculate.jar"
