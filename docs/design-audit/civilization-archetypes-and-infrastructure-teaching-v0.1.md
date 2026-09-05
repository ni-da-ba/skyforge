# Civilization Archetypes and Infrastructure Teaching v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Not yet an accepted ADR.

## Core principle

> Civilization should teach the player what a mature skyborne infrastructure network looks like through observation, spatial organization, and recoverable material evidence.

The player should not need a tutorial window explaining:

- why an airfield needs clear approaches;
- why farms and mines occupy different islands;
- why settlements require storage and transport;
- why beacons and weather stations matter;
- why cargo, fuel, maintenance, and navigation infrastructure exist.

A coherent settlement can demonstrate those relationships simply by existing.

## Density is a tuning parameter

Civilization density is not an architectural constraint at this stage.

The civilization system should support:

- isolated sites;
- sparse frontier networks;
- villages;
- towns;
- hubs;
- capitals;
- abandoned networks;
- hostile networks.

How often those plans occur is a later experience-tuning decision.

Do not weaken the semantic model merely to make current test worlds sparse.

## Infrastructure maturity ladder

Civilization can implicitly demonstrate increasing infrastructure maturity.

### M0 — Survival / isolated

Typical characteristics:

- one inhabited site;
- local food/water;
- little formal transport;
- improvised storage;
- no meaningful route network.

Player lesson:

> A single island can survive, but its options are limited by local geography.

### M1 — Connected

Typical characteristics:

- one primary settlement;
- one satellite farm, quarry, dock, or beacon;
- basic route markers;
- regular movement between sites.

Player lesson:

> Separate islands can specialize if transportation exists.

### M2 — Specialized

Typical characteristics:

- dedicated agriculture;
- dedicated extraction;
- storage/cargo infrastructure;
- airfield or port;
- navigation support.

Player lesson:

> Geography creates comparative advantage; logistics ties it together.

### M3 — Integrated

Typical characteristics:

- several specialized islands;
- reliable fuel/storage;
- industry;
- weather/navigation systems;
- repair facilities;
- maintained routes;
- deliberate redundancy.

Player lesson:

> Mature infrastructure is a system, not a single machine or base.

### M4 — Resilient / regional

Typical characteristics:

- multiple transport nodes;
- alternate supply paths;
- distributed storage;
- major industry;
- defensive/navigation coverage;
- specialized maintenance;
- regional trade role.

Player lesson:

> Robust skyborne civilization survives failures by distributing functions and maintaining networks.

This ladder is semantic. Generated settlements need not be labeled with maturity levels in-game.

## Teaching through spatial arrangement

The first teaching layer is simply where structures are placed.

Examples:

### Airfield

The player sees:

- broad low-relief landing area;
- clear approach corridors;
- hangars beside, not on, the approach;
- fuel/storage nearby;
- windsocks/beacons/weather equipment;
- cargo transfer point to the rest of the settlement.

This teaches airfield design without text.

### Mine

The player sees:

- mine entrance aligned with real rock;
- hoist/loading area;
- stockpile;
- nearby processing/storage;
- cargo route to an airfield/dock;
- little or no farming on the same harsh island.

This teaches extraction logistics.

### Agricultural satellite

The player sees:

- water access;
- cultivated land;
- barns/storage;
- processing equipment;
- cargo staging area;
- route connection to population centers.

This teaches that food production is infrastructure, not decoration.

### Beacon/weather station

The player sees:

- exposed strategic location;
- minimal habitation;
- instruments/communications;
- visibility along a route;
- perhaps backup power/storage.

This teaches why tiny otherwise-unimportant islands matter.

## Teaching through functional adjacency

Players should be able to infer relationships from repeated patterns.

Examples:

~~~text
MINE
  -> stockpile
  -> processing
  -> warehouse
  -> cargo dock / airfield

FARM
  -> barn
  -> mill / food processing
  -> storage
  -> settlement market

