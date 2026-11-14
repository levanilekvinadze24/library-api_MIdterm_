@echo off
rem Downloads Maven + all Spring Boot libraries (first run only) and runs the 18 tests.
rem Full output is written to build-log.txt in this folder.
cd /d "%~dp0"
echo Downloading Maven and Spring Boot libraries (first run takes a few minutes), then running tests...
echo Full output: %~dp0build-log.txt
call mvnw.cmd -B test > build-log.txt 2>&1
if errorlevel 1 (
    echo.
    echo BUILD FAILED - see build-log.txt
) else (
    echo.
    echo BUILD SUCCESS - all tests passed
)
echo.
echo You can close this window.
pause
