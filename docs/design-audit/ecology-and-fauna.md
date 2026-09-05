# Ecology and Fauna Audit

**Snapshot:** 2026-09-05  
**Status:** Working design direction.

## Core ecology model

Skyforge should derive ecological feasibility before selecting concrete Minecraft species.

Candidate feasibility inputs:

```text
climate
temperature
moisture
surface area
primary productivity
water volume
water depth
vegetation structure
soil availability
cliff area
cave volume
altitude
isolation
human disturbance
predator pressure
sky exposure
```

Skyforge should first produce ecological niches/roles, then map them to vanilla or modded entities.

Example:

```text
MEDIUM_BROWSER    -> Naturalist deer
SOIL_BURROWER     -> Naturalist mole
POLLINATOR        -> Naturalist butterfly
DETRITIVORE       -> Critters & Companions microfauna
SMALL_AERIAL      -> Fowl Play songbird
AERIAL_RAPTOR     -> Fowl Play hawk
THERMAL_SOARER    -> adapted red-tailed-hawk / selected hawk realization
```

This keeps ecological semantics independent of a particular content dependency.

## Local complexity rule

A large installed catalogue is acceptable because any one island should realize only a constrained subset.

Typical ordinary island target, subject to tuning:

- several microfauna roles;
- several small-animal roles;
- a small number of medium herbivore/omnivore roles;
- zero or one ordinary predator hierarchy;
- exceptional megafauna/predators only when justified.

Performance budgets apply to realized population and behavior frequency, not total installed species count.

## Leading ecology dependencies

### Naturalist — probable/core

Role: broad terrestrial, freshwater, marine, predator, scavenger, reptile, and insect backbone.

Useful terrestrial fauna includes deer, boar, bears, hedgehog, mole, rat, reptiles, tortoise, turkey, insects, bass/catfish, and vulture.

Regional specialists include tropical/wetland fauna, dry/desert specialists, savanna megafauna, large predators, and cold megafauna.

Marine fauna should be retained in the global catalogue because ocean-island types are planned. Marine animals should only realize when authored water volume, depth, temperature, and open-water conditions support them.

Naturalist whales are not considered a conflict with Sky Whales once true marine habitats exist:

```text
Naturalist whale -> aquatic megafauna
Sky Whale        -> aerial megafauna
```

Disable duplicate bird roles where Fowl Play is preferred.

### Fowl Play — probable/core

Role: ordinary bird behavior backbone.

Useful niches:

- woodland songbirds;
- settlement pigeons/crows;
- ravens;
- gulls;
- ducks/geese;
- ordinary hawks;
- cold birds.

Fowl Play hawks are a strong candidate for a thin wind/thermal integration hook.

A particularly useful behavior target is the previously discussed **adapted red-tailed-hawk / ordinary thermal soarer** role:

- searches for authored or simulated lift rather than flapping continuously;
- circles while climbing in thermals;
- transitions into long glides between lift sources;
- favors cliff/ridge lift where appropriate;
- can exploit strong natural heat sources and, if technically practical, modest anthropogenic thermal sources;
- serves as atmospheric legibility for players using gliders.

This should preferably be a behavioral adaptation/integration of an existing hawk asset rather than a bespoke new species unless the available model/animation set proves inadequate.

The design payoff is substantial:

~~~text
player sees hawk circling
    -> infers rising air
    -> approaches with glider
    -> catches same thermal
~~~

Fauna therefore teaches atmosphere through observation rather than exposition.

### Critters & Companions — probable ecology dependency, pending performance/visual testing

Role: specialist and microfauna enrichment.

Particularly useful potential niches:

- jumping spider;
- roly-poly and other detritivore/arthropod roles;
- otter/riparian specialization;
- future ocean-island small marine fauna.

Policy:

> Dependencies may overlap; realized ecology should not.

Choose one preferred realization for overlapping roles such as generic snails or dragonflies.

### Sky Whales — probable/core

Role: signature open-sky megafauna.

Desired integration:

- Skyforge habitat/spawn ownership;
- terrain-aware movement;
- wind/weather response;
- population/migration;
- radar classification;
- richer behavior before adding more aerial megafauna species.

### Birds/Boids Reforged — prototype only

Use only if Fowl Play grouping fails to create convincing flock motion.

### Hybrid Birds — optional

Potentially useful for albatross/puffin-like open-sky and cliff niches. Treat as dependency only; not an asset source.

## Bespoke species

### Cliff raptor

Current strongest bespoke-fauna requirement.

Intended role:

- large territorial cliff/underside predator;
- solitary or paired;
- mostly soaring;
- persistent nest and territory;
- hunts small aerial and edge prey;
- threatens exposed players, gliders, balloons, and light aircraft;
- uses thermals/ridge lift;
- does not routinely kill Sky Whales.

Encounter escalation should progress from distant warning/territorial evidence to inspection/feints and only then committed attack.

### Legendary dragon

Exceptional, very rare threat rather than normal ecology.

Its territory should be environmentally legible before encounter through evidence such as unusual whale carcasses, wreckage, scorched remains, abandoned routes, and fauna suppression.

## Ecology palettes

Representative intended palettes:

### Temperate woodland
- deer, rabbits, mole, hedgehog, snails, butterflies, songbirds;
- occasional boar, crow/raven, snake, hawk;
- rare bear or cliff raptor.

### Wetland/lush waterbody
- capybara, dragonflies, butterflies, amphibians, bass/catfish, waterfowl;
- alligator only for large wetlands;
- piranha only as a special tropical-water condition.

### Dry scrub/desert
- lizards, rattlesnake, scorpion, tortoise, sparse small birds;
- occasional Komodo where size/productivity permits;
- vultures.

### Savanna plateau
Large islands only:
- zebra, giraffe, ostrich;
- elephant/rhino where carrying capacity permits;
- lion hierarchy;
- vulture.

### Cold upland
- sparse ordinary fauna;
- cold birds;
- rabbits/wolves where suitable;
- rare mammoth;
- cliff/thermal birds.

### Jungle
- rich insect/reptile/bird ecology;
- tropical water fauna;
- one coherent large-predator hierarchy.

## Cave and underside ecology

Treat cliff, underside, cave mouth, twilight cave, deep cave, and aquifer as a connected ecological gradient.

Useful anchors:

```text
PERCH
ROOST
NEST
FORAGE
WATER
CLIFF
UNDERSIDE
THERMAL
CARCASS
BAT_ROOST
CAVE_ENTRANCE
TWILIGHT_ZONE
AQUIFER
SUBMERGED_CAVE
```

Vanilla bats should be retained but realized around semantic roost anchors rather than random meaningless cave density. Colony size may be semantically large while only a handful of entities are active.

Deep caves should become biologically sparse. Cave mouths/twilight zones are richer; very deep spaces should rely more on geology, water, sound, darkness, and rare specialized life.

Future ocean islands extend this into submerged caves, coastal cliffs, deep water, and seafloor ecology.

## Cross-mod ecological integration

Desired tags/relationships include:

```text
prey
predator
scavenger
small_fauna
large_fauna
cliff_fauna
aerial_fauna
aquatic_fauna
```

Examples:

- Fowl Play hawks hunt selected small Naturalist fauna.
- adapted thermal-soaring hawks prefer local thermal/ridge-lift opportunities shared with glider gameplay.
- Naturalist deer recognize cliff raptor as predator.
- Naturalist vultures recognize Skyforge carcass anchors.
- Fowl Play birds avoid active raptor nesting cores.

A thin shared habitat/relationship layer is preferred over every entity independently querying expensive Skyforge state each tick.

## Ecological scarcity

Desired open-sky rhythm:

```text
MOST        silence / wind / distant terrain
SOMETIMES   small flock / solitary bird / insects near habitat
UNCOMMON    large soarer / scavenger / nesting colony
RARE        whale / territorial predator / migration
EXCEPTIONAL storm fauna / dragon / legendary site
```

The world should remain sparse even with a rich species catalogue.
