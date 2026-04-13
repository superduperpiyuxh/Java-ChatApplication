@echo off
:: Enable ANSI escape codes in Windows CMD (safe to run, no effect on Windows Terminal)
reg add HKCU\Console /v VirtualTerminalLevel /t REG_DWORD /d 1 /f >nul 2>&1

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
    echo  Starting server on port 5000...
    echo  Share your IP and port 5000 with others on the same WiFi.
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
