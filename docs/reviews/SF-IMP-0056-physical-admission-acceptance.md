# SF-IMP-0056 — Physical volume admission acceptance

**Status:** Accepted

## Scope

SF-IMP-0056 separates **generation-domain isolation** from **physical occupancy compatibility**.

BASE_WORLD continues to generate without observing Skyforge ownership. Before a planned Skyforge exact volume may destructively realize into completed native Minecraft state, the NeoForge backend now collects finite native-occupancy evidence and reaches one terminal physical decision:

```text
PLANNED -> ADMITTED
PLANNED -> REJECTED
```

A conflict rejects the whole planned volume. A clear volume is not admitted until every required chunk in its finite footprint has reported evidence. Deferred realization then services only already-loaded stable chunks and does not force unavailable chunks to generate.

## Accepted implementation

Exact accepted implementation head:

```text
b84870dd186ae2089623ec5ecdbf048037ef3814
```

PR: **#63 — `SF-IMP-0056: prevent physical occupancy collisions`**

Merged to `main` as:

```text
4fc06c294cd41a038d971b872ff18c89d27c6460
```

The final implementation-head change corrected only the development acceptance fixture's expected population-phase count. The production collision policy, realization gate, catch-up service, and population behavior were unchanged by that correction.

## Automated evidence

Exact-head public CI run **#363** completed successfully on `b84870dd186ae2089623ec5ecdbf048037ef3814`.

The green run included the repository build/test gates, backend-independence verification, NeoForge/FML and Mixin bootstrap, deterministic evidence generation, evidence entry-point verification, and compact review-bundle publication.

## Runtime fixture

The accepted interactive run used:

```text
:skyforge-neoforge-1211:runPhysicalAdmissionClient
```

in a new disposable Skyforge Development world on the exact accepted implementation head.

The fixture planned two deterministic tablelands over the same 5x5 / 25-chunk X/Z footprint:

- a **lower** candidate intersecting the vanilla Overworld bedrock-floor band, which must be rejected without mutating the native conflict;
- an **upper** clear high-air candidate, which must wait for complete footprint evidence, become admitted atomically, catch up only through already-loaded stable chunks, and then execute native taiga surface population.

## Accepted runtime evidence

The run emitted:

```text
SF-IMP-0056 PHYSICAL ADMISSION PASS: lower={volume=6000564149924409430/sf-imp-0056-physical/0/0/6000563822688433412, state=REJECTED, conflict=BlockPos{x=3, y=-64, z=-16}, block=Block{minecraft:bedrock}, blockEntity=false, preserved=true}, upper={volume=6000564149924409430/sf-imp-0056-physical/0/1/6000563920952128772, state=ADMITTED, observedChunks=25, requiredChunks=25, pendingCatchup=0, originSurfaceY=248, completedPopulationPhases=21, expectedPopulationPhases=21}.
```

This proves the acceptance fixture observed all of the following on the exact implementation head:

- lower volume reached terminal `REJECTED`;
- the first conflicting native state was vanilla bedrock at `(3, -64, -16)`;
- that native conflict remained unchanged after rejection;
- the upper volume reached terminal `ADMITTED`;
- admission required all **25 / 25** footprint chunks;
- deferred catch-up completed with **0** pending chunks;
- the upper origin contained materially realized Skyforge terrain;
- exactly **21 / 21** chunks containing exact-volume surface completed the admitted native population phase.

The distinction between the 25-chunk admission footprint and the 21 actually populatable surface chunks is intentional. Corner footprint chunks may be required for complete physical evidence while legitimately containing no exact-volume surface eligible for vegetation.

## Visual acceptance

Manual inspection reported that the physical result looked correct:

1. the upper floating island was present and coherent;
2. native vegetation was present on the admitted upper island;
3. the deliberately conflicting lower Skyforge candidate was absent;
4. native deep terrain around the rejected candidate appeared intact;
5. no crash, unstable ProtoChunk mutation, or obvious partial-volume realization was observed.

Visual plausibility was supporting evidence only. The terminal marker independently checked the admission states, native-conflict preservation, complete evidence counts, realized upper terrain, and completed population count.

## Separate follow-up: deferred native post-processing

The accepted run still emitted Minecraft warnings of the form:

```text
ChunkAccess: Trying to mark a block for PostProcessing ..., but this operation is not supported.
```

Those warnings occur because SF-IMP-0056 replays native population on stable loaded `LevelChunk`s after deferred terrain catch-up, while ordinary world-generation population normally records post-processing work through proto-chunk lifecycle semantics.

This concern is explicitly separate from physical admission and is tracked by **issue #64 / SF-IMP-0057** and PR #77. It did not invalidate the accepted physical-admission invariants.

## Separate follow-up: client-visible biome presentation

During manual inspection, the admitted upper island's visible surface coloration appeared to match the jungle biome below rather than the taiga identity used for its native population.

That observation does not change physical admission acceptance. It concerns the distinction between execution-scoped exact-volume biome identity and client-visible biome query/tint presentation, and is tracked separately by **issue #78 / SF-IMP-0058**.

## Accepted invariant

The accepted physical-composition invariant is:

> A planned Skyforge exact volume may not destructively realize while its physical admission is unresolved. Any detected native occupancy conflict rejects the whole volume without mutating the native conflict; a clear volume admits only after its complete finite footprint reports evidence, and deferred realization may service only already-available stable chunks without forcing future generation.

This invariant supplements, rather than replaces, the earlier generation-domain rule that ordinary BASE_WORLD generation does not observe Skyforge terrain.

## Merge record

SF-IMP-0056 acceptance was recorded on PR #63 before merge. PR #63 was then merged normally into `main` as `4fc06c294cd41a038d971b872ff18c89d27c6460`.
