# AUTH-0032 — Mesoscale Subsurface Material Domains

AUTH-0032 converts the continuous material tendencies accepted in AUTH-0031 into connected mesoscale material systems.

The milestone remains backend-neutral. It does not choose named rock types, mineral species, ore blocks, loot, Minecraft materials, or backend palettes.

## Dependency

~~~text
AUTH-0022 continuous geology
    -> AUTH-0023 mesoscale geological systems
    -> AUTH-0031 continuous material character
        -> matrix integrity
        -> alteration
        -> saturation
        -> mineralization tendency
        -> cave-wall alteration
    -> AUTH-0032 connected overlapping material domains
        -> altered zones
        -> saturated host bodies
        -> mineralized bodies
        -> structural-fabric domains
    -> future rock/mineral semantic families
    -> backend realization
~~~

## Why domains are downstream of continuous material character

AUTH-0031 answers what one host-material point tends to be like.

That is insufficient for authored geological geography.

A useful world needs systems that can be reasoned about as regions:

- a weathered zone crossing an island shoulder;
- a saturated body occupying a coherent subsurface lens;
- a mineralized structure following fracture-supported host material;
- a strong structural-fabric domain that can later influence strata or rock-family realization.

AUTH-0032 therefore groups supported host-material cells into connected mesoscale systems.

## Planning representation

The first-generation planner uses the same semantic scale as AUTH-0023:

- 25 x 13 x 25 deterministic island-local planning lattice;
- x/z span nominal island radius;
- semantic depth spans 0 through 1;
- only AUTH-0031 material-present cells are active.

The grid is a planning/evidence representation, not a backend voxelization contract.

Authored AUTH-0030 cave void is inactive and cannot participate in a material domain.

## Overlapping domains

Material-domain kinds are intentionally non-exclusive.

A host-material cell may simultaneously belong to several domain kinds.

For example, a fractured saturated altered zone may also support mineralization.

AUTH-0032 does not collapse these systems into one categorical material label.

### Altered zones

Altered-zone membership derives from:

- AUTH-0031 alteration;
- inverse matrix integrity;
- shallow semantic-depth weathering support.

The threshold responds to descriptor erosion maturity and rock competence.

This lets highly eroded/weak material develop larger connected altered domains without turning all shallow host material into one automatic shell.

### Saturated host bodies

Saturated-body membership derives from:

- AUTH-0031 saturation;
- AUTH-0022 groundwater potential;
- AUTH-0022 connected permeability.

The threshold responds to island hydrological potential and permeability.

A wet but impermeable island therefore does not automatically become one saturated body, and a permeable but dry island does not either.

### Mineralized bodies

Mineralization is deliberately carrier-gated.

AUTH-0032 first computes a geological support base from:

- AUTH-0031 mineralization tendency;
- AUTH-0022 fracture intensity;
- AUTH-0031 alteration.

That support is then modulated by a small deterministic family of oblique mineral carriers with bounded depth extent.

The carrier alone cannot create a mineralized body.

The continuous AUTH-0031 tendency alone also does not automatically become a whole-island mineral field.

This creates coherent fracture/hydrothermal-style mineralization bodies without reverting to independent per-block ore noise.

### Structural-fabric domains

Structural-fabric domains identify coherent, relatively intact host material suitable for later strata/fabric/rock-family realization.

Membership derives from:

- AUTH-0031 matrix integrity;
- inverse alteration;
- a small deterministic family of broad oblique fabric carriers.

The carriers are subordinate to actual host integrity.

Weak, heavily altered material therefore cannot become a strong structural-fabric domain solely because a mathematical carrier passes through it.

## Carrier counts

Mineral-carrier count responds to:

- permeability;
- hydrological potential;
- erosion maturity.

Fabric-carrier count responds to:

- rock competence;
- inverse erosion maturity.

Both remain in the first-generation range 1–3.

Carrier geometry is deterministic from island authorship identity.

## Mesoscale localization

Absolute geological thresholds remain the first gate.

If one material condition is so broadly elevated that it would occupy most of the host interior, AUTH-0032 keeps only the strongest deterministic support cells up to a first-generation maximum of 78% of active host-material planning volume.

This is not percentile normalization of weak material:

- cells below the absolute semantic threshold remain excluded;
- the relative localization rule can only remove already-qualified cells;
- it cannot promote low-support material into a domain.

The rule exists because a mesoscale domain must remain geographically discriminating even on an island whose overall alteration, saturation, or structural integrity is high.

## Connected-component filtering

Each domain kind is flood-filled independently using face-adjacent planning cells.

Components smaller than five cells are discarded.

This prevents threshold flecks from becoming authored material systems.

Domain identifiers are deterministic within each kind.

## Evidence

The `authorship-mesoscale-material-domains-v1` corpus uses the canonical six subsurface representatives:

- competent massif key 2332;
- weak basin key 653;
- permeable lobed key 1051;
- hydrologic massif key 2211;
- eroded tableland key 1439;
- spine key 3670.

Each specimen renders x/z maximum-membership projections through semantic depth:

- ALTERED;
- SATURATED;
- MINERALIZED;
- FABRIC;
- COMPOSITE PLAN — overlap of all four material systems.

Using plan projections preserves naturalized island morphology in the evidence instead of making broad three-dimensional systems appear as rectangular maximum-through-z section bands.

White remains nonparticipating host/unowned/void evidence background.

The interpolated projections are evidence views, not stored material geometry.

`manifest.csv` records:

- active host-material planning-cell count;
- carrier counts;
- connected-domain count for each kind;
- total participating cell count;
- largest connected-domain size;
- participating-cell coverage fraction relative to active host material.

## Acceptance gate

Reject AUTH-0032 if:

- any domain includes authored cave void or unowned material;
- components smaller than the accepted minimum survive;
- domain components are disconnected;
- all four kinds become mutually exclusive categorical labels;
- any domain kind exceeds the accepted 78% mesoscale support fraction of active host-material planning volume;
- mineralization appears without meaningful AUTH-0031 mineralization support;
- mineralized bodies become high-frequency specks or generic uniform ore sheets;
- saturated bodies ignore groundwater/permeability common causes;
- altered zones ignore erosion/weakness common causes;
- structural-fabric domains ignore host integrity;
- obvious grid-aligned rectangular regions dominate the evidence;
- every island produces nearly identical material-domain geography;
- named rocks, ores, Minecraft blocks, registry keys, or backend APIs enter the world layer.

## Parallel implementation boundary

SF-IMP-0065 may continue the sealed authored-cave Minecraft realization proof.

AUTH-0032 changes no cave geometry, carve field, or backend mutation contract.

No implementation agent should map AUTH-0032 directly to Minecraft blocks until the semantic material-domain tranche has an accepted downstream realization boundary.

## Next milestone

If AUTH-0032 is accepted, the next Skyforge-native milestone should define a **small semantic lithologic/mineral family vocabulary** that can interpret these overlapping material domains without collapsing them into Minecraft-specific materials.

That vocabulary should describe authored material identity in backend-neutral terms such as:

- coherent massive host;
- layered/fabric-rich host;
- strongly altered host;
- water-conditioned host;
- mineral-bearing structural host.

Specific mineral species and backend block palettes should remain one layer further downstream unless the evidence shows that the semantic families need that specificity.
