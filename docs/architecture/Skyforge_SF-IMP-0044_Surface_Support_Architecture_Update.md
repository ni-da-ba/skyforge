# Skyforge SF-IMP-0044 — Surface Support Architecture Update

SF-IMP-0044 introduces a backend-neutral suitability primitive between semantic island geometry and backend structure realization.

```text
backend structure candidate
        -> world-space footprint requirements
        -> SkyIslandSurfaceSupportEvaluator
        -> one assessment per relevant island volume
        -> coverage / clearance / relief / coherence
        -> accept or reject recommendation
```

The evaluator does not know about desert pyramids, villages, jigsaw pieces, chunks, blocks, or NeoForge. It consumes the already-compiled upper-surface and density fields owned by each `SkyIslandWorldVolume`.

Support is evaluated independently per island. Two islands occupying the same X/Z footprint at different elevations therefore produce two assessments rather than one combined height envelope. This is the required basis for future stacked-island selection and authored structure relationships.

For each deterministic sample point, the evaluator reads the compiled upper-surface height and probes density immediately below that mathematical surface. A positive density value means that the candidate island actually supports that sample. No voxel-neighbor inspection or geometry mutation is involved.

The first policy dimensions are intentionally small:

- footprint coverage;
- clearance-ring coverage;
- upper-surface height span;
- whether the footprint crosses the surface boundary;
- connected-component coherence of supported samples.

Thresholds remain caller-owned inputs. SF-IMP-0044 does not encode a permanent definition of what a pyramid, village, road, ruin, or modded structure requires.

The result is diagnostic rather than merely Boolean. `SurfaceSupportAssessment` retains the supporting volume identity, sample counts, coverage fractions, surface extrema, height span, boundary crossing, connected component count, coherence and final acceptance recommendation.

This preserves the accepted SF-IMP-0043 boundary:

```text
truthful generic height query
        !=
structure footprint suitability
```

The future Minecraft adapter may derive a candidate footprint from native structure data and submit it to this evaluator. Minecraft may continue to own concrete structure pieces and realization while Skyforge owns higher-level support policy.
