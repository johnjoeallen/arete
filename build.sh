#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

mvn --no-transfer-progress -f "$DIR/pom.xml" clean package -DskipTests

JAR=$(ls "$DIR"/speculate-app/target/speculate-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "Build succeeded but no JAR found in target/" >&2
  exit 1
fi

PLUGIN_JAR=$(ls "$DIR"/zally-validation-plugin/target/zally-validation-plugin-*.jar 2>/dev/null | head -1)
if [ -z "$PLUGIN_JAR" ]; then
  echo "Build succeeded but no plugin JAR found in zally-validation-plugin/target/" >&2
  exit 1
fi

cp "$JAR" "$DIR/scripts/speculate.jar"
mkdir -p "$DIR/scripts/plugins"
cp "$PLUGIN_JAR" "$DIR/scripts/plugins/zally-validation-plugin.jar"
echo "Built: scripts/speculate.jar (+ scripts/plugins/zally-validation-plugin.jar)"
