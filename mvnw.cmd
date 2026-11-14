@echo off
rem Minimal Maven wrapper for Windows: downloads Apache Maven once into %USERPROFILE%\.m2\wrapper and runs it.
setlocal
set "MAVEN_VERSION=3.9.11"
set "MAVEN_DIR=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%MAVEN_VERSION%"
set "MVN_CMD=%MAVEN_DIR%\apache-maven-%MAVEN_VERSION%\bin\mvn.cmd"

rem Use the JDK that IntelliJ downloaded (%USERPROFILE%\.jdks) if Java is not configured.
if not defined JAVA_HOME (
    where java >nul 2>nul
    if errorlevel 1 (
        for /d %%J in ("%USERPROFILE%\.jdks\*") do set "JAVA_HOME=%%~fJ"
    )
)

if exist "%MVN_CMD%" goto run

echo Downloading Apache Maven %MAVEN_VERSION% ...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $d=$env:MAVEN_DIR; New-Item -ItemType Directory -Force -Path $d | Out-Null; $zip=Join-Path $d 'maven.zip'; Invoke-WebRequest -UseBasicParsing -Uri ('https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/' + $env:MAVEN_VERSION + '/apache-maven-' + $env:MAVEN_VERSION + '-bin.zip') -OutFile $zip; Expand-Archive -Force -Path $zip -DestinationPath $d; Remove-Item $zip"
if errorlevel 1 (
    echo Failed to download Apache Maven.
    exit /b 1
)

:run
call "%MVN_CMD%" %*
exit /b %ERRORLEVEL%
