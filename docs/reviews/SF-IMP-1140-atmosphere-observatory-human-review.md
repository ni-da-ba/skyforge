# SF-IMP-1140 — Atmosphere Observatory Human Review Runbook

**Status:** Ready for project-owner review. Automated authority, coherence, cost, and terrain-correlation gates pass; human gameplay/legibility review remains.

## Purpose

This is the final human-review gate for issue #1140.

The automated work has already proven:

- Aerodynamics4MC remains the sole server-authoritative atmosphere owner.
- The accepted DR-50 `P2_DRESSED_REGION_A` world can be reopened and sampled through `SkyforgeAtmosphereView`.
- The #1142 terrain-provider seam can feed realized Skyforge island terrain into A4MC without arbitrary chunk loading or a duplicate weather field.
- A fixed-coordinate control/treatment run changes A4MC's trusted L1 field when the realized island terrain input is enabled.
- Reliable Gliders and Fowl Play hawks consume the same accepted A4MC lift authority through the existing C7/C6 adapters.

This review is **not** permission to tune A4MC physics, hawk thresholds, glider smoothing, climatology, or production weather.

## Accepted machine evidence

Reference seam run: GitHub Actions **A4MC Terrain Provider Seam**, run `36246549265`, exact head `897b3a77ad730dee19db10c6b2d772c6cc3626bf`.

The treatment specimen used:

- DR-50 specimen: `P2_DRESSED_REGION_A`
- Minecraft level seed: `493030`
- authored world root seed: `6001989086914692933`
- canonical island: province 8 / cluster 81 / island 1471
- fixed atmosphere center: X=-32, Z=32
- surface reference: Y=285
- realized semantic terrain sample: Y=274
- ordinary base-world control at the same column: Y=63

Treatment spatial field:

- updraft: **-0.505 .. +0.423 m/s**, mean +0.0018
- horizontal effective wind: **0.491 .. 4.409 m/s**, mean 1.915
- turbulence: **0.355 .. 1.960**, mean 0.540
- shear: **0.000963 .. 0.127446 / block**, mean 0.00698
- adjacent 64-block updraft |Δ|: mean ~0.210 m/s, max ~0.705
- glider-improved snapshot cells: **50/100**
- hawk-enter snapshot cells: **0/100**

Thirty-second opportunity scan:

- samples: **5,022**
- maximum observed updraft: **+0.674 m/s**
- maximum position: approximately **X=160, Y=365, Z=-32**
- hawk 1.5 m/s enter threshold: **0 samples / 0 cells**

The strongest measured near-surface hazard sample is localized rather than global:

- turbulence peak: approximately **1.960** near X=-32, Y=301, Z=-32
- shear peak: approximately **0.127 / block** near X=32, Y=301, Z=32

The four fixed temporal traces remain smooth at the one-second sampling cadence; maximum frame-to-frame updraft changes are approximately **0.133–0.184 m/s**.

## Plot review

To regenerate a fresh artifact without changing code:

1. Open GitHub Actions.
2. Choose **A4MC Terrain Provider Seam**.
3. Run the workflow manually on `main`.
4. Download the `a4mc-terrain-seam-build-<sha>` artifact.
5. Open:

```text
skyforge-neoforge-1211/build/acceptance/a4mc-terrain-seam/treatment/observatory/index.html
```

Review at minimum:

- `field-atlas.svg`
- `hazard-atlas.svg`
- `vertical-profiles.svg`
- `temporal-updraft.svg`
- `opportunity-atlas.svg`
- `opportunity-max-over-time.svg`

### Plot questions

Record **PASS**, **TUNE-LATER**, or **FAIL** for each:

1. Does the field read as spatially coherent rather than random cell noise?
2. Do adjacent wind vectors and updraft regions change gradually enough to suggest navigable features?
3. Do the temporal traces look like evolving air rather than violent one-second flicker?
4. Do altitude profiles avoid obvious discontinuous layer jumps?
5. Is the near-surface turbulence/shear hotspot localized and understandable rather than dominating the whole volume?
6. Does the opportunity atlas make it plausible that a player could learn where lift tends to occur, even though this specimen never reaches the current hawk-enter threshold?

Automated evidence strongly supports 1–4. Question 5 is intentionally left for human judgment. Question 6 is a gameplay-legibility judgment, not a correctness assertion.

## Interactive review launch — Windows

From the repository root, run:

```bat
scripts\run-sf-imp-1140-atmosphere-human-review.bat
```

The launcher will:

