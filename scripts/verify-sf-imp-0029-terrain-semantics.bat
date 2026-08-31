@echo off
setlocal

echo === Skyforge SF-IMP-0029 terrain-semantics verification ===
echo.

echo [1/3] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/3] Continuous compiled-volume terrain semantics
call gradlew.bat :skyforge-world:test --tests "io.github.nidaba.skyforge.world.SkyIslandTerrainInterpreterTest"
if errorlevel 1 goto :failed

echo.
echo [3/3] Tiled semantic equivalence and occupancy preservation
call gradlew.bat :skyforge-world:test --tests "io.github.nidaba.skyforge.world.ReferenceTiledSkyIslandTerrainBackendTest"
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0029 terrain-semantics verification completed successfully ===
exit /b 0

:failed
echo.
echo === SF-IMP-0029 terrain-semantics verification FAILED ===
echo Preserve the console output and send it back for diagnosis.
exit /b 1
