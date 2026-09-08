# SF-IMP-0083 representative seed/scale carriers

**Status:** IN PROGRESS  
**Issue:** #284  
**Accepted predecessor:** SF-IMP-0082 / PR #273

SF-IMP-0083 completes the remaining twenty built-in AUTH-0083 seed/scale specimens without
requiring twenty equivalent Minecraft lifecycle runs.

## Evidence layering

Tier 0 remains exhaustive across all twenty remaining members:

- exact AUTH-0083 member ID;
- family, seed, scale, and built-in provider identity;
- provider-neutral deterministic compilation;
- tight integer support and occupied chunk footprint;
- pure integer-Y relocation into the development carrier;
- build-range feasibility.

Tier 2 Minecraft lifecycle evidence is representative by risk class. A representative failure widens
the affected class under the canonical validation policy.

## Single-member Minecraft carrier

The retired draft #285 stacked four members into one 1,616-block-high family world. The Tableland
characterization then exhausted runner memory while warming 1,886 chunks. That was a carrier-resource
failure, not morphology evidence.

The replacement carrier realizes exactly one member per world:

- development-only dimension: min Y 320, height 544, max Y exclusive 864;
- translated exact support minimum Y: 336;
- finite warmup: exact occupied chunk keys only;
- nonblocking explicit warmup: target-only radius-0 region tickets are installed once and completion
  is observed without serial synchronous getChunk forcing; accepted historical fixtures retain the
  harness default radius 3;
- native structures/surface/decoration are skipped only in this explicit morphology-only carrier;
- cave, ecology, and interior mutation remain intentionally absent;
- the objective surface gate requires stable land substrate plus exact top/underside occupancy and
  exterior air; grass count is retained as presentation evidence but is not required because this
  high-altitude carrier intentionally has no native Overworld surface to copy;
- save/reopen uses ownership-only runtime restoration and rechecks the same sampled top/underside digest.

The first recomposed Tableland diagnostic (run 34184041079) produced 3,192/3,192 stored top and
underside boundaries with zero height mismatches, then stopped on the inherited grass>0 predicate.
That predicate was narrowed because the high carrier deliberately has no native Overworld surface
to copy. A second run (34185431565) then exposed a later Java-heap failure in admitted deferred
exact-solid realization before the radius-3 explicit warmup completed. This proves the resource
hazard was reduced but not retired. SF-IMP-0083 therefore narrows only its development ticket radius
to zero so the proof holds the exact target chunks without the unnecessary entity-ticking halo;
the geometry/persistence gates remain unchanged. Run 34186680014 subsequently passed Tableland
preparation under this radius-0 contract. Its viewer timed out before integrated-server startup
because the shared development resources contained a Create Addition compatibility recipe while
Create Addition was absent from the stripped morphology client. That development-only recipe is now
guarded with `neoforge:mod_loaded(createaddition)`, preserving the retained-stack path while keeping
unrelated optional-mod data out of the morphology viewer.

Widened run 34188436241 then exposed two distinct scaling/launch concerns rather than morphology
rejections. Basin MEDIUM / seed-zero and Spine LARGE / seed-skyforge exhausted heap while deferred
per-block lighting work accumulated faster than the normally idle-triggered light executor could
start. SF-IMP-0056 lighting and block-broadcast semantics remain intact. A follow-up Basin run
(34189778665) proved that an end-of-chunk kick alone was insufficient and located the remaining OOM
inside Minecraft's own `LevelChunk.setBlockState -> ThreadedLevelLightEngine.checkBlock` path.
Because stable `LevelChunk#setBlockState` already submits the native lighting notification, the
deferred lifecycle no longer duplicates `checkBlock`; it keeps `blockChanged` and kicks
`tryScheduleUpdate()` every 256 changed blocks plus at scope close so very large exact-solid chunks
cannot starve the asynchronous light executor.

The corrected branch head `b3195c1e554b39f3595bec22122831525aae5238` then passed the three
previously failing/heaviest affected-class members end to end: Basin MEDIUM / seed-zero
(34191320102), Massif MEDIUM / seed-skyforge (34191688813), and Massif LARGE / seed-skyforge
(34191720235). The LARGE Massif carrier completed 2,124 exact chunks and 8,088 sampled top/underside
claims with zero height mismatches and identical persisted digest, without heap exhaustion. Separately, every MEDIUM specimen
that completed preparation reached the same pre-integrated-server QuickPlay timeout in the custom
544-high development dimension. Machine acceptance now acknowledges only Minecraft's expected
experimental-world `BackupConfirmScreen` through the exact "I Know What I'm Doing!" button, records
that acknowledgement as evidence, and otherwise leaves the persisted ownership-only viewer unchanged.

