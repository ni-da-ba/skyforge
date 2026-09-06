# Unified Skyforge Industrial Production Graph v0.1

**Snapshot:** 2026-09-05  
**Status:** Canonical working production graph for the current Minecraft 1.21.1 content stack. This is the player-facing industrial model; individual mod recipes remain subordinate to it.

# Governing doctrine

> Skyforge owns the economy. Mods supply machinery, behaviors, recipes, and assets.

The integrated pack should present **one coherent production system**, not several parallel mod economies.

A retained material/process must justify itself by contributing:

- a distinct engineering capability;
- a durable logistics role;
- a meaningful design tradeoff;
- an important cross-dimensional dependency;
- or a process loop sufficiently useful/fun to justify its cognitive load.

# Layer 1 — retained world inputs

These are the current **industrial-graph-relevant** raw or environmental inputs. This is not a list of every vanilla block/resource that remains in the game.

## Overworld raw inputs

~~~text
WOOD / BIOMASS
STONE
ANDESITE
IRON
COAL / CHARCOAL
COPPER
ZINC
GOLD
REDSTONE
DIAMOND where vanilla/selected machinery uses it
PETROLEUM / CRUDE OIL
WATER
CLAY
SAND
WOOL / FIBER
SLIME / HONEY adhesive inputs
SUGAR
EGGS
GUNPOWDER via renewable/farm/trade/loot systems
~~~

## Nether raw/environmental inputs

~~~text
NETHERRACK
    -> Cinder Flour

QUARTZ
    -> Create control / Electron Tube chain

BLAZE ACCESS
    -> HEATED processing
    -> brewing / End progression

LAVA
    -> Blaze Cakes / high-temperature infrastructure

NETHERITE SCRAP
    -> Nethersteel / vanilla Netherite

SOUL MATERIALS
    -> selected End/Levitite crystallization context and other retained systems
~~~

## End raw/environmental inputs

~~~text
END STONE
    -> End Stone Powder
    -> Levitite

SHULKER SHELLS
    -> portable late storage

CHORUS / END MATERIALS
    -> ordinary End life/building systems

DRAGON / END CITY / EXCEPTIONAL LOOT
    -> progression / expedition rewards
~~~

# Layer 2 — retained manufactured materials

## Andesite Alloy

~~~text
ANDESITE + IRON
    -> ANDESITE ALLOY
~~~

Role:

- basic Create machinery;
- first-flight workshop;
- mechanical infrastructure.

This remains a **pre-Brass** foundational manufactured material.

## Brass

~~~text
COPPER + ZINC + HEATED mixing
    -> BRASS
~~~

Role:

- precision/control;
- logistics;
- smart fluid handling;
- CC&A motor/storage/control components;
- Diesel Generator engines;
- selected Aeronautics/Simulated advanced machinery.

Brass is a **coordination material**, not merely Copper/Zinc upgraded.

## Cast Iron

~~~text
IRON + COAL/CHARCOAL + HEATED compacting
    -> CAST IRON
~~~

Role:

- inexpensive CBC cast cannon material;
- low-pressure industrial artillery;
- first true cannon-foundry material.

No Cast Iron geology.

## Bronze

Preferred CBC route:

~~~text
COPPER + ZINC + CINDER FLOUR + HEATED mixing
    -> BRONZE
~~~

Role:

- lighter CBC pressure-vessel/artillery material;
- pressure/failure/repair tradeoff distinct from Cast Iron/Steel.

No Tin required.

## Steel

Current CBC route:

~~~text
IRON + CARBON + HEATED mixing
    -> STEEL
~~~

Role:

- major heavy structural/artillery material;
- high-pressure CBC guns/autocannons;
- built-up cannon construction;
- potential shared advanced structural material for recipe rebases.

**Progression balance remains under test.**

Steel is retained regardless of Create: Metallurgy.

## Nethersteel

~~~text
NETHERITE SCRAP
+ CAST IRON or STEEL
+ SUPERHEATED processing
    -> NETHERSTEEL
~~~

Role:

- extreme CBC artillery;
- high-pressure / high-performance material;
- increased mass;
- reduced repair/weld flexibility.

Nethersteel is a strong Nether industrial payoff without new ore.

## Electrum — optional

