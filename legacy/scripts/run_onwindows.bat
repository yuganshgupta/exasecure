@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Secure Examination System (Swing GUI)

REM ------------------------------------------------------------
REM  Paths and setup
REM ------------------------------------------------------------
set "BASE=%~dp0"
cd /d "%BASE%"
set "SRC=src"
set "BIN=bin"
set "LIB=lib"
set "MAIN_CLASS=com.examsystem.Main"

REM Optional: switch console to UTF-8 for nicer output
chcp 65001 >nul

if not exist "%BIN%" mkdir "%BIN%" >nul 2>nul

echo.
echo =====================================================
echo   Secure Examination System - Swing GUI Launcher
echo =====================================================
echo Project root : %CD%
echo Source dir   : %SRC%
echo Output dir   : %BIN%
echo Lib dir      : %LIB%
echo.

REM ------------------------------------------------------------
REM  Resolve Java (PATH, explicit/local JAVA_HOME, then common JDK folders)
REM ------------------------------------------------------------
set "JAVAC_CMD="
set "JAVA_CMD="

for /f "delims=" %%J in ('where javac.exe 2^>nul') do (
  if not defined JAVAC_CMD set "JAVAC_CMD=%%~fJ"
)

if not defined JAVAC_CMD if defined EXASECURE_JAVA_HOME (
  if exist "%EXASECURE_JAVA_HOME%\bin\javac.exe" set "JAVAC_CMD=%EXASECURE_JAVA_HOME%\bin\javac.exe"
)
if not defined JAVAC_CMD if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\javac.exe" set "JAVAC_CMD=%JAVA_HOME%\bin\javac.exe"
)

if not defined JAVAC_CMD (
  for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-*") do (
    if exist "%%~fD\bin\javac.exe" set "JAVAC_CMD=%%~fD\bin\javac.exe"
  )
)
if not defined JAVAC_CMD (
  for /d %%D in ("%ProgramFiles%\Java\jdk-*") do (
    if exist "%%~fD\bin\javac.exe" set "JAVAC_CMD=%%~fD\bin\javac.exe"
  )
)
if not defined JAVAC_CMD (
  for /d %%D in ("%ProgramFiles%\Microsoft\jdk-*") do (
    if exist "%%~fD\bin\javac.exe" set "JAVAC_CMD=%%~fD\bin\javac.exe"
  )
)

if not defined JAVAC_CMD (
  echo [ERROR] javac not found. Install a JDK or set JAVA_HOME.
  pause
  exit /b 1
)

for %%D in ("!JAVAC_CMD!") do set "JAVA_CMD=%%~dpDjava.exe"
if not exist "!JAVA_CMD!" (
  echo [ERROR] java.exe was not found beside "!JAVAC_CMD!".
  pause
  exit /b 1
)

"!JAVAC_CMD!" -version >nul 2>nul
if errorlevel 1 (
  echo [ERROR] The resolved javac executable could not run: "!JAVAC_CMD!"
  pause
  exit /b 1
)
"!JAVA_CMD!" -version >nul 2>nul
if errorlevel 1 (
  echo [ERROR] The resolved java executable could not run: "!JAVA_CMD!"
  pause
  exit /b 1
)
echo Java compiler : !JAVAC_CMD!
echo Java runtime  : !JAVA_CMD!
echo.

REM ------------------------------------------------------------
REM  Detect MySQL Connector/J automatically (or pass as %1)
REM  You can run: run_onwindows.bat mysql-connector-j-9.5.0.jar
REM ------------------------------------------------------------
set "MYSQL_ARG=%~1"
set "MYSQL_JAR_FULL="
if not "%MYSQL_ARG%"=="" (
  if exist "%MYSQL_ARG%" (
    set "MYSQL_JAR_FULL=%MYSQL_ARG%"
  ) else if exist "%LIB%\%MYSQL_ARG%" (
    set "MYSQL_JAR_FULL=%LIB%\%MYSQL_ARG%"
  )
)
if not defined MYSQL_JAR_FULL (
  for /f "delims=" %%F in ('dir /b /a:-d "%LIB%\mysql-connector-j-*.jar" ^| sort /r') do (
    if not defined MYSQL_JAR_FULL set "MYSQL_JAR_FULL=%LIB%\%%F"
  )
)
if not defined MYSQL_JAR_FULL (
  echo [ERROR] Put mysql-connector-j-*.jar into the lib folder or pass it as an argument.
  pause
  exit /b 1
)
echo Using MySQL Connector: %MYSQL_JAR_FULL%
echo.

REM ------------------------------------------------------------
REM  Build argfile (list of all .java source files)
REM  Use forward slashes + quotes (safe with spaces)
REM ------------------------------------------------------------
set "SRC_LIST=%TEMP%\sources_%RANDOM%.txt"
if exist "%SRC_LIST%" del /f /q "%SRC_LIST%" >nul 2>nul
type nul > "%SRC_LIST%"

set "COUNT=0"
for /r "%SRC%" %%F in (*.java) do (
  set "p=%%~fF"
  set "p=!p:\=/!"
  echo "!p!">>"%SRC_LIST%"
  set /a COUNT+=1 >nul
)

if "%COUNT%"=="0" (
  echo [ERROR] No .java files found under "%SRC%".
  del /f /q "%SRC_LIST%" >nul 2>nul
  pause
  exit /b 1
)
echo [INFO] Source files found: %COUNT%

REM ------------------------------------------------------------
REM  Compile
REM ------------------------------------------------------------
echo [INFO] Compiling...
"!JAVAC_CMD!" --release 17 -encoding UTF-8 -cp ".;%LIB%\*" -d "%BIN%" "@%SRC_LIST%"
if errorlevel 1 (
  echo.
  echo [ERROR] Compilation failed. See messages above.
  del /f /q "%SRC_LIST%" >nul 2>nul
  pause
  exit /b 1
)
del /f /q "%SRC_LIST%" >nul 2>nul
echo [OK] Build successful.
echo.

REM ------------------------------------------------------------
REM  Run
REM ------------------------------------------------------------
echo [INFO] Launching GUI...
echo.
"!JAVA_CMD!" -cp "%BIN%;%LIB%\*" %MAIN_CLASS%
set "EXITCODE=%ERRORLEVEL%"

echo.
echo [INFO] Java exited with code: %EXITCODE%
pause
endlocal
