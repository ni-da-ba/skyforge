@echo off
setlocal

echo === Skyforge SF-IMP-0020 local verification ===
echo.

echo [1/4] Java runtime
java -version
if errorlevel 1 goto :failed

echo.
echo [2/4] Family-aware secondary morphology recipe tests
call gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.FamilyAwareMorphologySkyIslandVolumeRecipeTest"
if errorlevel 1 goto :failed

echo.
echo [3/4] Fifteen-member full-resolution family-aware acceptance
call gradlew.bat :skyforge-reference:test --tests "io.github.nidaba.skyforge.reference.acceptance.FamilyAwareMorphologySuspendedVolumeAcceptanceTest"
if errorlevel 1 goto :failed

echo.
echo [4/4] Fifteen-member lightweight family-aware visual atlas
call gradlew.bat :skyforge-reference:familyAwareMorphologySuspendedVolumeCorpus
if errorlevel 1 goto :failed

echo.
echo === SF-IMP-0020 local verification completed successfully ===
echo Visual atlas:
echo skyforge-reference\build\evidence\family-aware-morphology-suspended-volume-v1\index.html
exit /b 0

:failed
echo.
echo === SF-IMP-0020 local verification FAILED ===
echo Preserve the console output and send it back for diagnosis.
exit /b 1
