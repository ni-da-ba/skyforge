# Structure–Authorship Interaction Policy v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design decision. Not yet an accepted ADR.

This document defines how structure site requirements interact with existing Skyforge authorship systems.

It is grounded in the accepted/current authorship model:

- naturalized island ownership propagates into ecology and hydrology;
- hydrology authors coherent channels and terrain influence;
- geology authors fracture/aquifer/void-prone domains;
- cave topology emerges from geological support rather than arbitrary tunnel noise;
- cave exposure is separately authored and may remain sealed;
- lithologic assemblages remain semantically caused by geology/material state;
- morphology families and hybrids remain independent sources of visible landform identity.

The structure system should preserve those causal relationships.

## Terminology: do not overload reservation

The active authorship lane already uses **support reservation** for proof-backed island/world support/query extents, including AUTH-0053 reservation preflight.

Structure planning needs a different concept.

Preferred terminology:

~~~text
StructureSitePlan
    -> OccupancyClaim
    -> SupportClaim
    -> ClearanceClaim
    -> ConnectivityClaim
    -> EnvironmentalRequirement
~~~

A **structure site claim** is a semantic/physical claim inside planned world composition.

It is not the same thing as SkyIsland support reservation metadata, which describes space reserved for island realization/proof.

## Governing rule

> Structures constrain the authored world at the weakest level necessary for coherent realization.

The normal priority is:

1. select a naturally compatible site;
2. condition ordinary island descriptor/morphology sampling;
3. locally negotiate with downstream systems;
4. directly reshape visible terrain only for explicitly exceptional structure-landform compositions.

## Interaction classes

For each authorship system, a structure claim may have one of five relationships:

### SELECT

The structure chooses sites where the authored system already supports it.

Example: mineshaft selects mineral-bearing geology.

### CONSTRAIN

The structure requires a range/capability before authorship is finalized.

Example: Stronghold requires enough connected interior mass.

### YIELD

The structure adapts to the authored system.

Example: village layout adapts to ordinary terrain relief.

### NEGOTIATE

Both systems retain meaning and locally adjust.

Example: a cave can route around a Trial Chamber core while preserving a deliberate breach connection.

### DOMINATE

The structure-landform concept intentionally supersedes ordinary local authorship.

Use sparingly.

Example: an exposed Ancient City landmark may own the central basin/cavern composition.

## Primary morphology

### Default relationship: CONSTRAIN + SELECT

Structures should not normally select one morphology family.

A structure may constrain:

- minimum scale;
- certified horizontal/vertical support;
- minimum connected interior capacity;
- availability of broad surface/cliff/water features.

Then normal Massif/Tableland/Spine/Basin/Lobed/hybrid authorship remains authoritative.

### Tier behavior

- Tier 0: YIELD completely.
- Tier 1: SELECT among compatible existing/planned islands.
- Tier 2: condition descriptor sampling; still retain morphology diversity.
- Tier 3: require a qualifying island to exist; morphology remains sampled from the compatible ordinary vocabulary.

### Exceptional DOMINATE cases

Only explicit landform-structure concepts should substantially bias visible primary form, e.g.:

- exposed Ancient City basin;
- monumental artificial/faction island;
- boss crater whose landform is the encounter.

Even then, use semantic tendencies rather than copying structure footprints into terrain.

## Secondary morphology and local surface character

### Default relationship: YIELD / NEGOTIATE

Secondary morphology should remain one of the major sources of island individuality.

Structure compatibility may require local traits such as:

- low-relief patch;
- cliff patch;
- ridge access;
- basin floor;
- protected shoulder;
- open approach corridor.

The structure site selector should first find those features in the normally authored island.

### Local suppression

Once a site is selected, a narrow claim may suppress or limit secondary relief only where it would make the structure physically impossible.

Examples:

- avoid placing a sharp spur through the floor of a village building;
- preserve a runway/airfield clearance strip;
- keep a cliff dock's approach corridor open.

Do not suppress secondary morphology across the entire island.

### Surface-supported structures

The existing SF-IMP fill-only accommodation remains the lowest-impact response.

If accommodation would require broad flattening/cutting, choose another site or promote the structure's terrain authority explicitly.

## Geology

### Default relationship: SELECT

Most structures should **consume geology, not rewrite it**.

Examples:

- mineshaft prefers mineral-bearing structural units;
- Deep Dark/Ancient City prefers appropriate deep/anomalous subsurface context;
- settlement quarry or mine selects useful material domains;
- underground structure may prefer competent host rock and low aquifer pressure.

The geology/material system should remain semantically truthful even after a structure is planned.

### Structure-required solid support

Embedded structures may place a SupportClaim requiring continuous host material around critical pieces.

This is a structural capacity constraint, not permission to relabel geology.

A Stronghold-bearing island may need more rock mass, but the resulting rock/assemblage composition remains authored by the geology/material pipeline.

### Anthropogenic materials

Concrete structure blocks are a later realization overlay.

