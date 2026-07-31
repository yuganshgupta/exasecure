#!/usr/bin/env bash
# POSIX-friendly launcher for macOS (handles paths with spaces)
set -eu
  
BASE="$(cd "$(dirname "$0")" && pwd)"
SRC="$BASE/src"
BIN="$BASE/bin"
LIB="$BASE/lib"
MAIN_CLASS="com.examsystem.Main"

printf "\n=====================================================\n"
printf "  Secure Examination System - Swing GUI (macOS)\n"
printf "=====================================================\n"
printf "Project root : %s\n" "$BASE"
printf "Source dir   : %s\n" "$SRC"
printf "Output dir   : %s\n" "$BIN"
printf "Lib dir      : %s\n\n" "$LIB"

mkdir -p "$BIN"
mkdir -p "$LIB"

MYSQL_JAR_ARG="${1:-}"
MYSQL_JAR_FULL=""

if [ -n "$MYSQL_JAR_ARG" ]; then
  if [ -f "$MYSQL_JAR_ARG" ]; then
    MYSQL_JAR_FULL="$MYSQL_JAR_ARG"
  elif [ -f "$LIB/$MYSQL_JAR_ARG" ]; then
    MYSQL_JAR_FULL="$LIB/$MYSQL_JAR_ARG"
  fi
fi

if [ -z "$MYSQL_JAR_FULL" ]; then
  last=""
  for f in "$LIB"/mysql-connector-j-*.jar; do
    if [ -e "$f" ]; then
      last="$f"
    fi
  done
  if [ -z "$last" ]; then
    echo "[ERROR] No mysql-connector-j-*.jar found in $LIB"
    exit 1
  fi
  MYSQL_JAR_FULL="$last"
fi

printf "Using MySQL Connector: %s\n\n" "$MYSQL_JAR_FULL"

# Build quoted sources list (each path wrapped with quotes)
SRC_LIST="$(mktemp "/tmp/sources_XXXXX.txt")"
# ensure it's empty
: > "$SRC_LIST"
# find files and write each quoted line
find "$SRC" -name '*.java' -print0 | while IFS= read -r -d '' file; do
  printf '"%s"\n' "$file" >> "$SRC_LIST"
done

LINECOUNT=0
if [ -f "$SRC_LIST" ]; then
  LINECOUNT=$(wc -l < "$SRC_LIST" | tr -d ' ')
fi

if [ "$LINECOUNT" -eq 0 ]; then
  echo "[ERROR] No .java files found under $SRC"
  rm -f "$SRC_LIST"
  exit 1
fi

printf "[INFO] Found %s Java source files.\n\n" "$LINECOUNT"

# Compile using javac @argfile (argfile contains quoted paths)
printf "[INFO] Compiling...\n"
javac -encoding UTF-8 -cp ".:$MYSQL_JAR_FULL" -d "$BIN" @"$SRC_LIST" || {
  echo
  echo "[ERROR] Compilation failed."
  rm -f "$SRC_LIST"
  exit 1
}
rm -f "$SRC_LIST"
echo "[OK] Build successful."
echo

echo "[INFO] Launching GUI..."
echo
java -cp "$BIN:$MYSQL_JAR_FULL" "$MAIN_CLASS"
EXITCODE=$?
echo
echo "[INFO] Java exited with code: $EXITCODE"
exit $EXITCODE