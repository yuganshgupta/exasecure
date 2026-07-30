@echo off
setlocal
cd /d "%~dp0"

echo [1/4] Ensuring bin directory exists...
if not exist bin mkdir bin

echo [2/4] Compiling Java classes...
javac -encoding UTF-8 -cp "lib/mysql-connector-j-9.5.0.jar" -d bin src\com\examsystem\*.java src\com\examsystem\models\*.java src\com\examsystem\dao\*.java src\com\examsystem\db\*.java src\com\examsystem\services\*.java src\com\examsystem\gui\*.java src\com\examsystem\gui\panels\*.java src\com\examsystem\gui\dialogs\*.java src\com\examsystem\gui\exam\*.java

echo [3/4] Extracting MySQL connector to bin...
cd bin
"C:\Program Files\Java\jdk-25\bin\jar.exe" xf ..\lib\mysql-connector-j-9.5.0.jar
if exist META-INF rmdir /S /Q META-INF

echo [4/4] Building exasecure.jar...
echo Main-Class: com.examsystem.Main> manifest.txt
"C:\Program Files\Java\jdk-25\bin\jar.exe" cvfm exasecure.jar manifest.txt -C . .
move exasecure.jar .. > nul
cd ..

echo Done! Run it with: java -jar exasecure.jar
pause
