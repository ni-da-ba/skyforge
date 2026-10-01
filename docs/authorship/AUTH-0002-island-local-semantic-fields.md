# AUTH-0002 — Island-local semantic fields

**Status:** implementation candidate

## Objective

Turn the stable island-scale common causes established by AUTH-0001 into deterministic, coherent fields that can be evaluated at positions relative to an authored island.

The milestone establishes the reusable question:

> At this island-local position, what semantic environmental state does Skyforge author?

It does not yet choose Minecraft biomes, blocks, structures, river paths, or backend coordinates.

## Evaluation boundary

```text
SkyIslandDescriptor
        |
        v
SkyIslandSemanticFieldSet
        |
        +--> interiority
        +--> elevation tendency
        +--> temperature
        +--> moisture
        +--> exposure
```

All fields implement the small `SkyIslandSemanticField` contract and are sampled with `SkyIslandLocalPosition`.

`SkyIslandLocalPosition(0, 0)` is the semantic island center. These coordinates are not a realized backend position. A backend may later translate or otherwise place the authored island without changing its native semantic geography.

## Initial fields

### Interiority

Represents interior-to-edge influence. It remains high through the island core and smoothly falls toward zero near the nominal boundary.

This field is the first explicit reusable edge influence. Future ecology, structures, hydrology, and morphology can consume it rather than independently rediscovering island boundaries.

### Elevation tendency

Represents normalized tendency toward higher authored surface relief.

Primary morphology family is a common cause:

- Massif favors a concentrated high core.
- Tableland favors a broad high interior.
- Spine favors an elongated high axis.
- Basin favors a raised rim around a lower center.
- Lobed morphology modulates the radial form into connected shoulders.

Low-frequency deterministic variation enriches these forms without replacing morphology identity.

### Temperature

Begins from the descriptor thermal tendency, then adds:

- a smooth island-local north/south gradient;
- low-frequency coherent variation;
- cooling associated with local elevation tendency.

The north/south axis is currently an island-local semantic axis. Province-scale climate orientation can replace or influence this prior later without introducing backend coordinates.

### Exposure

Begins from descriptor exposure tendency, then responds to:

- edge influence;
- low-frequency coherent local structure.

This gives downstream systems a common exposure signal rather than independent edge/noise heuristics.

### Moisture

Begins from descriptor moisture tendency, then responds to:

- coherent local variation;
- exposure drying;
- interior low-elevation retention scaled by descriptor hydrological potential.

This is not yet drainage or watershed simulation. AUTH-0004 will derive hydrology from authored geography rather than treating this moisture field as a river generator.

## Coherence

AUTH-0002 uses deterministic lattice value noise with smooth interpolation only as controlled variation inside semantic fields.

Noise is not the ontology. The causal direction remains:

```text
stable identity
    -> descriptor meaning
    -> semantic field meaning
    -> coherent variation
```

not:

```text
noise
    -> attempt to infer meaning afterward
```

## Ownership and outside evaluation

Fields remain mathematically evaluable outside the nominal island boundary because composable fields are easier to reason about when they are total functions.

The visual corpus masks samples whose interiority is zero so diagnostic images clearly represent the bounded island. Downstream consumers that require exact ownership must use the appropriate island ownership/volume system rather than treating field visualization as an ownership oracle.

## Visual evidence

AUTH-0002 introduces a permanent authorship review corpus containing:

- `overview.png`;
- `elevation.png`;
- `temperature.png`;
- `moisture.png`;
- `exposure.png`;
- `interiority.png`;
- canonical `descriptor.json`;
- scalar `stats.csv`;
- `index.html`.

The corpus is produced by the Java implementation during repository `check`, verified by CI, and included in the compact evidence artifact.

The purpose is to inspect whether Skyforge authored sensible semantic geography before a Minecraft adapter is involved.

## Acceptance questions

The visual corpus should answer:

1. Is the island boundary smooth and legible?
2. Does primary morphology remain visible through local variation?
3. Are environmental gradients coherent rather than checkerboard noise?
4. Does elevation affect temperature in a visible but non-dominant way?
5. Does exposure increase plausibly toward edges?
6. Does moisture form broad regions that could later support ecological differentiation and hydrology?
7. Are all of these observations independent of Minecraft realization?

## Next milestone

AUTH-0003 should classify a deliberately small semantic ecology vocabulary from these fields while preserving spatial coherence and multi-region behavior on sufficiently large islands.

Biome/ecology classification must remain backend-neutral. Mapping semantic ecological state onto genuine registered Minecraft biome identities remains a downstream adapter concern.
