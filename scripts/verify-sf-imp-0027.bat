@echo off
setlocal

echo === Skyforge SF-IMP-0027 local verification ===
echo.

echo [1/4] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/4] Hierarchical archipelago planner proof
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlannerTest"
if errorlevel 1 goto :failed

echo.
echo [3/4] Six-scene hierarchical regional realization acceptance
call gradlew.bat :skyforge-reference:test --tests "io.github.nidaba.skyforge.reference.acceptance.SkyIslandArchipelagoRealizationAcceptanceTest"
if errorlevel 1 goto :failed

echo.
echo [4/4] Stable-seed Hub and Arc regional visual atlas
call gradlew.bat :skyforge-reference:hierarchicalArchipelagoCorpus
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0027 local verification completed successfully ===
echo Visual atlas:
echo skyforge-reference\build\evidence\hierarchical-archipelago-v1\index.html
exit /b 0

:failed
echo.
echo === SF-IMP-0027 local verification FAILED ===
echo Preserve the console output and send it back for diagnosis.
exit /b 1
