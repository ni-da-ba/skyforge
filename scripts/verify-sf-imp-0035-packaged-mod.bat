@echo off
setlocal EnableExtensions EnableDelayedExpansion

echo === SF-IMP-0035 packaged NeoForge mod verification ===

echo [1/4] Backend-neutral module independence
call gradlew.bat verifyBackendIndependence
if errorlevel 1 exit /b %errorlevel%

echo.
echo [2/4] NeoForge tests and final Jar-in-Jar mod archive build
call gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:test :skyforge-neoforge-1211:jar
if errorlevel 1 exit /b %errorlevel%

echo.
echo [3/4] Locate distributable Jar-in-Jar artifact by archive contents
set "PACKAGE="
set "CANDIDATE_LISTING=%TEMP%\skyforge-sf-imp-0035-candidate-listing.txt"
for %%F in (skyforge-neoforge-1211\build\libs\*.jar) do (
    jar tf "%%~fF" > "!CANDIDATE_LISTING!" 2>nul
    if !errorlevel! equ 0 (
        findstr /x /c:"META-INF/neoforge.mods.toml" "!CANDIDATE_LISTING!" >nul
        if !errorlevel! equ 0 (
            findstr /x /c:"META-INF/jarjar/metadata.json" "!CANDIDATE_LISTING!" >nul
            if !errorlevel! equ 0 set "PACKAGE=%%~fF"
        )
    )
)
del /q "!CANDIDATE_LISTING!" >nul 2>&1
if not defined PACKAGE (
    echo ERROR: no distributable Jar-in-Jar artifact with NeoForge and JIJ metadata found under skyforge-neoforge-1211\build\libs
    echo Available jars:
    dir /b skyforge-neoforge-1211\build\libs\*.jar 2>nul
    exit /b 1
)
echo Package: !PACKAGE!

echo.
echo [4/4] Inspect package metadata and embedded Skyforge libraries
set "LISTING=%TEMP%\skyforge-sf-imp-0035-jar-listing.txt"
jar tf "!PACKAGE!" > "!LISTING!"
if errorlevel 1 exit /b %errorlevel%

findstr /x /c:"META-INF/neoforge.mods.toml" "!LISTING!" >nul
if errorlevel 1 (
    echo ERROR: packaged mod is missing META-INF/neoforge.mods.toml
    exit /b 1
)

findstr /x /c:"META-INF/jarjar/metadata.json" "!LISTING!" >nul
if errorlevel 1 (
    echo ERROR: packaged mod is missing META-INF/jarjar/metadata.json
    exit /b 1
)

for %%M in (skyforge-kernel skyforge-model skyforge-recipes skyforge-world) do (
    findstr /i /c:"%%M" "!LISTING!" >nul
    if errorlevel 1 (
        echo ERROR: packaged mod does not contain embedded %%M library
        exit /b 1
    )
)

del /q "!LISTING!" >nul 2>&1

echo.
echo === SF-IMP-0035 packaged mod verification completed successfully ===
echo Copy this artifact into the clean CurseForge NeoForge 1.21.1 profile:
echo !PACKAGE!
endlocal
