# Cross-Dimension Skyforge Authorship Strategy v0.1

**Snapshot:** 2026-09-05
**Status:** Working design direction. Nether/End Skyforge authorship is a future target, not an accepted implementation milestone.

## Core position

> The Nether and the End should be treated as candidate Skyforge-authored worlds, not permanent vanilla exceptions.

> Their terrain must be derived from gameplay requirements, not the other way around.

See [Dimension Gameplay Requirements v0.1](dimension-gameplay-requirements-v0.1.md).

The terrain families and world grammars in this document are architectural hypotheses. They are retained only where later gameplay testing supports them.

Skyforge is a procedural **world synthesis** architecture, not merely an Overworld floating-island generator.

The current Minecraft implementation only proves the Overworld suspended-island realization.

That narrow implementation scope should not become an accidental architectural ceiling.

At the same time, extending Skyforge to other dimensions must not mean:

~~~text
Overworld island recipe
    + netherrack palette
    = Nether

Overworld island recipe
    + end stone palette
    = End
~~~

Each dimension needs its own semantic world grammar.

The shared architecture should govern:

- meaning;
- hierarchy;
- deterministic planning;
- volumetric ownership;
- morphology;
- materials;
- structures;
- ecology;
- resources;
- navigation/topology;
- backend realization.

The dimension-specific grammar should decide what those concepts mean.

## Current implementation audit

The present repository is encouraging but clearly asymmetric.

### Backend-neutral kernel — strongly reusable

The mathematical kernel already operates on:

- abstract 2D/3D coordinates;
- deterministic fields;
- signed density;
- procedural graphs;
- semantic seed derivation;
- backend-neutral evaluation.

Nothing fundamental in that layer requires an Overworld, Minecraft biome, island, sky, or dimension.

This is the strongest evidence that cross-dimension authorship is architecturally plausible.

### Semantic/model/recipe layer — currently island-specialized

The current mature authoring vocabulary contains many explicit types such as:

~~~text
SkyIslandDescriptor
SkyIslandVolumeDescriptor
SkyIslandGroupPlan
SkyIslandArchipelagoPlan
SkyIslandGeology*
SkyIslandHydrology*
SkyIslandEcology*
SkyIslandMaterial*
~~~

These are correct for the current proof.

They should not be prematurely renamed into vague generic abstractions.

But the naming shows that the current semantic layer is **not yet dimension-neutral**.

A future Nether implementation would be poorly served by pretending a cavern province is a SkyIsland.

### World/composition layer — reusable ideas, island-specific API

The current world layer has highly valuable general concepts:

- exact 3D ownership;
- finite authored volumes;
- overlap admission;
- support envelopes;
- deterministic spatial querying;
- authored/native composition;
- exact-volume material and population ownership.

Those concepts should survive.

The concrete API is still heavily SkyIsland-named and island-shaped.

The likely future task is therefore **generalization by proven need**, not wholesale abstraction now.

### Minecraft backend — currently Overworld-only Skyforge authority

The current development world preset explicitly uses:

~~~text
minecraft:overworld
    -> skyforge:noise_overlay

minecraft:the_nether
    -> minecraft:noise

minecraft:the_end
    -> minecraft:noise
~~~

So Nether and End are presently ordinary vanilla dimensions.

That is an implementation fact, not a final design decision.

## Candidate hierarchy extension

The accepted doctrine currently uses:

~~~text
World
-> Province
-> Cluster
-> Island
-> Primary Morphology
-> Secondary Morphology
-> Signals
-> Materials
-> Blocks
~~~

A multi-dimension Minecraft realization may eventually need an additional semantic domain layer.

Candidate—not yet accepted—extension:

~~~text
WORLD / SAVE
    -> REALM / DIMENSION DOMAIN
        -> Province
            -> Cluster / System
                -> Authored Body / Site
                    -> Primary Morphology
                    -> Secondary Morphology
                    -> Signals
                    -> Materials
                    -> Backend Blocks
~~~

Concrete mappings could be:

~~~text
OVERWORLD DOMAIN
    Authored Body -> suspended island / grounded exceptional body