AIRFIELD
  -> fuel
  -> repair
  -> storage
  -> navigation/weather
  -> route beacon
~~~

Repeated exposure to these patterns makes the infrastructure legible.

## Teaching through loot

Loot should reinforce the visible function of a site.

Avoid generic "valuable chest" logic where possible.

### Mine / quarry loot

Possible categories:

- tools;
- rails/carts;
- raw materials;
- industrial components;
- spare mechanical parts;
- survey/navigation items;
- food/basic supplies for workers.

### Airfield loot

Possible categories:

- fuel;
- repair components;
- mechanical parts;
- navigation instruments;
- maps;
- spare propulsive/control components;
- cargo supplies.

### Weather / beacon station loot

Possible categories:

- maps;
- compasses;
- CC/computing parts;
- radio/navigation components;
- weather instruments;
- batteries/electrical supplies if the selected stack supports them.

### Industrial loot

Possible categories:

- Create components;
- processed materials;
- spare machine parts;
- belts/gears/shafts/pipes;
- storage/logistics components;
- fuel.

### Agricultural loot

Possible categories:

- seeds;
- food;
- Farmer's Delight ingredients;
- tools;
- animal supplies;
- simple processing components.

### Abandoned site loot

Best used for:

- damaged but useful infrastructure components;
- partial supplies;
- repairable systems;
- maps/route evidence;
- spare parts;
- unusual salvage.

The player should often recover **pieces of a system**, not merely treasure.

## Repairable demonstrations

Where practical, generated infrastructure should sometimes be incomplete but understandable.

Examples:

- disabled beacon with intact tower and missing power/control component;
- weather station with broken sensor or dead power;
- hangar containing partially dismantled aircraft parts;
- industrial line with missing belts/gearboxes;
- abandoned dock with damaged crane;
- derelict radio/GPS installation.

This can create a natural gameplay loop:

~~~text
observe function
-> understand missing piece
-> salvage/repair
-> reproduce idea at home
~~~

Do not require every structure to be repairable or interactive.

## Avoid bespoke tutorial machinery

Preferred teaching order:

1. visible layout;
2. existing block functionality;
3. loot;
4. maps/signage/books only where useful;
5. custom tutorial UI only if later playtesting proves necessary.

Skyforge should not explain mature infrastructure through exposition when the world itself can demonstrate it.

## Settlement archetypes

The following archetypes are semantic grammars, not rigid templates.

Each can have many morphology/layout realizations.

### A. Frontier Homestead

Purpose: show the minimum viable inhabited sky-island site.

Typical roles:

- RESIDENTIAL;
- AGRICULTURAL or PASTORAL;
- BEACON_NAVIGATION optional.

Visual cues:

- one or a few buildings;
- small managed field/pasture;
- improvised storage;
- simple dock or landing patch;
- little industrial infrastructure.

Services / loot:

- basic food;
- simple tools;
- modest trade;
- route information.

Player lesson:

> Local self-sufficiency works at small scale, but terrain limits growth.

### B. Route Station

Purpose: demonstrate that transportation/navigation infrastructure can be a settlement function by itself.

Typical roles:

- BEACON_NAVIGATION;
- WEATHER_STATION;
- STORAGE;
- AIRFIELD or DOCK_PORT;
- minimal RESIDENTIAL.

Visual cues:

- tower/beacon;
- windsock;
- antenna/radar/computer;
- small fuel/storage;
- clear approach.

Services / loot:

- maps;
- navigation components;
- fuel;
- repair supplies;
- weather information.

Player lesson:

> Routes require infrastructure even where few people live.

### C. Agricultural Cluster

Purpose: show distributed food production.

Typical roles:

- AGRICULTURAL;
- PASTORAL;
- RESOURCE_PROCESSING;
- STORAGE;
- RESIDENTIAL small core;
- AIRFIELD/DOCK optional.

Visual cues:

- cultivated surfaces on suitable islands;
- barns;
- mills/food-processing equipment;
- storage;
- cargo staging.

Services / loot:

- food;
- seeds;
- farm tools;
- processing equipment;
- livestock supplies.

Player lesson:

> Productive land is valuable enough to specialize and export.

### D. Mining Settlement

Purpose: tie civilization directly to authored geology.

Typical roles:

- MINE;
- RESOURCE_PROCESSING;
- STORAGE;
- RESIDENTIAL;
- AIRFIELD/DOCK;
- BEACON optional.

Visual cues:

- shafts/adits;
- hoists;
- stockpiles;
- processing machinery;
- cargo infrastructure;
- rough worker habitation.

Services / loot:

- raw materials;
- tools;
- mechanical parts;
- fuel;
- maps/survey items.

Player lesson:

> Resource extraction creates transport and processing requirements.

### E. Industrial Hub

Purpose: demonstrate mature production and logistics.

Typical roles:

- INDUSTRIAL;
- RESOURCE_PROCESSING;
- STORAGE;
- AIRFIELD;
- DOCK_PORT;
- RESIDENTIAL secondary;
- NAVIGATION/WEATHER.

Visual cues:

- machinery;
- warehouses;
- fuel;
- cranes;
- cargo platforms;
- hangars;
- stacks/exhaust;
- dense utility infrastructure.

Services / loot:

- Create components;
- processed materials;
- power/fuel components;
- storage/logistics systems;
- repair supplies.

Player lesson:

> Mature production depends on supply, storage, maintenance, and transport.

### F. Civilian Town

Purpose: show a complete integrated settlement network.

Typical roles:

- RESIDENTIAL;
- MARKET_CIVIC;
- AGRICULTURAL satellite;
- STORAGE;
- AIRFIELD or DOCK;
- INDUSTRIAL light;
- BEACON_NAVIGATION;
- WEATHER_STATION optional.

Visual cues:

- distinct civic/residential core;
- visible functional satellites;
- maintained route infrastructure;
- lighting;
- traffic evidence;
- coherent service distribution.

Services / loot:

- trades;
- food;
- fuel;
- repair;
- maps;
- common components;
- storage;
- possibly rare goods.

Player lesson:

> A town is a network of mutually supporting functions.

### G. Regional Hub

Purpose: show the mature upper end of civilian skyborne infrastructure.

Typical roles include multiple instances of:

- RESIDENTIAL;
- MARKET_CIVIC;
- INDUSTRIAL;
- AIRFIELD;
- STORAGE;
- AGRICULTURAL;
- NAVIGATION;
- DEFENSIVE;
- WEATHER.

Visual cues:

- multiple routes;
- major airfield;
- redundant storage;
- dedicated industry;
- strong navigation signature;
- large skyline visible at distance.

Player lesson:

> Mature regional systems gain resilience from specialization and redundancy.

This is where players should be able to observe the closest thing to a reference implementation of mature infrastructure.

### H. Abandoned Network

Purpose: teach infrastructure through failure, salvage, and absence.

Typical former roles: any civilian archetype above.

Current state:

- ABANDONED / RUINED / RECLAIMED;
- traffic absent;
- maintenance failed;
- ecology returning;
- hostile/scavenger pressure possible.

Visual cues:

- dark beacons;
- broken airfield;
- empty warehouses;
- wrecks;
- overgrown farms;
- failed machinery;
- collapsed routes.

Loot:

- salvageable components;
- fuel remnants;
- maps;
- tools;
- damaged machinery;
- rare preserved stock.

Player lesson:

> Infrastructure requires maintenance; its ruins reveal how the system once worked.

Abandoned sites may be better teaching spaces than active towns because the player can dismantle them without social friction.

### I. Illager Frontier

Purpose: introduce hostile civilization as territorial infrastructure.

Typical roles:

- WATCH_POST;
- CAMP;
- STORAGE;
- EXTRACTION;
- DEFENSIVE;
- small AIR role.

