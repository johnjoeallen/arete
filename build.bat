@echo off
setlocal

mvn --no-transfer-progress clean package -DskipTests
if %errorlevel% neq 0 exit /b %errorlevel%

for %%f in ("%~dp0target\openapi-viewer-*.jar") do (
    copy /y "%%f" "%~dp0scripts\speculate.jar" >nul
    echo Built: scripts\speculate.jar
    exit /b 0
)

echo Build succeeded but no JAR found in target\ >&2
exit /b 1
