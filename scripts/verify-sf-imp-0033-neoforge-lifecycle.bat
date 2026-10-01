@echo off
setlocal

echo === SF-IMP-0033 NeoForge new-chunk lifecycle verification ===

echo [1/4] Java runtime
java -version
if errorlevel 1 exit /b %errorlevel%

echo.
echo [2/4] Backend-neutral module independence
call gradlew.bat verifyBackendIndependence
if errorlevel 1 exit /b %errorlevel%

echo.
echo [3/4] Production mod metadata and NeoForge compile-link proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:processResources :skyforge-neoforge-1211:compileJava :skyforge-neoforge-1211:compileTestJava
if errorlevel 1 exit /b %errorlevel%

echo.
echo [4/4] FML mod load, new-chunk event, additive overlay, and scope proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:test --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211ChunkLifecycleTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211ChunkWriterTest"
if errorlevel 1 exit /b %errorlevel%

echo.
echo === SF-IMP-0033 NeoForge lifecycle verification completed successfully ===
endlocal