Current doctrine:

~~~text
ELECTRUM
    OPTIONAL MANUFACTURED MATERIAL
    ONLY if high-current electrical networks justify it
~~~

Silver geology is excluded.

Gold must carry required baseline electrical progression.

Electrum survives only if integrated electrical testing proves a useful high-current problem.

## Levitite

~~~text
END STONE
    -> END STONE POWDER

END STONE POWDER
+ ZINC
+ WATER
+ HEATED mixing
    -> LEVITITE BLEND

LEVITITE BLEND
+ CRYSTALLIZATION
    -> LEVITITE
~~~

Role:

- passive/near-passive lift support;
- heavy/hybrid aircraft architecture;
- End-derived engineering capability.

No self-climb.

# Layer 3 — retained process gates

## P-MECH — basic mechanical processing

Includes:

- pressing;
- crushing;
- sawing;
- mixing without special heat;
- assembly;
- rotational power.

Primary payoff:

- Create workshop;
- first-flight closure;
- basic resource processing.

## P-HEAT — heated processing

Current natural unlock:

~~~text
NETHER / BLAZE BURNER
    -> HEATED PROCESSING
~~~

Major uses:

- Brass;
- CBC Cast Iron;
- CBC Bronze;
- CBC Steel;
- selected Create/addon recipes;
- Levitite Blend.

This gives the Nether immediate industrial relevance.

## P-SUPERHEAT — superheated processing

Current standard fuel path:

~~~text
EGG
+ SUGAR
+ CINDER FLOUR
    -> BLAZE CAKE BASE

BLAZE CAKE BASE
+ LAVA
    -> BLAZE CAKE

BLAZE BURNER + BLAZE CAKE
    -> SUPERHEAT
~~~

Major uses:

- CBC Nethersteel;
- improved Diesel Generators crude-oil distillation;
- Create high-output boiler/process uses;
- potential retained advanced processes.

Superheat remains retained even if Create: Metallurgy is removed.

## P-FLUID — fluid handling/refining

Copper/Create infrastructure:

~~~text
PIPES
TANKS
PUMPS
VALVES
FILLING
~~~

Petroleum extension:

~~~text
OIL FIELD
    -> PUMPJACK
    -> CRUDE STORAGE
    -> DISTILLATION
    -> DIESEL / GASOLINE
    -> TANK FARM
    -> DISTRIBUTION
~~~

This is one of the strongest recurring freight loops.

## P-ELECTRICAL — electrical generation/distribution

Leading CC&A structure:

~~~text
MECHANICAL POWER
    -> ALTERNATOR
    -> ELECTRICAL NETWORK
    -> STORAGE
    -> MOTOR
    -> remote kinetic power
    -> computation/control integration
~~~

Material vocabulary:

- Copper;
- Gold;
- Zinc;
- Brass;
- Redstone;
- optional Electrum.

No Silver geology.

## P-FOUNDRY — advanced general foundry — conditional

Create: Metallurgy is retained only if A/B testing proves that its:

- bulk melting;
- multi-fluid crucible;
- generic casting;
- molds;
- molten logistics;
- heat-scaled throughput;

materially improves CBC/large-scale industrial play.

If retained:

~~~text
CBC MATERIAL ECONOMY
    remains canonical

METALLURGY
    becomes shared PROCESS INFRASTRUCTURE
    not a separate material tree
~~~

Wolframite/Tungsten/Obdurium remain excluded.

## P-END — End-derived crystallization / expedition industry

Current principal chain:

~~~text
END STONE
    -> LEVITITE
    -> new aircraft architecture
~~~

The End's industrial role should remain specialist/expeditionary rather than imitate Overworld bulk industry.

# Layer 4 — capability branches

## Basic Create / first flight

Inputs:

~~~text
IRON
ANDESITE
WOOD
WOOL/FIBER
ADHESIVE
FUEL
~~~

Outputs/capabilities:

- workshop;
- Physics Assembler;
- Portable Engine;
- propellers;
- sails/control;
- first practical aircraft.

No Brass/petroleum/electricity/Nether/End dependency.

## Fluid logistics

Inputs:

~~~text
COPPER
CREATE MACHINERY
~~~

Capabilities:

- piping;
- tank storage;
- transfer;
- filling;
- refinery infrastructure;
- aircraft fuel logistics.

## Mature Create logistics/control

Inputs:

~~~text
COPPER
ZINC
BRASS
REDSTONE
NETHER QUARTZ
~~~

Capabilities:

- smart routing;
- Mechanical Arms;
- advanced funnels/tunnels;
- Electron Tube chain;
- mature control/logistics.

## Electricity

Inputs:

~~~text
COPPER
GOLD
ZINC
BRASS
REDSTONE
optional ELECTRUM
~~~

Capabilities:

- generation;
- distributed transmission;
- storage;
- electrical-to-kinetic conversion;
- CC/control integration.

## Petroleum/heavy engine branch

Inputs:

~~~text
PETROLEUM
COPPER / FLUID INFRASTRUCTURE
BRASS
HEAT / SUPERHEAT
~~~

Capabilities:

- diesel/gasoline;
- heavy engines;
- sustained fuel economy;
- strategic depots;
- industrial/aviation power.

## CBC artillery/heavy industry

Inputs:

~~~text
IRON
COAL
GUNPOWDER
COPPER
ZINC
NETHERRACK / CINDER FLOUR
BLAZE HEAT
NETHERITE SCRAP
SUPERHEAT
REDSTONE / QUARTZ for controls/fuzes
~~~

Capabilities:

- Wrought-Iron artillery;
- Cast-Iron/Bronze/Steel cannon foundry;
- autocannons;
- built-up guns;
- Nethersteel extreme artillery;
- ammunition/fuze/propellant industry;
- recurring heavy freight.

## End aeronautics

Inputs:

~~~text
END STONE
ZINC
WATER
HEAT
~~~

Capabilities:

- Levitite;
- lift-support architectures;
- heavier expeditionary craft;
- new End route assumptions.

## Advanced Propulsion — conditional

Create Propulsion: Simulated remains R&D.

Useful retained capabilities may include:

- chemical/reaction thrust;
- ion thrust;
- vectored thrust;
- advanced low-pressure propulsion;
- compact fluid/power systems.

But:

~~~text
PLATINUM WORLDGEN
    EXCLUDED

PLATINUM-GATED RECIPES
    REBASE if feature retained
~~~

Candidate replacement vocabulary:

- Steel / Nethersteel for load-bearing structure;
- Brass for precision/control;
- Copper/Gold/optional Electrum for conduction;
- existing advanced mechanisms/sensors.

# Player-visible progression graph

The current intended shape is branching rather than linear.

~~~text
START / SURVIVAL
    |
    v
BASIC CREATE
    |
    v
FIRST POWERED FLIGHT
    |
    +-------------------------------+
    |               |               |
    v               v               v
COPPER/ZINC      REDSTONE        RESOURCE ROUTES
    |               |               |
    +----> BRASS <--+               |
    |                               |
    +--> FLUIDS                     |
    +--> LOGISTICS / CONTROL         |
    +--> EARLY ELECTRICITY           |
                                    |
                         +----------+-----------+
                         |                      |
                         v                      v
                    NETHER                PETROLEUM
                         |                      |
          +--------------+---------+            |
          |              |         |            v
          v              v         v      REFINERY / FUEL
      BLAZE HEAT      QUARTZ   NETHERITE        |
          |              |         |            v
          |              |         |       HEAVY ENGINES
          |              |         |
          v              v         |
  CAST IRON / BRONZE / STEEL      |
          |                        |
          +----------+-------------+
                     |
                     v
                 SUPERHEAT
                     |
          +----------+-----------+
          |                      |
          v                      v
     NETHERSTEEL          HIGH-EFFICIENCY
     ARTILLERY            INDUSTRY/REFINING

                     |
                     v
                    END
                     |
                     v
                 END STONE
                     |
              + ZINC + WATER
                     |
                     v
                 LEVITITE
                     |
                     v
          EXPEDITIONARY AIRCRAFT
~~~

This is not a quest-book mandate.

It is the dependency/capability graph the final recipes and world sources must respect.

# Geographic consequences

## Overworld

Primary industrial geography:

- Iron;
- Coal;
- Copper;
- Zinc;
- Redstone;
- Gold;
- Petroleum;
- agriculture/biological process inputs.

