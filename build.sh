#!/usr/bin/env bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLUGINS_DIR="$PROJECT_DIR/minecraft_server/plugins"

echo "==> Building plugin..."
cd "$PROJECT_DIR"
mvn package -q -DskipTests

# Find the built JAR (exclude *-original.jar)
JAR=$(find "$PROJECT_DIR/target" -maxdepth 1 -name "*.jar" ! -name "*-original.jar" | head -1)
if [ -z "$JAR" ]; then
    echo "ERROR: No JAR found in target/" >&2
    exit 1
fi

JAR_NAME="$(basename "$JAR")"
DEST="$PLUGINS_DIR/$JAR_NAME"

# Remove old plugin JARs with the same base name (ignoring version differences)
BASE_NAME=$(echo "$JAR_NAME" | sed 's/-[0-9].*//')
find "$PLUGINS_DIR" -maxdepth 1 -name "${BASE_NAME}*.jar" -delete

cp "$JAR" "$DEST"
echo "==> Deployed: $DEST"
