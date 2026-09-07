# AUTH-0088 — Published surface ecology projection

## Purpose

AUTH-0088 projects the accepted AUTH-0003 ecological field into the exact world-space horizontal
frame of one AUTH-0087 validated production publication.

The concrete production gap is specific:

- AUTH-0003 already authors local ecological regimes and continuous vegetation/saturation/thermal
  potentials;
- AUTH-0087 proves which exact authored island corresponds to each exact published realization;
- Minecraft's exact-volume biome resolver already receives one exact volume id and a representative
  world coordinate;
- no accepted Authorship contract currently converts that exact published volume + world X/Z into
  the authored local ecology sample.

Development fixtures therefore still need fixed backend biome identities such as forest/taiga even
though the semantic ecology already exists.

AUTH-0088 closes only that backend-neutral projection gap.

## Scope

AUTH-0088 adds:

- `SkyIslandPublishedSurfaceEcologyResolver`;
- `SkyIslandPublishedSurfaceEcologySample`.

The resolver consumes one accepted `SkyIslandPublishedAuthoredRealizationBinding`.

A query consumes:

- one exact `SkyIslandWorldVolumeId`;
- one abstract world-space horizontal `Coordinate2`.

It emits the exact AUTH-0003 `SkyIslandEcologySample` only when the associated published
realization has physical horizontal support and the current native-authored domain owns that same
recovered local position.

It does not choose a backend biome.

## Why this is a surface projection

Minecraft biome presentation needs a semantic answer for the island surface and its adapter-owned
near-surface presentation envelope. That envelope may include the immediate air cell used for player
biome reads.

A 3-D authored-material ownership query would be the wrong abstraction here because it requires a
physical Y inside the volume.

AUTH-0088 therefore answers a narrower question:

> For this exact published island and this world X/Z, what surface ecology did Authorship assign?

The backend remains responsible for deciding which Y/quart cells are allowed to present that
surface ecology.

## Exact volume identity

The caller must supply an exact published `SkyIslandWorldVolumeId`.

The resolver is constructed from AUTH-0087, so every available volume identity already belongs to
the exact accepted publication and exact explicit AUTH-0046 association catalog.

Unknown volume identity is rejected.

AUTH-0088 does not discover or rank an island from:

- world position;
- bounds;
- nearest center;
- geometry seed;
- morphology;
- caller order;
- backend encounter order.

This preserves the AUTH-0046/AUTH-0087 identity contract.

## World/local transform

For the exact associated realized volume:

~~~text
localX = worldX - realized.centerX
localZ = worldZ - realized.centerZ
~~~

No scale, rotation, or new coordinate convention is introduced.

The output sample retains both the authoritative world input and the exact recovered local
coordinate.

## Physical horizontal support gate

The exact associated compiled volume is queried through
`SkyIslandCompiledVolumeColumnField.columnAt(local)`.

If no positive physical column exists at that horizontal location:

- `physicalColumnPresent = false`;
- no authored surface ecology is exposed.

This prevents native ecology from being projected into a horizontal location where the accepted
realization has no island surface to carry it.

The gate introduces no new threshold.

## Current authored-domain gate

Physical support alone is not native authored ownership.

AUTH-0088 samples the current
`SkyIslandSemanticFieldSet.create(descriptor).interiority()`.

Only:

~~~text
physical column present
    AND
current authored interiority > 0
~~~

may expose AUTH-0003 ecology.

If the independently compiled physical realization contains a fringe outside the current
naturalized native domain, no ecology is emitted there.

This distinction is essential because AUTH-0003 classifies non-owned positions as
`COLD_BARREN` for local semantic diagnostics. AUTH-0088 must not reinterpret that diagnostic
fallback as an authored cold-barren biome covering physical or empty fringe.

No second interiority threshold is introduced.

## Exact AUTH-0003 equivalence

Where both gates pass, the resolver returns exactly:

~~~text
SkyIslandEcologyField.create(authoredDescriptor).sample(recoveredLocalPosition)
~~~

The sample envelope independently rejects a different ecology value for the same exact association
and recovered local position.

AUTH-0088 adds no:

- ecological regime;
- temperature/moisture formula;
- vegetation threshold;
- saturation threshold;
- hydrology threshold;
- biome classification threshold.

## Deliberate absence of Y

The public query surface accepts only:

~~~text
SkyIslandWorldVolumeId
Coordinate2(worldX, worldZ)
~~~

It does not accept physical Y or `Coordinate3`.

This is intentional.

Implementation already owns:

- exact-volume physical admission;
- the surface-air presentation envelope;
- Minecraft 4x4x4 quart-biome quantization;
- conflicting-volume handling inside indivisible biome cells;
- persistent biome storage;
- client synchronization.

Moving those policies into Authorship would create a competing backend lifecycle.

## Concrete downstream consumer

The intended consumer is the existing Minecraft/NeoForge
`SkyforgeExactVolumeBiomeResolver`.

A conforming adapter may:

1. retain its existing exact volume/quart-cell eligibility policy;
2. convert the supplied exact volume id + representative world X/Z to AUTH-0088;
3. map the resulting backend-neutral ecological regime/potentials to a registered Minecraft biome
   according to adapter-owned policy;
4. preserve its existing final-registry lookup, population, persistent presentation, and
   conflict handling.

A development fixture may still use fixed forest/taiga identities where appropriate. AUTH-0088
does not retroactively claim that SF-IMP-0080's human ecology showcase is a semantic-biome
translation acceptance.

## Relationship to SF-IMP-0080

SF-IMP-0080 is a separate Implementation showcase-quality milestone.

Its human gate asks whether forest/taiga land ecology is visibly legible after native population and
save/reopen.

AUTH-0088 asks whether the backend-neutral authored ecology can be queried correctly from one exact
published world-space horizontal location.

Therefore:

- AUTH-0088 needs no Minecraft manual run;
- SF-IMP-0080's manual gate remains required for its own acceptance;
- neither milestone implies issue #214 production morphology acceptance.

## Acceptance gate

Reject AUTH-0088 if:

- an island is inferred instead of named by exact published volume id;
- a volume outside the AUTH-0087 binding can be sampled;
- world X/Z are treated as local coordinates without the explicit realized-center translation;
- ecology is exposed without a positive compiled physical column;
- ecology is exposed outside the current authored naturalized domain;
- a new authored-domain threshold replaces the existing `interiority > 0` ownership rule;
- returned ecology differs from exact AUTH-0003 evaluation at the recovered local point;
- physical Y becomes part of the public surface-ecology query;
- a Minecraft/NeoForge biome key, registry object, BlockPos, quart coordinate, chunk type, or
  presentation policy enters `skyforge-world`.

## Evidence target

The compact proof corpus should demonstrate:

- exact volume-id routing;
- direct AUTH-0003 equivalence;
- deterministic repeat sampling;
- world/local translation;
- physical-support rejection;
- physical-but-native-unowned fringe rejection;
- unknown-volume rejection;
- no-Y public query contract;
- broad ecology variation across representative exact published associations.

The proof is architecture/provenance evidence, not an aesthetic Minecraft biome review.

## Next boundary

After AUTH-0088, the backend-neutral producer side is sufficient for an Implementation milestone to
replace fixed proof-only biome identity with an adapter-owned AUTH-0003 ecology-to-Minecraft mapping.

Further Authorship work should move only where a concrete semantic failure remains, with likely
priorities including ecology/geography coupling at regional scale or deterministic Bootstrap
Province convergence rather than another adapter-shaped wrapper.