END DOMAIN
    Authored Body -> end landmass / shard / central ritual island

NETHER DOMAIN
    Authored Body/System -> cavern vault / rock mass / lava basin / connective passage
~~~

This hierarchy should be reviewed by the architecture/authorship lane before becoming a code contract.

Do not rename current accepted types merely to anticipate this possibility.

## The three-dimensional grammar — hypothesis after gameplay

The dimensions should share architecture but may differ at the first-order occupancy level.

The following occupancy models are candidate ways to satisfy the gameplay roles. They are **not** prior requirements.

### Overworld — air-dominant suspended geography

~~~text
most space = AIR
authored solid bodies = islands
surface habitability = common
open horizon = fundamental
~~~

Core composition:

- suspended terrestrial masses;
- open air;
- hydrology;
- ecological surfaces;
- long-distance visual geography;
- aviation network.

### End — void-dominant alien archipelago

~~~text
most space = VOID
authored solid bodies = End landmasses
surface habitability = alien / sparse
open horizon = fundamental
~~~

The End is therefore geometrically the closest sibling to the current Overworld work.

It can reuse many concepts:

- finite suspended volumes;
- group/archipelago planning;
- exact ownership;
- surface/underside distinction;
- long-distance silhouettes;
- sparse destination structure placement.

But its semantic identity must diverge strongly.

### Nether — solid-dominant enclosed geography

~~~text
most local world volume = SOLID / ENCLOSED
authored negative space = caverns / vaults / passages
surface habitability = interior interfaces
open horizon = exceptional
~~~

This is almost the conceptual inverse of the current island problem.

The same signed-density / field architecture can plausibly express it, but the authoring grammar should think in terms of:

- cavern provinces;
- vaults;
- chambers;
- vertical shafts;
- arches;
- buttresses;
- rock columns;
- hanging masses;
- lava basins;
- lava falls;
- connective tunnels;
- buried structures;
- heat/geochemical regions.

If Skyforge can author this convincingly, it demonstrates that the architecture is genuinely a world-synthesis engine rather than an island generator.

## End authorship direction

### Gameplay precondition

Do not select outer-End morphology until the pack has measured the actual Aeronautics/Sable experience there.

Sable currently provides an End-specific pressure curve, and its lift-provider/propeller APIs scale wing lift/drag and propeller thrust by local pressure.

Therefore the End may already create a distinct thin-air aircraft problem.

That gameplay behavior should be characterized first.

### Preserve the End's macro-progression identity

Current vanilla progression distinguishes:

~~~text
central dragon island
    -> dragon victory
    -> End gateway
    -> large void transition
    -> outer End islands
    -> End cities / shulkers / Elytra
~~~

Skyforge should not casually erase this grammar.

The exact vanilla spacing is an implementation detail and could eventually be tuned, but the **ritual center -> gated outer world** relationship is valuable.

### Central End domain

The central island is not an ordinary random island.

Treat it as a required authored/vanilla-special site with hard obligations:

- valid player arrival platform;
- dragon fight arena;
- obsidian pillars / crystal gameplay;
- exit portal;
- dragon respawn compatibility;
- post-victory End gateway creation;
- enough open combat volume;
- no random terrain that invalidates the encounter.

Possible realization strategies:

1. preserve the central vanilla island initially;
2. let Skyforge author only outer End regions;
3. later author a Skyforge central island using a dedicated dragon-fight compatibility contract.

The second option is the preferred first cross-dimension prototype because it isolates risk.

### Outer End provinces

Outer End world composition is an excellent Skyforge target.

Desired grammar:

~~~text
vast negative space
    -> sparse landmass constellations
    -> occasional denser archipelagos
    -> large silent expanses
    -> rare End-city/civilization destinations
    -> exceptional formations
~~~

Do not simply make the End as densely populated as the Overworld starter world.

The End should be:

- more austere;
- more repetitive at the material level;
- stranger at the morphology level;
- more extreme in negative space;
- more dependent on landmark navigation;
- less ecologically familiar.

### End morphology vocabulary

Potential families:

