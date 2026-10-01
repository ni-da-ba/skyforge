@echo off
setlocal EnableExtensions EnableDelayedExpansion

echo === SF-IMP-0041 supplemental multi-block vegetation verification ===

echo [1/5] Java runtime
java -version
if errorlevel 1 exit /b %errorlevel%

echo.
echo [2/5] Backend-neutral module independence
call gradlew.bat verifyBackendIndependence
if errorlevel 1 exit /b %errorlevel%

echo.
echo [3/5] NeoForge tree-proof development resource compile proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:compileJava :skyforge-neoforge-1211:compileTestJava :skyforge-neoforge-1211:processResources :skyforge-neoforge-1211:processDevelopmentResources
if errorlevel 1 exit /b %errorlevel%

set "TREE=skyforge-neoforge-1211\build\resources\development\data\skyforge\worldgen\placed_feature\additional_surface_trees_plains.json"
set "MODIFIER=skyforge-neoforge-1211\build\resources\development\data\skyforge\neoforge\biome_modifier\add_additional_surface_trees_plains.json"
set "LANG=skyforge-neoforge-1211\build\resources\development\assets\skyforge\lang\en_us.json"

for %%P in ("!TREE!" "!MODIFIER!" "!LANG!") do (
    if not exist %%P (
        echo ERROR: required SF-IMP-0041 development resource missing: %%~P
        exit /b 1
    )
)

for %%P in (
    "skyforge-neoforge-1211\build\resources\development\data\skyforge\worldgen\configured_feature\dry_open_marker.json"
    "skyforge-neoforge-1211\build\resources\development\data\skyforge\worldgen\configured_feature\submerged_water_marker.json"
    "skyforge-neoforge-1211\build\resources\development\data\skyforge\worldgen\configured_feature\open_water_marker.json"
    "skyforge-neoforge-1211\build\resources\development\data\skyforge\worldgen\placed_feature\dry_open_marker.json"
    "skyforge-neoforge-1211\build\resources\development\data\skyforge\worldgen\placed_feature\submerged_water_marker.json"
    "skyforge-neoforge-1211\build\resources\development\data\skyforge\worldgen\placed_feature\open_water_marker.json"
    "skyforge-neoforge-1211\build\resources\development\data\skyforge\neoforge\biome_modifier\add_dry_open_marker.json"
    "skyforge-neoforge-1211\build\resources\development\data\skyforge\neoforge\biome_modifier\add_submerged_water_marker.json"
    "skyforge-neoforge-1211\build\resources\development\data\skyforge\neoforge\biome_modifier\add_open_water_marker.json"
) do (
    if exist %%P (
        echo ERROR: obsolete colored-marker development fixture remains: %%~P
        exit /b 1
    )
)

findstr /c:"SF-IMP-0041" "!LANG!" >nul
if errorlevel 1 (
    echo ERROR: development world label does not identify SF-IMP-0041
    exit /b 1
)
findstr /c:"minecraft:trees_plains" "!TREE!" >nul
if errorlevel 1 (
    echo ERROR: supplemental tree proof does not use minecraft:trees_plains
    exit /b 1
)
findstr /c:"skyforge:suitable_surfaces" "!TREE!" >nul
if errorlevel 1 (
    echo ERROR: supplemental tree proof does not use the accepted suitability modifier
    exit /b 1
)
findstr /c:"dry_open" "!TREE!" >nul
if errorlevel 1 (
    echo ERROR: supplemental tree proof does not request dry_open suitability
    exit /b 1
)
findstr /c:"minecraft:would_survive" "!TREE!" >nul
if errorlevel 1 (
    echo ERROR: supplemental tree proof does not preserve the Minecraft survival predicate
    exit /b 1
)
findstr /c:"minecraft:oak_sapling" "!TREE!" >nul
if errorlevel 1 (
    echo ERROR: supplemental tree proof survival predicate is not oak-sapling based
    exit /b 1
)

echo.
echo [4/5] Accepted multi-surface and worldgen integration regressions
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:test --tests "io.github.nidaba.skyforge.neoforge1211.MinecraftAdditionalSurfaceIndexTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211FeatureStageTest" --tests "io.github.nidaba.skyforge.neoforge1211.MinecraftNativeSurfaceTopAdapterTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211SurfaceStageTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNoiseBasedChunkGeneratorTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211DevRuntimeTest"
if errorlevel 1 exit /b %errorlevel%

echo.
echo [5/5] Production jar excludes SF-IMP-0041 development tree data
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:jar
if errorlevel 1 exit /b %errorlevel%
set "LISTING=%TEMP%\skyforge-sf-imp-0041-production-listing.txt"
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
    "data/skyforge/worldgen/placed_feature/additional_surface_trees_plains.json"
    "data/skyforge/neoforge/biome_modifier/add_additional_surface_trees_plains.json"
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
echo === SF-IMP-0041 supplemental tree verification completed successfully ===
echo Next automated gate: gradlew.bat check
echo Manual proof already observed: real Minecraft trees generated on compatible lower dry ground
echo Final manual persistence gate: reopen the accepted SF-IMP-0041 world once
endlocal
