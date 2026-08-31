@echo off
setlocal

echo === SF-IMP-0036 post-surface worldgen verification ===

echo [1/4] Java runtime
java -version
if errorlevel 1 exit /b %errorlevel%

echo.
echo [2/4] Backend-neutral module independence
call gradlew.bat verifyBackendIndependence
if errorlevel 1 exit /b %errorlevel%

echo.
echo [3/4] NeoForge generator compile proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:compileJava :skyforge-neoforge-1211:compileTestJava :skyforge-neoforge-1211:processResources
if errorlevel 1 exit /b %errorlevel%

echo.
echo [4/4] Registered generator and post-surface heightmap proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:test --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNoiseBasedChunkGeneratorTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211SurfaceStageTest"
if errorlevel 1 exit /b %errorlevel%

echo.
echo === SF-IMP-0036 post-surface worldgen verification completed successfully ===
echo Next gate after focused success: gradlew.bat check
endlocal
