@echo off
setlocal enabledelayedexpansion

set "JAR=%~dp0speculate.jar"
set "DATA_DIR=%USERPROFILE%\.speculate\data"
set "PORT="
set "WIPE=0"
set "GROOVY=0"

:parse
if "%~1"=="" goto :afterparse
if /i "%~1"=="--port" (
    set "PORT=%~2"
    shift
    shift
    goto :parse
)
if /i "%~1"=="-p" (
    set "PORT=%~2"
    shift
    shift
    goto :parse
)
if /i "%~1"=="--wipe-db" (
    set "WIPE=1"
    shift
    goto :parse
)
if /i "%~1"=="--reset-db" (
    set "WIPE=1"
    shift
    goto :parse
)
if /i "%~1"=="--enable-groovy-detectors" (
    set "GROOVY=1"
    shift
    goto :parse
)
if /i "%~1"=="-h" goto :usage
if /i "%~1"=="--help" goto :usage
echo Unknown option: %~1
goto :usage

:usage
echo Usage: %~nx0 [--port PORT] [--wipe-db] [--enable-groovy-detectors] [-h^|--help]
echo.
echo   --port, -p PORT   Run the server on PORT instead of the configured default.
echo   --wipe-db         Delete the local database ^(%DATA_DIR%^) before starting.
echo   --enable-groovy-detectors
echo                     Enable the legacy, unsandboxed Groovy detector runtime.
echo   -h, --help        Show this help and exit.
exit /b 0

:afterparse
if not exist "%JAR%" (
    echo Error: %JAR% not found. Run build.bat first.
    exit /b 1
)

rem Prefer JAVA_HOME when set, so a machine with multiple JDKs on PATH still
rem runs the one the user pointed at. Only touch PATH once we know JAVA_HOME
rem is actually set -- prepending an empty value would corrupt PATH.
if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

where java >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: Java 17 or later is required.
    echo Download from https://adoptium.net
    exit /b 1
)

for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set JAVA_VER=%%v
)
set JAVA_VER=%JAVA_VER:"=%
for /f "delims=." %%m in ("%JAVA_VER%") do set JAVA_MAJOR=%%m
if %JAVA_MAJOR% LSS 17 (
    echo Error: Java 17 or later is required ^(found Java %JAVA_MAJOR%^).
    echo Download from https://adoptium.net
    exit /b 1
)

if "%WIPE%"=="1" (
    echo Wiping database at %DATA_DIR%
    rmdir /s /q "%DATA_DIR%" 2>nul
)

set "JAVA_OPTS="
if "%GROOVY%"=="1" set "JAVA_OPTS=-Dspeculate.policy.detector-language=groovy"
set "ARGS="
if not "%PORT%"=="" set "ARGS=--server.port=%PORT%"

java %JAVA_OPTS% -jar "%JAR%" %ARGS%
