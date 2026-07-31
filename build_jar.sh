#!/bin/bash

# Navigate to the directory where the script is located
cd "$(dirname "$0")"

echo "[1/4] Ensuring bin directory exists..."
mkdir -p bin

echo "[2/4] Compiling Java classes..."
javac -encoding UTF-8 -cp "lib/mysql-connector-j-9.5.0.jar" -d bin src/com/examsystem/*.java src/com/examsystem/models/*.java src/com/examsystem/dao/*.java src/com/examsystem/db/*.java src/com/examsystem/services/*.java src/com/examsystem/gui/*.java src/com/examsystem/gui/panels/*.java src/com/examsystem/gui/dialogs/*.java src/com/examsystem/gui/exam/*.java

echo "[3/4] Extracting MySQL connector to bin..."
cd bin
jar xf ../lib/mysql-connector-j-9.5.0.jar

# Remove META-INF to avoid manifest conflicts
if [ -d "META-INF" ]; then
    rm -rf META-INF
fi

echo "[4/4] Building exasecure.jar..."
echo "Main-Class: com.examsystem.Main" > manifest.txt
jar cvfm exasecure.jar manifest.txt -C . .
mv exasecure.jar .. > /dev/null
cd ..

echo "Done! Run it with: java -jar exasecure.jar"
read -p "Press Enter to continue..."