- plate / mesa landmass;
- shattered plate;
- radial shard;
- needle / spear;
- inverted or double-sided mass;
- fractured chain;
- ring / partial ring;
- shell / bowl;
- thin bridge-like natural connection;
- enormous rare monolith.

These are content hypotheses, not implementation commitments.

The point is to derive a distinct morphology vocabulary from End meaning rather than repainting Overworld families.

### End structures

Important native compatibility targets:

- End cities;
- End ships;
- End gateways;
- obsidian pillars / dragon-fight structures.

Skyforge should ultimately own **site meaning and admissibility** while preserving native structures where they work.

End cities are particularly well suited to structure-site planning because their isolation and skyline presence can become semantically intentional.

### End ecology

Do not import Overworld ecology wholesale.

The End should probably have:

- extremely low ordinary biomass;
- chorus-dominated productive regions;
- endermen as a major ambient population;
- sparse specialist fauna only if a selected mod materially improves the dimension;
- exceptional organisms/phenomena rather than normal animal analogues.

A future End ecology grammar can still use the general niche-first doctrine.

### End flight

Do not assume Overworld atmosphere semantics apply unchanged.

Possible End profile:

- weak or absent ordinary thermals;
- unusual low-turbulence or static-flow regions;
- anomalous lift phenomena;
- strong navigation challenge;
- powered flight still useful for freight and route independence.

Elytra remain thematically tied to the End, but the existing policy against cheap firework propulsion can remain.

The eventual atmosphere system should support a **dimension profile**, not hardcode Overworld weather everywhere.

## Nether authorship direction

### Gameplay precondition

Do not select Nether cavern morphology merely because it is architecturally elegant.

First prove what traversal, resource, structure, and Aeronautics loops the Nether should support.

Sable's current Nether pressure profile decreases toward zero at the dimension ceiling, which may naturally discourage ordinary aerodynamic craft from treating the roof as the optimal aviation layer.

That behavior is a gameplay asset if it survives pack testing.

### Preserve enclosed-world identity

The Nether should not become:

> red floating islands in an empty red sky.

That would lose one of Minecraft's strongest dimensional contrasts.

Preferred macro-identity:

~~~text
ceiling / enclosing rock
    +
vast connected cavern systems
    +
lava seas / basins
    +
vertical shafts and vaults
    +
dense hazardous passages
~~~

The world feels volumetrically enclosed even where individual chambers become enormous.

### Nether province grammar

Potential province-scale semantic drivers:

- volcanic / magma-rich;
- basaltic;
- fungal;
- soul-sand / necrotic;
- fortress/civilization corridor;
- lava-sea margin;
- high-vault;
- crushed/maze-like;
- rare immense open chamber.

Existing vanilla Nether biomes can initially provide realization vocabulary while Skyforge authors the larger causal geography.

### Solid-dominant volumetric model

Conceptual density composition:

~~~text
BASE SOLID DOMAIN
    - authored cavern systems
    - vertical shafts
    - lava basins / channels
    - structure accommodation
    + secondary rock masses / columns / bridges
    + local detail
~~~

This is the inverse of:

~~~text
BASE AIR
    + authored suspended island solids
~~~

The kernel can already represent signed scalar fields, intersections, arithmetic composition, and deterministic signals.

The missing work is a semantic recipe/model layer for **enclosed volumetric worlds**.

### Nether hydrology analogue

Do not force terrestrial water hydrology semantics onto the Nether.

Possible domain-specific subsystem:

~~~text
MAGMATIC / THERMAL FLUID SYSTEM
    lava basins
    lava falls
    magma conduits
    heat gradients
    cooled basalt margins
    gas / vent regions if useful
~~~

The general doctrine remains:

> derive visible consequence from semantic cause.

The particular physical story differs.

### Nether ecology

Existing vanilla Nether biomes already imply distinct ecological regimes.

A Skyforge grammar can treat these as:

- fungal productivity;
- heat tolerance;
- soul-sand biome specialization;
- basaltic barren zones;
- lava-associated niches;
- piglin/hoglin civilization/ecology interaction.

Do not classify Nether fauna using ordinary Overworld carrying-capacity assumptions without adaptation.

### Nether civilization

The Nether is especially suitable for semantic civilization geography.

