# AUTH-0087 — Published authored-material binding

## Purpose

AUTH-0087 closes one concrete production handoff gap between the accepted compiled-world
publication path and the already accepted authored-material composition path.

The repository already has:

- AUTH-0046 explicit authored-island <-> realized-volume association;
- AUTH-0047 world-space authored-material sampling;
- AUTH-0048 exact multi-island authored ownership;
- AUTH-0049 multi-island world material composition through stable AUTH-0045 application keys;
- AUTH-0058 proof-backed compiled-world publication.

The missing production invariant was narrower:

> given one accepted AUTH-0058 publication, prove that the explicit AUTH-0046 association catalog
> used for authored material composition covers that exact published world, with no missing,
> extra, or substituted realized volume.

Without this boundary, a backend attempting to make geology/material authorship visible could be
tempted to reconstruct associations from publication list order, geometry seeds, nearest centers,
bounds, or other forbidden heuristics.

AUTH-0087 makes that unnecessary.

## Scope

AUTH-0087 adds one backend-neutral binding:

`SkyIslandPublishedAuthoredMaterialBinding`

It consumes:

1. one exact accepted `SkyIslandCompiledWorldPublication`;
2. one explicit `SkyIslandAuthoredRealizationCatalog`.

It emits no new geology, material role, palette decision, world geometry, or backend material
identity.

When construction succeeds, the binding may create the already accepted
`SkyIslandWorldAuthoredMaterialComposer` from the proven exact association catalog.

## Exact publication coverage

The association catalog realization root must equal the publication catalog root exactly.

The association count must equal the publication volume count exactly.

For every exact published `SkyIslandWorldVolume`:

- an association for that exact `SkyIslandWorldVolumeId` must exist;
- the association's realized volume must equal the exact published volume value.

Therefore these cases fail closed:

- missing published association;
- extra association not present in the publication;
- same volume id paired with substituted bounds or compiled-volume state;
- different realization root.

AUTH-0087 does not repair any mismatch.

## No association inference

AUTH-0087 does not infer authored identity from:

- publication order;
- group/member ordinal;
- geometry seed;
- morphology family;
- nominal radius;
- center position;
- bounds overlap;
- nearest island;
- backend encounter order.

The association catalog remains explicit AUTH-0046 input.

The authored world seed also remains independent of the realization root. Equality of those two root
domains is neither required nor implied.

## Material composer handoff

After exact coverage succeeds:

~~~text
AUTH-0058 accepted publication
        +
explicit AUTH-0046 association catalog
        ↓
AUTH-0087 exact publication coverage gate
        ↓
AUTH-0049 material composer
        ↓
world point
        -> exact authored owner
        -> AUTH-0047 semantic material sample
        -> AUTH-0045 stable application key
        ↓
adapter-owned concrete material binding
~~~

AUTH-0087 does not alter AUTH-0049 ownership or material semantics.

## Concrete downstream consumer

The immediate production consumer is the Minecraft/NeoForge realization lane.

The current Minecraft terrain adapter can materialize accepted solid occupancy but still uses a
minimal proof palette such as dirt/stone/deepslate. A future Implementation milestone may replace
that generic material choice at authored solid points with:

1. exact backend coordinate conversion;
2. an AUTH-0087 validated material composer;
3. the accepted AUTH-0049/AUTH-0045 application key;
4. an adapter-owned concrete block/material binding table;
5. existing exact-volume mutation and lifecycle authority.

The adapter must preserve authored cave void and fail closed on ambiguous ownership.

This document does not choose Minecraft blocks.

## Relationship to AUTH-0086

AUTH-0086 closes visible authored hydrology intent.

AUTH-0087 independently makes accepted geology/material authorship safe to consume from one exact
published production world.

Neither milestone authorizes the Authorship lane to perform backend mutation.

## Acceptance gate

Reject AUTH-0087 if:

- a raw world catalog can replace the accepted publication without an explicit reviewed change;
- the association realization root differs from the publication root;
- missing or extra associations are accepted;
- matching volume ids can conceal substituted realized-volume values;
- association identity is inferred rather than explicitly supplied;
- authored and realization root seeds are required to be equal;
- AUTH-0049 ownership/material semantics are reimplemented;
- new geology roles or material thresholds are introduced;
- Minecraft, NeoForge, registry, BlockState, ResourceLocation, chunk, or placement types enter
  `skyforge-world`.

## Evidence target

The compact architecture/provenance proof must demonstrate:

- exact publication coverage accepted;
- AUTH-0049 composer available only after the exact gate;
- missing association rejected;
- extra association rejected;
- same-id substituted realized volume rejected;
- authored-world root remains independent from realization root;
- no backend material identity is introduced.

The evidence is not an aesthetic Minecraft material review.

## Next boundary

After AUTH-0087, the Authorship material chain is sufficiently exposed for a production adapter.

The next action for visible geology should therefore be an Implementation consumption milestone
rather than another Authorship wrapper.

If Authorship continues independently, the next semantic priority should move to ecology/geography
coupling or another demonstrated world-authorship failure, not recursive publication protocol work.
