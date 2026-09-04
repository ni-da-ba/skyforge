# AUTH-0034 — Coherent Lithologic Assemblages and Contacts

AUTH-0034 converts the overlapping semantic material-family affinities accepted in AUTH-0033 into connected authored lithologic assemblages and explicit contact relationships.

The milestone remains backend-neutral.

It does not choose named rock species, mineral species, ore blocks, Minecraft materials, registry keys, or backend palettes.

## Problem

AUTH-0033 deliberately keeps material identity compositional.

That is necessary, but downstream realization also needs a larger-scale authored answer to two questions:

1. Which neighboring host-material cells belong to the same coherent material unit?
2. What kind of semantic transition exists where two such units meet?

AUTH-0034 provides those units and contacts without pretending that the unit label is itself a finished rock or block choice.

## Dependency

~~~text
AUTH-0031 continuous material character
    -> AUTH-0032 mesoscale material domains
        -> AUTH-0033 overlapping semantic material families
            -> AUTH-0034 coherent lithologic assemblages
                -> explicit assemblage contacts
                -> retained complete family state per cell
            -> future continuous contact realization
            -> future named lithologic/mineral vocabularies if needed
            -> future backend palettes
~~~

## Assemblage vocabulary

AUTH-0034 uses five backend-neutral assemblage interpretations:

- MASSIVE_HOST_UNIT;
- FABRIC_RICH_HOST_UNIT;
- ALTERED_HOST_UNIT;
- WATER_CONDITIONED_HOST_UNIT;
- MINERAL_BEARING_STRUCTURAL_UNIT.

These names describe the dominant semantic reason that a connected planning region is authored as one material unit.

They do not erase the complete AUTH-0033 state.

Every assemblage cell retains all five AUTH-0033 affinities.

A later backend therefore remains free to realize a water-conditioned mineral-bearing fabric-rich cell from the full semantic state even when the AUTH-0034 planning unit is classified as mineral-bearing structural.

## Why a unit label is allowed downstream of AUTH-0033

AUTH-0033 correctly forbids a dominant-family field at the affinity layer.

AUTH-0034 has a different responsibility.

It is a mesoscale composition layer whose purpose is to establish coherent units and boundaries.

A unit interpretation is therefore allowed, provided:

- the complete AUTH-0033 affinity vector is retained;
- the label remains semantic rather than material-specific;
- conditioned units cannot appear without their accepted AUTH-0033 support;
- the label is not treated as a backend block palette.

## Initial classification

Every active AUTH-0033 host cell receives a provisional assemblage interpretation.

Massive host is the ordinary fallback.

Fabric-rich host requires meaningful layered/fabric-rich affinity.

Altered, water-conditioned, and mineral-bearing structural units require their corresponding AUTH-0033 conditioned-host affinity to exceed an absolute semantic threshold.

Conditioned affinity may therefore displace the ordinary host-fabric interpretation where it is sufficiently strong, but weak values are never normalized upward.

## Coherence filtering

AUTH-0034 flood-fills face-connected provisional cells of the same assemblage kind.

Small specialized components below five planning cells revert to MASSIVE_HOST_UNIT.

This operation is deliberately one-way:

- it may remove a threshold fleck;
- it may not promote unsupported massive host into alteration, water-conditioning, mineralization, or fabric-rich material.

The final assemblages cover the complete AUTH-0033 active host-material planning volume exactly once.

Authored cave void remains absent.

## Contacts

A contact exists whenever two different final assemblages share one or more planning-cell faces.

All face adjacency between the same ordered assemblage pair is aggregated into one first-class contact.

Each contact records:

- first and second assemblage identifiers;
- number of shared planning faces;
- host-fabric contrast;
- alteration contrast;
- hydrologic contrast;
- mineralization contrast;
- one semantic contact kind.

The first-generation contact vocabulary is:

- GRADATIONAL_CONTACT;
- HOST_FABRIC_CONTACT;
- ALTERATION_FRONT;
- HYDROLOGIC_FRONT;
- MINERALIZATION_FRONT.

Contact kind is selected from the strongest normalized semantic contrast, with small biases that keep alteration and mineralization fronts legible.

A contact label is explanatory metadata over a measured transition. It does not prescribe a Minecraft boundary treatment.

## Structural meaning

AUTH-0034 establishes an explicit authored graph:

~~~text
assemblage
    -> contact
        -> neighboring assemblage
~~~

That graph is the first material-composition structure in Skyforge that can answer questions such as:

- Which altered unit borders this massive host?
- How much shared boundary exists?
- Is the transition primarily structural, hydrologic, alteration-driven, or mineralization-driven?
- Does one material system cross several host units?
- Which contacts should later be realized sharply and which should grade continuously?

The last question is intentionally deferred.

AUTH-0034 authors semantic contact identity, not final contact geometry.

## Evidence

The authorship-lithologic-assemblages-v1 corpus uses the canonical six subsurface representatives:

- competent massif key 2332;
- weak basin key 653;
- permeable lobed key 1051;
- hydrologic massif key 2211;
- eroded tableland key 1439;
- spine key 3670.

Each specimen renders:

- PLAN UNITS — x/z projection of coherent assemblage interpretation;
- SECTION UNITS — central x/depth section;
- CONTACTS — projected first-class contact relationships;
- ALTERATION — underlying AUTH-0033 altered-host affinity;
- WATER — underlying AUTH-0033 water-conditioned affinity;
- MINERAL — underlying AUTH-0033 mineral-bearing structural affinity.

The final three panels exist specifically to make it possible to audit whether the unit/contact map remains grounded in its semantic causes.

manifest.csv records:

- active host-material cells;
- total assemblages;
- total contacts;
- smallest and largest assemblage sizes;
- assemblage count by kind;
- contact count by kind.

## Acceptance gate

Reject AUTH-0034 if:

- any active AUTH-0033 host cell is omitted or assigned to more than one assemblage;
- authored cave void receives an assemblage;
- any assemblage is not face-connected;
- specialized units appear without nonzero supporting AUTH-0033 affinity;
- small specialized threshold flecks survive coherence filtering;
- contact pairs are duplicated or unordered;
- a contact exists without real face adjacency;
- real inter-assemblage face adjacency exists without a corresponding contact;
- canonical representatives collapse to one identical assemblage signature;
- unit maps become high-frequency checkerboards rather than mesoscale geography;
- contact classification ignores the measured AUTH-0033 family contrasts;
- named rocks, mineral species, ores, Minecraft materials, registry keys, or backend APIs enter the world layer.

## Parallel implementation boundary

AUTH-0034 changes no accepted cave geometry, Minecraft mutation contract, persistence format, or backend block-selection seam.

The implementation lane may continue consuming previously accepted contracts.

It should not map AUTH-0034 assemblage kinds directly to Minecraft block palettes.

## Next milestone

If AUTH-0034 is accepted, the next native-authorship milestone should define continuous contact realization.

That milestone should answer how a semantic contact becomes a finite-width transition, boundary surface, or graded zone while remaining subordinate to:

- neighboring assemblage identities;
- measured semantic contrast;
- local structure;
- cave exposure;
- semantic depth.

Named rock species and backend block palettes should still remain downstream unless the evidence proves that additional semantic specificity is required.
