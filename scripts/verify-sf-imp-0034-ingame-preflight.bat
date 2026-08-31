@echo off
setlocal

echo === SF-IMP-0034 in-game smoke preflight ===

echo [1/4] Java runtime
java -version
if errorlevel 1 exit /b %errorlevel%

echo.
echo [2/4] Backend-neutral module independence
call gradlew.bat verifyBackendIndependence
if errorlevel 1 exit /b %errorlevel%

echo.
echo [3/4] Production mod and development-client compile proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:compileJava :skyforge-neoforge-1211:compileTestJava :skyforge-neoforge-1211:processResources
if errorlevel 1 exit /b %errorlevel%

echo.
echo [4/4] FML lifecycle and deterministic development-specimen preflight
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:test --tests "io.github.nidaba.skyforge.neoforge1211.*"
if errorlevel 1 exit /b %errorlevel%

echo.
echo === SF-IMP-0034 in-game smoke preflight completed successfully ===
echo Next manual gate: gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
endlocal
