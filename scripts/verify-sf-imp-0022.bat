@echo off
setlocal

echo === Skyforge SF-IMP-0022 local verification ===
echo.

echo [1/4] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/4] Pairwise hybrid recipe and provider-seam tests
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.HybridMorphologySkyIslandVolumeRecipeTest"
if errorlevel 1 goto :failed

echo.
echo [3/4] Thirty-member full-resolution pairwise midpoint acceptance
call gradlew.bat :skyforge-reference:test --tests "io.github.nidaba.skyforge.reference.acceptance.HybridMorphologySuspendedVolumeAcceptanceTest"
if errorlevel 1 goto :failed

echo.
echo [4/4] Thirty-member 25/50/75-percent hybrid visual progression atlas
call gradlew.bat :skyforge-reference:hybridMorphologySuspendedVolumeCorpus
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0022 local verification completed successfully ===
echo Visual atlas:
echo skyforge-reference\build\evidence\hybrid-morphology-suspended-volume-v1\index.html
exit /b 0

:failed
echo.
echo === SF-IMP-0022 local verification FAILED ===
echo Preserve the console output and send it back for diagnosis.
exit /b 1
