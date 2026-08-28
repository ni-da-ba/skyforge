@echo off
setlocal

echo === Skyforge SF-IMP-0016 local verification ===
echo.

echo [1/5] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/5] Gradle wrapper / Java toolchain
call gradlew.bat --version
if errorlevel 1 goto :failed

echo.
echo [3/5] Focused seeded recipe and SF-VOL-006 acceptance tests
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.SeededSkyIslandVolumeRecipeTest" :skyforge-reference:test --tests "io.github.nidaba.skyforge.reference.acceptance.SeededSuspendedVolumeAcceptanceTest"
if errorlevel 1 goto :failed

echo.
echo [4/5] Complete repository verification
call gradlew.bat check
if errorlevel 1 goto :failed

echo.
echo [5/5] Six-seed visual evidence corpus
call gradlew.bat :skyforge-reference:seededSuspendedVolumeCorpus
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0016 local verification completed successfully ===
echo Visual atlas:
echo skyforge-reference\build\evidence\seeded-suspended-volume-v1\index.html
exit /b 0

:failed
echo.
echo === SF-IMP-0016 local verification FAILED ===
echo Please preserve the console output and send it back for diagnosis.
exit /b 1
