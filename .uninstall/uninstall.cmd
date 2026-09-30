@echo off
setlocal enabledelayedexpansion
set "ans="
set /p ans="Do u want to delete XOVER? (y\n): "
if /i not "!ans!"=="y" exit /b 0

cd /d "%~dp0"
del /f /q *.exe
del /f /q /s *.jar

cd /d "%~dp0.."
if exist "Xover" rmdir /s /q "Xover"
cd /d "%USERPROFILE%"
if exist ".xover" rmdir /s /q ".xover"

endlocal
