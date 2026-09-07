# Nether Gameplay and Aviation Contract v0.1

**Snapshot:** 2026-09-05
**Status:** Working design direction based on the current Minecraft/Create/Aeronautics/CBC stack, with Create: Metallurgy retained only as an optional foundry A/B candidate. Terrain grammar remains downstream of gameplay proof.

## Core rule

> The Nether should be a hostile route-and-industry world where access, extraction, heat, and safe movement matter more than open-air cruising.

Skyforge should not begin by deciding what the Nether looks like.

It should begin by preserving and strengthening the reasons a mature Overworld player enters it:

- progression-critical fortress/Blaze access;
- brewing/Nether materials;
- quartz and other dimension-native resources;
- bastions and hostile civilization sites;
- advanced heavy industry through CBC Steel/Nethersteel and optional general foundry processing;
- high-temperature / soul-fire processes;
- transport and recovery problems that differ from the Overworld.

The terrain should then be authored to make those jobs interesting.

## Desired gameplay identity

Working distinction:

~~~text
OVERWORLD
    build broad aviation/logistics networks

NETHER
    establish and operate dangerous corridors through enclosed terrain

END
    conduct long-range aeronautical expeditions through alien negative space
~~~

The Nether should reward:

- reconnaissance;
- route finding;
- tunneling/bridging where useful;
- defended stations;
- compact transport;
- cargo extraction;
- local aircraft in suitably large spaces;
- robust recovery planning.

It should not reward simply climbing above all meaningful content.

## Progression obligations

### Fortress / Blaze progression

Nether Fortresses remain an important progression site.

The pack should preserve a reliable route to:

- Blazes / Blaze products;
- Nether Wart where normal progression expects it;
- fortress loot and encounter content.

This matters to Skyforge engineering because high-temperature Create processes often intersect with Blaze Burner progression.

Do not make fortress access dependent on a late Nether vehicle that itself requires Blaze-derived capability.

### Bastions

Bastion Remnants should remain meaningful high-risk destinations.

Potential Skyforge roles:

- hostile-civilization stronghold;
- salvage;
- rare trade/loot;
- route anchor;
- defended resource district;
- visible evidence of Piglin territorial geography.

If Skyforge eventually authors Nether structure sites, bastions should arise from regional/faction/topological meaning rather than random decoration.

### Ordinary Nether resources

Retain deliberate access to ordinary dimension resources such as:

- quartz;
- soul materials;
- fungi/biological materials;
- lava;
- gold-bearing Nether material where relevant;
- Ancient Debris / Netherite if retained in normal pack progression.

Exact rarity and geography should be reviewed when Nether authorship begins.

Do not make every resource strategic merely because Skyforge can localize it.

## Heavy industry: Steel / Nethersteel / CBC

Create: Big Cannons is a retained industrial system and gives the Nether a cleaner payoff than the previously proposed Wolframite/Tungsten ladder.

### Current source-backed material chain

CBC can produce its mature cannon materials entirely from retained resources:

~~~text
Iron + Coal / Charcoal + HEATED
    -> Steel

Netherite Scrap
+ Steel or Cast Iron
+ SUPERHEATED
    -> Nethersteel
~~~

CBC material properties make this an engineering transition rather than a cosmetic tier.

Steel provides a major increase in viable cannon/autocannon performance.

Nethersteel further improves safe propellant stress and ballistic performance, but is heavier and not weldable.

Therefore the Nether industrial loop can be:

~~~text
find / establish Blaze and superheat capability
    -> extract Netherite Scrap
    -> maintain Steel supply from wider economy
    -> manufacture Nethersteel
    -> build high-performance artillery / fortification
~~~

This uses already meaningful Nether resources instead of adding a new ore solely to support an addon.

### Wolframite/Tungsten decision

The material-subtraction audit rejects Wolframite, Tungsten, and Obdurium from current Skyforge progression.

~~~text
WOLFRAMITE WORLDGEN
    -> DISABLE / DO NOT AUTHOR

TUNGSTEN / OBDURIUM
    -> NOT REQUIRED
~~~

Create: Metallurgy may still survive as a general-purpose foundry if its bulk melting, multi-fluid crucibles, casting, and factory layout are sufficiently useful/fun in A/B testing.

If retained, its Industrial Crucible should be re-gated with retained materials and its molten fluids should be bridged to CBC common molten-metal tags.