Examples:

- Piglin bastion territories;
- fortress corridors;
- trade/extraction routes;
- ruined infrastructure;
- lava crossings;
- defended resource districts.

Fortresses and Bastion Remnants should not be random decorations disconnected from geography if Skyforge authors the dimension.

They can become results of:

~~~text
regional route topology
resource opportunity
faction history
defensible terrain
~~~

while still reusing vanilla structures/assets.

### Nether aviation

Flight in the Nether should be meaningfully different from open Overworld aviation.

Possible consequences of enclosed terrain:

- constrained wingspan;
- tunnel/chamber route planning;
- thermal/turbulent hazards;
- ceiling collisions;
- limited sightlines;
- value of compact craft;
- value of airships only in major vaults;
- strong role for mapped safe corridors.

This may create a different vehicle ecology rather than merely duplicating Overworld routes.

## Portal and gateway topology

If Skyforge authors multiple dimensions, cross-dimension travel should become part of the authored world graph.

Candidate neutral concept:

~~~text
INTERDOMAIN_EDGE {
    sourceDomain
    sourceSite
    destinationDomain
    destinationSite
    activationRule
    progressionGate
    coordinateRelationship
    throughputClass
    entitySupport
    freightSupport
}
~~~

Examples:

~~~text
Overworld Nether portal
End portal
End gateway
future authored exceptional gateway
~~~

This does **not** imply making portal destinations arbitrary.

It means the relationship becomes explicit and inspectable rather than an unexamined vanilla side effect.

## Nether coordinate scaling

The current leading mobility-bypass prototype changes Nether coordinate scale from vanilla distance-compression behavior toward 1:1.

That remains a sensible short-term solution while Nether is vanilla-authored.

If Skyforge later authors Nether topology, the final answer could be richer.

Possible long-term models:

### Model A — 1:1 geometric correspondence

Simple and predictable.

Pros:

- no distance-compression bypass;
- low complexity;
- easy portal reasoning.

Cons:

- Nether cannot function as a distinctive alternate transport topology.

### Model B — semantically bounded compression

Some controlled compression exists, but portal routes are not arbitrary universal shortcuts.

Possible controls:

- authored portal-compatible regions;
- expensive stabilization;
- route capacity;
- destination uncertainty before survey;
- only selected cross-domain corridors.

Higher bespoke complexity.

### Model C — authored portal graph

Portal endpoints are semantic infrastructure rather than direct coordinate transforms.

Most powerful, but risks becoming non-Minecraft-like and requires substantially more custom behavior.

Current recommendation:

> Use 1:1 as the low-risk prototype. Do not lock it as permanent cosmology until Nether gameplay and authorship have been explored.

## End gateway topology

End gateways already behave more like authored/gated topology than Nether portals.

They are tied to:

- dragon victory;
- the central island;
- outer-island access;
- paired return behavior.

Skyforge should preserve this high-level role.

If outer End provinces become Skyforge-authored, gateway destination selection should be made aware of:

- safe landing volume;
- province identity;
- progression;
- avoidance of void/structure conflicts;
- deterministic pairing.

This is a natural future adapter seam.

## Resource progression

Cross-dimension authorship must not reintroduce bootstrap dependencies.

Current rules remain:

- Nether material is not required for the leading first powered aircraft;
- first flight occurs before dimension-dependent industry;
- dimension resources can become post-flight/post-Nether rewards;
- End resources remain late/exceptional by default.

### Nether resources

Candidate roles:

- quartz;
- blaze products;
- Ancient Debris / Netherite feeding CBC Nethersteel;
- dimension-specific biological materials;
- late heat/superheat and heavy-industry capability;
- optional Create: Metallurgy foundry infrastructure, but no planned Wolframite/Tungsten progression.

If Skyforge begins authoring Nether geology, the current ALLOW_DIMENSION_NATIVE resource decisions must be reopened.

### End resources

Potential roles:

- chorus materials;
- purpur;
- shulker-derived portable storage;
- Elytra;
- structure loot;
- future exceptional resources.

Because Shulker Boxes affect freight integrity, End progression and cargo balance should be reviewed together.

