# AUTH-0097 — Directional Surface-Access Evidence

## Purpose

Bootstrap Province issue #224 requires a sparse mature-infrastructure encounter that can visibly
support dock/aircraft handling and route/navigation infrastructure. AUTH-0096 supplies local
surface support, relief, and hydrologic context, but it does not describe how a candidate anchor
opens toward the edge of the exact realized island.

The working structure-site-capability design also identifies landing/air-approach, edge/cliff, and
access context as Stage-B authored evidence.

AUTH-0097 supplies the next narrow semantic seam:

> From this exact AUTH-0096 surface anchor, what does realized support do along each canonical
> local-lattice direction before the accepted watershed-square boundary?

It does **not** answer:

> Is this a valid runway, dock, cliff settlement, flight corridor, road, or concrete structure site?

Those judgments remain downstream.

## Exact source boundary

The public profiler consumes exactly one accepted
`SkyIslandSurfaceSiteCapabilityProfile` from AUTH-0096.

AUTH-0097 retains:

- the exact AUTH-0046 authored-realization association;
- the exact AUTH-0096 watershed plan;
- the exact AUTH-0096 cell order/identity;
- the exact realized compiled volume;
- the same 49×49 watershed-square physical lattice.

A package-private `SkyIslandWatershedPhysicalSurfaceGrid` is shared by AUTH-0096 and AUTH-0097 so
both profilers sample one authoritative physical lattice rather than reproducing subtly different
coordinate/index rules.

The accepted watershed square spans `[-R,+R]` on both local horizontal axes:

```text
grid size = 49
cardinal step = R / 24
diagonal step = sqrt(2) * R / 24
```

The lattice is a planning/evidence square, not an assertion that physical island support fills that
square.

## Canonical directions

AUTH-0097 exposes eight directions in stable order:

```text
NEGATIVE_Z
POSITIVE_X_NEGATIVE_Z
POSITIVE_X
POSITIVE_X_POSITIVE_Z
POSITIVE_Z
NEGATIVE_X_POSITIVE_Z
NEGATIVE_X
NEGATIVE_X_NEGATIVE_Z
```

These names refer only to island-local semantic axes. Backend placement may translate/rotate those
axes. They are not Minecraft cardinal-orientation policy.

## Per-direction evidence

For each AUTH-0096 anchor with exact realized physical surface support, AUTH-0097 reports one
`SkyIslandSurfaceAccessRay` for every canonical direction.

### Available extent

- `availableStepCount`: number of complete lattice steps from the anchor to the watershed-square
  boundary in that direction;
- `stepDistanceNormalized`: one ray step divided by nominal radius.

### Immediate support run

- `consecutiveSupportedStepCount`: consecutive exact physical-support samples immediately after
  the anchor;
- `firstOpenDistanceNormalized`: distance / R to the first unsupported sample, when one is
  observed before the square boundary.

This is raw geometry evidence. No minimum run length is interpreted as usable access.

### Furthest support and open tail

- `furthestSupportedDistanceNormalized`: distance / R to the furthest supported sample observed
  along the ray;
- `boundaryOpenTailStepCount`: consecutive unsupported samples from the watershed-square boundary
  back inward;
- `boundaryOpenTailDistanceNormalized`: corresponding open-tail distance / R.

An open tail is not a guaranteed aircraft approach corridor. It only proves absence of this island's
sampled horizontal surface support on those lattice points. Other islands, structures, 3D terrain,
entities, weather, and concrete vehicle requirements remain outside this contract.

### Fragmentation / re-entry

- `supportTransitionCount`: number of supported↔unsupported transitions after leaving the
  supported anchor.

This preserves lobed/re-entering support evidence instead of collapsing the ray to one edge distance.

AUTH-0097 does not classify a ray as simple, fragmented, cliff-like, or accessible.

### Vertical character

Relative to the exact anchor upper surface:

- maximum sampled rise / R;
- maximum sampled fall / R;
- mean absolute grade across adjacent ray samples where both endpoints have exact physical support.

These are descriptive realized measurements. They do not define walkability, runway slope, road
grade, or structure-clearance thresholds.

## Unsupported AUTH-0096 anchors

AUTH-0097 preserves every AUTH-0096 cell in exact canonical order.

If an AUTH-0096 source cell has no exact realized physical surface, the corresponding AUTH-0097 cell
contains no rays. It cannot acquire synthetic access evidence.

## Exact-realization semantics

As established by AUTH-0096, realized physical evidence is tied to the exact AUTH-0046 realization.
Accepted planar detail signals sample world X/Z, so translating an otherwise semantically identical
realization may change fine physical support and relief.

AUTH-0097 therefore never substitutes a translation-invariant surrogate for exact realized geometry.

## Ownership

### Authorship

Owns:

- exact source provenance;
- deterministic local directional evidence;
- raw support/open-tail/transition/vertical measurements;
- the shared backend-neutral physical lattice used by AUTH-0096/AUTH-0097.

### Content / Experience

Owns:

- whether a semantic role needs directional access;
- role-specific thresholds or ranking;
- runway/dock/road/cliff-site meaning;
- progression/service/logistics significance;
- site selection and fallback/re-plan policy.

### Implementation

Owns:

- concrete Minecraft/mod structure and vehicle geometry;
- exact orientation;
- full 3D clearance/obstruction;
- block-space approach envelope;
- terrain accommodation;
- mutation;
- persistence/lifecycle;
- interactions with neighboring islands/structures and backend entities.

## Explicit non-contract

AUTH-0097 defines no:

- `airfieldCapable`, `runwayValid`, `dockCapable`, `buildable`, or `walkable` Boolean;
- runway length/width/slope standard;
- aircraft performance envelope;
- cliff-site class;
- road/path class;
- concrete structure orientation;
- cross-island air route;
- neighboring-island obstruction model;
- civilization role/density;
- faction/history state;
- progression guarantee;
- loot/salvage policy.

## Evidence

Evidence identity:

```text
authorship-directional-surface-access-v1
```

Files:

- `index.html`;
- `atlas.png`;
- `manifest.csv`;
- `rays.csv`.

Proof panels:

- `AUTH0096_SOURCE`;
- `SAME_LATTICE`;
- `DETERMINISTIC`;
- `RAY_RECONSTRUCTION`;
- `EXACT_REALIZATION`;
- `NO_ROLE_POLICY`.

The dedicated unit test independently reconstructs every emitted ray from
`SkyIslandCompiledVolumeColumnField`, rather than comparing the profiler against its own shared
helper.

## Acceptance

Reject AUTH-0097 if:

- it accepts anything other than one exact AUTH-0096 profile;
- AUTH-0096 and AUTH-0097 use competing physical lattices;
- AUTH-0096 regression changes after the shared-lattice refactor;
- exact source cell order/identity changes;
- a ray differs from independent direct physical-column reconstruction;
- an unsupported AUTH-0096 anchor gains access rays;
- a role/buildability/runway/dock threshold enters Authorship;
- open-tail evidence is promoted to region-level or 3D clearance proof;
- exact realized placement is replaced with translation-invariant physical assumptions.

## Downstream handoff

AUTH-0097 is sufficient for Content to begin defining a **specific** Bootstrap surface-access
requirement when it is ready to choose a dock/aircraft-handling or route-infrastructure role.

Do not immediately add regional route graphs, cliff classes, interior/cave access, or airfield
thresholds in Authorship. Open another semantic milestone only when a retained consumer demonstrates
the next missing backend-neutral cause.
