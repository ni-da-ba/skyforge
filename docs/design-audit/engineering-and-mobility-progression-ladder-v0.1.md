# Engineering and Mobility Progression Ladder v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Exact recipes and numerical costs remain a later balance pass.

## Core principle

> Skyforge progression should convert geography from an obstacle into a network the player learns to exploit.

Progression is not merely stronger tools.

It should change:

- reachable distance;
- cargo capacity;
- weather tolerance;
- navigation quality;
- resource throughput;
- infrastructure scale;
- autonomy.

## Stage P0 — local survival

Player capabilities:

- ordinary Minecraft survival;
- bridge/climb short gaps;
- basic tools;
- local shelter/agriculture;
- very limited cargo movement.

Resource expectations:

- survival-complete local region;
- iron-class access;
- renewable heat/fuel;
- no dependence on strategic regional resources.

Civilization interaction:

- homestead/village useful for food, shelter, basic trade;
- no required settlement dependency.

Design goal:

> The player can establish themselves without already solving the sky.

## Stage P0.5 — local glide / assisted crossing

This is a semantic planning tier, not necessarily a player-facing progression label.

Player capabilities may include:

- a cheap personal glider if selected for the pack;
- launch-height- and thermal-dependent crossings;
- safer descent and emergency recovery;
- local scouting and potentially longer personal soaring where lift permits.

The glider supplies a **movement envelope**, not a logistics envelope.

It should remain:

- personal-scale;
- negligible-freight;
- terrain/launch/thermal dependent;
- unable to create powered climb by itself;
- unsuitable as the routine answer to ordinary inter-cluster **logistics**, even if skilled soaring can sometimes cross those distances.

Starting-group authoring may deliberately include glider-feasible directed edges, but progression-critical edges must also have a proven return/recovery path.

See [Early Glider Mobility Contract v0.1](early-glider-mobility-contract-v0.1.md).

Design goal:

> Let the player learn to read and ride the air without giving them regional logistics for free.

## Stage P1 — mechanical bootstrap

Player capabilities:

- basic Create machinery;
- mechanical processing;
- better storage/crafting;
- first deliberate inter-island infrastructure;
- primitive navigation.

Possible movement:

- bridges where short;
- the P0.5 glider/local-crossing path if selected;
- rope/lift solutions;
- first buoyant or mechanically simple flight experiments.

Resource expectations:

- all transitive recipe inputs for this stage guaranteed within the bootstrap-access domain;
- foundational iron/andesite and selected glider-material closure available;
- copper and zinc are not mandatory merely because Create exists;
- petroleum not required.

The current first-flight closure audit indicates that copper, zinc, brass, petroleum, and electricity can remain post-bootstrap unless playable testing proves a hidden dependency.

Civilization interaction:

- observation of simple mills/workshops;
- trade can reduce grind;
- abandoned sites can provide small component head starts.

## Stage P2 — reliable personal flight

Player capabilities:

- practical reusable aircraft/airship;
- safe travel between ordinary clusters;
- modest cargo;
- routine landing/docking;
- basic weather awareness.

This is the critical transition.

After P2, Skyforge can ask the player to solve regional resource geography.

Resource expectations:

- specialized metals may require regional travel;
- better fuels become valuable;
- redstone/electrical materials become more important;
- first meaningful trade routes become attractive.

Civilization interaction:

- route stations;
- airfields;
- maps/navigation;
- fuel;
- repair;
- mining/agricultural specialization.

Design goal:

> Flight should unlock economic geography, not merely sightseeing.

## Stage P3 — cargo and industrial aviation

Player capabilities:

- larger aircraft/airships;
- bulk cargo;
- stable fuel logistics;
- stronger engines/power;
- dedicated hangars/docks;
- heavier Create industry.

Resource geography:

- petroleum/fuel districts;
- rich mineral provinces;
- industrial feedstocks;
- processed materials;
- settlement trade networks.

