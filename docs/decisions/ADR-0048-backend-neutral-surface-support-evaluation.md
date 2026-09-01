# ADR-0048 — Backend-Neutral Surface Support Evaluation

**Status:** Accepted

## Context

SF-IMP-0043 proved that Minecraft's native structure-start path can observe elevated Skyforge geometry through the accepted early height-query bridge. The same proof showed that scalar height visibility is insufficient for multi-block structure placement: a structure footprint can span an island edge or steep surface and still receive individually truthful heights that produce a poor foundation.

Extending `getBaseHeight(...)` to invent flattened support would make generic generator queries geometrically false. Hard-coding Minecraft structure types or dimensions into the Skyforge kernel would reverse the accepted dependency boundary.

## Decision

Add a generic surface-support contract to `skyforge-world`:

- `SurfaceSupportRequirements` describes a world-space X/Z footprint, deterministic sample spacing, clearance ring and caller-owned policy thresholds.
- `SkyIslandSurfaceSupportEvaluator` evaluates compiled Skyforge geometry independently for every relevant `SkyIslandWorldVolume`.
- `SurfaceSupportAssessment` reports the geometric evidence and a recommendation.

The evaluator derives support from the compiled upper-surface and density fields. At each X/Z sample it evaluates the upper surface and probes density a small deterministic distance below that mathematical surface. Positive density means that the candidate volume supports the sample.

## Why `skyforge-world`

The evaluator coordinates independently compiled world volumes and their stable identities. It is therefore above the mathematical kernel and recipes but below any backend adapter.

The mathematical kernel remains unaware of structure policy. The NeoForge backend may later translate native structure candidates into this neutral contract.

## Per-island isolation

Catalog candidates are never fused before assessment.

```text
shared X/Z footprint
    -> island A -> assessment A
    -> island B -> assessment B
```

This prevents vertically stacked islands from becoming one fictitious support envelope and preserves the information needed for later target-surface selection.

## First policy dimensions

SF-IMP-0044 evaluates:

1. interior support coverage;
2. clearance-ring support coverage;
3. minimum and maximum supported surface elevation;
4. surface height span;
5. footprint boundary crossing;
6. cardinally connected support-component count; and
7. coherent-surface status.

Acceptance requires nonzero support, threshold-satisfying interior/clearance coverage, height span within the caller's limit and one coherent connected support component.

The thresholds are test/policy inputs rather than permanent global constants.

## Data ownership

`SurfaceSupportRequirements` owns only the requested footprint and policy thresholds.

`SkyIslandSurfaceSupportEvaluator` owns no persistent state. It consumes a catalog or one world volume and compiles temporary scalar-field evaluators from the already-authoritative compiled graphs.

`SurfaceSupportAssessment` owns only immutable diagnostic output.

## Invariants

1. No Minecraft or NeoForge type enters `skyforge-world` through this feature.
2. Every island volume is assessed independently.
3. Support is derived from authoritative compiled fields, not backend blocks or neighbor scans.
4. The evaluator never modifies, flattens or extends island geometry.
5. Sampling order and result order are deterministic.
6. Catalog result order remains stable plan order.
7. Policy thresholds are explicit caller inputs.
8. A zero-support candidate cannot be accepted.
9. Disconnected support cannot be reported as coherent.
10. Generic early height queries remain truthful and unchanged.

## Testing

Focused deterministic synthetic-volume tests cover:

- a broad flat interior with supported clearance;
- an edge-crossing footprint;
- a sloped surface exceeding the allowed height span;
- disconnected support; and
- two vertically stacked volumes sharing the same X/Z footprint.

Repository-wide CI is the acceptance gate.

SF-IMP-0044 was accepted after pull-request CI run #107 completed successfully on commit `bd7f8a5197e98607c158702a6412a910b92c025c`, exercising the repository-wide build, test, backend-independence and evidence workflow with the new evaluator and deterministic tests present.

## Extension path

SF-IMP-0045 may add a Minecraft adapter seam that derives a native candidate footprint and consumes the accepted assessment. Later work may add richer policy dimensions such as orientation, local normal/slope statistics, accommodation classes, authored relationships or target ranking without teaching the kernel about Minecraft structures.

## Explicit non-goals

SF-IMP-0044 does not:

- intercept Minecraft structure generation;
- rewrite vanilla structure pieces or templates;
- choose between native ground and Skyforge support;
- flatten terrain or build foundations;
- encode permanent per-structure thresholds;
- solve villages/jigsaw realization;
- define structure frequency or biome eligibility; or
- solve ecology replay beneath floating terrain.
