# SF-IMP-0029 Terrain Semantics Visual Review

- **Date:** 2026-08-31
- **Verdict:** PASS
- **Evidence corpus:** `terrain-semantics-v1`
- **Specimen semantic SHA-256:** `b1dd1978c41afab4c7afd09b3c288c5bc9576793145b5ef39721405f23669598`
- **Regional Hub semantic SHA-256:** `ee4b1344c69e54735c6c718e8363f594c7d1ed321802bb4f3a5ae2d868469389`

## Review method

Visual evidence is read in this order:

1. plane/view;
2. encoded quantity;
3. invariant or feature demonstrated;
4. remaining visual limitation.

Numerical/programmatic acceptance is separate and was already green through the focused local verifier.

## Close specimen — east-west section

### Plane / view

Vertical east-west section through one independently compiled stable-seed Hub member on the fine 4-unit evidence lattice.

### Encoded quantity

Each solid sample is colored by `SkyIslandTerrainSemantic`:

- green — `SURFACE_MANTLE`;
- ochre — `EDGE_SHELL`;
- blue-gray — `UNDERSIDE_SHELL`;
- tan — `SHALLOW_INTERIOR`;
- dark brown — `DEEP_MASS`;
- background — `AIR`.

### Demonstrated invariant / feature

The upper mantle follows the upper surface as a thin coherent band. The underside shell independently tracks the exposed lower surface. Shallow interior separates both explicit surface shells from the deep core. Deep mass remains substantial in thick columns and contracts as the island body thins. Edge-shell samples remain localized at pinched lateral terminations rather than appearing arbitrarily through the interior.

### Remaining limitation

This is a sampled engineering section. Pixel stair-stepping reflects the finite evidence lattice, not a claimed block-final terrain profile.

## Close specimen — north-south section

### Plane / view

Orthogonal vertical north-south section through the same specimen.

### Encoded quantity

The same backend-neutral terrain-role palette.

### Demonstrated invariant / feature

The same vertical semantic ordering survives a second independent section through different local morphology. The concave/uneven upper relief does not collapse the interior bands or leak underside semantics upward.

### Remaining limitation

As with the east-west section, this view demonstrates classification coherence rather than final artistic material stratigraphy.

## Close specimen — topmost-solid plan

### Plane / view

Top-down map of the semantic attached to the topmost solid sample in every occupied horizontal column.

### Encoded quantity

Topmost structural terrain semantic.

### Demonstrated invariant / feature

The usable island crown is overwhelmingly `SURFACE_MANTLE`, with `EDGE_SHELL` localized to the thin perimeter. `SHALLOW_INTERIOR`, `DEEP_MASS`, and `UNDERSIDE_SHELL` do not leak through the crown. The semantic projection follows the actual island footprint rather than imposing a generic radial mask.

### Remaining limitation

A topmost-solid projection cannot show interior vertical layering by design. Cross-sections remain the authoritative visual evidence for those bands.

## Close specimen — isometric topmost-solid view

### Plane / view

Fit-to-scene isometric projection of topmost solid semantic samples.

### Encoded quantity

Topmost structural terrain semantic over the actual upper morphology.

### Demonstrated invariant / feature

Surface/edge semantics follow the nontrivial morphology spatially rather than flattening it into a rectangular, circular, or chunk-shaped classification. No sampled semantic discontinuity attributable to tiling is visible.

### Remaining limitation

The dot renderer is deliberately an engineering visualization and is not intended to approximate final Minecraft terrain appearance.

## Regional Hub — topmost-solid plan

### Plane / view

Top-down regional map of the complete stable-seed SF-IMP-0027 Hub scene using a coarser regional semantic lattice.

### Encoded quantity

Topmost structural terrain semantic across every independently compiled island returned through the accepted world catalog/query boundary.

### Demonstrated invariant / feature

The accepted regional hierarchy remains recognizable: a central formation, extended secondary formation, remote satellite/outlier formations, and substantial intentional empty-sky corridors. Individual island footprints remain discrete. `SURFACE_MANTLE` occupies island crowns while `EDGE_SHELL` remains concentrated around thin sampled margins. No chunk/tile-shaped semantic seams appear across any member.

### Remaining limitation

The regional grid is intentionally coarse. Small members are represented by relatively few pixels, so this view proves organizational consistency rather than fine semantic-band thickness.

## Regional Hub — isometric topmost-solid view

### Plane / view

Regional fit-to-scene isometric projection of topmost-solid semantic samples.

### Encoded quantity

Topmost terrain semantic distributed over the complete Hub hierarchy.

### Demonstrated invariant / feature

All accepted groups remain spatially distinct and no visible tile seam cuts through an island. The same surface/edge classification applies across built-in, blended, and external-provider morphology members without backend-specific visual artifacts.

### Remaining limitation

Large intentional empty-sky corridors make individual islands small in the frame. This image is suitable as regional engineering evidence but not as a fine morphology or material-quality render.

## Numerical context

The accepted close specimen contains 160,295 solid samples partitioned exactly as:

| Semantic | Samples |
| --- | ---: |
| `EDGE_SHELL` | 2,946 |
| `SURFACE_MANTLE` | 19,413 |
| `UNDERSIDE_SHELL` | 25,884 |
| `SHALLOW_INTERIOR` | 72,746 |
| `DEEP_MASS` | 39,306 |

The five solid-role counts sum exactly to the solid sample count.

## Final verdict

**PASS.**

SF-IMP-0029 visibly demonstrates a coherent structural interpretation over accepted suspended geometry at both single-island and regional scales. The semantic layer preserves morphology and regional composition, differentiates upper, underside, shallow, deep, and pinched-edge roles, and introduces no visible tile-seam artifacts.

The principal visual limitation is evidence presentation rather than architecture: regional topmost-solid views cannot communicate fine vertical bands, and the sparse isometric is not a cinematic terrain render. Future cutaway/exploded evidence could improve readability without changing the accepted semantic model.
