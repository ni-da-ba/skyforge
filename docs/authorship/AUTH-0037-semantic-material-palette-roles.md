# AUTH-0037 — Semantic Material Palette Roles and Selection Constraints

AUTH-0037 defines the backend-neutral semantic roles that a downstream material adapter may bind from the stable AUTH-0036 realization vocabulary.

It still does not choose concrete materials.

## Problem

AUTH-0036 gives backends a stable compositional realization contract.

A backend still needs an intermediate answer to a different question:

> Given this semantic material state, which kinds of palette entries are allowed to participate, and how strongly may optional roles express themselves?

Answering that in a Minecraft adapter would duplicate world-authorship policy inside backend code.

AUTH-0037 therefore defines semantic palette roles and selection constraints in the Skyforge world layer while leaving actual palette binding downstream.

## Roles

The first-generation semantic material palette contains five possible roles:

- PRIMARY_MATRIX;
- SECONDARY_MATRIX;
- ALTERATION_OVERPRINT;
- HYDROLOGIC_CONDITIONING;
- MINERAL_BEARING_STRUCTURE.

Every material-present sample requires exactly one PRIMARY_MATRIX.

All other roles are optional.

No role is a named rock, mineral, ore, block, registry object, texture, or backend material.

## Primary matrix

PRIMARY_MATRIX is always present for material.

Its source AUTH-0036 channel is whichever host-matrix channel is stronger:

- MASSIVE_MATRIX; or
- FABRIC_RICH_MATRIX.

PRIMARY_MATRIX is required and has expression ceiling 1.0.

This does not mean a backend must render a single concrete material with 100% coverage.

It means the primary semantic matrix cannot be removed by optional overlays.

## Secondary matrix

SECONDARY_MATRIX uses the weaker host-matrix channel.

It is eligible only when both conditions hold:

- weaker host support >= 0.18;
- weaker / stronger host support >= 0.28.

This prevents trivial traces of the weaker host channel from forcing a second palette entry everywhere.

Its expression ceiling is bounded to [0.18, 0.48].

The secondary role may therefore enrich the primary host fabric without becoming a second unconstrained dominant matrix.

## Conditioned roles

ALTERATION_OVERPRINT is eligible when:

- ALTERATION_OVERPRINT support >= 0.22.

HYDROLOGIC_CONDITIONING is eligible when:

- WATER_CONDITIONING support >= 0.24.

MINERAL_BEARING_STRUCTURE is eligible when:

- MINERAL_BEARING_STRUCTURE support >= 0.20.

The conditioned roles retain their exact AUTH-0036 source channel.

They never become required.

Their expression ceilings are bounded below 0.60:

- alteration <= 0.56;
- hydrologic <= 0.48;
- mineral-bearing structure <= 0.34.

These ceilings deliberately prevent conditioned roles from wholesale replacing the primary matrix.

They are semantic constraints, not final placement probabilities.

## Expression ceilings

A candidate's expressionCeiling answers:

> What is the maximum local share/strength a downstream realization may assign to this semantic role without violating native authorship intent?

The ceiling does not prescribe:

- random selection;
- exact block count;
- dithering pattern;
- texture blend equation;
- voxel placement.

A backend may express less than the ceiling.

It may not exceed the ceiling without explicitly leaving the AUTH-0037 contract.

## Provenance

Every palette-role selection retains:

- ownership/material presence;
- local AUTH-0034 assemblage id and kind;
- active AUTH-0035 contact id and kind when present;
- role;
- exact AUTH-0036 source channel;
- support;
- expression ceiling;
- required/optional status.

This allows downstream bindings to remain coherent by assemblage and contact instead of making unrelated per-voxel palette decisions.

AUTH-0037 does not itself define a binding key or registry handle.

## Contact behavior

AUTH-0037 does not add a special contact material role.

Contacts already modify the AUTH-0036 realization channels continuously.

Candidate eligibility therefore changes only when the blended semantic evidence itself crosses a role threshold.

This avoids inventing generic "contact blocks" while still allowing assemblage transitions to change palette-role eligibility naturally.

## Material-presence boundary

AUTH-0037 inherits AUTH-0036 ownership and material presence exactly.

Therefore:

- unowned space has no palette candidates;
- AUTH-0030 authored cave void has no palette candidates;
- every material-present sample has exactly one required PRIMARY_MATRIX.

## Adapter contract

A downstream adapter may:

1. sample AUTH-0037;
2. bind each eligible semantic role to one or more backend-specific candidate materials;
3. respect required status and expression ceilings;
4. apply a backend-specific spatial realization strategy.

A downstream adapter may not infer unsupported roles or exceed AUTH-0037 expression ceilings while claiming conformance to the semantic contract.

Concrete binding remains intentionally separate.

## Evidence

The authorship-semantic-material-palette-roles-v1 corpus uses the canonical six subsurface representatives at semantic depth 0.52.

Each specimen renders:

- PRIMARY — required primary host source, massive or fabric-rich;
- SECONDARY — optional secondary host eligibility weighted by support;
- ALTERATION — alteration role eligibility weighted by support;
- WATER — hydrologic role eligibility weighted by support;
- MINERAL — mineral-bearing structure eligibility weighted by support;
- ROLE COUNT — number of eligible semantic roles.

manifest.csv records:

- material sample count;
- massive-primary and fabric-primary counts;
- eligible sample counts for every optional role;
- mean candidate count;
- mean expression ceiling for every optional role.

## Acceptance gate

Reject AUTH-0037 if:

- ownership/material presence diverges from AUTH-0036;
- authored cave void receives palette candidates;
- material-present samples lack exactly one required PRIMARY_MATRIX;
- PRIMARY_MATRIX is sourced from a non-host channel;
- SECONDARY_MATRIX appears without both support and relative-support gates;
- conditioned roles appear below their AUTH-0036 support thresholds;
- conditioned roles lose their source-channel identity;
- optional expression ceilings reach or exceed 0.60;
- candidate roles duplicate within one sample;
- AUTH-0034/AUTH-0035 provenance is lost;
- canonical representatives collapse to one identical eligibility signature;
- named rocks, minerals, ores, Minecraft blocks, registry keys, or backend APIs enter the world layer.

## Parallel implementation boundary

AUTH-0037 changes no cave/carver/persistence/mutation contract and no Minecraft block placement behavior.

The implementation lane may continue independently.

AUTH-0037 is suitable as an input to a later backend-material binding milestone, but it should not itself import backend registries.

## Next milestone

If AUTH-0037 is accepted, the next native-authorship milestone should define **palette-binding coherence domains and stable binding keys**.

That milestone should answer how long a backend-specific binding persists across:

- one assemblage;
- neighboring assemblages;
- contact transitions;
- island-local conditioned regions;

without moving concrete backend registry objects into the Skyforge world model.
