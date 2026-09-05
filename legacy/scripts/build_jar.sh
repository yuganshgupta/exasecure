#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
VERSION="1.0.0"
BUILD_DIR="$ROOT/build/release"
CLASSES_DIR="$BUILD_DIR/classes"
STAGING_DIR="$BUILD_DIR/staging"
DIST_DIR="$ROOT/dist"
OUTPUT="$DIST_DIR/exasecure-$VERSION.jar"

command -v javac >/dev/null 2>&1 || {
  echo "[ERROR] javac was not found. Install JDK 17 or newer." >&2
  exit 1
}
command -v jar >/dev/null 2>&1 || {
  echo "[ERROR] jar was not found. Install a complete JDK." >&2
  exit 1
}

compgen -G "$ROOT/lib/mysql-connector-j-*.jar" >/dev/null || {
  echo "[ERROR] MySQL Connector/J is missing from lib/." >&2
  exit 1
}
compgen -G "$ROOT/lib/jbcrypt-*.jar" >/dev/null || {
  echo "[ERROR] jBCrypt is missing from lib/." >&2
  exit 1
}

rm -rf "$BUILD_DIR"
mkdir -p "$CLASSES_DIR" "$STAGING_DIR" "$DIST_DIR"
find "$ROOT/src" -name '*.java' -print > "$BUILD_DIR/sources.txt"

echo "[1/3] Compiling Java 17 classes..."
javac --release 17 -encoding UTF-8 -cp "$ROOT/lib/*" \
  -d "$CLASSES_DIR" @"$BUILD_DIR/sources.txt"

echo "[2/3] Bundling runtime dependencies..."
cp -R "$CLASSES_DIR/." "$STAGING_DIR/"
for dependency in "$ROOT"/lib/*.jar; do
  (cd "$STAGING_DIR" && jar xf "$dependency")
done
rm -f "$STAGING_DIR/META-INF/MANIFEST.MF"
rm -f "$STAGING_DIR"/META-INF/*.SF "$STAGING_DIR"/META-INF/*.RSA "$STAGING_DIR"/META-INF/*.DSA

cat > "$BUILD_DIR/MANIFEST.MF" <<EOF
Manifest-Version: 1.0
Main-Class: com.examsystem.Main
Implementation-Title: ExaSecure
Implementation-Version: $VERSION

EOF

echo "[3/3] Creating $OUTPUT..."
jar cfm "$OUTPUT" "$BUILD_DIR/MANIFEST.MF" -C "$STAGING_DIR" .
echo "[OK] Built $OUTPUT"
echo "Run with: java -jar $OUTPUT"