This supports the broad permanent economy.

## Nether

Primary industrial geography/capability:

- Blaze heat;
- Netherrack/Cinder Flour;
- Quartz;
- Lava;
- Netherite Scrap;
- soul materials where relevant.

This replaces the previous weaker Wolframite/Tungsten thesis.

The Nether should reward dangerous traversal through **process capability and retained high-value materials** rather than arbitrary new ores.

## End

Primary specialist industry:

- End Stone / Levitite;
- Shulker logistics;
- exceptional expedition rewards.

The End remains exploration/engineering-heavy rather than ore-heavy.

# Explicitly excluded industrial raw-resource economies

Do not author Skyforge geology for:

~~~text
SILVER
TIN
PLATINUM
WOLFRAMITE
TUNGSTEN
OBDURIUM
~~~

unless a future independent capability audit overturns the decision.

A recipe reference alone is not sufficient evidence.

# Material identity rules

## Rule M-1 — one material, one economy

If multiple mods provide Steel/Bronze/etc.:

- use common tags;
- normalize recipes;
- prevent loops;
- choose one player-facing identity where practical.

## Rule M-2 — capability before rarity

Do not make a resource rare to manufacture importance.

Its mechanical/process role must justify importance first.

## Rule M-3 — manufactured complexity beats geological clutter

Prefer:

~~~text
few ores
-> alloys/processes
-> differentiated machines
~~~

over:

~~~text
many ores
-> one machine each
~~~

## Rule M-4 — no orphan process tiers

A process such as superheat/foundry survives only if several useful systems consume it or it creates a uniquely worthwhile factory loop.

## Rule M-5 — dimensions provide capabilities, not colored ore tiers

Nether:

- heat;
- hostile route value;
- Quartz;
- Netherite;
- special processing.

End:

- Levitite;
- expedition/logistics rewards;
- strange engineering.

# Capability/source closure requirements

The integrated pack must explicitly prove:

~~~text
IRON_SOURCE
COAL_SOURCE
COPPER_SOURCE
ZINC_SOURCE
REDSTONE_SOURCE
GOLD_SOURCE
PETROLEUM_SOURCE
GUNPOWDER_SOURCE
EGG_SOURCE
SUGAR_SOURCE
BLAZE_HEAT
NETHERRACK_SOURCE
QUARTZ_SOURCE
LAVA_ACCESS
SUPERHEAT_FUEL
NETHERITE_SCRAP_SOURCE
END_STONE_SOURCE
WATER_ACCESS
ADHESIVE_PATH
~~~

These are stronger requirements than "the block exists somewhere."

Each source must be:

- progression-appropriate;
- practically reachable;
- scalable enough for its intended industry.

# Acceptance tests

## IND-GRAPH-1 — first-flight closure

The first powered aircraft remains independent of every post-flight strategic branch.

## IND-GRAPH-2 — no orphan raw material

Every authored industrial raw material has multiple meaningful consumers or one extremely strong unique capability.

## IND-GRAPH-3 — no missing hidden input

Every retained advanced process has explicit closure for biological/farm/world inputs.

## IND-GRAPH-4 — one Steel economy

CBC/Metallurgy/other Steel producers and consumers interoperate without player-facing duplication or conversion exploits.

## IND-GRAPH-5 — no rejected-material regression

Installing a retained mod does not silently reintroduce required Silver/Tin/Platinum/Wolframite/Tungsten/Obdurium progression.

## IND-GRAPH-6 — capability payoff

Every strategic route can be explained by the engineering capability it enables.

## IND-GRAPH-7 — process payoff

Heated, superheated, electrical, refinery, and optional foundry stages each produce visibly different industrial capability.

## IND-GRAPH-8 — cross-dimensional coherence

Nether and End inputs feed the same industrial civilization rather than isolated mini-tech trees.

## IND-GRAPH-9 — recipe discoverability

JEI/EMI/quest guidance presents the canonical route without requiring the player to understand mod namespaces.

## IND-GRAPH-10 — subtraction safety

Removing a rejected material does not strand a retained machine/capability.

# Acceptance principle

> The player should perceive one evolving industrial civilization: raw geography becomes manufacturing, manufacturing becomes capability, and capability becomes routes and infrastructure.
