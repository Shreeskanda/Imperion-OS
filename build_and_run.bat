@echo off
setlocal
cd /d "%~dp0"

call build.bat
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed, aborting launch.
    pause
    exit /b %ERRORLEVEL%
)

call run.bat