They do not retroactively change Skyforge's neutral lithologic semantics.

## Cave topology

AUTH-0024 currently derives sparse cave systems from void-prone geologic regions and supported geological bridges.

### Default relationship: NEGOTIATE

Structure claims should become visible before final cave geometry while preserving the geological reasons caves exist.

The preferred behavior is:

~~~text
geology-derived cave-system intent
        +
structure occupancy/connectivity claims
        ↓
compatible cave topology/geometry
~~~

### Hard occupancy

Critical structure cores must not be destroyed by authored cave volume.

Rather than deleting an entire cave system because it crosses a structure claim, a later cave geometry stage should usually:

- steer passages around critical occupancy;
- choose another chamber realization inside the same supported geologic domain;
- omit one unsupported local branch;
- preserve the broader cave system if geological support remains.

### Deliberate intersections

ConnectivityClaims can explicitly request:

- cave breach into dungeon;
- cave-to-Stronghold access;
- mine/cave intersection;
- Ancient City cavern connection.

These intersections should be authored as relationships, not accidental clipping.

### Structure-specific cave policies

#### Stronghold
- critical portal/room envelope protected;
- incidental cave intersections outside critical geometry allowed;
- at least one discoverable access path desirable but not necessarily guaranteed through a natural cave.

#### Trial Chamber
- critical rooms/spawners/vaults protected;
- limited cave breach acceptable;
- avoid turning the whole chamber into an exposed cave shell.

#### Mineshaft
- cave intersections broadly desirable;
- secondary mine tunnels can yield to caves;
- mine topology should remain coherent.

#### Ancient City
- city/cavern relationship is co-authored;
- ordinary cave topology in the central claim may be replaced or subordinated to the landmark cavern;
- surrounding regional cave systems may connect into it.

This is a legitimate DOMINATE case at local cavern scale.

## Cave exposure

AUTH-0028 allows sparse authored exterior exposure and explicitly permits systems to remain sealed.

Structure connectivity must not cause every nearby cave system to become exposed.

Examples:

- dungeon with a cave connection does not imply surface exposure;
- mine may request one cliff/surface adit independently of natural cave exposure;
- Stronghold does not need an exterior cave mouth;
- exposed Ancient City obviously overrides ordinary buried-exposure expectations.

Natural cave exposure remains its own authored semantic decision unless a structure explicitly owns an artificial access route.

## Hydrology — surface channels and retained water

### Default relationship: SELECT + NEGOTIATE

Hydrology should remain physically meaningful.

A structure can prefer:

- dry ground;
- water access;
- river crossing;
- waterfall adjacency;
- protected harbor;
- standing water.

The first response is site selection, not deleting rivers.

### Surface settlement

Villages/towns should adapt to coherent drainage.

Preferred behaviors:

- roads bridge or follow channels;
- agriculture prefers water access without occupying flood-prone ground;
- docks select real water edges;
- buildings avoid standing-water footprints unless designed for them.

Do not erase a coherent authored watershed because a village wants flat space.

### Major infrastructure

Tier-2 cities/airfields may locally constrain channels or drainage only if the infrastructure role justifies it.

Even then, preserve watershed/outflow consistency through rerouting/culverts/bridges rather than simply deleting water semantics.

## Aquifers and groundwater

Geology already carries groundwater/aquifer support into cave semantics.

### Dry embedded structures

Trial Chambers, Strongholds, and ordinary dungeons should preferably SELECT sites with acceptable groundwater conditions.

If an otherwise good site intersects a strong aquifer:

1. prefer relocation within allowed policy;
2. prefer a different vertical/local placement;
3. only use explicit water-tolerant/flooded variants if the structure supports them;
4. avoid magical dry exclusion boxes that contradict the authored aquifer without explanation.

### Mines

Aquifer intersections can be meaningful:

- flooded galleries;
- drainage works;
- abandoned wet shafts.

### Ancient City

Water relationship should be a deliberate semantic choice. Ordinary city core should not be randomly half-flooded unless a variant is intentionally authored.

### Ocean Monument

Opposite rule: water volume is a hard EnvironmentalRequirement and ClearanceClaim.

## Hydrology above buried structures

A buried structure does not automatically constrain surface hydrology.

A river may flow above a Stronghold if:

- sufficient rock cover remains;
- no structure piece breaches the surface;
- hydrologic terrain influence does not violate required support.

This is desirable because it prevents hidden structures from leaving obvious surface signatures.

## Geologic materials around structures

AUTH-0034 lithologic assemblages and contacts should generally pass through structure host regions unchanged.

Potential structure responses:

- construction material palette may sample local geology downstream;
- mines may reveal assemblage contacts;
- ruins may use locally plausible stone palettes;
- altered/mineralized zones may influence salvage/resources.

Structure presence should not flatten the geologic assemblage map into a generic dungeon-stone semantic region.

## Structure claims and authored ores/resources

Mineshaft occurrence should be downstream of real resource semantics.

