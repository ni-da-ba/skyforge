@echo off
setlocal

echo === SF-IMP-0031 NeoForge 1.21.1 adapter verification ===

echo [1/4] Java runtime
java -version
if errorlevel 1 exit /b %errorlevel%

echo.
echo [2/4] Backend-neutral module independence
call gradlew.bat verifyBackendIndependence
if errorlevel 1 exit /b %errorlevel%

echo.
echo [3/4] Minecraft/NeoForge compile-link proof
call gradlew.bat :skyforge-neoforge-1211:compileJava :skyforge-neoforge-1211:compileTestJava
if errorlevel 1 exit /b %errorlevel%

echo.
echo [4/4] Chunk translation, block-key projection, determinism, and seam proof
call gradlew.bat :skyforge-neoforge-1211:test --tests "io.github.nidaba.skyforge.neoforge1211.*"
if errorlevel 1 exit /b %errorlevel%

echo.
echo === SF-IMP-0031 NeoForge adapter verification completed successfully ===
endlocal