## Structure authority

The same high-level structure rule should apply in every domain:

> Skyforge decides where a structure makes semantic/geometric sense; Minecraft or a mod may still realize the concrete structure.

Dimension-specific compatibility matrices will be required.

### Overworld examples

- villages;
- mansions;
- strongholds;
- dungeons.

### Nether examples

- fortresses;
- bastions;
- ruined portals;
- modded Nether structures.

### End examples

- central dragon arena;
- gateways;
- End cities;
- End ships;
- modded End structures.

The current exact-volume structure work is therefore potentially useful beyond the Overworld, but each structure family has different assumptions.

## Atmosphere/environment authority

The current "one authoritative environmental quantity" rule should become dimension-aware.

Candidate model:

~~~text
Skyforge domain profile
    -> environmental authority
        -> wind / convection / local lift
        -> temperature / heat
        -> visibility / particles
        -> weather or equivalent phenomena
        -> vehicle interaction
        -> ecology interaction
~~~

Do not require each dimension to possess the same quantities.

Example:

~~~text
OVERWORLD
    ordinary wind
    weather
    thermals
    ridge lift

NETHER
    convective / vent flows
    heat plumes
    enclosed turbulence
    no ordinary rain-weather cycle

END
    sparse / anomalous flow
    no ordinary terrestrial weather
    dimension-specific phenomena
~~~

This preserves one authority without forcing one environmental fiction.

## Recommended implementation sequence

### DIM-0 — preserve optionality now

Immediate content/architecture policy:

- stop treating Nether/End vanilla generation as permanent;
- do not add dependencies that require their exact vanilla terrain layout without review;
- keep dimension-native resources provisional;
- keep portal rules separately configurable;
- document all cross-dimension progression assumptions.

No runtime work required.

### DIM-1 — dimension-domain boundary

Before generating new terrain, introduce or prove a backend concept equivalent to:

~~~text
Minecraft dimension key
    -> Skyforge domain policy
~~~

Expected states:

~~~text
VANILLA_AUTHORITY
SKYFORGE_AUTHORITY
HYBRID / SPECIAL_SITE_AUTHORITY
~~~

Do not generalize every SkyIsland class at this step.

### DIM-2 — End outer-island pilot

Preferred first terrain pilot.

Scope:

- keep central End/dragon fight vanilla;
- author one deterministic outer End province/archipelago;
- reuse exact suspended-volume ownership;
- preserve End-city and gateway compatibility;
- compare with vanilla outer End for identity and playability.

This tests cross-dimension architecture with minimal new volumetric mathematics.

### DIM-3 — Nether cavern-volume prototype

Deliberately choose a proof that cannot be described as an island.

Scope:

- one deterministic bounded cavern province;
- solid-dominant signed-density realization;
- at least one major vault;
- connective passages;
- lava body;
- biome/material realization;
- one representative fortress/bastion compatibility test.

This tests whether the semantic/recipe architecture generalizes beyond suspended bodies.

### DIM-4 — dimension semantic systems

Only after geometry works:

- dimension-specific geology;
- ecology;
- resource geography;
- structures;
- civilization;
- environmental fields;
- travel topology.

Reuse existing subsystems where semantics genuinely match.

Create parallel semantic systems where they do not.

### DIM-5 — full world integration

Only after both pilots succeed should Skyforge consider owning routine generation for:

- Overworld;
- Nether;
- End.

## Generalization rule

Do not generalize an abstraction merely because two dimensions might eventually use it.

Preferred sequence:

~~~text
working Overworld concept
    + real End requirement
    -> extract genuine shared abstraction

working shared abstraction
    + real Nether counterexample
    -> generalize again only where evidence requires
~~~

This prevents a premature universal ontology.

The Nether is particularly valuable as a counterexample because it will expose assumptions such as:

- every terrain body has an upper habitable surface;
- every region is finite solid in air;
- hydrology means water runoff;
- sky exposure is universally meaningful;
- ecology is surface-primary.

## Compatibility constraints

Before Skyforge authority is enabled for a dimension, audit:

### Nether

