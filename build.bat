@echo off
setlocal
cd /d "%~dp0"

echo [IMPERION OS] Compiling IMPERION OS source code...
if not exist "bin" mkdir bin

javac -encoding UTF-8 -d bin -sourcepath src src\javaos\Main.java src\javaos\core\*.java src\javaos\models\*.java src\javaos\services\*.java src\javaos\util\*.java src\javaos\ui\*.java src\javaos\ui\components\*.java src\javaos\apps\*.java

if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Compilation failed!
    exit /b %ERRORLEVEL%
)

echo [SUCCESS] Compilation successful. Output in bin/
exit /b 0
