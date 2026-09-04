# AUTH-0035 — Continuous Lithologic Contact Realization

AUTH-0035 converts the discrete semantic contact graph accepted in AUTH-0034 into finite-width continuous transition fields.

The milestone remains backend-neutral.

It does not choose named rock species, mineral species, ore blocks, Minecraft materials, registry keys, or backend palettes.

## Problem

AUTH-0034 establishes coherent assemblages and first-class contacts between them.

Those contacts are still topological/discrete.

A backend-neutral realization layer now needs to answer:

- where does a contact influence nearby host material?
- how wide is the transition?
- how sharp is it?
- which assemblage lies on each side?
- which semantic contrast is expressed through the transition?
- how should local structure, cave exposure, and semantic depth modify that realization?

AUTH-0035 answers those questions without yet choosing any concrete material.

## Dependency

~~~text
AUTH-0033 overlapping material-family affinities
    -> AUTH-0034 coherent assemblages + semantic contact graph
        -> AUTH-0035 finite contact patches
            -> continuous compact-support transition field
            -> assemblage-side blend weights
            -> alteration / hydrologic / mineralization transition channels
            -> cave-exposure coupling
        -> future lithologic realization vocabulary
        -> future backend palettes
~~~

## Finite contact patches

Every real face adjacency contributing to an AUTH-0034 contact becomes one AUTH-0035 contact patch.

A patch records:

- parent contact id;
- ordered parent assemblage ids;
- face-normal axis;
- semantic contact center;
- which assemblage lies on the negative normal side;
- finite tangential face span;
- normalized half-width;
- transition sharpness;
- local structural influence;
- cave-exposure influence.

The patch is therefore directly grounded in real AUTH-0034 adjacency.

AUTH-0035 does not invent independent transition planes.

## Coordinate metric

Horizontal patch distances are normalized by nominal island radius.

Vertical distance remains semantic depth.

This gives AUTH-0035 a backend-neutral local metric that is independent of Minecraft block scale or any concrete world Y coordinate.

The planning lattice remains the source of the finite contact faces, but the realized field can be sampled continuously between those planning positions.

## Width

Each contact kind has a first-generation base half-width.

The realized patch width is then modified by:

- the parent contact's measured primary semantic contrast;
- local AUTH-0022 fracture intensity;
- local bulk competence;
- cave proximity from the accepted exterior-connected cave field;
- semantic depth.

Stronger contrast tends to narrow a transition.

Fracturing and cave exposure can broaden local expression.

The first-generation normalized half-width is clamped to:

- minimum 0.022;
- maximum 0.135.

The width remains semantic, not a block count.

## Sharpness

Patch sharpness derives from:

- parent semantic contrast;
- inverse realized width;
- local host competence;
- inverse cave exposure.

This separates the concepts of "where a transition has support" and "how abrupt that transition is."

AUTH-0035 does not yet use sharpness to select concrete materials.

It is authored semantic evidence for later realization.

## Compact continuous field

The field uses compact support around every finite patch.

Influence is exactly zero at and beyond 2.25 realized half-widths from the finite patch.

Within support, influence falls smoothly from the patch.

This avoids infinite low-amplitude tails and prevents a contact from becoming an island-wide material modifier.

At every material-present sample, the field reports the strongest local contact realization and preserves:

- parent contact kind;
- parent contact id;
- both assemblage ids;
- first/second assemblage blend weights;
- overall contact influence;
- host-fabric transition strength;
- alteration transition strength;
- hydrologic transition strength;
- mineralization transition strength;
- cave-exposure coupling.

Blend weights vary continuously across the contact normal and sum to one wherever a contact is active.

## Authored cave void

AUTH-0035 is a material transition layer.

It may use cave proximity to modify nearby host-material contacts, but it cannot place material transition inside authored cave void.

Unowned positions and authored cave void therefore report zero contact influence.

This preserves the accepted AUTH-0030/AUTH-0031 material-presence boundary.

## Overlap policy

Several finite patches may geometrically influence one sample.

The first-generation sample reports the strongest realized contact for provenance and transition channels.

This is not a claim that other nearby contacts cease to exist.

The full AUTH-0035 plan retains every contact and every patch.

A later realization layer may consume the full patch set if multi-contact blending proves necessary.

## Evidence

The authorship-continuous-contact-realization-v1 corpus uses the canonical six subsurface representatives:

- competent massif key 2332;
- weak basin key 653;
- permeable lobed key 1051;
- hydrologic massif key 2211;
- eroded tableland key 1439;
- spine key 3670.

Each specimen renders:

- MID-DEPTH PLAN — continuous contact influence at semantic depth 0.52;
- SECTION — continuous contact influence on a central x/depth plane;
- ALTERATION — alteration-transition channel;
- WATER — hydrologic-transition channel;
- MINERAL — mineralization-transition channel;
- CAVE — contact influence coupled to cave proximity.

manifest.csv records:

- contact count;
- contact-patch count;
- minimum / mean / maximum half-width;
- contact count by semantic kind;
- mean patch sharpness;
- mean cave-exposure influence.

## Acceptance gate

Reject AUTH-0035 if:

- any AUTH-0034 contact lacks a realization;
- any contact patch is not grounded in a real AUTH-0034 face adjacency;
- patch provenance disagrees with its parent contact;
- width or sharpness becomes non-deterministic;
- width escapes the accepted bounded semantic range;
- continuous contact material appears outside island ownership;
- authored cave void receives nonzero contact influence;
- patch-center host material does not show strong local contact influence;
- canonical representatives collapse to one identical contact-realization signature;
- transition fields become island-wide haze rather than finite contact zones;
- transition channels ignore the measured AUTH-0034 semantic contrasts;
- named rocks, mineral species, ores, Minecraft materials, registry keys, or backend APIs enter the world layer.

## Parallel implementation boundary

AUTH-0035 changes no Minecraft mutation contract, carver precedence rule, persistence format, or backend block-selection seam.

The implementation lane may continue consuming previously accepted contracts.

AUTH-0035 should not yet be mapped directly to Minecraft block palettes.

## Next milestone

If AUTH-0035 is accepted, the next native-authorship milestone should define a **backend-neutral lithologic realization vocabulary**.

That layer should combine:

- AUTH-0033 material-family affinities;
- AUTH-0034 assemblage identity;
- AUTH-0035 continuous contact influence and blend weights;

into a stable semantic realization contract suitable for backend adapters.

Named geological species should still be introduced only if they materially improve authored behavior rather than merely providing labels.
