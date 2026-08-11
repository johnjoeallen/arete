#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$DIR/speculate.jar"

DATA_DIR="$HOME/.speculate/data"
PORT=""
WIPE=0

usage() {
  cat <<EOF
Usage: $(basename "$0") [--port PORT] [--wipe-db] [-h|--help]

  --port, -p PORT   Run the server on PORT instead of the configured default.
  --wipe-db         Delete the local database ($DATA_DIR) before starting.
  -h, --help        Show this help and exit.
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --port=*) PORT="${1#*=}"; shift ;;
    --port|-p) PORT="$2"; shift 2 ;;
    --wipe-db|--reset-db) WIPE=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
done

if [ ! -f "$JAR" ]; then
  echo "Error: $JAR not found. Run build.sh first." >&2
  exit 1
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
if [ -n "$PORT" ]; then
  ARGS+=("--server.port=$PORT")
fi

exec java -jar "$JAR" "${ARGS[@]}"
