@echo off
setlocal enabledelayedexpansion

set "JAR=%~dp0speculate.jar"
set "DATA_DIR=%USERPROFILE%\.speculate\data"
set "PORT="
set "WIPE=0"

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
if /i "%~1"=="-h" goto :usage
if /i "%~1"=="--help" goto :usage
echo Unknown option: %~1
goto :usage

:usage
echo Usage: %~nx0 [--port PORT] [--wipe-db] [-h^|--help]
echo.
echo   --port, -p PORT   Run the server on PORT instead of the configured default.
echo   --wipe-db         Delete the local database ^(%DATA_DIR%^) before starting.
echo   -h, --help        Show this help and exit.
exit /b 0

:afterparse
if not exist "%JAR%" (
    echo Error: %JAR% not found. Run build.bat first.
    exit /b 1
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

set "ARGS="
if not "%PORT%"=="" set "ARGS=--server.port=%PORT%"

java -jar "%JAR%" %ARGS%
