#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$DIR/arete.jar"

DATA_DIR="$HOME/.arete/data"
PORT=""
WIPE=0
GROOVY=0
FORK=0
RULE_LANGUAGES=""

usage() {
  cat <<EOF
Usage: $(basename "$0") [--port PORT] [--wipe-db] [--enable-groovy-rules] [--fork-rules] [--rule-languages LIST] [-h|--help]

  --port, -p PORT   Run the server on PORT instead of the configured default.
  --wipe-db         Delete the local database ($DATA_DIR) before starting.
  --enable-groovy-rules
                    Allow the legacy, unsandboxed Groovy rule runtime as a
                    fallback (precedence: distill,starlark,groovy).
  --fork-rules  Run each rule in a disposable JVM with a timeout.
  --rule-languages LIST
                    Comma-separated rule language precedence, e.g.
                    "distill,starlark" (the default) or "starlark". The first
                    language with a source file present is used per rule.
  -h, --help        Show this help and exit.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --port=*) PORT="${1#*=}"; shift ;;
    --port|-p) PORT="$2"; shift 2 ;;
    --wipe-db|--reset-db) WIPE=1; shift ;;
    --enable-groovy-rules) GROOVY=1; shift ;;
    --fork-rules) FORK=1; shift ;;
    --rule-languages=*) RULE_LANGUAGES="${1#*=}"; shift ;;
    --rule-languages) RULE_LANGUAGES="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
done

if [ ! -f "$JAR" ]; then
  echo "Error: $JAR not found. Run build.sh first." >&2
  exit 1
fi

# Prefer JAVA_HOME when set, so a machine with multiple JDKs on PATH still
# runs the one the user pointed at. Only touch PATH once we know JAVA_HOME is
# actually set — prepending an empty value would corrupt PATH.
if [ -n "$JAVA_HOME" ]; then
  PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v java &>/dev/null; then
  echo "Error: Java 17 or later is required." >&2
  echo "Download from https://adoptium.net" >&2
  exit 1
fi

JAVA_VER=$(java -version 2>&1 | awk -F[\".] '/version/ {print $2}')
if [ "${JAVA_VER:-0}" -lt 17 ] 2>/dev/null; then
  echo "Error: Java 17 or later is required (found Java ${JAVA_VER})." >&2
  echo "Download from https://adoptium.net" >&2
  exit 1
fi

if [ "$WIPE" -eq 1 ]; then
  echo "Wiping database at $DATA_DIR"
  rm -rf "$DATA_DIR"
fi

ARGS=()
if [ -n "$RULE_LANGUAGES" ]; then
  ARGS+=("-Darete.policy.rule-languages=$RULE_LANGUAGES")
elif [ "$GROOVY" -eq 1 ]; then
  ARGS+=("-Darete.policy.rule-language=groovy")
fi
if [ "$FORK" -eq 1 ]; then
  ARGS+=("-Darete.policy.fork-rules=true")
fi
if [ -n "$PORT" ]; then
  ARGS+=("--server.port=$PORT")
fi

exec java "${ARGS[@]}" -jar "$JAR"
