@echo off
setlocal

echo === Skyforge SF-IMP-0017 local verification ===
echo.

echo [1/4] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/4] Structured morphology recipe tests
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.SecondaryMorphologySkyIslandVolumeRecipeTest"
if errorlevel 1 goto :failed

echo.
echo [3/4] Six-seed structured morphology acceptance
call gradlew.bat :skyforge-reference:test --tests "io.github.nidaba.skyforge.reference.acceptance.SecondaryMorphologySuspendedVolumeAcceptanceTest"
if errorlevel 1 goto :failed

echo.
echo [4/4] Six-seed structured morphology visual corpus
call gradlew.bat :skyforge-reference:secondaryMorphologySuspendedVolumeCorpus
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0017 local verification completed successfully ===
echo Visual atlas:
echo skyforge-reference\build\evidence\secondary-morphology-suspended-volume-v1\index.html
exit /b 0

:failed
echo.
echo === SF-IMP-0017 local verification FAILED ===
echo Please preserve the console output and send it back for diagnosis.
exit /b 1
