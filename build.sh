#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

mvn --no-transfer-progress -f "$DIR/pom.xml" clean package -DskipTests

JAR=$(ls "$DIR"/arete-app/target/arete-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "Build succeeded but no JAR found in target/" >&2
  exit 1
fi

cp "$JAR" "$DIR/scripts/arete.jar"
echo "Built: scripts/arete.jar"
