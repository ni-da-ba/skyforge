# AUTH-0087 — Published authored-realization binding

## Purpose

AUTH-0087 closes one concrete production handoff gap between the accepted compiled-world
publication path and explicit native authorship.

The repository already has:

- AUTH-0046 explicit authored-island <-> realized-volume association;
- AUTH-0047 world-space authored-material sampling;
- AUTH-0048 exact multi-island authored ownership;
- AUTH-0049 multi-island world material composition through stable AUTH-0045 application keys;
- AUTH-0058 proof-backed compiled-world publication.

The missing production invariant is general rather than material-specific:

> given one accepted AUTH-0058 publication, prove that one explicit AUTH-0046 association catalog
> covers that exact published world, with no missing, extra, or substituted realized volume.

AUTH-0049 material composition is the first concrete production consumer. The same exact association
coverage may later support ecology or other authored world-space semantics without defining a second
publication-binding protocol.

## Scope

AUTH-0087 adds one backend-neutral binding:

`SkyIslandPublishedAuthoredRealizationBinding`

It consumes:

1. one exact accepted `SkyIslandCompiledWorldPublication`;
2. one explicit `SkyIslandAuthoredRealizationCatalog`.

It emits no new geology, ecology, material role, spatial heuristic, world geometry, or backend
identity.

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

AUTH-0087 does not infer authored identity from publication order, group/member ordinal, geometry
seed, morphology family, nominal radius, center position, bounds overlap, nearest island, or backend
encounter order.

The association catalog remains explicit AUTH-0046 input.

The authored world seed remains independent of the realization root. Equality of those root domains
is neither required nor implied.

## First concrete consumer: authored material composition

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

`materialComposer()` is intentionally a convenience over the already accepted AUTH-0049 composer.
It adds no ownership or material-selection behavior.

## Concrete downstream motivation

The current Minecraft terrain adapter can materialize accepted solid occupancy but still uses a
minimal proof palette such as dirt/stone/deepslate. A future Implementation milestone may consume
AUTH-0087 to ensure its AUTH-0049 material composer belongs to the exact accepted production
publication before mapping stable application keys to adapter-owned block/material identities.

The same publication association proof can later support an authored ecology sampler for the exact
volume-biome resolver without repeating this gate.

Minecraft block identity, biome identity, quart-cell presentation, exact-volume mutation, scheduling,
and persistence remain Implementation-owned.

## Relationship to AUTH-0086

AUTH-0086 closes visible authored hydrology intent.

AUTH-0087 proves which explicit authored islands correspond to one exact accepted production
publication so downstream authored semantics can be consumed without identity inference.

Neither milestone authorizes backend mutation.

## Acceptance gate

Reject AUTH-0087 if:

- a raw world catalog can replace the accepted publication without explicit review;
- the association realization root differs from the publication root;
- missing or extra associations are accepted;
- matching volume ids can conceal substituted realized-volume values;
- association identity is inferred rather than explicitly supplied;
- authored and realization root seeds are required to be equal;
- AUTH-0049 ownership/material semantics are reimplemented;
- new geology, ecology, or material thresholds are introduced;
- Minecraft, NeoForge, registry, BlockState, ResourceLocation, biome, chunk, or placement types enter
  `skyforge-world`.

## Evidence target

The compact architecture/provenance proof demonstrates:

- exact publication coverage accepted;
- AUTH-0049 composer available from the proven association set;
- missing association rejected;
- extra association rejected;
- same-id substituted realized volume rejected;
- authored-world root remains independent from realization root;
- no backend identity is introduced.

This is not an aesthetic Minecraft material or ecology review.

## Next boundary

After AUTH-0087, the accepted publication has one reusable explicit authored-realization association
gate.

For visible geology, the next action should be Implementation consumption of AUTH-0049 rather than
another Authorship wrapper.

For Authorship, the next useful semantic boundary is world-space surface ecology projection through
this exact association, so the existing Minecraft exact-volume biome resolver can eventually consume
AUTH-0003 ecological regimes without fixed proof-only biome identity.