Civilization interaction:

- mining towns;
- industrial hubs;
- regional markets;
- fuel depots;
- major ports.

The player starts building systems analogous to mature generated civilization.

## Stage P4 — instrumentation and automation

Player capabilities:

- CC:Tweaked navigation/computation;
- quantitative weather/wind awareness;
- radar/contact sensing;
- electrical distribution if selected;
- route automation;
- better cargo handling;
- possibly autopilot/assisted control through existing APIs.

Resource geography:

- electrical/advanced components;
- specialized processing;
- mature industry.

Civilization interaction:

- weather stations;
- navigation network;
- advanced airfields;
- industrial salvage;
- regional hubs.

Design goal:

> Information becomes an infrastructure resource.

## Stage P5 — regional logistics network

Player capabilities:

- multiple bases/sites;
- routine cargo routes;
- distributed storage;
- fuel network;
- industrial specialization;
- possibly automated logistics where robust.

The player is no longer merely exploring the world.

They are operating a network.

Resource geography:

- bulk throughput matters more than simple possession;
- route efficiency matters;
- resource-rich but distant provinces become valuable.

Civilization interaction:

- generated hubs become peers/reference examples rather than miraculous superior technology.

## Stage P6 — exceptional / legendary engineering

Optional late capabilities may include:

- extreme-range craft;
- advanced propulsion;
- highly autonomous systems;
- legendary/structure-derived components;
- specialized vehicles for severe environments.

This stage should depend on exceptional exploration only where that improves the experience.

Basic Create/Aeronautics competence must not require legendary loot.

## Mobility before scarcity rule

Resource specialization should activate gradually.

Conceptually:

~~~text
P0/P1:
  local completeness high
  regional specialization low

P2:
  regional specialization becomes meaningful

P3/P4:
  strategic-node resources strongly relevant

P5:
  throughput/logistics dominates simple scarcity

P6:
  exceptional resources may support optional capabilities
~~~

This avoids soft locks.

## Cargo matters

Progression should not only increase player travel speed.

Cargo capability should be one of the largest functional differences between transport stages.

Do not define this only by raw inventory slots.

Minecraft portable storage can make a player an effective manual courier, especially late game.

Instead distinguish:

~~~text
glider / Elytra
  strong personal mobility
  ordinary inventory
  later portable-container couriering
  poor bulk / fluid / entity / contraption throughput

small aircraft
  good personal mobility
  modest freight
  repeatable supply runs

cargo aircraft
  bulk containers / fuel / regional hauling
  landing-space/fuel cost

airship
  very large freight / mobile infrastructure
  machinery / entities / contraption-scale payloads
  slower / weather / handling tradeoffs

mature logistics network
  repeated / scheduled / automated throughput
~~~

Vanilla inventory should not be reduced merely to force vehicle use.

Vanilla Shulker Boxes remain provisionally acceptable as a late manual-courier upgrade. Large early backpack capacity or recursive portable-container nesting is not.

See [Portable Storage and Freight Integrity v0.1](portable-storage-and-freight-integrity-v0.1.md).

Exact vehicle balance remains downstream.

## No universal vehicle

Avoid one transport form dominating every progression stage.

Different craft should vary in:

- range;
- cargo;
- fuel;
- landing needs;
- wind tolerance;
- maneuverability;
- construction cost;
- speed.

This keeps geography meaningful even after late progression.

## Settlement as demonstration

Generated civilization can show the ladder indirectly.

### Frontier settlement

Displays P1/P2-like technology.

### Town

Displays P2/P3 infrastructure.

### Industrial hub

Displays P3/P4.

### Regional hub

Displays P4/P5 distributed infrastructure.

The player can therefore infer what more mature engineering looks like.

## Salvage acceleration policy

Salvage can move the player horizontally within a stage or provide a preview of the next one.

Examples:

~~~text
P1 player finds navigation component
-> can experiment with P2/P3 instrumentation

