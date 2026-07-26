@echo off
setlocal
title Secure Exam - Sanity Check

echo === Java on PATH ===
where java  || (echo [ERROR] java not found on PATH & pause & exit /b 1)
where javac || (echo [ERROR] javac not found on PATH & pause & exit /b 1)
for /f "tokens=*" %%v in ('java -version 2^>^&1') do echo %%v
for /f "tokens=*" %%v in ('javac -version 2^>^&1') do echo %%v
echo.

echo === Project structure ===
if not exist src echo [ERROR] Missing src\ folder & pause & exit /b 1
if not exist lib echo [WARN ] Missing lib\ folder (creating) & mkdir lib
dir /b lib\mysql-connector-j-*.jar 2>nul || echo [WARN ] No MySQL driver jar in lib\ yet
echo.

echo === Must-exist sources ===
if exist src\com\examsystem\Main.java (echo OK: Main.java) else echo [MISS] src\com\examsystem\Main.java
if exist src\com\examsystem\gui\LoginWindow.java (echo OK: LoginWindow.java) else echo [MISS] src\com\examsystem\gui\LoginWindow.java
echo.

echo === Count sources under src\ ===
for /f "delims=" %%F in ('dir /s /b "src\*.java" 2^>nul') do @echo %%F>>"%TEMP%\sources_chk.txt"
for /f %%A in ('find /v /c "" ^< "%TEMP%\sources_chk.txt"') do set COUNT=%%A
echo Files: %COUNT%
if "%COUNT%"=="0" (
  echo [ERROR] No .java files were found under src\ .
  del /f /q "%TEMP%\sources_chk.txt" 2>nul
  pause & exit /b 1
)
del /f /q "%TEMP%\sources_chk.txt" 2>nul
echo.
echo Done. If any [MISS]/[ERROR] above, fix that first. Otherwise run run_gui_build_debug.cmd next.
pause
endlocal
