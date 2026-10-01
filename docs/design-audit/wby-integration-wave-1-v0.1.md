# WBY Integration Wave 1 — Long-Range Visibility and Performance v0.1

**Snapshot:** 2026-09-30  
**Status:** Ready for implementation qualification after Wave 0  
**Target:** Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21

## Purpose

Wave 1 proves the foundational Wild Blue Yonder visibility contract:

> distant terrain and distant physical aircraft must remain simultaneously visible, correct, and performant.

This is a gameplay requirement, not cosmetic polish.

## Base

Wave 1 inherits the exact accepted Wave 0 substrate.

## Candidate pins

### Renderer / long-range visibility

- Sodium **0.8.13** for NeoForge 1.21.1.
- Distant Horizons **3.3.2** for Minecraft 1.21.1 NeoForge/Fabric release line.
- Separate Sable Render Distance **1.8.7** for Minecraft 1.21.1 NeoForge.

SSRD 1.8+ requires Sodium 0.8. Distant Horizons 3.3.2 and later explicitly mark SSRD 1.8.6 and older incompatible after DH's reverse-Z depth change. Therefore Wave 1 must not regress to SSRD 1.8.6 or below.

Known SSRD 1.8.7 artifact evidence:

- filename: `SSRD-1.8.7-1.21.1.jar`
- Modrinth version ID: `iZflRKdV`
- observed SHA-1: `7da02fc5f783bedb55a29268e4c8ba7b0c3c9e6f`

Sodium 0.8.13 CurseForge file ID: `8756580`.

Distant Horizons project ID: CurseForge `508933`; Modrinth `uCdwusMi`.

## Performance stack admission order

Do **not** add all performance candidates at once.

### W1-A — visibility minimum

Wave 0 +:

- Sodium
- Distant Horizons
- SSRD

This profile must pass before any additional optimizer enters the specimen.

### W1-B — conservative baseline optimization

After W1-A passes, add one at a time:

1. Lithium
2. FerriteCore
3. ImmediatelyFast
4. Dynamic FPS

Each addition must preserve the W1-A result.

### W1-C — broad/experimental optimization

Do not promote these automatically:

- ModernFix
- Create: Catalyst
- StellarCreateOptimization
- Entity Culling
- behavior-changing ServerCore knobs

These remain later A/B performance gates because they have broader correctness or transformed-rendering risk.

## Fixed acceptance scene

Use one persisted Skyforge test world and one known working aircraft.

Required observation cases:

1. Aircraft inside vanilla render distance.
2. Aircraft crossing vanilla render-distance boundary into SSRD distance.
3. Aircraft moving laterally across Distant Horizons LOD terrain.
4. Aircraft approaching from long range.
5. Aircraft receding to configured SSRD maximum.
6. Two simultaneous distant craft.
7. Create train / moving contraption sanity case.
8. Rope/attached moving-element sanity case if present.
9. Save/reload with distant craft retained.
10. Multiplayer: remote craft viewed while another player is near it.

## Correctness gates

Reject the profile for any of:

- sublevels visible through terrain/LODs without an explicitly understood presentation limitation;
- craft disappearing at the vanilla render boundary;
- frozen distant craft;
- incorrect transforms;
- trains failing to render;
- ropes/attached elements failing after chunk transition;
- server/client disagreement about maximum SSRD range;
- unbounded force-loading behavior;
- crash during contraption-on-sublevel cases;
- Distant Horizons LOD corruption after save/reload.

## Performance measurements

Capture at minimum:

- median client frame time;
- 95th-percentile frame time;
- VRAM usage;
- client memory use;
- dedicated-server MSPT;
- loaded/forced chunk counts;
- number of simultaneously visible Sable sublevels;
- configured vanilla render distance;
- configured DH LOD distance;
- configured SSRD max render distance.

Benchmark the same camera path/profile before and after each performance mod is added.

## Configuration policy

- Shader/Iris testing is explicitly **out of scope** for Wave 1.
- Better Clouds is out of scope.
- Keep the image pipeline unshaded so DH/SSRD/Sable correctness can be isolated.
- SSRD force-loading must remain bounded.
- Server-controlled SSRD maximum should be tested, not left at an unexamined default.
- Distant Horizons is foundational; a minor visual incompatibility is a bug to solve, not an excuse to silently remove long-range visibility.

## Pass result

Wave 1 passes when W1-A has a correct long-range aircraft/terrain scene and the conservative W1-B optimizers can be layered without correctness regression.

## Follow-on

A passed Wave 1 unlocks:

- engineering content integration;
- ecology/civilization integration;
- later PT-04 atmosphere/shader testing against a known-good unshaded baseline.
