# AUTH-0096 — Local Surface-Site Capability Evidence

## Purpose

Bootstrap Province issue #224 requires optional civilization/historical infrastructure and later mature
regional infrastructure without fixing one settlement template or morphology family. The design record
`structure-site-capability-profile-v0.1.md` already calls for a Stage-B backend-neutral capability
layer between authored world semantics and concrete structure pairing.

AUTH-0096 supplies the first narrow executable seam: local surface candidate-anchor evidence.

The profile answers:

> What does this exact authored/realized island provide at this local surface anchor?

It does **not** answer:

> Is a village, airfield, ruin, dungeon, civic site, or other concrete structure valid here?

Concrete requirement matching remains Content-owned; exact geometry/admission remains
Implementation-owned.

## Exact producer boundary

The public profiler accepts exactly one accepted
`SkyIslandAuthoredRealizationAssociation`.

It reuses:

- the exact AUTH-0046 authored descriptor and realized volume;
- the accepted 49×49 watershed lattice;
- current authored naturalized-domain interiority;
- current visible hydrologic realization:
  - coherent channels;
  - retained-waterbody footprints;
  - waterbody margins;
  - riparian corridors;
  - coherent hydrologic terrain response;
- exact compiled physical upper/underside columns from the associated realization.

No second surface grid, river network, site seed, or structure-specific morphology recipe is created.

## Canonical local lattice

The accepted watershed lattice spans `[-R,+R]` on both horizontal axes with 49 samples.

Therefore:

```text
spacing = 2R / 48 = R / 24
```

AUTH-0096 preserves that exact spacing and the exact watershed cell identities/order.

## Per-anchor evidence

For each accepted watershed cell, AUTH-0096 records:

### Exact identity

- watershed cell index;
- island-local position;
- exact source association through the containing profile.

### Physical surface

- whether an exact realized physical column exists at the anchor;
- upper-surface offset from realized suspension elevation divided by nominal radius.

The physical offset is descriptive evidence for the exact associated realization. It is not a Minecraft block Y and must not be inferred to be translation-invariant: accepted planar detail signals sample world X/Z.

### Local support

AUTH-0096 measures physical-column coverage in square neighborhoods centered on the anchor:

- 3×3 samples;
- 5×5 samples;
- 9×9 samples.

The corresponding full horizontal spans are:

- `R/12`;
- `R/6`;
- `R/3`.

Coverage remains a continuous fraction. AUTH-0096 does not turn it into a buildability threshold.

### Local relief

For the same 3×3 / 5×5 / 9×9 windows, maximum minus minimum physical upper-surface elevation is
divided by nominal radius.

Relief is emitted only when the center anchor has physical surface support.

### Cardinal grade

The mean absolute rise/run from the center to physically supported cardinal neighbors is reported as
a dimensionless grade.

This is not a walkability or structure-relief limit.

### Authored hydrologic context

The anchor retains accepted source semantics:

- authored interiority;
- normalized watershed flow accumulation;
- retained-waterbody membership;
- shoreline membership;
- retained-water depth potential;
- waterbody-margin potential;
- coherent-riparian potential;
- coherent-channel relative discharge;
- absolute coherent hydrologic terrain-response magnitude.

No hydrologic threshold is re-authored by AUTH-0096.

## Candidate anchors, not island labels

AUTH-0096 deliberately does not collapse one island to values such as:

```text
supportsVillage = true
supportsAirfield = false
```

A complex island can contain many different local contexts. Downstream requirements may inspect
different anchors and neighborhood scales for different roles.

This preserves the design rule that structures constrain admissible local terrain without dictating
the visible morphology family.

## Ownership

### Authorship

Owns:

- exact local world evidence;
- deterministic provenance;
- backend-neutral physical/semantic measurements;
- future additional capability evidence only when a concrete consumer requires it.

### Content / Experience

Owns:

- civilization/structure roles;
- settlement archetypes;
- progression guarantees;
- route/teaching/service meaning;
- policy thresholds used to rank or accept a semantic site.

### Implementation

Owns:

- concrete Minecraft/mod structure identity;
- exact structure geometry;
- orientation/placement;
- exact physical compatibility/admission;
- terrain mutation/accommodation;
- persistence/lifecycle.

AUTH-0096 is not permission to skip exact structure proof.

## Scope intentionally deferred

The design roadmap also discusses interior, cliff/underside, cave, water-volume, access, and
composition capability.

AUTH-0096 does not implement those domains merely for completeness.

Add them only when the next retained structure/civilization consumer demonstrates that the surface
profile is insufficient.

## Evidence

Evidence identity:

```text
authorship-surface-site-capability-v1
```

Files:

- `index.html`;
- `atlas.png`;
- `manifest.csv`;
- `cells.csv`.

Proof panels:

- `AUTH0046_ASSOCIATION`;
- `WATERSHED_COVERAGE`;
- `DETERMINISTIC`;
- `LOCAL_PHYSICAL`;
- `HYDROLOGY_SOURCE`;
- `NO_SITE_POLICY`.

## Acceptance

Reject AUTH-0096 if:

- the profiler accepts inferred/spatially discovered island identity instead of one exact AUTH-0046 association;
- watershed cell identity/order changes;
- another local planning grid is introduced;
- current hydrology is recomputed through different thresholds or topology;
- physical support/relief values become Minecraft coordinates or block policy;\n- exact realized physical evidence is replaced by an assumed translation-invariant surrogate;
- any village/airfield/dungeon/settlement/buildable/walkable classification enters the Authorship API;
- exact concrete structure admission is treated as unnecessary after semantic profiling.
