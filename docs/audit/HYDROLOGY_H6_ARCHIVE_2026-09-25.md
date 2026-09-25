# Hydrology H6 archive — 2026-09-25

**Status:** historical / retired from active development  
**Frozen branch:** `archive/hydrology-h6-2026-09-25`  
**Frozen snapshot:** `aa2ac58f15f67c6b64040bea71dfbc4d6a0c43cf`  
**Current replacement authority:** issue #1084

## Why this archive exists

The H6 line accumulated substantial useful hydrology work, but the 2026-09-25 key-287 human review hard-failed after exact-head machine qualification had gone green. The failure therefore cannot be treated as one more local presentation defect.

The frozen branch preserves the complete latest H6 state so individual components, diagnostics, tests, or historical decisions can be recovered without leaving the obsolete PR chain active.

## Retired active PR chain

The following hydrology PRs were closed unmerged as part of the reset:

- #949 — DR-70: realize authored hydrologic geomorphology;
- #1062 — DR-70 hydrology presentation / human-review atlas;
- #1075 — H2 validation pulse;
- #1076 — H2b terrain-routed macro reaches;
- #1077 — H2-H3 terrain routing and discharge geometry;
- #1079 — first H6 visual-review repairs;
- #1080 — H6 realization-contract repairs;
- #1083 — latest H6 review follow-up.

PR #1078 remains merged historical audit work. PRs #1081 and #1082 were already closed/superseded.

Closing these PRs does not erase their evidence. Their commits, review discussions, CI results, and the frozen branch remain available for forensic reuse.

## What the H6 line successfully established

Preserve as reusable evidence/components unless later analysis disproves them:

- deterministic watershed/catchment accumulation and drainage-fate semantics;
- separation of Priority-Flood spill/connectivity authority from physical flow-direction selection;
- terrain-aware route diagnostics and bounded refinement experiments;
- discharge/profile semantics and channel hierarchy;
- AUTH-0105 continuous fluvial landform vocabulary;
- exact-volume ownership, stacked-volume isolation, persistence, lifecycle, and authored-fluid fencing;
- deterministic/fail-closed physical admission;
- human-review harness and proving-ground workflow;
- machine checks for connected/flat retained water, downstream-nonclimbing channel grades, water/bed continuity, junction ownership, and deterministic realization.

## Why the line is retired

The latest review showed that all of the above can be true while the produced landform is still physically implausible.

The audit identified the systemic authority chain:

```
coarse semantic planning
    -> progressively hardened exact route / footprint obligations
    -> continuous local shaping around those obligations
    -> backend reconciliation excavation to make them physically feasible
```

This permits planning-resolution artifacts to become mandatory geometry.

Concrete failure mechanisms include:

- material ridge-crossing incidence remaining in accepted route evidence while the physical channel is required to preserve the route;
- no sufficiently hard coupling between vertical incision and horizontal recovery distance;
- retained-water boundaries derived from coarse watershed cells plus rounding/corridor smoothing rather than a continuous basin contour;
- backend reconciliation capable of rescuing a geometrically incompatible solution with multi-block terrain surgery;
- qualification metrics proving topology/lifecycle correctness but not geomorphic plausibility.

## Reuse rule

Do **not** restart by reopening, rebasing, or extending the retired H6 PRs.

Code may be reused only by deliberately importing a component whose authority still fits the new #1084 architecture. In particular, do not carry forward an assumption merely because an H6 test protects it. Tests that encode obsolete authority boundaries must be revised or retired when the new mathematical contract is accepted.

This archive is evidence, not the next implementation base.
