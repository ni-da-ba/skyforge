@echo off
setlocal EnableExtensions EnableDelayedExpansion

echo === SF-IMP-0038 supplemental multi-surface feature verification ===

echo [1/5] Java runtime
java -version
if errorlevel 1 exit /b %errorlevel%

echo.
echo [2/5] Backend-neutral module independence
call gradlew.bat verifyBackendIndependence
if errorlevel 1 exit /b %errorlevel%

echo.
echo [3/5] NeoForge placement modifier and development-resource compile proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:compileJava :skyforge-neoforge-1211:compileTestJava :skyforge-neoforge-1211:processResources :skyforge-neoforge-1211:processDevelopmentResources
if errorlevel 1 exit /b %errorlevel%

if not exist "skyforge-neoforge-1211\build\resources\development\data\skyforge\worldgen\placed_feature\additional_surface_grass.json" (
    echo ERROR: development additional-surface placed feature was not produced
    exit /b 1
)
if not exist "skyforge-neoforge-1211\build\resources\development\data\skyforge\neoforge\biome_modifier\add_additional_surface_grass.json" (
    echo ERROR: development biome modifier was not produced
    exit /b 1
)
if not exist "skyforge-neoforge-1211\build\resources\development\assets\skyforge\lang\en_us.json" (
    echo ERROR: SF-IMP-0038 development language resource was not produced
    exit /b 1
)
findstr /c:"SF-IMP-0038" "skyforge-neoforge-1211\build\resources\development\assets\skyforge\lang\en_us.json" >nul
if errorlevel 1 (
    echo ERROR: development world label does not identify SF-IMP-0038
    exit /b 1
)

echo.
echo [4/5] Additional-surface index, scoped feature stage, and accepted integration regression proof
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:test --tests "io.github.nidaba.skyforge.neoforge1211.MinecraftAdditionalSurfaceIndexTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211FeatureStageTest" --tests "io.github.nidaba.skyforge.neoforge1211.MinecraftNativeSurfaceTopAdapterTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNeoForge1211SurfaceStageTest" --tests "io.github.nidaba.skyforge.neoforge1211.SkyforgeNoiseBasedChunkGeneratorTest"
if errorlevel 1 exit /b %errorlevel%

echo.
echo [5/5] Production jar excludes development multi-surface feature data
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:jar
if errorlevel 1 exit /b %errorlevel%
set "LISTING=%TEMP%\skyforge-sf-imp-0038-production-listing.txt"
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
findstr /x /c:"data/skyforge/worldgen/placed_feature/additional_surface_grass.json" "!LISTING!" >nul
if !errorlevel! equ 0 (
    echo ERROR: development additional-surface placed feature leaked into production jar: !PACKAGE!
    del /q "!LISTING!" >nul 2>&1
    exit /b 1
)
findstr /x /c:"data/skyforge/neoforge/biome_modifier/add_additional_surface_grass.json" "!LISTING!" >nul
if !errorlevel! equ 0 (
    echo ERROR: development multi-surface biome modifier leaked into production jar: !PACKAGE!
    del /q "!LISTING!" >nul 2>&1
    exit /b 1
)
findstr /x /c:"data/skyforge/worldgen/world_preset/development.json" "!LISTING!" >nul
if !errorlevel! equ 0 (
    echo ERROR: development world preset leaked into production jar: !PACKAGE!
    del /q "!LISTING!" >nul 2>&1
    exit /b 1
)
del /q "!LISTING!" >nul 2>&1

echo.
echo === SF-IMP-0038 supplemental multi-surface feature verification completed successfully ===
echo Next automated gate: gradlew.bat check
echo Next manual gate: gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
endlocal