1. clone the exact pinned A4MC source commit into `build/sf-imp-1140-a4mc-source`;
2. apply the retained public terrain-provider seam patch;
3. run upstream `buildAndCollect`;
4. select the exact NeoForge 1.21.1 patched core;
5. regenerate accepted DR-50 A;
6. open an interactive client directly into the `acceptance` world;
7. enable read-only DR-50 terrain authority for A4MC;
8. enable the accepted Fowl Play hawk and Reliable Gliders shared-lift adapters.

The review runtime remains development-only. Normal packaged Skyforge and stock A4MC 0.2.1 are unchanged.

## Interactive checks

After the world opens:

```text
/gamemode creative
/tp @s -32 340 32
/give @s reliable_gliders:glider 1
```

Reliable Gliders' accepted prototype treats a main/offhand glider on an airborne descending player as gliding.

### A. Ordinary glider behavior

From a safe elevated point, hold the glider and descend across/around the island.

Judge:

- whether atmospheric lift produces legible differences in descent without feeling like a rocket column;
- whether lift changes feel smooth rather than frame-jittery;
- whether weak-lift areas still preserve ordinary glider sink;
- whether route choice appears potentially meaningful.

C7 currently translates only **trusted vertical air** into the glider's final Y velocity. Horizontal wind, turbulence, shear, downdrafts, airspeed/polar behavior, and client prediction remain deferred and are **not** part of this review.

### B. Lift-biased route comparison

Compare the center area with a route toward the machine-observed opportunity region around:

```text
X ~ 160
Z ~ -32
Y ~ 330–380
```

Do not expect a guaranteed identical instantaneous updraft; A4MC evolves over time. The question is whether stronger-lift regions can be perceived through repeated gliding strongly enough to support route learning.

### C. Hawk behavior

Spawn one or a few Fowl Play hawks near the review volume:

```text
/summon fowlplay:hawk -32 340 32
```

Observe for several minutes while moving around the island.

Expected result for this specific bounded specimen:

- hawks should remain valid stock/adapted entities;
- the shared A4MC sampler should remain authoritative;
- **persistent thermal-soaring entry is not expected**, because the measured field never reaches the current 1.5 m/s enter threshold.

Failure means broken/stuttering/pathological behavior, errors, or obvious adaptation malfunction. A hawk simply not entering SOAR is **not** a failure of this review; it is evidence for later balance discussion.

### D. Local hazard sanity check

Inspect/fly near the low-altitude central region around Y~300 where the observatory found the largest turbulence/shear values.

Because C7 does not currently map turbulence/shear into glider control, do **not** use the glider response as evidence that those channels are balanced. Instead judge whether the plotted hotspot's existence and localization look physically/plausibly tied to terrain rather than like an obviously broken isolated numerical spike.

Aircraft-control disturbance from these channels belongs to the later aircraft/weather tuning lane.

## Required verdicts

Record one verdict for each review question:

| Review question | Allowed result |
| --- | --- |
| Thermal/updraft spatial-temporal coherence | PASS / TUNE-LATER / FAIL |
| Ordinary wind field legibility | PASS / TUNE-LATER / FAIL |
| Altitude transitions | PASS / TUNE-LATER / FAIL |
| Turbulence/shear suitability | PASS / TUNE-LATER / FAIL |
| Shared glider/fauna opportunity legibility | PASS / TUNE-LATER / FAIL |
| Sampling cost / runtime stability | PASS / FAIL |

A **TUNE-LATER** result is acceptable for #1140 completion. It means the authority and diagnostic field are good enough to proceed, but balance/presentation belongs to a later tranche.

## Recommended disposition from current evidence

Before owner play review, the engineering evidence supports:

- thermal/updraft coherence: **provisional PASS**
- ordinary wind smoothness/local variation: **provisional PASS**
- altitude transitions: **provisional PASS**
- turbulence/shear: **TUNE-LATER**
- glider/fauna opportunities: **TUNE-LATER**
- sampling cost/runtime stability: **PASS**

The project-owner review should confirm or overturn only the experiential/legibility portions. It should not revisit the already accepted authority architecture unless a runtime defect is actually observed.

## Stop boundary

Do not, during this review:

- lower the hawk 1.5 m/s threshold merely because DR-50 does not cross it;
- increase A4MC thermal strength merely to make the review dramatic;
- change glider smoothing;
- create Skyforge-local synthetic thermals;
- add visual weather/climatology systems;
- treat one live weather realization as a balance target.

If the review returns **TUNE-LATER**, record the observation and close #1140 without production tuning. If it returns **FAIL**, capture the location, consumer, observed behavior, and reproduction conditions before changing code.
