@echo off
setlocal

echo === Skyforge SF-IMP-0026 local verification ===
echo.

echo [1/5] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/5] Deterministic chain and cluster planner proof
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandGroupPlannerTest"
if errorlevel 1 goto :failed

echo.
echo [3/5] Single-provider and provider-blend group morphology compilation
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.group.SkyIslandMorphologySpecCompilerTest"
if errorlevel 1 goto :failed

echo.
echo [4/5] Six-group mixed-provider realization acceptance
call gradlew.bat :skyforge-reference:test --tests "io.github.nidaba.skyforge.reference.acceptance.SkyIslandGroupRealizationAcceptanceTest"
if errorlevel 1 goto :failed

echo.
echo [5/5] Stable-seed chain and cluster visual atlas
call gradlew.bat :skyforge-reference:multiIslandGroupCorpus
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0026 local verification completed successfully ===
echo Visual atlas:
echo skyforge-reference\build\evidence\multi-island-group-v1\index.html
exit /b 0

:failed
echo.
echo === SF-IMP-0026 local verification FAILED ===
echo Preserve the console output and send it back for diagnosis.
exit /b 1
