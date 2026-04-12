@echo off
:: FIX: Use %~dp0 to always run from the script's own directory,
:: regardless of where the user calls it from.
cd /d "%~dp0"

echo.
echo  Compiling...
javac *.java
if errorlevel 1 (
    echo  Compilation failed. Fix errors above and try again.
    pause
    exit /b 1
)
echo  Compiled successfully.
echo.
echo  ────────────────────────────────────
echo   What do you want to do?
echo  ────────────────────────────────────
echo.
echo   1) Start SERVER  (same WiFi network)
echo   2) Join as CLIENT
echo.
set /p choice=" Enter 1 or 2 > "

if "%choice%"=="1" (
    echo.
    echo  SERVER STARTING (Local Network)
    echo.
    echo  Your IP:
    ipconfig | findstr /i "IPv4"
    echo  Port: 5000
    echo.
    echo  Share the IPv4 address above and port 5000
    echo  with everyone on the same WiFi.
    echo  Press Ctrl+C to stop.
    echo.
    java Server

) else if "%choice%"=="2" (
    echo.
    java ChatTUI
) else (
    echo  Invalid choice. Run the script again.
    pause
    exit /b 1
)

pause
