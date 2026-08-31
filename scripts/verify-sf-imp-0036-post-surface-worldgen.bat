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
echo [3/4] NeoForge generator and development-preset compile proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:compileJava :skyforge-neoforge-1211:compileTestJava :skyforge-neoforge-1211:processResources :skyforge-neoforge-1211:processDevelopmentResources
if errorlevel 1 exit /b %errorlevel%

echo.
echo [4/4] Registered generator, post-surface heightmap, and dev-runtime proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:test --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNoiseBasedChunkGeneratorTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211SurfaceStageTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211DevRuntimeTest"
if errorlevel 1 exit /b %errorlevel%

echo.
echo === SF-IMP-0036 post-surface worldgen verification completed successfully ===
echo Next automated gate: gradlew.bat check
echo Next manual gate: gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
endlocal
