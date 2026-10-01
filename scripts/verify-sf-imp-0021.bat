@echo off
setlocal

echo === Skyforge SF-IMP-0021 local verification ===
echo.

echo [1/4] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/4] Descriptor schema 1 compatibility and schema 2 model tests
call gradlew.bat :skyforge-model:test --tests "io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptorTest" --tests "io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptorSchema2Test" --tests "io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptorJsonTest"
if errorlevel 1 goto :failed

echo.
echo [3/4] Descriptor-driven semantic morphology recipe tests
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.SemanticSkyIslandVolumeRecipeTest"
if errorlevel 1 goto :failed

echo.
echo [4/4] Fifteen-member full-resolution semantic-control acceptance
call gradlew.bat :skyforge-reference:test --tests "io.github.nidaba.skyforge.reference.acceptance.SemanticMorphologyDescriptorAcceptanceTest"
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0021 local verification completed successfully ===
exit /b 0

:failed
echo.
echo === SF-IMP-0021 local verification FAILED ===
echo Preserve the console output and send it back for diagnosis.
exit /b 1