- portal arrival safety;
- fortress generation;
- bastion generation;
- ruined portals;
- biome-specific mobs/features;
- lava behavior;
- Nether roof / bedrock assumptions;
- modded Nether ores/features;
- Create/engineering recipe dependencies;
- map/navigation behavior;
- Distant Horizons behavior.

### End

- player spawn platform;
- central dragon arena if touched;
- End spikes/crystals;
- dragon respawn;
- exit portal;
- End gateway creation and pairing;
- outer-island gateway safety;
- End cities;
- End ships;
- chorus features;
- shulkers/Elytra acquisition;
- Distant Horizons behavior.

## Evidence requirements

### Shared

For each dimension proof retain:

- descriptor/domain plan;
- deterministic seed;
- density/occupancy evidence;
- material realization;
- structure compatibility;
- progression invariants;
- navigation/travel graph;
- reload determinism;
- chunk-order determinism;
- visual atlas.

### End pilot visual gate

The result should visibly read as:

> The End, authored with stronger spatial composition.

It should **not** read as:

> Skyforge Overworld islands recolored yellow.

### Nether pilot visual gate

The result should visibly read as:

> A coherent infernal cavern world whose empty spaces were composed deliberately.

It should **not** read as:

> Floating Nether islands.

## Acceptance tests

### DIM-A1 — kernel reuse

Cross-dimension proofs require no Minecraft or dimension-specific dependency in the mathematical kernel.

### DIM-A2 — semantic distinction

Overworld, Nether, and End have visibly and mechanically distinct first-order world grammars.

### DIM-A3 — End identity

End authorship preserves the ritual-center / gated-outer-world progression and supports End cities/gateways.

### DIM-A4 — Nether identity

Nether authorship remains predominantly enclosed/solid-dominant rather than becoming another open-sky archipelago.

### DIM-A5 — no progression regression

No dimension resource becomes a hidden prerequisite for the already-established first-flight bootstrap without explicit review.

### DIM-A6 — portal logistics

Cross-dimension travel does not accidentally erase the regional aviation/logistics game.

### DIM-A7 — native compatibility

Dimension-critical vanilla structures and encounter mechanics either function under Skyforge authorship or remain under explicitly isolated vanilla authority.

### DIM-A8 — shared architecture without forced sameness

At least the kernel, deterministic planning principles, provenance, exact ownership, and backend boundary are shared without requiring identical terrain semantics.

### DIM-A9 — explainability

A generated point can be explained through:

~~~text
Minecraft dimension
    -> Skyforge domain
    -> province/system
    -> authored body/negative-space system
    -> morphology
    -> material/population rule
    -> backend block/entity/structure
~~~

## Manual-eye questions

The eventual visual review should ask:

### End

- Is negative space still oppressive and meaningful?
- Are silhouettes distinct from the Overworld?
- Does the player still feel they have crossed into an alien late-game realm?
- Do End cities become better destinations rather than ordinary settlements?
- Does powered flight help without trivializing the void?

### Nether

- Does the player feel enclosed even in giant vaults?
- Are cavern provinces legible enough to navigate?
- Do lava, geology, structures, and biomes appear causally connected?
- Does the space create interesting compact-aircraft and route-planning problems?
- Does it still feel unmistakably Nether?

## Current recommendation

1. **Commit now to considering Skyforge authorship for all three vanilla dimensions.**
2. Keep the current Overworld implementation focus.
3. Define and test Nether/End gameplay requirements before freezing morphology.
4. Treat the **outer End** as the preferred first future cross-dimension pilot because it can test Skyforge authorship with lower geometry risk and strong Aeronautics relevance.
5. Treat a **solid-dominant Nether cavern province** as the leading architecture stress-test hypothesis only if it best supports the selected Nether gameplay.
6. Preserve vanilla critical sites until their Skyforge replacements have explicit compatibility contracts.
7. Keep the 1:1 Nether portal scale as a low-bespoke interim prototype, not a permanent cosmological decision.
8. Do not prematurely rename or universalize the existing SkyIsland code.

## Acceptance principle

> One Skyforge architecture should be capable of authoring many kinds of worlds. Their shared machinery should make them coherent; their different semantics should keep them strange.
