# AUTH-0036 — Backend-Neutral Lithologic Realization Vocabulary

AUTH-0036 establishes the first stable semantic material-realization contract suitable for downstream backend adapters.

It composes accepted native-authorship work without introducing named rocks or backend material ids.

## Problem

AUTH-0033 supplies overlapping semantic material-family affinities.

AUTH-0034 supplies coherent assemblage identity.

AUTH-0035 supplies finite-width continuous contact influence and assemblage-side blend weights.

A backend adapter should not need to reconstruct those three layers independently before it can ask what semantic material character is intended at one position.

AUTH-0036 therefore provides one stable sampling contract.

## Vocabulary

The stable first-generation realization vocabulary contains five compositional channels:

- MASSIVE_MATRIX;
- FABRIC_RICH_MATRIX;
- ALTERATION_OVERPRINT;
- WATER_CONDITIONING;
- MINERAL_BEARING_STRUCTURE.

These are not mutually exclusive material classes.

They are semantic realization weights.

A material-present sample always has at least one nonzero host-matrix channel.

Conditioned channels may overlap freely.

No channel is a named rock, mineral species, ore, Minecraft block, registry key, or backend palette entry.

## Provenance contract

Every material-present sample preserves:

- nearest local AUTH-0034 assemblage id and kind;
- whether AUTH-0035 contact blending is active;
- parent contact id and kind when active;
- ordered first/second AUTH-0034 assemblage ids and kinds;
- continuous first/second assemblage blend weights;
- all five AUTH-0036 realization channels.

Outside contact support, the sample resolves completely to the local AUTH-0034 assemblage and retains the exact local AUTH-0033 family vector.

Inside contact support, the sample retains local provenance but blends toward the mean semantic family profiles of the two parent assemblages according to:

1. AUTH-0035 side weights;
2. AUTH-0035 contact influence.

This keeps the transition continuous while preserving local material character at the edge of compact contact support.

## Local character

AUTH-0036 does not replace AUTH-0033 with assemblage averages.

Away from realized contacts, the five output channels are exactly the local AUTH-0033 family values at the nearest authored planning cell.

Assemblage means are used only as the target state of an active AUTH-0035 transition.

This prevents coherent units from becoming internally uniform merely because they share an AUTH-0034 identity.

## Contact blending

For each channel:

~~~text
target =
    firstWeight * firstAssemblageMean
  + secondWeight * secondAssemblageMean

realized =
    lerp(localFamilyValue, target, contactInfluence)
~~~

This construction has three useful invariants:

- contact influence approaching zero returns the local AUTH-0033 state;
- crossing a contact continuously exchanges first/second assemblage weight;
- no channel can escape the normalized range because both endpoints are normalized semantic values.

AUTH-0036 does not add arbitrary contact-specific boosts.

The AUTH-0035 transition channels remain available upstream if a later layer needs explicit contrast emphasis.

## Spatial lookup

AUTH-0036 is continuously sampleable at semantic subsurface positions.

The local authored cell is the nearest active AUTH-0034 planning cell under an island-normalized horizontal plus semantic-depth metric.

This nearest-cell lookup is deterministic and tie-breaks by planning-cell index.

The lookup supplies provenance and local compositional character.

AUTH-0035 remains responsible for smoothing actual unit boundaries.

## Material-presence boundary

AUTH-0036 inherits AUTH-0031 material presence exactly.

Therefore:

- unowned space contains no realization state;
- AUTH-0030 authored cave void contains no realization state;
- material-present host contains the stable realization vocabulary.

No downstream backend should infer material where AUTH-0036 reports materialPresent=false.

## Adapter boundary

AUTH-0036 is the intended stable semantic input for future material backends.

A backend may eventually map the five channels plus provenance into:

- palette weights;
- texture families;
- block-state distributions;
- mesh/material parameters;
- simulation material parameters.

That mapping is explicitly downstream.

The world layer still contains no backend registry lookup and no Minecraft block decision.

## Evidence

The authorship-lithologic-realization-vocabulary-v1 corpus uses the canonical six subsurface representatives:

- competent massif key 2332;
- weak basin key 653;
- permeable lobed key 1051;
- hydrologic massif key 2211;
- eroded tableland key 1439;
- spine key 3670.

All panels sample semantic depth 0.52.

Each specimen renders:

- MASSIVE;
- FABRIC;
- ALTERATION;
- WATER;
- MINERAL;
- CONTACT.

CONTACT visualizes the degree to which both parent assemblages participate in an active AUTH-0035 transition.

manifest.csv records:

- material sample count;
- contact-active sample count;
- mean and peak value for every realization channel.

## Acceptance gate

Reject AUTH-0036 if:

- ownership or material presence diverges from AUTH-0031;
- authored cave void receives realization channels;
- material-present samples lose both host-matrix channels;
- non-contact planning-cell samples fail to preserve exact AUTH-0033 family character;
- contact samples lose valid AUTH-0034 parent provenance;
- contact blend weights do not sum to one;
- contact blending produces values outside [0,1];
- canonical representatives collapse to one identical realization signature;
- visualization shows conditioned channels appearing without upstream authored support;
- named rocks, mineral species, ores, Minecraft blocks, registry keys, or backend APIs enter the world layer.

## Parallel implementation boundary

AUTH-0036 does not change accepted Minecraft carver precedence, cave mutation, persistence, or block-placement behavior.

Implementation work may consume accepted earlier contracts independently.

The implementation lane should not map AUTH-0036 directly to Minecraft blocks until an explicit backend-material realization milestone is opened.

## Next milestone

If AUTH-0036 is accepted, the next native-authorship milestone should define **semantic material palette roles and selection constraints**.

That layer should specify how a backend can request coherent candidate material roles from the AUTH-0036 channels without embedding Minecraft registry objects in the Skyforge world model.

It should still separate:

- semantic role selection;
- backend palette binding;
- final spatial/block realization.
