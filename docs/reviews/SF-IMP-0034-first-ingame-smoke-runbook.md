# SF-IMP-0034 First In-Game Smoke Runbook

**Status:** Accepted

## Purpose

SF-IMP-0034 is the first deliberately human-visible Minecraft validation of Skyforge. It does not claim final world-generation timing or finished terrain presentation. It proves that the accepted NeoForge lifecycle path can be exercised in a real client world and produce a finite, deterministic floating-landform specimen that survives normal chunk persistence.

## Development-only activation

The normal packaged mod remains inert unless a runtime binding is configured.

The ModDevGradle `client` run sets:

```text
-Dskyforge.dev.specimen=true
```

This opt-in installs exactly one deterministic Overworld specimen. No user-facing configuration contract is implied.

## Specimen

- Morphology: built-in `MASSIF`
- Root seed: `0x534b59464f524745`
- Horizontal center: `x=0, z=0`
- Suspension elevation: approximately `y=224`
- Inspection coordinate: `x=0, y=300, z=0`
- Realization mode: additive Skyforge-solid overlay
- Temporary engineering material palette: dirt / stone / deepslate semantic projection

The specimen is intentionally compact enough to fit inside the ordinary Overworld vertical range and large enough to cross multiple Minecraft chunks.

## Launch

From the repository root:

```bat
scripts\verify-sf-imp-0034-ingame-preflight.bat
gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
```

The development client uses:

```text
skyforge-neoforge-1211/run-sf-imp-0034
```

as its isolated game directory.

## Manual validation procedure

1. Launch the ModDevGradle client.
2. Confirm Skyforge appears in the loaded mod set / logs without a mod-loading failure.
3. Create a **new disposable Creative Overworld**. Do not reuse an older test world because the lifecycle proof acts only on newly generated chunks.
4. Once in-world, run:

   ```text
   /tp @s 0 300 0
   ```

5. Look downward and around the origin.
6. Confirm a substantial floating Massif is present around the documented inspection region.
7. Fly around its perimeter and underside. Look specifically for obvious 16-block chunk-seam cuts or missing strips.
8. Confirm ordinary Minecraft terrain below/around the specimen has not been globally erased where Skyforge contributes AIR.
9. Save and quit to title.
10. Re-enter the same world and confirm the already-generated Skyforge terrain remains present. Existing-chunk loads must not cause a second realization pass.
11. Capture screenshots when useful for morphology/visual review.

## Accepted observations — 2026-08-31

Manual in-client validation reported:

- the Massif is clearly present at the expected location;
- it appears to have slotted into the Minecraft world cleanly;
- no obvious destructive AIR behavior was observed in surrounding native terrain;
- the specimen persists across save/reload;
- the shape is recognizably preliminary rather than production-ready;
- the underside is oversized relative to the desired playable landform;
- overall playability/form quality requires later morphology tuning.

The morphology critique is retained as design evidence rather than treated as a failure of the integration milestone.

## Pass criteria

The manual smoke gate passes when:

- the real Minecraft client launches with Skyforge loaded;
- a new Overworld generates without crashing;
- the deterministic specimen is visible near the documented location;
- it spans chunks without an obvious ownership seam;
- Skyforge AIR has not erased unrelated native terrain;
- save/reload preserves the generated blocks;
- any remaining defects are attributable to the already-documented late lifecycle timing, temporary palette, or morphology tuning rather than loss of Skyforge geometry.

**Result: PASS.**

## Expected imperfections

Do **not** reject SF-IMP-0034 solely because of:

- crude dirt/stone/deepslate material choice;
- preliminary or poorly tuned morphology;
- missing vegetation on Skyforge surfaces;
- lighting anomalies;
- heightmap-dependent behavior;
- vanilla features failing to recognize the floating terrain;
- structure interaction;
- occasional collision/intersection with native terrain.

Those are precisely the reasons the accepted `ChunkEvent.Load(isNewChunk=true)` seam remains provisional.

## CurseForge note

This work item intentionally uses ModDevGradle `runClient` because it exercises source code directly and keeps the first visual proof isolated. A subsequent packaged-mod smoke gate should produce a self-contained Skyforge JAR and install it into the user's clean CurseForge NeoForge 1.21.1 profile. That packaging step should not be conflated with proving the in-game realization path itself.