See [Material and Process Retention Audit v0.1](material-and-process-retention-audit-v0.1.md) and [Create: Big Cannons Industrial Integration Audit v0.1](create-big-cannons-industrial-integration-audit-v0.1.md).

## Aeronautics: Sable Nether pressure## Aeronautics: Sable Nether pressure

Sable already gives the Nether a distinct pressure profile.

Current built-in approximate values:

~~~text
Y 0     pressure 1.1366
Y 32    pressure 1.0000
Y 88    pressure 0.7993
Y 128   pressure 0.0000
~~~

Gravity remains ordinary downward gravity.

Sable's current lift-provider and propeller code scale:

- wing lift;
- aerodynamic drag;
- propeller thrust;

by local air pressure.

Therefore the current stack already creates an altitude-dependent aircraft envelope.

### Desired consequence

Low/mid Nether:

- ordinary aerodynamic craft can remain useful;
- dense enough air may even provide strong low-altitude aerodynamic authority;
- compact aircraft may cross lava basins or major vaults.

High Nether / roof approach:

- propeller thrust declines;
- wing/control authority declines;
- roof-level aviation becomes progressively unattractive.

At the dimension ceiling ordinary aerodynamic authority approaches zero under the current profile.

This is potentially excellent because it protects Nether topology through existing physics rather than arbitrary bans.

Do not lock the exact curve until it is flown in-game.

## Aircraft role in the Nether

The desired result is **specialization**, not prohibition.

### Healthy aircraft jobs

Potential niches:

- scouting major caverns;
- crossing lava seas/basins;
- moving ore between mine and processing station;
- compact cargo shuttle;
- rescue/recovery;
- fortress/bastion approach in suitable spaces;
- route inspection.

### Terrain jobs that should remain valuable

Even with aircraft, useful infrastructure should include:

- tunnels;
- bridges;
- stairs/elevators;
- rail;
- marked corridors;
- depots;
- defended stations;
- portal terminals.

The optimal Nether should not reduce to one universal transport mode.

### Vehicle-form pressure

If cavern geometry becomes the selected terrain answer, it should produce meaningful design differences:

~~~text
COMPACT CRAFT
    high maneuverability
    easier passage
    lower cargo

LARGE CARGO CRAFT
    efficient in major vaults / corridors
    route-constrained
    needs infrastructure

GROUND / RAIL
    robust in narrow corridors
    infrastructure-heavy
    strong repeatability
~~~

Do not impose artificial size classes.

Let geometry and physics create them.

## Soul-fire / Levitite manufacturing interaction

Create Aeronautics' current Levitite crystallization system provides another useful Nether connection.

Current catalyst tags distinguish:

### Ordinary crystallization catalysts

Examples in current source:

- campfire;
- magma block;
- torch;
- wall torch;
- lit Blaze Burner;
- fire.

### Soul crystallization catalysts

Examples:

- soul campfire;
- soul torch;
- soul wall torch;
- soul fire.

Adjacent soul-fire-base blocks can influence crystallization context.

The Soul crystallization path produces **Pearlescent Levitite**.

Current inspected Levitating component data does **not** establish Pearlescent Levitite as a stronger levitation tier; its role remains to be tested.

Therefore do not invent a mechanical upgrade merely because the manufacturing context is Nether-flavored.

What matters now is that the selected stack naturally links:

~~~text
OVERWORLD
    zinc / Create industry

NETHER
    Blaze heat / soul-fire materials / advanced metallurgy

END
    End Stone feedstock

    -> advanced aeronautical manufacturing
~~~

That cross-dimensional industrial grammar is promising.

## Heat as Nether capability

The Nether should be a natural place to encounter and master high-temperature processes.

This can be expressed through existing mechanics rather than a bespoke "Nether heat stat."

Possible progression relationships:

- Blaze Burner acquisition / operation;
- lava-rich industrial sites;
- superheated processing;
- foundry/metallurgy;
- Levitite crystallization contexts;
- future advanced propulsion/fuel production where selected mods support it.

The Nether's gameplay value can therefore be partly **process capability**, not only raw resource loot.

## Portal arrival and foothold

A Nether portal should not routinely place the player into an unrecoverable or structurally nonsensical location.

If Skyforge authors the Nether, portal admission must become a real site constraint.

A valid ordinary arrival should provide:

- collision-safe portal frame volume;
- stable footing;
- no direct lava immersion;
- enough local space to exit and orient;
- at least one viable path toward the regional route network;
- rebuild/recovery possibility if the portal is damaged.

This does not require every arrival to be comfortable.

The Nether should remain hostile.

It should not be randomly invalid.

## Portal transport and 1:1 interim scale

The current low-bespoke interim proposal remains:

~~~text
Nether coordinate_scale = 1.0
~~~

instead of vanilla distance compression.

This preserves the Nether as a destination without letting it automatically replace Overworld aviation with short portal corridors.

If Skyforge later authors the Nether, revisit this alongside:

- authored portal sites;
- regional route topology;
- contraption transfer;
- freight throughput;
- progression.

Do not freeze 1:1 as permanent cosmology yet.

## Contraption transfer

Current design must not assume a complete Aeronautics craft can traverse a Nether portal.

Required runtime audit:

1. player on foot through portal;
2. ordinary items/storage;
3. assembled Sable/Aeronautics contraption;
4. passengers/entities on contraption;
5. disassembly/reassembly workflow if direct transfer fails.

Potential healthy progression models:

### Local craft

~~~text
enter Nether
-> establish workshop
-> construct Nether-specialized vehicle locally
~~~

### Imported craft

~~~text
portal infrastructure
-> complete craft transfer
~~~

### Late stabilized transfer

~~~text
ordinary portal
    players/items

advanced portal terminal
    assembled freight craft
~~~

Do not bespoke-build the third model unless the actual stack creates a strong need and reuse options are exhausted.

## Fortress / bastion accessibility

Skyforge-authored terrain must not make critical structures nominally present but practically invalid.

For each required site prove:

~~~text
structure exists
    -> traversable approach exists
    -> encounter space works
    -> return/recovery path exists
~~~

Aircraft access does not have to be direct.

A fortress may deliberately require:

~~~text
fly / rail / walk to staging site
    -> enter structure on foot
~~~

That is often healthier than giving every structure a runway.

## Route topology requirements

A gameplay-first Nether authoring system should consider a network of:

~~~text
PORTAL_NODE
FORTRESS_NODE
BASTION_NODE
ANCIENT_DEBRIS_OPERATION
NETHERSTEEL_WORKS
ARTILLERY_FOUNDRY
QUARTZ / ORDINARY_RESOURCE_DISTRICT
LAVA_BASIN
INDUSTRIAL_FOOTHOLD
SAFE_STATION
MAJOR_VAULT
CHOKE_POINT
CONNECTOR_PASSAGE
~~~

The generated terrain should support multiple route types between them.

Potential route capability classes:

~~~text
WALK
TUNNEL
BRIDGE
RAIL
COMPACT_AIR
LARGE_AIR
LAVA_SURFACE / SPECIALIZED
~~~

Not every edge needs every mode.

This is where a cavern/vault grammar becomes useful **if** it produces better route gameplay.

## Terrain requirements derived from gameplay

The leading terrain hypothesis remains solid-dominant enclosed geography, but now for explicit reasons.

A useful Nether should likely contain:

- traversable connected chambers;
- some very large vaults where aircraft are valuable;
- narrow connectors where aircraft are poor;
- meaningful vertical differences;
- lava obstacles;
- route choices;
- staging shelves/ledges;
- sites where rail/bridge/tunnel construction pays off;
- strong landmarks despite short sightlines;
- no trivial universally safe roof transit.

Exact morphology remains unselected.

## Recovery

Nether failure differs from Overworld void failure.

Potential losses include:

- vehicle trapped behind narrow geometry;
- crash into lava;
- hostile recovery site;
- portal disconnect;
- cargo stranded in inaccessible cavern.

Recovery support can include:

- compact rescue craft;
- rail/foot fallback;
- marked stations;
- spare fuel/parts;
- alternate passages;
- recoverable wrecks where feasible.

Do not make every crash safe.

Do ensure normal experimentation does not routinely create progression-ending states.

## Resource geography when Skyforge authors Nether

If/when Skyforge takes terrain authority, classify resources semantically.

Possible causes:

~~~text
ANCIENT_DEBRIS
    deep / old / exceptional geological context supporting Nethersteel industry

QUARTZ
    broader but regionally variable mineral systems

ANCIENT DEBRIS
    deep / old / exceptional geological context

SOUL MATERIALS
    soul-biome / historical / ecological context

