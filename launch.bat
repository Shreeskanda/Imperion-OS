@echo off
setlocal
cd /d "%~dp0"
echo [IMPERION OS] Starting IMPERION OS Cyberpunk Desktop...
start "IMPERION OS" /B javaw -cp bin javaos.Main
echo [IMPERION OS] Window launched. Check your taskbar if it doesn't appear immediately.
