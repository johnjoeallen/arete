@echo off
setlocal

mvn --no-transfer-progress -f "%~dp0pom.xml" clean package -DskipTests
if %errorlevel% neq 0 exit /b %errorlevel%

set "JAR="
for %%f in ("%~dp0arete-app\target\arete-*.jar") do set "JAR=%%f"
if "%JAR%"=="" (
    echo Build succeeded but no JAR found in target\ >&2
    exit /b 1
)

copy /y "%JAR%" "%~dp0scripts\arete.jar" >nul
echo Built: scripts\arete.jar
