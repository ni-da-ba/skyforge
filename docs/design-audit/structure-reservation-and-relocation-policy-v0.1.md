# Structure Reservation and Relocation Policy v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design decision. Not yet an accepted ADR.

This document defines how much authority a structure intent has over Skyforge world composition.

> Common structures adapt to the world; important structures negotiate with the world; exceptional structures may shape the world; progression-critical structures are guaranteed by the world.


## Morphology-authority clarification

This policy's references to terrain forcing or terrain influence mean **requirement satisfaction**, not direct structure-shaped terrain generation.

By default, a structure declares constraints such as minimum connected interior capacity, support, cover, surface area, cliff/water capability, clearance, or access. Skyforge remains free to satisfy those constraints with any compatible accepted morphology family or hybrid. See [Structure-to-Terrain Compatibility Contract v0.1](structure-terrain-compatibility-contract-v0.1.md).

Only explicitly exceptional content should directly bias visible morphology.

## Four authority tiers

### Tier 0 — Opportunistic

Common, incidental, replaceable structures.

Examples:

- guideposts;
- small ruins;
- caches;
- ordinary huts;
- tiny camps;
- isolated shrines;
- many minor decorative modded structures.

Policy:

~~~text
planning entry = late
relocation = free within compatible local sites
reservation = none or soft
terrain forcing = never
failure = omit or weighted fallback
~~~

Tier-0 structures must fit the world that already exists. Skyforge should not create a new island for a minor ruin.

### Tier 1 — Semantic-relocatable

Structures that should occur at roughly intended frequency, but do not need a specific coordinate.

Examples:

- ordinary villages;
- ordinary YUNG dungeons;
- mineshafts;
- pillager camps and forts;
- most Trial Chambers;
- regional temples;
- common faction infrastructure.

Policy:

~~~text
planning entry = semantic island/cluster phase
relocation = allowed within bounded semantic region
reservation = soft, then hard once site is chosen
terrain forcing = limited; usually no new island
failure = alternate site, alternate asset, or omit according to target frequency
~~~

These structures may be dragged to a nearby suitable island during semantic planning. They should not wait until chunk generation and then discover that the intended coordinate is void.

### Tier 2 — Landmark / exceptional

Rare structures important enough to shape a destination or regional identity.

Examples:

- Ancient City;
- major illager capital;
- exceptional monastery or fort;
- large boss site;
- major industrial ruin;
- exposed Ancient City;
- major ocean monument complex;
- important air/naval infrastructure.

Policy:

~~~text
planning entry = province/cluster planning
relocation = allowed, but must preserve regional meaning
reservation = hard
terrain forcing = yes when selected
failure = alternate regional site or structure-seeded terrain
~~~

Once Skyforge commits to one of these intents, the structure is part of world composition.

It may force a sufficiently large or thick island, alter local morphology, require a cavern, influence cave/hydrology planning, reserve an approach envelope, or suppress incompatible nearby structures.

Tier 2 does not mean every raw candidate survives. It means that once the semantic planner selects the landmark, downstream worldgen must respect it.

### Tier 3 — Required / progression-critical

Structures whose absence would break expected progression or a hard world contract.

Primary example:

- Stronghold / End portal progression.

Policy:

~~~text
planning entry = before island composition
relocation = only according to explicit topology contract
reservation = hard plus exclusion/clearance
terrain forcing = mandatory if no compatible terrain exists
failure = deterministic fallback or generation error; never silent omission
~~~

If a Stronghold topology point lands in empty sky, Skyforge authors a compatible island or cluster component around the required structure intent.

## Rarity is not authority

A structure may be rare but still disposable.

The real distinction is whether the semantic planner has committed to its meaning.

Example:

~~~text
Ancient City candidate
    |
    +--> not selected as regional landmark
    |      -> may disappear
    |
    +--> selected as Deep Dark landmark
           -> hard reservation
           -> island/cavern must now support it
~~~

Likewise, an ordinary village may move or disappear if settlement geography offers better alternatives. A provincial capital cannot.

## Occurrence signal versus final site

Skyforge should distinguish an occurrence signal from a final site.

An occurrence signal may come from:

- vanilla progression topology;
- mod structure-set frequency;
- Skyforge regional semantics;
- faction/history simulation;
- geology/resource semantics;
- exceptional-landmark sampling.

The signal says that the planner should consider a structure within some domain.

