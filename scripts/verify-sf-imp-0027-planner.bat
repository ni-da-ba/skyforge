@echo off
setlocal

echo === Skyforge SF-IMP-0027 hierarchical planner verification ===
echo.

echo [1/2] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/2] Hierarchical archipelago planner proof
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.archipelago.SkyIslandArchipelagoPlannerTest"
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0027 hierarchical planner verification completed successfully ===
exit /b 0

:failed
echo.
echo === SF-IMP-0027 hierarchical planner verification FAILED ===
echo Preserve the console output and send it back for diagnosis.
exit /b 1