GOLD
    specific Nether mineral context
~~~

These are hypotheses.

Do not assign geological fiction that harms recognizable Minecraft progression.

## Civilization / hostile geography

Piglin and fortress content can provide a stronger causal structure.

Potential semantic relationships:

~~~text
BASTION
    defensible resource/route node
    Piglin territorial center

FORTRESS
    dangerous strategic corridor / ancient infrastructure
    Blaze progression site

RUINED PORTAL
    cross-domain historical evidence

ABANDONED INDUSTRY
    extraction / foundry history
~~~

Reuse vanilla/mod assets.

Avoid bespoke civilization systems where block arrangements, loot, mob governance, and route context suffice.

## Acceptance tests

### NETH-1 — progression integrity

Fortress/Blaze, brewing, and other retained vanilla progression remain reliably completable.

### NETH-2 — useful route network

Representative important destinations can be connected through understandable route choices rather than arbitrary digging or omnidirectional flight.

### NETH-3 — aircraft specialization

At least one representative Nether operation is materially improved by an aircraft, while at least one meaningful route remains better served by walking/tunneling/rail or other infrastructure.

### NETH-4 — roof non-dominance

Ordinary aerodynamic aircraft do not rationally make the roof/ceiling the universal safest/faster route.

Sable's pressure decay should be tested before adding any extra restriction.

### NETH-5 — compact/large tradeoff

If the selected terrain uses chamber/corridor geometry, compact and heavy aircraft gain different practical route envelopes without hardcoded vehicle classes.

### NETH-6 — heavy-industry payoff

Nether access materially enables CBC Nethersteel and other high-temperature/heavy-industry capabilities worth maintaining after the first progression visit.

### NETH-7 — resource scale

Quartz, Ancient Debris/Netherite, fuel/heat inputs, and other retained Nether resources appear at scales appropriate to real recipe/operational demand; neither trivial one-time fetch nor forced bulk freight is assumed without evidence.

### NETH-8 — structure usability

Fortresses and bastions remain encounter-valid and reachable under authored terrain.

### NETH-9 — portal safety

Ordinary portal arrival is hostile but valid and recoverable.

### NETH-10 — portal logistics

Nether portals do not erase Overworld regional aviation/logistics through uncontrolled distance compression.

### NETH-11 — contraption transfer

The pack has an explicit tested rule for assembled Aeronautics craft crossing—or not crossing—Nether portals.

### NETH-12 — heat/process identity

Nether access materially expands at least one processing capability through existing heat/soul/metallurgy mechanics rather than a bespoke arbitrary gate.

### NETH-13 — terrain follows gameplay

Every major Nether world-shape decision can cite one or more gameplay requirements above.

## Manual evidence required

When the pack prototype exists, test:

- ordinary Nether portal arrival;
- fortress discovery and Blaze acquisition;
- bastion approach;
- representative Ancient Debris/Netherite extraction loop;
- representative Steel -> Nethersteel -> CBC production loop;
- representative quartz/resource loop;
- compact aircraft in low/mid Nether;
- heavy aircraft in a large chamber;
- attempted roof-level aerodynamic flight;
- rail/tunnel route against aircraft route;
- lava-basin crossing;
- crash/recovery;
- craft portal transfer;
- Levitite crystallization using ordinary and soul catalyst contexts.

Record:

- route time;
- danger;
- cargo moved;
- infrastructure built;
- sightline/navigation burden;
- aircraft size constraints;
- pressure/altitude behavior;
- recovery cost;
- whether one transport mode dominates every route.

## Current gameplay hypothesis

~~~text
ENTER NETHER
    -> hostile valid foothold

EXPLORE
    -> find fortress / bastion / resource districts

ESTABLISH CORRIDOR
    -> mark, tunnel, bridge, rail, or fly where appropriate

EXTRACT
    -> quartz / Ancient Debris / other retained dimension resources

INDUSTRIALIZE
    -> Blaze heat / superheat
    -> Nethersteel
    -> advanced artillery / heavy engineering
    -> optional general foundry capability if Metallurgy survives A/B

OPERATE
    -> compact aircraft + ground infrastructure coexist

RETURN
    -> dimension resources feed the wider Skyforge economy
~~~

The terrain should be authored only after this loop proves compelling.

## Acceptance principle

> The Nether should make the player engineer a route through danger, not merely engineer a vehicle that lets them ignore it.
