# AUTH-0098 — Petroleum-System Geological Opportunity

## Purpose

The accepted resource-geography direction places petroleum in mature R3 industry rather than the
first-flight bootstrap.

The retained design explicitly requires:

```text
hydrocarbon-capable subsurface context
-> petroleum deposit eligibility
```

and expects petroleum concentration to create oilfield/refinery/fuel-depot/freight geography.

AUTH-0093 deliberately excluded petroleum from base-metal opportunity and said a later Authorship
milestone should add another geological resource family only when a concrete retained resource
requires a distinct cause.

AUTH-0098 closes exactly that geological gap.

## Boundary

AUTH-0098 authors **petroleum-system opportunity**.

It does not author literal oil deposits.

The public producer accepts exactly one authored `SkyIslandDescriptor` and internally reuses:

- the exact AUTH-0033 `SkyIslandMaterialFamilyPlan`;
- AUTH-0022 geology at each exact AUTH-0033 cell position;
- AUTH-0031 subsurface material character at each exact AUTH-0033 cell position;
- descriptor temperature tendency and semantic depth;
- one new broad coherent organic-source affinity subordinate to accepted layered host support.

Callers cannot supply:

- a custom subsurface grid;
- arbitrary source/reservoir/seal cells;
- an eligibility threshold;
- petroleum availability class;
- deposit count;
- deposit geometry or volume;
- Minecraft/mod fluid or block identity.

## Existing planning lattice

AUTH-0098 preserves the exact AUTH-0033 planning lattice:

```text
horizontal grid: 25 x 25
depth samples:   13
semantic depth:  0 .. 1
```

Only active AUTH-0033 host cells receive petroleum-system evidence. Authored cave void and unowned
space remain absent from the source plan and cannot become petroleum opportunity.

## Source potential

Petroleum source potential is subordinate to accepted:

```text
AUTH-0033 LAYERED_FABRIC_RICH_HOST
```

The broad source compatibility combines:

- water-conditioned host;
- intact material matrix;
- deterministic organic-source affinity;
- first-generation burial/temperature maturity proxy.

The final source value is multiplicatively gated by layered-host support.

Therefore:

```text
sourcePotential <= layeredFabricRichHost
```

and organic affinity cannot create free-floating hydrocarbon source noise.

The maturity proxy is deliberately coarse. It is not a thermal-history/basin-maturation simulator.

## Reservoir potential

Reservoir opportunity is subordinate to accepted connected permeability.

Compatibility additionally considers:

- matrix integrity;
- strongest accepted host fabric;
- water-conditioned host.

Therefore:

```text
reservoirPotential <= connectedPermeability
```

This is pore/connectivity planning evidence, not measured porosity, saturation, pressure, or flow rate.

## Seal potential

Seal opportunity favors:

- coherent massive host;
- bulk competence;
- low connected permeability.

Therefore:

```text
sealPotential <= 1 - connectedPermeability
```

It is a relative sealing-context proxy, not a named cap-rock taxonomy.

## Vertical petroleum-system opportunity

AUTH-0098 does not treat one favorable cell as a petroleum system.

For every exact AUTH-0033 host cell used as a local reservoir candidate:

1. deeper source support is the strongest deeper source potential in the same x/z planning column,
   deterministically decayed by integer depth-sample distance;
2. shallower seal support is the strongest shallower seal potential in the same column with the same
   distance rule;
3. system opportunity is:

```text
reservoirPotential
* deeperSourceSupport
* shallowerSealSupport
```

This guarantees:

- no reservoir -> zero system opportunity;
- no deeper source -> zero system opportunity;
- no shallower seal -> zero system opportunity;
- system opportunity cannot exceed any of the three component supports.

This is a deliberately small vertical source/reservoir/seal juxtaposition model. It is not a
structural-trap solver, migration simulator, pressure model, or sedimentary-basin simulation.

## Organic-source affinity

AUTH-0098 adds one low-frequency deterministic organic-source affinity.

It is:

- derived from stable authorship seed plus an AUTH-0098 domain constant;
- sampled only at exact AUTH-0033 cell positions;
- expressed in normalized island/depth coordinates;
- broad enough to avoid block-scale deposit noise;
- strictly subordinate to accepted layered host support.

Its purpose is to let otherwise similar layered host regions differ coherently in source opportunity
without inventing named rocks or backend deposits.

## Scale covariance

The new affinity and maturity terms use normalized horizontal coordinates and semantic depth.

For a descriptor changed only by nominal radius:

- exact AUTH-0033 cell identity/order remains unchanged;
- normalized source/reservoir/seal/system values remain unchanged;
- physical spacing remains upstream AUTH-0033 evidence.

AUTH-0098 therefore does not decide physical deposit scale.

## Profile semantics

The profile retains:

- exact AUTH-0033 source plan;
- every active host cell in exact source order;
- per-cell source potential;
- per-cell reservoir potential;
- per-cell seal potential;
- deeper source support;
- shallower seal support;
- final petroleum-system opportunity;
- mean source/reservoir/seal/system statistics;
- peak system opportunity;
- count of cells with nonzero system opportunity.

A nonzero system value is geological planning evidence only.

It is **not** a guarantee that a literal recoverable petroleum deposit exists.

## Downstream ownership

### Content / Experience

Owns:

- R3 / STRATEGIC_NODE availability;
- province selection/replanning;
- petroleum progression timing;
- refinery/fuel/logistics role;
- trade/salvage alternatives;
- route pressure and freight consequences;
- whether any hard guarantee exists.

### Implementation

Owns:

- concrete petroleum resource/block/fluid identity;
- deposit geometry/count/thickness/volume;
- pressure/depletion/extraction mechanics;
- pumpjack/refinery integration;
- exact worldgen/placement;
- persistence and lifecycle.

### Authorship

Owns only the backend-neutral geological opportunity and provenance defined here.

## Explicit non-contract

AUTH-0098 defines no:

- crude-oil registry id;
- oil block/fluid;
- reserve or recoverable volume;
- physical saturation;
- pressure;
- grade/API gravity;
- deposit thickness;
- deposit count;
- pumpjack site;
- refinery site;
- STRATEGIC_NODE frequency;
- province guarantee;
- trade/salvage quantity;
- Minecraft/mod worldgen.

## Acceptance

Reject AUTH-0098 if:

- exact AUTH-0033 cell provenance/order is lost;
- a second subsurface planning grid is introduced;
- source potential exceeds layered-host support;
- reservoir potential exceeds connected permeability;
- seal potential exceeds low-permeability support;
- system opportunity survives without local reservoir, deeper source, or shallower seal support;
- organic affinity can create opportunity outside accepted host/material/geology support;
- radius-only scaling changes normalized opportunity;
- backend deposit identity, availability tier, reserves, extraction, or progression policy enters
  Authorship.

## Evidence

Evidence identity:

```text
authorship-petroleum-system-opportunity-v1
```

Files:

- `index.html`;
- `atlas.png`;
- `manifest.csv`;
- `profiles.csv`;
- `cells.csv`.

Proof panels:

- `AUTH0033_PROVENANCE`;
- `COMPONENT_GATING`;
- `VERTICAL_SYSTEM`;
- `DETERMINISTIC`;
- `SCALE_COVARIANT`;
- `NO_RESOURCE_POLICY`.

## Next boundary

AUTH-0098 is island-scale geological opportunity only.

If a concrete Content planner next needs province-level petroleum selection, a regional inventory may
aggregate exact AUTH-0098 profiles through AUTH-0087 provenance in the same pattern as AUTH-0094.

Do not add that wrapper merely for completeness. Do not realize oil deposits in Authorship.
