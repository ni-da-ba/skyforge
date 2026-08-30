@echo off
setlocal

echo === Skyforge SF-IMP-0024 local verification ===
echo.

echo [1/4] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/4] Public morphology-provider contract and built-in compatibility
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.SkyIslandMorphologyProviderContractTest"
if errorlevel 1 goto :failed

echo.
echo [3/4] Standalone custom provider and five built-in/provider hybrid full-resolution acceptance
call gradlew.bat :skyforge-reference:test --tests "io.github.nidaba.skyforge.reference.acceptance.ProviderHybridMorphologyAcceptanceTest"
if errorlevel 1 goto :failed

echo.
echo [4/4] Sixteen-member custom-provider visual progression atlas
call gradlew.bat :skyforge-reference:providerMorphologySuspendedVolumeCorpus
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0024 local verification completed successfully ===
echo Visual atlas:
echo skyforge-reference\build\evidence\provider-morphology-suspended-volume-v1\index.html
exit /b 0

:failed
echo.
echo === SF-IMP-0024 local verification FAILED ===
echo Preserve the console output and send it back for diagnosis.
exit /b 1
