@echo off
setlocal EnableExtensions
cd /d "%~dp0\.."

set "A4MC_COMMIT=62a52a584e9c65246e50226b29a1f0449e43995e"
set "A4MC_DIR=%CD%\build\sf-imp-1140-a4mc-source"
set "A4MC_PATCH=%CD%\third_party\patches\aerodynamics4mc\0001-public-terrain-provider-api.patch"

echo [SF-IMP-1140] Preparing exact patched Aerodynamics4MC review runtime...

where git >nul 2>nul
if errorlevel 1 (
  echo FAIL: git is required on PATH.
  exit /b 1
)

if exist "%A4MC_DIR%" rmdir /s /q "%A4MC_DIR%"
git clone --quiet https://github.com/MozillaFiredoge/Aerodynamics4MC-Core.git "%A4MC_DIR%"
if errorlevel 1 exit /b 1

git -C "%A4MC_DIR%" checkout --quiet "%A4MC_COMMIT%"
if errorlevel 1 exit /b 1

git -C "%A4MC_DIR%" apply --check "%A4MC_PATCH%"
if errorlevel 1 (
  echo FAIL: the retained A4MC terrain-provider patch no longer applies cleanly.
  exit /b 1
)
git -C "%A4MC_DIR%" apply "%A4MC_PATCH%"
if errorlevel 1 exit /b 1

pushd "%A4MC_DIR%"
call gradlew.bat buildAndCollect --no-daemon
set "A4MC_BUILD_RESULT=%ERRORLEVEL%"
popd
if not "%A4MC_BUILD_RESULT%"=="0" exit /b %A4MC_BUILD_RESULT%

set "A4MC_PATCHED_CORE="
for /f "delims=" %%F in ('dir /b /s "%A4MC_DIR%\build\libs\aerodynamics4mc-0.2.1-neoforge+1.21.1-SNAPSHOT.jar" 2^>nul') do set "A4MC_PATCHED_CORE=%%F"
if not defined A4MC_PATCHED_CORE (
  echo FAIL: exact patched NeoForge 1.21.1 A4MC core jar was not produced.
  exit /b 1
)

echo [SF-IMP-1140] Patched A4MC core:
echo   %A4MC_PATCHED_CORE%
echo [SF-IMP-1140] Preparing accepted DR-50 P2_DRESSED_REGION_A world...

call gradlew.bat :skyforge-neoforge-1211:runDr50IntegratedRegionAcceptanceA --no-configuration-cache
if errorlevel 1 exit /b 1

echo [SF-IMP-1140] Launching interactive atmosphere review.
echo [SF-IMP-1140] Close Minecraft normally when the review is complete.

call gradlew.bat :skyforge-neoforge-1211:runWaveC3AtmosphereHumanReviewClient "-PskyforgeA4mcProbeCoreOverrideJar=%A4MC_PATCHED_CORE%" -PskyforgeA4mcTerrainProvider=true --no-configuration-cache
exit /b %ERRORLEVEL%
