#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

mvn --no-transfer-progress -f "$DIR/pom.xml" clean package -DskipTests

JAR=$(ls "$DIR"/arete-app/target/arete-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "Build succeeded but no JAR found in target/" >&2
  exit 1
fi

POLICY_BASED_PLUGIN_JAR=$(ls "$DIR"/policy-based-validation-plugin/target/policy-based-validation-plugin-*.jar 2>/dev/null | head -1)
if [ -z "$POLICY_BASED_PLUGIN_JAR" ]; then
  echo "Build succeeded but no plugin JAR found in policy-based-validation-plugin/target/" >&2
  exit 1
fi

cp "$JAR" "$DIR/scripts/arete.jar"
mkdir -p "$DIR/scripts/plugins"
# Clear stale bundled plugin jars first — an old copy under a former name
# (e.g. generic-policy-validation-plugin.jar) would load a second plugin with
# the same id.
rm -f "$DIR"/scripts/plugins/*.jar
cp "$POLICY_BASED_PLUGIN_JAR" "$DIR/scripts/plugins/policy-based-validation-plugin.jar"
echo "Built: scripts/arete.jar (+ bundled validation plugins)"
