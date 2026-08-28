@echo off
setlocal

mvn --no-transfer-progress -f "%~dp0pom.xml" clean package -DskipTests
if %errorlevel% neq 0 exit /b %errorlevel%

set "JAR="
for %%f in ("%~dp0speculate-app\target\speculate-*.jar") do set "JAR=%%f"
if "%JAR%"=="" (
    echo Build succeeded but no JAR found in target\ >&2
    exit /b 1
)

set "ZALLY_PLUGIN_JAR="
for %%f in ("%~dp0zally-validation-plugin\target\zally-validation-plugin-*.jar") do set "ZALLY_PLUGIN_JAR=%%f"
if "%ZALLY_PLUGIN_JAR%"=="" (
    echo Build succeeded but no plugin JAR found in zally-validation-plugin\target\ >&2
    exit /b 1
)

set "GENERIC_POLICY_PLUGIN_JAR="
for %%f in ("%~dp0generic-policy-validation-plugin\target\generic-policy-validation-plugin-*.jar") do set "GENERIC_POLICY_PLUGIN_JAR=%%f"
if "%GENERIC_POLICY_PLUGIN_JAR%"=="" (
    echo Build succeeded but no plugin JAR found in generic-policy-validation-plugin\target\ >&2
    exit /b 1
)

copy /y "%JAR%" "%~dp0scripts\speculate.jar" >nul
if not exist "%~dp0scripts\plugins" mkdir "%~dp0scripts\plugins"
copy /y "%ZALLY_PLUGIN_JAR%" "%~dp0scripts\plugins\zally-validation-plugin.jar" >nul
copy /y "%GENERIC_POLICY_PLUGIN_JAR%" "%~dp0scripts\plugins\generic-policy-validation-plugin.jar" >nul
echo Built: scripts\speculate.jar (+ bundled validation plugins)