P2 player finds advanced engine part
-> accelerates one aircraft upgrade

P3 player salvages electrical equipment
-> begins P4 experimentation
~~~

Avoid salvage allowing:

~~~text
P0
-> complete P5 industrial network
~~~

from one structure.

## Trade acceleration policy

Trade is best at:

- replenishment;
- common components;
- limited rare ingredients;
- emergency fuel;
- maps/information.

Trade should not replace the need for industrial-scale extraction once the player reaches P3/P5 throughput.

## Starting civilization interaction

Do not require a settlement to spawn near the player for bootstrap completeness.

A no-civilization starting region should still permit P0/P1 and first practical flight.

Settlements are accelerators and teachers, not mandatory tutorial NPCs.

## Death / recovery resilience

Because the world is vertical and travel can be dangerous, progression should avoid making one lost aircraft an unrecoverable total reset.

Possible resilience sources:

- component reuse;
- settlement repair/fuel;
- recoverable wrecks;
- backup low-tier flight methods;
- distributed storage.

This is an experience-tuning concern but should inform recipes and salvage later.

## Dimension progression

Skyforge's first Minecraft realization should generally preserve Nether/End **identity and resource progression**.

For the current implementation, Nether and End terrain remain under vanilla authority.

That is provisional rather than architectural: both dimensions are now explicit future Skyforge-authorship candidates.

The outer End is the preferred first cross-dimension terrain pilot because suspended-volume concepts transfer naturally; a solid-dominant Nether cavern province is the more demanding proof that Skyforge can author worlds other than islands.

That does not imply preserving every vanilla dimension as an unrestricted transportation shortcut.

Nether and End resources can remain dimension-native for the current implementation, but their placement authority must be reopened if Skyforge begins authoring those dimensions.

However, normal Nether portal distance compression is a mandatory audit item because a short Nether corridor between portals can erase Overworld province-scale aviation geography and can participate in entity/item transport.

The desired separation is:

~~~text
dimension content / progression
    may remain intact

dimension transit / coordinate compression
    must prove compatibility with flight logistics
~~~

Overworld sky geography should enrich normal Minecraft progression without being reduced to decorative scenery between portal endpoints.

## Acceptance principles

1. The player can reach reliable flight before regional specialization becomes mandatory.
2. Each mobility stage changes economic reach, not just movement speed.
3. Cargo/logistics capacity is a major progression axis.
4. Generated civilization demonstrates later infrastructure without giving it away wholesale.
5. Trade and salvage accelerate progression but do not replace industrial throughput.
6. No settlement is required for basic bootstrap.
7. No one vehicle solves all geography.
8. Information/navigation becomes increasingly important at mature stages.
9. Nether/End progression remains intact by default.
10. Late game is about operating networks, not merely owning rarer items.
11. A selected early glider may solve starter-group crossings and longer personal soaring, but must not make P2 powered flight economically optional.
12. Progression-critical glider traversal is evaluated as a directed, recoverable capability graph rather than raw distance alone.
13. Elytra may remain as later personal soaring equipment, but vanilla-style firework propulsion should not create cheap self-contained powered flight.
14. Nether/portal transit must be explicitly shown not to erase ordinary regional aviation before progression is locked; a 1:1 Nether coordinate-scale datapack is the leading low-bespoke prototype.
15. Convenience teleportation is rejected by default unless it has a deliberate late-tier logistics role with meaningful constraints.
16. Personal portable storage may improve manual couriering, but mature aircraft/logistics must retain clear advantages in bulk, fluid, entity, contraption, and repeated-route throughput.
17. Inventory or encumbrance nerfs are a last resort, not a foundational progression tool.
18. Nether/End vanilla terrain authority is current implementation scope, not a permanent world-design rule.
19. Cross-dimension authorship must preserve each dimension's distinct world grammar rather than repainting Overworld islands.
