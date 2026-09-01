@echo off
setlocal EnableExtensions
cd /d "%~dp0"

set "VERSION=1.0.0"
set "BUILD_DIR=build\release"
set "CLASSES_DIR=%BUILD_DIR%\classes"
set "STAGING_DIR=%BUILD_DIR%\staging"
set "DIST_DIR=dist"
set "OUTPUT=%DIST_DIR%\exasecure-%VERSION%.jar"

for /f "delims=" %%J in ('where javac.exe 2^>nul') do if not defined JAVAC set "JAVAC=%%~fJ"
if not defined JAVAC (
  echo [ERROR] javac.exe was not found. Install JDK 17 or newer and add it to PATH.
  exit /b 1
)

for /f "delims=" %%J in ('where jar.exe 2^>nul') do if not defined JAR set "JAR=%%~fJ"
if not defined JAR if defined EXASECURE_JAVA_HOME if exist "%EXASECURE_JAVA_HOME%\bin\jar.exe" set "JAR=%EXASECURE_JAVA_HOME%\bin\jar.exe"
if not defined JAR if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jar.exe" set "JAR=%JAVA_HOME%\bin\jar.exe"
if not defined JAR for /d %%J in ("%ProgramFiles%\Java\jdk-*") do if exist "%%~fJ\bin\jar.exe" set "JAR=%%~fJ\bin\jar.exe"
if not defined JAR for /d %%J in ("%ProgramFiles%\Eclipse Adoptium\jdk-*") do if exist "%%~fJ\bin\jar.exe" set "JAR=%%~fJ\bin\jar.exe"
if not defined JAR for /d %%J in ("%ProgramFiles%\Microsoft\jdk-*") do if exist "%%~fJ\bin\jar.exe" set "JAR=%%~fJ\bin\jar.exe"
if not defined JAR (
  echo [ERROR] jar.exe was not found. Install a complete JDK 17 or newer.
  exit /b 1
)

if not exist "lib\mysql-connector-j-*.jar" (
  echo [ERROR] MySQL Connector/J is missing from lib\. Run setup.ps1 first.
  exit /b 1
)
if not exist "lib\jbcrypt-*.jar" (
  echo [ERROR] jBCrypt is missing from lib\. Run setup.ps1 first.
  exit /b 1
)

if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
mkdir "%CLASSES_DIR%" "%STAGING_DIR%" "%DIST_DIR%" 2>nul

dir /s /b "src\*.java" > "%BUILD_DIR%\sources.txt"
echo [1/3] Compiling Java 17 classes...
"%JAVAC%" --release 17 -encoding UTF-8 -cp "lib\*" -d "%CLASSES_DIR%" @"%BUILD_DIR%\sources.txt"
if errorlevel 1 exit /b 1

echo [2/3] Bundling runtime dependencies...
xcopy "%CLASSES_DIR%\*" "%STAGING_DIR%\" /e /i /q >nul
for %%D in (lib\*.jar) do (
  pushd "%STAGING_DIR%"
  "%JAR%" xf "%%~fD"
  if errorlevel 1 (
    popd
    exit /b 1
  )
  popd
)
del /q "%STAGING_DIR%\META-INF\MANIFEST.MF" 2>nul
del /q "%STAGING_DIR%\META-INF\*.SF" "%STAGING_DIR%\META-INF\*.RSA" "%STAGING_DIR%\META-INF\*.DSA" 2>nul

(
  echo Manifest-Version: 1.0
  echo Main-Class: com.examsystem.Main
  echo Implementation-Title: ExaSecure
  echo Implementation-Version: %VERSION%
  echo.
) > "%BUILD_DIR%\MANIFEST.MF"

echo [3/3] Creating %OUTPUT%...
"%JAR%" cfm "%OUTPUT%" "%BUILD_DIR%\MANIFEST.MF" -C "%STAGING_DIR%" .
if errorlevel 1 exit /b 1

echo [OK] Built %OUTPUT%
echo Run with: java -jar %OUTPUT%
exit /b 0
