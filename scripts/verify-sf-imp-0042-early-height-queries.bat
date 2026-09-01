@echo off
setlocal EnableExtensions

echo === SF-IMP-0042 early generator height-query verification ===

echo [1/4] Java runtime
java -version
if errorlevel 1 exit /b %errorlevel%

echo.
echo [2/4] Backend-neutral module independence
call gradlew.bat verifyBackendIndependence
if errorlevel 1 exit /b %errorlevel%

echo.
echo [3/4] NeoForge early-query compile and regression proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:compileJava :skyforge-neoforge-1211:compileTestJava :skyforge-neoforge-1211:test --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeEarlyHeightQueryTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211SurfaceStageTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNoiseBasedChunkGeneratorTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211FeatureStageTest"
if errorlevel 1 exit /b %errorlevel%

echo.
echo [4/4] Generator bridge wiring guard
findstr /c:"public int getBaseHeight(" "skyforge-neoforge-1211\src\main\java\io\github\nidaba\skyforge\neoforge1211\SkyforgeNoiseBasedChunkGenerator.java" >nul
if errorlevel 1 (
    echo ERROR: SkyforgeNoiseBasedChunkGenerator does not override getBaseHeight
    exit /b 1
)
findstr /c:"SkyforgeNeoForge1211SurfaceStage.queryBaseHeight" "skyforge-neoforge-1211\src\main\java\io\github\nidaba\skyforge\neoforge1211\SkyforgeNoiseBasedChunkGenerator.java" >nul
if errorlevel 1 (
    echo ERROR: getBaseHeight does not consult the Skyforge early-query bridge
    exit /b 1
)
findstr /c:"Math.max(vanillaHeight, skyforgeHeight.getAsInt())" "skyforge-neoforge-1211\src\main\java\io\github\nidaba\skyforge\neoforge1211\SkyforgeNoiseBasedChunkGenerator.java" >nul
if errorlevel 1 (
    echo ERROR: generator does not preserve vanilla terrain through max(vanilla, skyforge)
    exit /b 1
)

echo.
echo === SF-IMP-0042 early height-query verification completed successfully ===
echo Next gate: gradlew.bat check
echo No manual client gate: SF-IMP-0042 is a non-mutating prerequisite for structure integration.
endlocal
