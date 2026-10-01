@echo off
setlocal

echo === Skyforge SF-IMP-0025 local verification ===
echo.

echo [1/4] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/4] Provider-aware enrichment focused recipe proof
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.EnrichedProviderHybridMorphologySkyIslandVolumeRecipeTest"
if errorlevel 1 goto :failed

echo.
echo [3/4] Eighteen-member full-resolution custom-provider enrichment acceptance
call gradlew.bat :skyforge-reference:test --tests "io.github.nidaba.skyforge.reference.acceptance.EnrichedProviderHybridMorphologyAcceptanceTest"
if errorlevel 1 goto :failed

echo.
echo [4/4] Sixteen-member enriched custom-provider visual progression atlas
call gradlew.bat :skyforge-reference:enrichedProviderMorphologySuspendedVolumeCorpus
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0025 local verification completed successfully ===
echo Visual atlas:
echo skyforge-reference\build\evidence\enriched-provider-morphology-suspended-volume-v1\index.html
exit /b 0

:failed
echo.
echo === SF-IMP-0025 local verification FAILED ===
echo Preserve the console output and send it back for diagnosis.
exit /b 1
