@echo off
setlocal EnableExtensions EnableDelayedExpansion

echo === SF-IMP-0043 native structure-start verification ===

echo [1/5] Java runtime
java -version
if errorlevel 1 exit /b %errorlevel%

echo.
echo [2/5] Backend-neutral module independence
call gradlew.bat verifyBackendIndependence
if errorlevel 1 exit /b %errorlevel%

echo.
echo [3/5] Early-height bridge regression and development-resource compile proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:compileJava :skyforge-neoforge-1211:compileTestJava :skyforge-neoforge-1211:processDevelopmentResources :skyforge-neoforge-1211:test --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeEarlyHeightQueryTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNoiseBasedChunkGeneratorTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211SurfaceStageTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211DevRuntimeTest"
if errorlevel 1 exit /b %errorlevel%

set "STRUCTURE_SET=skyforge-neoforge-1211\build\resources\development\data\skyforge\worldgen\structure_set\sf_imp_0043_desert_pyramids.json"
set "BIOME_TAG=skyforge-neoforge-1211\build\resources\development\data\minecraft\tags\worldgen\biome\has_structure\desert_pyramid.json"
set "LANG=skyforge-neoforge-1211\build\resources\development\assets\skyforge\lang\en_us.json"

for %%P in ("!STRUCTURE_SET!" "!BIOME_TAG!" "!LANG!") do (
    if not exist %%P (
        echo ERROR: required SF-IMP-0043 development resource missing: %%~P
        exit /b 1
    )
)

powershell -NoProfile -Command "$j = ConvertFrom-Json -InputObject (Get-Content -Raw -LiteralPath $env:STRUCTURE_SET); if ($j.placement.type -ne 'minecraft:random_spread' -or $j.placement.spacing -ne 4 -or $j.placement.separation -ne 3 -or $j.structures.Count -ne 1 -or $j.structures[0].structure -ne 'minecraft:desert_pyramid') { exit 1 }"
if errorlevel 1 (
    echo ERROR: SF-IMP-0043 structure set does not match the deterministic vanilla-pyramid proof contract
    exit /b 1
)
powershell -NoProfile -Command "$j = ConvertFrom-Json -InputObject (Get-Content -Raw -LiteralPath $env:BIOME_TAG); if ($j.replace -ne $false -or $j.values -notcontains '#c:is_overworld') { exit 1 }"
if errorlevel 1 (
    echo ERROR: SF-IMP-0043 biome tag must append c:is_overworld to vanilla desert-pyramid eligibility
    exit /b 1
)
findstr /c:"SF-IMP-0043" "!LANG!" >nul
if errorlevel 1 (
    echo ERROR: development world label does not identify SF-IMP-0043
    exit /b 1
)

echo.
echo [4/5] Retired benchmark-fixture and early-query wiring guard
for %%P in (
    "skyforge-neoforge-1211\src\development\resources\data\skyforge\neoforge\biome_modifier\add_additional_surface_grass.json"
    "skyforge-neoforge-1211\src\development\resources\data\skyforge\neoforge\biome_modifier\add_additional_surface_trees_plains.json"
    "skyforge-neoforge-1211\src\development\resources\data\skyforge\worldgen\placed_feature\additional_surface_grass.json"
    "skyforge-neoforge-1211\src\development\resources\data\skyforge\worldgen\placed_feature\additional_surface_trees_plains.json"
) do (
    if exist %%P (
        echo ERROR: retired vegetation benchmark fixture is still active: %%~P
        exit /b 1
    )
)

findstr /c:"SkyforgeNeoForge1211SurfaceStage.queryBaseHeight" "skyforge-neoforge-1211\src\main\java\io\github\nidaba\skyforge\neoforge1211\SkyforgeNoiseBasedChunkGenerator.java" >nul
if errorlevel 1 (
    echo ERROR: structure proof lost the accepted early generator height-query bridge
    exit /b 1
)

echo.
echo [5/5] Production jar excludes SF-IMP-0043 forcing data
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:jar
if errorlevel 1 exit /b %errorlevel%
set "LISTING=%TEMP%\skyforge-sf-imp-0043-production-listing.txt"
set "PACKAGE="
for %%F in (skyforge-neoforge-1211\build\libs\*.jar) do (
    jar tf "%%~fF" > "!LISTING!" 2>nul
    if !errorlevel! equ 0 (
        findstr /x /c:"META-INF/neoforge.mods.toml" "!LISTING!" >nul
        if !errorlevel! equ 0 set "PACKAGE=%%~fF"
    )
)
if not defined PACKAGE (
    echo ERROR: could not locate the production NeoForge jar
    del /q "!LISTING!" >nul 2>&1
    exit /b 1
)
jar tf "!PACKAGE!" > "!LISTING!"
for %%P in (
    "data/skyforge/worldgen/structure_set/sf_imp_0043_desert_pyramids.json"
    "data/minecraft/tags/worldgen/biome/has_structure/desert_pyramid.json"
    "data/skyforge/worldgen/world_preset/development.json"
    "data/minecraft/tags/worldgen/world_preset/normal.json"
) do (
    findstr /x /c:%%P "!LISTING!" >nul
    if !errorlevel! equ 0 (
        echo ERROR: development resource leaked into production jar: %%~P in !PACKAGE!
        del /q "!LISTING!" >nul 2>&1
        exit /b 1
    )
)
del /q "!LISTING!" >nul 2>&1

echo.
echo === SF-IMP-0043 native structure-start verification completed successfully ===
echo Next automated gate: gradlew.bat check
echo Next manual gate: gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
echo Positive client proof: vanilla desert pyramid anchored to the elevated Massif near the origin
endlocal
