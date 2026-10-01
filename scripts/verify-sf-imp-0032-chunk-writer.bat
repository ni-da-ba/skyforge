@echo off
setlocal

echo === SF-IMP-0032 live BlockState / ChunkAccess verification ===

echo [1/4] Java runtime
java -version
if errorlevel 1 exit /b %errorlevel%

echo.
echo [2/4] Backend-neutral module independence
call gradlew.bat verifyBackendIndependence
if errorlevel 1 exit /b %errorlevel%

echo.
echo [3/4] Minecraft/NeoForge compile-link proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:compileJava :skyforge-neoforge-1211:compileTestJava
if errorlevel 1 exit /b %errorlevel%

echo.
echo [4/4] Live registry resolution and real ProtoChunk write proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:test --tests "io.github.nidaba.skyforge.neoforge1211.MinecraftBlockStateResolverTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211ChunkWriterTest"
if errorlevel 1 exit /b %errorlevel%

echo.
echo === SF-IMP-0032 live chunk-writer verification completed successfully ===
endlocal
