# AUTH-0033 — Semantic Material Families

AUTH-0033 interprets the continuous material character of AUTH-0031 and the connected overlapping material domains of AUTH-0032 as a small backend-neutral semantic vocabulary.

The milestone does not choose named rock species, mineral species, ores, Minecraft blocks, registry keys, or backend palettes.

## Problem

AUTH-0031 answers how host material behaves at a point.

AUTH-0032 answers where coherent altered, saturated, mineralized, and structural-fabric systems occur.

Neither layer yet gives downstream realization code a compact semantic answer to the question:

What broad kind of host material is this position expressing?

AUTH-0033 supplies that vocabulary without collapsing the overlapping geological systems into one categorical label.

## Dependency

~~~text
AUTH-0031 continuous material character
    + AUTH-0032 overlapping mesoscale material domains
        -> AUTH-0033 semantic material families
            -> coherent massive host
            -> layered / fabric-rich host
            -> strongly altered host
            -> water-conditioned host
            -> mineral-bearing structural host
        -> future coherent lithologic assemblages and contacts
        -> future backend material realization
~~~

## Vocabulary

### Coherent massive host

A broad host-fabric affinity supported by:

- matrix integrity;
- inverse alteration;
- inverse structural-fabric-domain membership.

This is the ordinary competent-host tendency. It is intentionally not a named rock.

### Layered / fabric-rich host

A host-fabric affinity that requires AUTH-0032 structural-fabric-domain membership.

Support combines:

- matrix integrity;
- inverse alteration;
- structural-fabric-domain membership.

A mathematical carrier cannot create this family outside the accepted AUTH-0032 structural domain.

### Strongly altered host

A conditioned-host affinity that requires AUTH-0032 altered-zone membership.

Support combines:

- AUTH-0031 alteration;
- AUTH-0032 altered-domain membership;
- inverse matrix integrity;
- local cave-wall alteration.

The family therefore remains a geological condition of actual host material rather than a decorative surface palette.

### Water-conditioned host

A conditioned-host affinity that requires AUTH-0032 saturated-body membership.

Support combines:

- AUTH-0031 saturation;
- AUTH-0032 saturated-domain membership;
- host integrity.

This describes a water-conditioned material regime. It does not prescribe water blocks, aquifer fluids, or a backend wetness effect.

### Mineral-bearing structural host

A conditioned-host affinity that requires AUTH-0032 mineralized-body membership.

Support combines:

- AUTH-0031 mineralization tendency;
- AUTH-0032 carrier-gated mineralized-domain membership;
- overlap with structural-fabric or altered systems;
- host integrity.

The word mineral-bearing is semantic. AUTH-0033 still chooses no mineral species and no ore block.

## Family roles

The vocabulary distinguishes two broad roles.

HOST_FABRIC describes the ordinary structural identity of solid host material:

- coherent massive host;
- layered / fabric-rich host.

CONDITIONED_HOST describes secondary geological states that may overlap either host-fabric interpretation:

- strongly altered host;
- water-conditioned host;
- mineral-bearing structural host.

The role distinction prevents later code from treating water conditioning or alteration as if either were a complete lithology.

## Planning representation

AUTH-0033 reuses the AUTH-0032 25 x 13 x 25 semantic planning lattice.

Every AUTH-0033 cell is an AUTH-0031 material-present host cell.

Authored cave void and unowned positions do not receive family interpretations.

The plan covers exactly the active AUTH-0032 host-material planning volume.

## Composition rule

Material families are affinities, not exclusive labels.

One host-material cell may simultaneously express, for example:

- coherent massive host;
- water-conditioned host;
- mineral-bearing structural host.

Likewise, a structural-fabric region may remain partly massive while acquiring a strong layered/fabric-rich affinity.

No dominant-family field is stored.

Any later categorical backend choice must be a downstream realization decision made from the full semantic state.

## Domain grounding

Secondary semantic families are deliberately gated by AUTH-0032.

A cell cannot express:

- layered/fabric-rich host without structural-fabric-domain membership;
- strongly altered host without altered-zone membership;
- water-conditioned host without saturated-body membership;
- mineral-bearing structural host without mineralized-body membership.

This preserves the geological geography already accepted in AUTH-0032 and prevents AUTH-0033 from reintroducing independent local noise.

## Evidence

The authorship-semantic-material-families-v1 corpus uses the canonical six subsurface representatives:

- competent massif key 2332;
- weak basin key 653;
- permeable lobed key 1051;
- hydrologic massif key 2211;
- eroded tableland key 1439;
- spine key 3670.

Each specimen renders x/z maximum-affinity projections through semantic depth for:

- MASSIVE;
- FABRIC;
- ALTERED;
- WATER;
- MINERAL;
- COMPOSITE.

COMPOSITE blends overlapping affinities for review only. It is not a stored categorical material map.

manifest.csv records, for every family:

- the number of cells exceeding the evidence expression threshold;
- mean affinity over all active host cells;
- peak affinity.

## Acceptance gate

Reject AUTH-0033 if:

- any family cell occupies authored cave void or unowned material;
- the AUTH-0033 active host volume diverges from AUTH-0032;
- a conditioned family appears outside its required AUTH-0032 material domain;
- active host material loses all host-fabric interpretation;
- family interpretation becomes one mutually exclusive categorical label per cell;
- canonical geological representatives collapse to nearly identical material-family response;
- mineral-bearing material appears independently of AUTH-0032 carrier-gated mineralized bodies;
- the evidence is dominated by checkerboard, rectangular, or high-frequency per-cell noise;
- named rock species, named mineral species, ore blocks, Minecraft materials, registry keys, or backend APIs enter the world layer.

## Parallel implementation boundary

The implementation lane may consume already accepted cave and material-character contracts.

AUTH-0033 introduces no Minecraft block-selection contract.

No implementation task should map these semantic families directly to block palettes until a later authorship milestone defines a stable backend realization boundary.

## Next milestone

If AUTH-0033 is accepted, the next native-authorship milestone should establish coherent lithologic assemblages and contacts.

That layer should use the overlapping AUTH-0033 family affinities to author larger-scale material units, transitions, and structural relationships while preserving the same backend-neutral vocabulary.

Specific rock names, mineral species, and Minecraft palettes should remain downstream.