Visual cues:

- route surveillance;
- banners;
- crude supply areas;
- patrol evidence;
- limited extraction;
- defensive positioning.

Gameplay:

- low-to-moderate faction density;
- warning before full military center;
- loot emphasizes supplies, weapons, materials, route information.

Player lesson:

> Hostile factions also depend on logistics and territory.

### J. Illager Military-Industrial Center

Purpose: show the hostile equivalent of mature infrastructure.

Typical roles:

- FORT;
- BARRACKS;
- INDUSTRIAL;
- STORAGE;
- EXTRACTION;
- AIR_BASE;
- NAVAL_BASE where relevant;
- REGIONAL_STRONGHOLD.

Visual cues:

- balloon tower;
- fortifications;
- warehouses;
- production/extraction;
- air/naval assets;
- defended routes;
- patrol network.

Gameplay:

- FACTION population rather than ambient mob density;
- deliberate approach and infiltration;
- locally dense encounter spaces;
- valuable military/industrial salvage.

Player lesson:

> Organized hostile power is sustained by infrastructure, not random monster spawning.

## Maturity gradients within archetypes

Do not create one fixed version of each archetype.

A Mining Settlement may be:

- tiny frontier mine;
- active town;
- mature industrial extraction center;
- declining mine;
- abandoned ruin.

An Agricultural Cluster may be:

- subsistence;
- regional exporter;
- mechanized;
- abandoned.

The semantic role remains recognizable while scale, maintenance, and infrastructure maturity vary.

## Loot as progression hint, not progression gate

Civilization loot can introduce players to advanced systems earlier than they would build them from scratch.

Examples:

- obtain a few Create components from an abandoned industrial site;
- find navigation equipment at a route station;
- recover a specialized aircraft component from a wreck;
- discover useful fuel/storage practices at an airfield.

But generated civilization should not automatically hand the player complete mature infrastructure.

Prefer:

~~~text
component
partial mechanism
damaged example
small quantity
map / clue
~~~

over:

~~~text
fully functional endgame machine
complete aircraft
unlimited fuel
~~~

This preserves player engineering as the main progression activity.

## Observation before ownership

A mature settlement can contain systems the player cannot or should not simply dismantle freely in an active civilian context.

The player can still learn from:

- arrangement;
- visible machinery;
- operation;
- trade;
- signage/maps;
- nearby abandoned equivalents.

Abandoned and hostile sites are natural places for direct salvage.

## Generated infrastructure should remain technically plausible

Do not place machinery purely as decorative technobabble.

If a visible Create mechanism is meant to communicate:

- milling;
- cargo transfer;
- pumping;
- mechanical power;
- storage;

its block arrangement should at least broadly correspond to that function.

Where practical, some generated machines can be actually functional.

Where not practical, prefer incomplete/broken machinery over fake impossible machinery.

## Visual hierarchy

Mature networks should communicate function at several distances.

### Horizon

- beacon;
- cultivated patch;
- hangar silhouette;
- crane;
- industrial plume;
- large fortification.

### Approach

- route lights;
- dock/airfield;
- roads/paths;
- cargo staging;
- distinct island specialization.

### Ground level

- workstation placement;
- storage;
- tools;
- machinery;
- loot;
- local NPCs.

This makes civilization readable before the player lands.

## Acceptance principles

1. Infrastructure teaches primarily through visible causal organization.
2. Functional loot reinforces what the site visibly does.
3. Generated settlements demonstrate systems the player can later reproduce.
4. Mature infrastructure is distributed, specialized, and connected.
5. Broken/abandoned infrastructure teaches through failure and salvage.
6. The world should not require bespoke tutorial UI to explain these relationships.
7. Active civilian sites emphasize observation/trade; abandoned/hostile sites provide more direct salvage.
8. Structure density remains a later tuning concern and should not constrain this architecture.
