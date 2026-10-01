# SF-IMP-0082 — remaining built-in production morphology atlas

## Scope

SF-IMP-0082 extends the accepted SF-IMP-0081 exact Minecraft carrier across the remaining AUTH-0083
SMALL / seed-skyforge built-in members:

- `builtin-tableland-small-seed-skyforge`
- `builtin-spine-small-seed-skyforge`
- `builtin-basin-small-seed-skyforge`
- `builtin-lobed-small-seed-skyforge`

The Minecraft adapter does **not** retune those shapes. Every carrier preserves the canonical
Skyforge geometry seed, SMALL physical scale, built-in provider identity, full bounded detail, full
provider secondary morphology, and provider-neutral morphology-spec compilation. Minecraft changes
only suspension Y by one integer translation and proves that the discrete support is otherwise
identical.

## Exact acceptance footprint

SF-IMP-0081 fit the historical ±12-chunk square harness. The remaining built-in providers are
analytically certified to wider horizontal support, especially Spine/Lobed, so SF-IMP-0082 does not
inflate the global radius.

Instead, the development-only acceptance harness accepts an explicit finite chunk-key footprint from
the selected carrier. The footprint is the chunk rectangle covering the carrier's tight exact integer
voxel bounds. Preparation requires:

```text
footprintChunks == physicalAdmission.requiredChunks
physicalAdmission.observedChunks == requiredChunks
pendingCatchup == 0
```

This changes only test orchestration. Ordinary Skyforge still never forces unavailable chunks.

## Objective carrier lifecycle

For every member:

```text
native/base world
  -> native surface snapshot
  -> exact AUTH-0083 compiled occupancy
  -> tight integer support bounds
  -> whole-volume physical admission
  -> deferred stable-chunk catch-up
  -> save
  -> ownership-only actual-client reopen
```

Caves, ecology population, interior population, and biome-presentation mutation remain deliberately
absent so issue #214 can judge unobscured morphology. SF-IMP-0080 separately proves ecology.

Machine gates require:

- exact member/family identity;
- source descriptor parity with AUTH-0083 SMALL / seed-skyforge;
- pure integer-Y translation;
- Minecraft build-range fit;
- nonempty exact support;
- explicit finite warmup footprint matching admission requirements;
- ADMITTED state with complete evidence;
- zero pending catch-up;
- sampled exact top heights equal runtime height claims;
- stored top and underside boundary blocks;
- air immediately above and below those boundaries;
- nonzero native land/grass representation;
- deterministic sampled geometry digest;
- identical digest after mutation-inert actual-client reopen.

No aesthetic threshold is encoded.

### Tableland persistence edge case

The first four-family persistence run exposed one exact one-voxel Tableland fringe column at
`(-163, 160, -19)`. The canonical morphology and height claim remained present, but the stored block
was AIR after reopen. Preparation had proven the block existed before save.

ADR-0041 requires native surface adaptation to preserve Skyforge occupancy and explicitly deferred
falling-block policy. SF-IMP-0082 therefore adds the narrow representation rule required by that
invariant:

- non-falling native surface materials adapt exactly as before;
- a gravity-affected native material (for example sand/gravel) still adapts when the exact Skyforge
  materialization has a solid immediately below the exposed top;
- when the exposed Skyforge top has AIR immediately below it, a falling native material is not
  copied and the stable original Skyforge surface representation is retained.

This does not retune Tableland morphology, add filler depth, or define a general beach/shore policy.
It only prevents concrete backend representation from deleting authoritative Skyforge occupancy.
The focused adapter regression and full four-family prepare/reopen matrix are the acceptance gates.

## Automated commands

Windows PowerShell:

```powershell
.\gradlew.bat :skyforge-neoforge-1211:productionMorphologyAtlasTablelandPrepareVerify --no-configuration-cache
.\gradlew.bat :skyforge-neoforge-1211:productionMorphologyAtlasTablelandViewerVerify --no-configuration-cache

.\gradlew.bat :skyforge-neoforge-1211:productionMorphologyAtlasSpinePrepareVerify --no-configuration-cache
.\gradlew.bat :skyforge-neoforge-1211:productionMorphologyAtlasSpineViewerVerify --no-configuration-cache

.\gradlew.bat :skyforge-neoforge-1211:productionMorphologyAtlasBasinPrepareVerify --no-configuration-cache
.\gradlew.bat :skyforge-neoforge-1211:productionMorphologyAtlasBasinViewerVerify --no-configuration-cache

.\gradlew.bat :skyforge-neoforge-1211:productionMorphologyAtlasLobedPrepareVerify --no-configuration-cache
.\gradlew.bat :skyforge-neoforge-1211:productionMorphologyAtlasLobedViewerVerify --no-configuration-cache
```

Aggregate machine targets also exist:

```powershell
.\gradlew.bat :skyforge-neoforge-1211:productionMorphologyAtlasPrepareVerify --no-configuration-cache
.\gradlew.bat :skyforge-neoforge-1211:productionMorphologyAtlasViewerVerify --no-configuration-cache
```

## Human #214 review

After the corresponding prepare/viewer gates pass, launch one family at a time:

```powershell
.\gradlew.bat :skyforge-neoforge-1211:launchProductionMorphologyAtlasTableland --no-configuration-cache
.\gradlew.bat :skyforge-neoforge-1211:launchProductionMorphologyAtlasSpine --no-configuration-cache
.\gradlew.bat :skyforge-neoforge-1211:launchProductionMorphologyAtlasBasin --no-configuration-cache
.\gradlew.bat :skyforge-neoforge-1211:launchProductionMorphologyAtlasLobed --no-configuration-cache
```

Inside each world:

```text
/skyforge_morphology_atlas above
/skyforge_morphology_atlas approach
/skyforge_morphology_atlas below
/skyforge_morphology_atlas orbit
```

For every family judge:

1. long-range family identity;
2. readable macro/meso hierarchy rather than undifferentiated noise;
3. rim/coast transition quality;
4. underside coherence and relation to the upper form;
5. detached spikes, pinches, implausibly thin shelves, or repetitive signatures;
6. likely traversal character on the upper surface;
7. quality while actually flying around and beneath the island.

The Massif review already found one non-blocking traversal concern: frequent rises/falls made that
specific specimen feel potentially lumpy. That remains issue #267. Do not normalize every family
toward flat terrain merely to remove that observation.

## Acceptance boundary

SF-IMP-0082 may merge when all four carriers pass objective preparation/reopen gates and human review
confirms the four family morphologies are viable enough to continue the production atlas. Record
family-specific concerns separately rather than hiding them.

Full issue #214 remains open after this tranche for multiple seeds/scales, hybrids/providers,
AUTH-0084 regional contexts, and later material/ecology/hydrology contexts.
