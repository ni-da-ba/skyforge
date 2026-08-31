@echo off
setlocal

echo === Skyforge SF-IMP-0028 world-boundary verification ===
echo.

echo [1/3] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/3] Backend-neutral world catalog and region-query proof
call gradlew.bat :skyforge-world:test --tests "io.github.nidaba.skyforge.world.SkyIslandWorldCatalogTest"
if errorlevel 1 goto :failed

echo.
echo [3/3] Tiled backend equivalence and seam proof
call gradlew.bat :skyforge-world:test --tests "io.github.nidaba.skyforge.world.ReferenceTiledSkyIslandBackendTest"
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0028 world-boundary verification completed successfully ===
exit /b 0

:failed
echo.
echo === SF-IMP-0028 world-boundary verification FAILED ===
echo Preserve the console output and send it back for diagnosis.
exit /b 1
