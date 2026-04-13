@echo off
reg add HKCU\Console /v VirtualTerminalLevel /t REG_DWORD /d 1 /f >nul 2>&1
cd /d "%~dp0"

echo.
echo  Compiling...
javac *.java
if errorlevel 1 ( echo  Compilation failed. && pause && exit /b 1 )
echo  Done.
echo.
echo  1) Start SERVER
echo  2) Join as CLIENT
echo.
set /p choice=" Enter 1 or 2 > "

if "%choice%"=="1" ( java Server )
else if "%choice%"=="2" ( java ChatTUI )
else ( echo Invalid choice. && pause && exit /b 1 )

pause
