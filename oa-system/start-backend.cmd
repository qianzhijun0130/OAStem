@echo off
REM ============================================================
REM  OA System backend launcher (ASCII only)
REM  NOTE: cmd.exe parses .cmd files as GBK on Chinese Windows.
REM        Keep this file pure ASCII, otherwise Chinese comments
REM        are parsed as commands and the script breaks.
REM  Steps: compile -> copy runtime deps -> start (default 8080)
REM  Usage: start-backend.cmd [port]
REM ============================================================
setlocal
cd /d "%~dp0"

set "PORT=%~1"
if "%PORT%"=="" set "PORT=8080"
echo Port: %PORT%

echo [1/3] Compiling ...
call mvnw.cmd -B -q compile
if errorlevel 1 (
    echo [ERROR] Compile failed. See output above.
    exit /b 1
)

echo [2/3] Copying runtime dependencies ...
call mvnw.cmd -B -q dependency:copy-dependencies -DoutputDirectory=target\lib -DincludeScope=runtime
if errorlevel 1 (
    echo [ERROR] Dependency copy failed.
    exit /b 1
)

echo [3/3] Starting backend ...
echo Base URL: http://127.0.0.1:%PORT%
echo Press Ctrl+C to stop.
java -Dfile.encoding=UTF-8 -cp "target\classes;target\lib\*" com.kellen.oa_system.OaSystemApplication --server.port=%PORT%

endlocal
