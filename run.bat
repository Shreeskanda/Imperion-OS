@echo off
setlocal
cd /d "%~dp0"

echo [IMPERION OS] Launching IMPERION OS Cyberpunk Workstation...
start "IMPERION OS" javaw -cp bin javaos.Main
echo [IMPERION OS] IMPERION OS launched in background window.
echo             Check data\logs\javaos.log for runtime output.
