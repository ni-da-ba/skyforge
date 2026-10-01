# SF-IMP-0033 NeoForge Lifecycle Acceptance

- **Date:** 2026-08-31
- **Status:** Accepted
- **Accepted runtime/code head:** `060348c17b87064a5f54207884d3d4e597446ed6`
- **Target:** Minecraft 1.21.1 / NeoForge 21.1.249

## Accepted scope

SF-IMP-0033 establishes the first production-shaped NeoForge lifecycle seam for Skyforge.

The accepted path is:

```text
FML-loaded skyforge mod
    -> NeoForge ChunkEvent.Load
    -> require isNewChunk()
    -> backend-local level selector
    -> accepted Skyforge chunk adapter
    -> accepted materialization
    -> strict live BlockState resolution
    -> additive solid overlay into event ChunkAccess
```

The production NeoForge module now contains real mod metadata, a real `@Mod("skyforge")` entrypoint, and a real event subscriber.

## Concrete invariants proved

The focused proof demonstrates:

1. the production `skyforge` mod is discovered by the FML-aware NeoForge test runtime;
2. the event subscriber receives `ChunkEvent.Load` through `NeoForge.EVENT_BUS`;
3. existing chunk loads (`isNewChunk() == false`) are ignored;
4. newly generated chunk loads reach the installed runtime binding;
5. backend-side level selection can reject a chunk without mutation;
6. the callback operates only on the event's own `ChunkAccess` and does not request neighboring chunks;
7. Skyforge solid samples are resolved through Minecraft's live block registry and written to real chunk storage;
8. Skyforge AIR is additive absence and therefore preserves existing backend-native blocks;
9. the exact SF-IMP-0032 writer remains available for exact-ownership/equivalence proofs;
10. Minecraft/NeoForge concepts remain confined to the concrete adapter module;
11. repository-wide validation remains green.

## Integration finding: additive composition

The first real lifecycle integration exposed a concrete distinction that the isolated SF-IMP-0032 storage proof did not need.

For ordinary Minecraft composition:

```text
Skyforge solid -> overwrite the target position with Skyforge's resolved state
Skyforge AIR   -> do not write; preserve Minecraft's existing state
```

This prevents a floating-island overlay from erasing native terrain where Skyforge contributes no solid. It is a Minecraft backend composition rule, not a change to Skyforge's backend-neutral density semantics.

## Validation record

Authoritative local validation:

```text
scripts\verify-sf-imp-0033-neoforge-lifecycle.bat
    PASS

gradlew.bat check
    PASS
```

The focused verifier covers:

- Java/toolchain readiness;
- backend-neutral independence;
- production NeoForge metadata and compile linkage;
- FML mod load;
- actual NeoForge event-bus delivery;
- existing/new chunk discrimination;
- backend level scoping;
- additive overlay semantics;
- live registry and real chunk storage behavior.

No new visual evidence gate is required for SF-IMP-0033 because it changes neither accepted morphology nor density/semantic geometry.

## Important limitation

`ChunkEvent.Load(isNewChunk=true)` is an accepted **first lifecycle proof**, not the final world-generation insertion point.

NeoForge posts the event while a generated chunk is being promoted/loaded. Earlier vanilla generation work may already have occurred. Therefore this acceptance does not claim that:

- vanilla features or vegetation see Skyforge surfaces;
- structures evaluate or fit Skyforge terrain;
- heightmaps are correct for all earlier/downstream consumers;
- lighting behaves exactly as if Skyforge terrain had existed during earlier generation phases.

The next integration work must identify and prove an earlier worldgen seam if those native systems need Skyforge terrain before chunk-load promotion.

## Architectural consequence

The concrete Minecraft path has advanced from direct storage calls to real event-driven integration:

```text
Skyforge world catalog
    -> chunk-local realization
    -> terrain semantics
    -> Minecraft registry keys
    -> live BlockStates
    -> NeoForge-delivered real chunk storage
```

The next question is now worldgen timing and native-system participation, not whether Skyforge can enter the NeoForge lifecycle at all.