The final site is selected only after Skyforge knows enough about:

- island geometry;
- surface/interior support;
- ecology/climate;
- settlement/faction context;
- cave/hydrology constraints;
- route/horizon composition;
- other reservations.

This prevents raw Minecraft X/Z candidates from accidentally becoming world-design authority.

## Relocation classes

### FREE_SITE

May move to any compatible site inside the selected semantic region.

Use for ordinary ruins and replaceable content.

### LOCAL_DRIFT

May move within a nearby island/cluster neighborhood.

Useful when a structure should stay near its original occurrence signal.

### REGIONAL_DRIFT

May move anywhere inside the same province or large semantic region.

Useful for Trial Chambers, regional temples, and landmarks whose exact coordinate is unimportant.

### TOPOLOGY_PRESERVING

May move only according to an explicit topology-preserving transform or rule.

Use for Strongholds and future progression systems with navigation expectations.

### FIXED_ANCHOR

Exact position is authoritative.

Rarely desirable; reserve for content whose external contract truly requires it.

## Deterministic site search

Relocation must be deterministic.

Candidate site scoring should consider:

~~~text
distance from occurrence signal
semantic mismatch
surface/interior suitability
terrain modification cost
reservation conflicts
horizon composition
sky exposure / occlusion
route access
ecological conflict
~~~

Conceptually:

~~~text
site score
= suitability
- relocation cost
- modification cost
- composition penalty
- conflict penalty
~~~

Exact weights belong to later testing.

## Reservation types

### SOFT

Communicates preference while semantic planning is unresolved. May still move or disappear.

### HARD_OCCUPANCY

The final structure volume or footprint must remain available.

### CLEARANCE

Space that must remain free for entrances, aircraft approach, guardian navigation, cliff-dock clearance, or exterior structure geometry.

### SUPPORT

Terrain that must exist to support a surface, cliff, or embedded structure.

### CONNECTIVITY

Guarantees an intended access relationship such as a surface shaft, cave connection, road, dock, cliff entrance, or internal corridor.

A structure can own several reservation types at once.

## Reservations should be negotiated, not giant no-touch boxes

### Trial Chamber

~~~text
critical rooms        = HARD_OCCUPANCY
outer buffer          = SOFT/CLEARANCE
entrance paths        = CONNECTIVITY
surrounding rock      = SUPPORT
~~~

Authored caves may intersect permitted portions but must not erase critical rooms.

### Mineshaft

Main corridors and hubs should receive stronger occupancy protection than secondary tunnels. Cave intersections are desirable when coherent.

### Ancient City

The city and its cavern should be co-authored. The cavern is part of the landmark, not merely empty clearance around a pasted structure.

### Settlement

Buildings require support/occupancy. Roads, yards, farms, and docks need network/connectivity reservations. Do not flatten one giant rectangular settlement envelope.

## Terrain influence levels

Structure importance and terrain influence are related but separate.

### Level A — Fit only

Accept existing terrain or fail.

Typical Tier 0.

### Level B — Bounded accommodation

Minor foundations/support adaptation permitted.

Aligned with accepted SF-IMP surface accommodation.

### Level C — Local morphology influence

Terrain authorship may shape a plateau, basin, entrance, cliff anchor, or cavern around the structure.

Typical important Tier 1 and Tier 2.

### Level D — Structure-seeded terrain

The structure intent may cause a new island, waterbody, or major landform to be authored.

Typical Tier 2 and Tier 3.

## Representative assignments

| Structure | Default tier | Relocation | Terrain authority |
|---|---:|---|---|
| Guidepost / small ruin | 0 | FREE_SITE | A/B |
| Minor shrine / hut | 0 | FREE_SITE | A/B |
| Village | 1 | LOCAL/REGIONAL_DRIFT | B/C |
| Ordinary illager camp | 1 | LOCAL/REGIONAL_DRIFT | B |
| Major illager capital | 2 | REGIONAL_DRIFT | C/D |
| YUNG ordinary dungeon | 1 | REGIONAL_DRIFT | B/C |
| Mineshaft | 1 | REGIONAL_DRIFT | C |
| Trial Chamber | 1, promoted to 2 if regional frequency requires | REGIONAL_DRIFT | C/D |
| Stronghold | 3 | TOPOLOGY_PRESERVING | D |
| Ancient City, buried | 2 | REGIONAL_DRIFT | D |
| Ancient City, exposed | 2 | REGIONAL_DRIFT | D |
| Cliff dock | 1 | LOCAL_DRIFT | C |
| Major port / airfield | 2 | REGIONAL_DRIFT | C/D |
| Airship wreck | 0/1 | FREE/LOCAL | A/B |
| Ocean Monument | 2 in authored ocean regions | REGIONAL_DRIFT | C/D |

