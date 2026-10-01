@echo off
setlocal

set "ROOT=%~dp0.."
cd /d "%ROOT%"
if errorlevel 1 exit /b %errorlevel%

echo === SF-IMP-0030 minimal backend context seam ===
echo.

echo [1/3] Java runtime
java -version
if errorlevel 1 exit /b %errorlevel%
echo.

echo [2/3] Minimal backend-visible Skyforge sample context
call gradlew.bat :skyforge-world:test --tests "io.github.nidaba.skyforge.world.SkyIslandTerrainSampleContextTest"
if errorlevel 1 exit /b %errorlevel%
echo.

echo [3/3] Backend-native representation context without geometry mutation
call gradlew.bat :skyforge-reference:test --tests "io.github.nidaba.skyforge.reference.backend.ReferenceTerrainMaterialAdapterTest"
if errorlevel 1 exit /b %errorlevel%
echo.

echo === SF-IMP-0030 context-seam verification completed successfully ===
