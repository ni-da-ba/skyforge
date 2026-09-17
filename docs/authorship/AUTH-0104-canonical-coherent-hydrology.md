# AUTH-0104 — Canonical coherent hydrology authorization

## Purpose

The locked DR-00 specimen remains the required DR-70 specimen. Its identity is exactly
`SkyIslandIdentity.of(0x534B59464F524745, 8, 81, 1471)`.

AUTH-0104 adds one explicit backend-neutral retained-water authorization for that existing descriptor.
It does not relock or replace the specimen, and it does not modify morphology, physical-volume
identity, terrain, caves, or ecology.

## Authored expression

The authorization selects one stable connected pair from the exact existing watershed grid:

- both cells must be interior rather than edge outlets;
- their accepted surface and spill potentials must admit one shared retained-water surface;
- the pair with greatest available shared hydraulic head wins, with watershed cell identity as the
  deterministic tie-break;
- the result is expressed through the existing `POND`,
  `SkyIslandWaterbodyCandidate`, `SkyIslandWaterbodyFootprint`, margin, and AUTH-0086
  `RETAINED_WATER` vocabulary.

The footprint has two distinct watershed cells. Its source candidate and every footprint position
remain exact watershed-derived provenance; no backend coordinate, Minecraft fluid policy, or
implementation rasterization enters Authorship.

## Projection and discharge boundary

AUTH-0086 remains an exact one-for-one projection. The new retained-water footprint is projected as
`RETAINED_WATER`; existing coherent channel and drop sources remain unchanged. In particular,
existing `EDGE_FALL` source events remain visible only as explicit `EDGE_DISCHARGE` intents and are
not inferred from arbitrary exterior fluid propagation.

AUTH-0104 creates no drop event and no fluid class. AUTH-0085 remains unchanged: native molten
springs fail closed with `MISSING_GEOTHERMAL_SEMANTICS`, and no geothermal, volcanic, lava, or
molten-discharge authority is introduced.

## Verification target

GitHub Actions must verify deterministic repeatability on the exact locked descriptor, a retained
footprint containing more than one position, exact watershed/source provenance, exact AUTH-0086 drop
projection, the existing AUTH-0086 corpus/regressions, and the existing AUTH-0085 molten rejection.