These are semantic defaults, not backend-neutral hard-coded structure IDs.

## Trial Chamber refinement

Trial Chambers demonstrate the value of promotion.

Default:

~~~text
candidate occurrence
-> search suitable islands in region
-> embed if possible
-> otherwise omit and use a later regional opportunity
~~~

But Skyforge may maintain a target regional frequency.

If a large enough region would otherwise contain none:

~~~text
regional frequency deficit
-> promote one Trial Chamber intent
-> hard reserve
-> structure-seed suitable terrain if necessary
~~~

This preserves availability without letting the raw candidate lattice dominate island composition.

## Ancient City refinement

Ancient Cities should be selected as Tier-2 landmarks before detailed island/cave generation.

Buried realization requests:

- huge island mass;
- major cavern;
- Deep Dark semantics;
- low ordinary cave/ecology pressure;
- access connections;
- city occupancy.

Exposed realization requests:

- horizon-scale island;
- exposed or partly buried city;
- basin, caldera, or eroded plateau morphology;
- constrained landing/access geometry.

The city influences terrain from the start rather than being pasted onto a completed island.

## Stronghold refinement

Stronghold reservation occurs before ordinary island placement in the affected region.

~~~text
derive vanilla-compatible Stronghold topology intent
        |
        v
reserve topology location / allowed adjustment envelope
        |
        v
query planned world composition
        |
        +--> compatible island can satisfy envelope
        |       -> bind to that island
        |
        +--> otherwise
                -> request Stronghold-bearing island
        |
        v
author terrain
        |
        v
realize native/modded Stronghold
~~~

## Conflict resolution

When reservations conflict:

1. higher importance tier wins;
2. progression topology beats optional regional content;
3. hard reservations beat soft reservations;
4. same-tier conflicts use deterministic site score/fallback ordering;
5. prefer relocation over destructive terrain compromise;
6. if two exceptional structures are both regionally important, reshape the cluster only if composition remains coherent.

Runtime chunk order must never decide reservation conflicts.

## Interaction with island overlap and sky exposure

Structure-seeded terrain still must obey:

- physical overlap admission;
- cluster spacing/composition;
- layering-without-roofing;
- habitable-surface sky exposure;
- route/horizon composition.

Tier 3 requires terrain to exist, not that the first naive island shape be accepted.

## Planning order

~~~text
WORLD / PROVINCE
    |
    +--> Tier-3 progression intents
    +--> selected Tier-2 landmarks
    |
    v
CLUSTER / ISLAND SEMANTIC PLAN
    |
    +--> Tier-1 occurrence signals
    +--> site search / relocation
    |
    v
RESERVATION FINALIZATION
    |
    +--> occupancy
    +--> support
    +--> clearance
    +--> connectivity
    |
    v
MORPHOLOGY / CAVES / HYDROLOGY / GEOLOGY
    |
    v
LATE TIER-0 STRUCTURE FITTING
    |
    v
CONCRETE MINECRAFT/MOD STRUCTURE REALIZATION
~~~

This lets important structures influence worldgen while ordinary structures simply consume available sites.

## Avoid structure-shaped terrain

A reservation is a constraint, not permission to create visibly artificial landforms.

Bad:

~~~text
Stronghold footprint
-> exact cuboid island
~~~

Better:

~~~text
Stronghold envelope
-> island must provide sufficient mass/interior support
-> morphology generator remains free to produce natural terrain around the requirement
~~~

The same applies to Ancient Cities, Trial Chambers, villages, and cliff infrastructure.

## Backend-neutral implementation concepts implied

Future neutral concepts likely include:

- StructureImportance;
- StructureRelocationPolicy;
- StructureOccurrenceSignal;
- StructureSiteCandidate;
- StructureReservation;
- StructureReservationKind;
- StructureTerrainInfluence;
- deterministic site scoring/fallback;
- progression topology intent;
- reservation conflict resolution.

Concrete Minecraft structure identities and mod-specific adapters stay below that boundary.