## Representative set

The initial seven expensive representatives are selected from the exhaustive support/profile evidence:

| Family | Variant | Risk represented |
| --- | --- | --- |
| Massif | medium-seed-skyforge | #267 traversal context; ordinary canonical seed |
| Tableland | medium-seed-skyforge | prior OOM carrier pathology; #283 Massif/Tableland comparison |
| Spine | medium-seed-min | seed-min boundary and family coverage |
| Basin | medium-seed-zero | seed-zero boundary and family coverage |
| Lobed | medium-seed-skyforge | family coverage / ordinary case |
| Massif | large-seed-skyforge | largest vertical-span stress class |
| Spine | large-seed-skyforge | second LARGE vertical/work stress class |

This covers every built-in family at MEDIUM, all three canonical seed cases, two LARGE stress cases,
and both active morphology-quality questions. It is an engineering sampling policy, not an aesthetic
pass/fail threshold.

## Gradle review entry points

The member variant is selected with `-PskyforgeProductionMorphologyVariant=<variant>`, where variant is:

- `medium-seed-min`
- `medium-seed-zero`
- `medium-seed-skyforge`
- `large-seed-skyforge`

Examples:

```text
./gradlew :skyforge-neoforge-1211:productionMorphologySeedScaleTablelandPrepareVerify \
  -PskyforgeProductionMorphologyVariant=medium-seed-skyforge --no-configuration-cache

./gradlew :skyforge-neoforge-1211:productionMorphologySeedScaleTablelandViewerVerify \
  -PskyforgeProductionMorphologyVariant=medium-seed-skyforge --no-configuration-cache

./gradlew :skyforge-neoforge-1211:launchProductionMorphologySeedScaleTableland \
  -PskyforgeProductionMorphologyVariant=medium-seed-skyforge --no-configuration-cache
```

Human #214 review remains required for family identity, macro/meso hierarchy, underside quality,
repetition, approach/orbit readability, and traversal feel. #267 and #283 remain judgment questions;
machine diagnostics do not decide them.


## Machine acceptance and human review gate

Final synchronized lifecycle run **34215626713** passed all seven approved representatives through
both preparation and persisted actual-client reopen:

- Massif MEDIUM / seed-skyforge;
- Tableland MEDIUM / seed-skyforge;
- Spine MEDIUM / seed-min;
- Basin MEDIUM / seed-zero;
- Lobed MEDIUM / seed-skyforge;
- Massif LARGE / seed-skyforge;
- Spine LARGE / seed-skyforge.

The subsequent main movement only added the accepted Sable Portable Engine cutoff ModDev run and
bootstrap hook. Those additions do not touch the morphology carrier/generator/mutation/viewer
dependency surface, so the expensive seven-world evidence is portable under the canonical validation
policy. A final cheap exact-head CI pass is required after recomposition; the next substantive gate is
human #214/#267/#283 review.

For review, rebuild and launch the exact representative with:

```text
./gradlew :skyforge-neoforge-1211:launchProductionMorphologySeedScaleMassif -PskyforgeProductionMorphologyVariant=medium-seed-skyforge --no-configuration-cache
./gradlew :skyforge-neoforge-1211:launchProductionMorphologySeedScaleTableland -PskyforgeProductionMorphologyVariant=medium-seed-skyforge --no-configuration-cache
./gradlew :skyforge-neoforge-1211:launchProductionMorphologySeedScaleSpine -PskyforgeProductionMorphologyVariant=medium-seed-min --no-configuration-cache
./gradlew :skyforge-neoforge-1211:launchProductionMorphologySeedScaleBasin -PskyforgeProductionMorphologyVariant=medium-seed-zero --no-configuration-cache
./gradlew :skyforge-neoforge-1211:launchProductionMorphologySeedScaleLobed -PskyforgeProductionMorphologyVariant=medium-seed-skyforge --no-configuration-cache
./gradlew :skyforge-neoforge-1211:launchProductionMorphologySeedScaleMassif -PskyforgeProductionMorphologyVariant=large-seed-skyforge --no-configuration-cache
./gradlew :skyforge-neoforge-1211:launchProductionMorphologySeedScaleSpine -PskyforgeProductionMorphologyVariant=large-seed-skyforge --no-configuration-cache
```

Review #214 for distant silhouette, top-down/approach readability, section/rim character,
underside/below-flight quality, repetition, and family identity. Review #267 specifically across the
two Massif specimens for locally excessive rises/falls and traversal lumpiness. Review #283 by
comparing Massif MEDIUM against Tableland MEDIUM for sufficiently distinct family identity.