The structure may follow/seek mineralized units rather than causing ore deposits to appear solely because a mine exists.

Possible exception:

- tiny local residual resources or abandoned stockpiles in the built structure are loot/structure content, not geologic ore authorship.

## Interaction matrix

| System | Ordinary structure | Important landmark | Progression-critical |
|---|---|---|---|
| Primary morphology | SELECT/YIELD | CONSTRAIN | CONSTRAIN, must satisfy |
| Secondary morphology | YIELD/local NEGOTIATE | NEGOTIATE | NEGOTIATE |
| Geology | SELECT | SELECT/CONSTRAIN | CONSTRAIN support, preserve semantics |
| Cave topology | NEGOTIATE | NEGOTIATE/limited DOMINATE | NEGOTIATE around critical core |
| Cave exposure | Independent | contextual | independent unless access required |
| Surface hydrology | YIELD/SELECT | NEGOTIATE | normally independent |
| Aquifer | SELECT/relocate | semantic variant/NEGOTIATE | avoid conflict through site choice |
| Material assemblages | preserve | preserve | preserve |
| Ecology | site compatibility | structure may alter local ecology | local downstream effect |
| Threats | structure provenance | structure/anomaly provenance | structure provenance |

## Planning order refinement

The desired semantic order is not a single rigid linear pipeline.

A more accurate model is:

~~~text
WORLD / PROVINCE PLAN
        |
        +--> Tier-3 topology intents
        +--> selected Tier-2 landmark intents
        |
        v
COARSE ISLAND / GEOLOGY / HYDROLOGY CAPABILITIES
        |
        v
STRUCTURE SITE SEARCH / CONDITIONED SAMPLING
        |
        v
STRUCTURE SITE CLAIM SET
  occupancy / support / clearance / connectivity / environment
        |
        +-------------------+
        |                   |
        v                   v
DETAILED MORPHOLOGY      GEOLOGY / CAVE / HYDROLOGY
        |                   |
        +---------NEGOTIATE-+
                    |
                    v
EXACT COMPATIBILITY PROOF
                    |
                    v
MINECRAFT/MOD STRUCTURE REALIZATION
~~~

The semantic planner needs enough coarse authored information to choose a good site, while detailed authorship needs the selected structure claims to avoid incompatible realization.

This implies iterative/constraint-based planning rather than a naive strict one-pass ordering.

## Coarse-to-detailed contract

A future implementation should distinguish:

### Capability phase

Cheap neutral evidence that a candidate island/site *could* satisfy a structure:

- support certificate;
- size/depth class;
- hydrology summary;
- geology/aquifer summary;
- cliff/water capability;
- semantic region.

### Claim phase

A selected site creates concrete local claims.

### Detailed authorship phase

Caves/hydrology/secondary morphology resolve in awareness of those claims.

### Proof phase

Actual realized geometry is checked before native structure realization.

If proof fails:

- Tier 0/1 can relocate/fallback according to policy;
- Tier 2 can re-plan within its semantic region;
- Tier 3 requires deterministic re-plan/structure-seeded terrain rather than silent omission.

## Conflict philosophy

Avoid solving conflicts by simply giving structures highest priority.

Preferred order:

1. pick a naturally compatible site;
2. locally steer the lower-authority system;
3. preserve both systems through explicit connectivity/clearance;
4. relocate replaceable structure content;
5. re-plan conditioned terrain for high-tier content;
6. only use direct structure-dominant local authorship when the structure's world role explicitly justifies it.

## Acceptance examples

### Stronghold under river

PASS if:

- Stronghold remains contained;
- adequate cover exists;
- river remains hydrologically coherent;
- surface does not reveal a suspicious Stronghold-shaped platform.

### Trial Chamber beside cave system

PASS if:

- critical chamber volume survives;
- one cave breach may exist;
- cave system retains geological support and broader topology;
- chamber does not become an exposed hollow shell.

### Mineshaft in mineralized region

PASS if:

- mine correlates with real mineral-bearing geology;
- cave intersections occur naturally;
- mine does not create the ore field it supposedly exploits.

### Village across uneven terrain

PASS if:

- settlement network adapts to multiple local elevations;
- roads/bridges respect drainage;
- only bounded support accommodation occurs under buildings;
- entire island is not flattened.

### Buried Ancient City

PASS if:

- island morphology remains a normal large compatible family/hybrid;
- deep cavern/city relationship is intentional;
- surrounding cave systems can feed into the cavern;
- surface need not advertise the exact city footprint.

## Cross-agent consequence

The authorship lane does not need Minecraft structure identities.

It needs neutral claims and requirements.

The Minecraft implementation lane does not need to control morphology.

It needs to:

1. derive or associate neutral requirements from concrete structure content;
2. request semantic placement;
3. realize native/modded structures only after the selected site proves compatible.

## Acceptance principle

> A structure should feel as though it was built, buried, discovered, or ruined **within the same geological and hydrological world**, not pasted into a protected void carved out of that world.
