@echo off
setlocal

echo === Skyforge SF-IMP-0023 local verification ===
echo.

echo [1/5] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/5] Blend canonicalization regression
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.MorphologyBlendCanonicalizationTest"
if errorlevel 1 goto :failed

echo.
echo [3/5] Enriched hybrid recipe and differential tests
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.EnrichedHybridMorphologySkyIslandVolumeRecipeTest"
if errorlevel 1 goto :failed

echo.
echo [4/5] Thirty-member full-resolution enriched midpoint acceptance
call gradlew.bat :skyforge-reference:test --tests "io.github.nidaba.skyforge.reference.acceptance.EnrichedHybridMorphologySuspendedVolumeAcceptanceTest"
if errorlevel 1 goto :failed

echo.
echo [5/5] Thirty-member full-detail full-secondary 25/50/75 visual progression atlas
call gradlew.bat :skyforge-reference:enrichedHybridMorphologySuspendedVolumeCorpus
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0023 local verification completed successfully ===
echo Visual atlas:
echo skyforge-reference\build\evidence\enriched-hybrid-morphology-suspended-volume-v1\index.html
exit /b 0

:failed
echo.
echo === SF-IMP-0023 local verification FAILED ===
echo Preserve the console output and send it back for diagnosis.
exit /b 1
