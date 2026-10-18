# Creature Niche and Overlap Audit v0.1

**Snapshot:** 2026-09-29  
**Status:** Working audit. Not yet a final fauna lock.

## Scope

This audit evaluates the currently preferred fauna libraries by ecological role rather than by raw creature count:

- Naturalist;
- Fowl Play;
- Critters & Companions;
- Sky Whales;
- vanilla fauna;
- bespoke Skyforge species only where a genuine niche remains.

Skyforge remains authoritative for:

- habitat feasibility;
- island carrying capacity;
- population admission;
- realized density;
- predator/prey balance;
- migration/weather response;
- exceptional-creature placement;
- cross-mod ecological relationships.

Installed mods provide entity implementations, behaviors, art, sounds, and local interactions.

## Core selection direction

| Library | Preferred role | Working disposition |
|---|---|---|
| Vanilla | baseline domestics/common fauna | Core |
| Naturalist | broad terrestrial, wetland, reptile, invertebrate, freshwater, marine, and megafauna library | Strong keep |
| Fowl Play | ordinary avian ecology and flying-bird behavior | Strong keep / bird authority |
| Critters & Companions | selective microfauna, small companions, and distinctive aquatic/forest niches | Strong provisional, curate overlaps |
| Sky Whales | exceptional aerial megafauna + limited biological lift progression | Strong provisional keep |
| Bespoke Skyforge fauna | only missing iconic/systemic niches | Reserve |

## Bird authority

Fowl Play should be preferred wherever it overlaps Naturalist or Critters & Companions because its birds have dedicated flying, perching, foraging, sleeping, height-band, and hunting behavior.

### Fowl Play current bird set

- Emperor Penguin
- Herring / Ring-billed Gull
- American Robin
- Rock Pigeon
- Northern Cardinal
- Blue Jay
- House Sparrow
- Black-capped Chickadee
- Common Raven
- Mallard Duck
- Red-tailed Hawk
- American / Carrion Crow
- Greylag / Canada / Swan Goose
- domestic Emden / Chinese Goose

### Working overlap cuts

| Overlap | Preferred implementation | Reason |
|---|---|---|
| Naturalist generic Bird variants vs Fowl robin/blue jay/cardinal/sparrow/chickadee | Fowl Play | richer dedicated bird behavior |
| Naturalist Duck vs Fowl Mallard | Fowl Play | stronger flight/waterfowl behavior |
| C&C Shima Enaga vs Fowl small cold-forest songbirds | Fowl Play by default | same ambience niche; retain Shima only if visually/behaviorally distinctive enough |
| Naturalist Vulture | Naturalist for now | Fowl vultures are planned, not current |
| Naturalist Ostrich | Naturalist | Fowl ostrich is planned, not current |

Fowl Play should not automatically own every future planned bird. Re-audit when vultures, owls, eagles, woodpeckers, falcons, or ostriches actually ship.

## Aerial ecology

### Ambient small birds

Preferred: Fowl Play robin, cardinal, blue jay, sparrow, chickadee, pigeon.

Use island/habitat admission so a forest does not realize every compatible songbird simultaneously.

### Corvids

Fowl Raven and Crow occupy intelligent scavenger/generalist roles.

Suggested realization:

- ravens: cliffs, mountains, sparse/cold woodland, remote settlements;
- crows: farms, settlements, temperate woodland, disturbed landscapes;
- pigeons: settlements, ports, cliffs, built environments.

### Raptors

Fowl Red-tailed Hawk is the current ordinary aerial-predator authority.

Weather integration target:

- sample authoritative wind;
- prefer ridge lift / thermals;
- shelter or alter behavior in severe turbulence;
- use prey-bearing habitat rather than generic biome-only spawning.

A bespoke Skyforge cliff raptor remains reserve-only if a larger, more aviation-scale iconic predator is later required.

### Scavengers

Naturalist Vulture currently fills the large arid scavenger role.

Potential future overlap with Fowl vultures should be A/B tested rather than stacked.

### Aerial megafauna

Sky Whales remain a separate exceptional niche, not an avian competitor.

Their biological lift progression is acceptable provisionally because it is:

- rare;
- hunt/encounter based;
- biologically sourced;
- limited compared with scalable End-derived Levitite.

Preserve the distinction:

~~~text
Sky-whale lift
  -> rare / organic / limited / encounter-driven

Levitite
  -> engineered / scalable / mature passive-lift technology
~~~

Native near-player Phantom-style whale spawning should still be subordinated to Skyforge exceptional-fauna admission.

## Naturalist broad-fauna roles

Naturalist 2.0 materially improves its usefulness as an ecological library. Current behavior includes explicit predator cooldowns, herds/schools, configurable removals, ant colonies, aquatic food-chain behavior, nocturnal hostility, and large predators.

### Temperate forest / meadow

Strong candidates:

- Deer
- Boar
- Bear / Black Bear
- Hedgehog
- Mole
- Rat
- Snake
- Snail
- Butterfly / Caterpillar
- Firefly where wet/moist conditions fit
- Vulture only in appropriate open/arid profiles
- Fowl songbirds layered above these

Do not realize all at once. Use island carrying-capacity and niche slots.

### Large temperate predator roles

Naturalist Bear is useful as an omnivore/predator rather than a generic hostile.

Black Bear and Brown Bear should be treated as regional alternatives, not stacked populations.

Tiger variants should be rare and geography-specific.

### Savanna / open warm biome

Potential large-fauna set:

- Elephant
- Giraffe
- Zebra
- Rhino
- Lion
- Ostrich
- Boar
- Vulture
- selected snakes/scorpions
- Fowl hawks/corvids as appropriate

This set is deliberately too large to realize on one ordinary island.

Large herd/megafauna species should require large habitat envelopes and high carrying capacity.

Suggested ecological rule:

> A savanna profile selects a coherent subset of megafauna/predator roles rather than spawning the entire mod catalogue.

Example coherent profiles:

- grazer-heavy: zebra + giraffe + lion;
- heavy-herbivore: elephant + rhino + sparse scavenger;
- dry/open: ostrich + boar + vulture + reptile/invertebrate niches.

### Tropical / jungle

Potential roles:

- Tiger / Panther variants
- Capybara
- Komodo Dragon
- Lizard
- Snake
- Jungle Scorpion
- Alligator in wet areas
- C&C Red Panda only in suitable bamboo/cooler forest profile
- C&C Leaf Insect / selected bugs
- Fowl corvid/songbird species only where biogeographically appropriate

Again, not every tropical species should co-occur.

### Wetland / riparian

Strong set:

- Alligator
- Hippo on sufficiently large warm wetland
- Capybara
- Otter (C&C)
- Bass
- Catfish
- Naturalist Dragonfly or C&C Dragonfly, not both by default
- Fowl Duck / Goose
- Snail
- Firefly

Hippos and alligators require significantly larger water/shore habitats than ordinary decorative ponds.

### Arid / badland

Strong candidates:

- Tortoise
- Rattlesnake / arid Snake variant
- Desert Scorpion
- Komodo Dragon in selected warm profiles
- Vulture
- Ostrich
- Fowl Raven/Crow only where appropriate

### Cold / alpine

Potential set:

- Mammoth on exceptional large cold islands
- Bear
- Deer
- Fowl Penguin where water/ice habitat exists
- Fowl Raven / Chickadee
- possibly C&C Shima Enaga only if retained after A/B
- Sky Whales may transit rather than reside

Mammoths should be exceptional local megafauna, not common ambient mobs.

## Aquatic ecology

Aquatic content should be admitted only where Skyforge authors sufficiently large/deep water bodies.

### Freshwater

- Naturalist Bass
- Naturalist Catfish
- Naturalist Piranha in tropical/lush waters
- C&C Koi as ornamental/settlement-associated freshwater fish
- C&C Otter as riparian consumer
- dragonflies / wetland microfauna
- Fowl ducks/geese

### Marine / deep-water

Naturalist offers:

- Anglerfish
- Blobfish
- Clam
- Crab
- Giant Isopod
- Great White Shark
- Jellyfish
- Ray
- Starfish
- Whale

C&C offers:

- Dumbo Octopus
- Sea Bunny

These should remain dormant unless the Minecraft realization includes water volumes large enough to support them coherently.

Do not force 'ocean' fauna into small island ponds merely because biome tags match.

### Large marine fauna

Naturalist Whale and Great White Shark are habitat-scale entities.

They require a dedicated deep-water profile and should be absent if the realized Skyforge world does not provide genuine marine spaces.

Sky Whale is not an overlap: it occupies aerial megafauna, not marine megafauna.

## Microfauna audit

This is where Critters & Companions contributes most.

### Strong C&C unique niches

- Jumping Spider
- Leaf Insect
- Ladybug
- Stag Beetle
- Roly-Poly
- Stick Bug
- Acorn Weevil

These are useful for making forests/farms/lush habitats feel alive without adding more large animals.

Their individual pet mechanics should not dictate wild population density.

### Dragonfly overlap

Naturalist Dragonfly and C&C Dragonfly occupy the same broad niche.

Working disposition: A/B rather than keep both.

Selection criteria:

- flight behavior;
- visual quality;
- spawn/performance cost;
- wetland ecological behavior;
- weather/wind adaptability;
- whether C&C's taming/armor mechanics fit tone.

### Snail overlap

Naturalist Snail has an integrated lifecycle: defensive shell behavior, eggs, slime-ball production, cave/temperate spawning.

C&C Snail is primarily a companion/utility creature producing snail slime for resistance brewing.

Working preference: **Naturalist Snail**, disable C&C Snail unless its companion mechanics prove uniquely valuable.

## Critters & Companions medium/small fauna

### Strongly useful

- Otter: distinct riparian niche
- Ferret: small terrestrial predator/companion
- Red Panda: distinct bamboo/forest specialist
- Koi: ornamental freshwater / settlement pond niche
- Dumbo Octopus: distinctive deep-water specialist if marine habitat exists
- Sea Bunny: distinctive marine microfauna if marine habitat exists

### Conditional

- Shima Enaga: overlaps Fowl cold-forest songbird ambience
- Dragonfly: overlaps Naturalist
- Snail: overlaps Naturalist

## Predator/prey coherence

Prefer explicit food-web relationships where the underlying mods already support them.

Known Naturalist 2.0 examples include:

- bears hunting Salmon/Bass/Deer;
- Great White Sharks hunting fish;
- Komodo Dragons hunting chickens/rabbits/lizards/snakes/boars;
- Piranha schools targeting aquatic prey and players;
- Tigers hunting boars/pigs/deer/zebras/snakes;
- snakes hunting rabbits/chickens/silverfish/snails/slimes;
- catfish hunting fish/tadpoles/bass;
- blobfish hunting crabs/snails;
- turkeys hunting ants.

Skyforge should not attempt to simulate a full ecological population model initially.

Instead, use:

- niche admission;
- compatible predator/prey tags;
- bounded local populations;
- mod-native behavior;
- weather/habitat preferences;
- periodic population sanity checks.

## Domestic / settlement fauna

Vanilla remains the principal livestock substrate.

Fowl Play adds useful settlement-facing birds:

- domestic goose variants;
- chicken visual variants;
- pigeons around dense habitation.

Naturalist/C&C tameable animals should remain companions or specialist working animals rather than replace core livestock.

### Pack / riding animals

Naturalist 2.0 allows some large tameable animals to serve as mounts or carry inventories.

This is acceptable provisionally because they are surface-bound and do not erase inter-island logistics.

Still audit:

- Elephant chest capacity;
- Mammoth chest capacity;
- Ostrich speed/jump;
- any rideable Deer/Giraffe/Zebra behavior still present in the current release.

They should complement local/preindustrial mobility rather than outclass engineered ground vehicles.

## Density doctrine

High installed biodiversity must not imply high simultaneous entity density.

Suggested realization hierarchy:

~~~text
island habitat profile
  -> feasible niche set
  -> carrying-capacity class
  -> choose representative species
  -> establish local population budget
  -> admit entities
  -> weather / disturbance / predation modify behavior
~~~

Examples:

- tiny exposed island: perhaps gulls + one microfauna niche;
- small forest island: 1 small herbivore + 1-2 bird niches + sparse bugs;
- medium wet island: riparian fish + otter + waterfowl + microfauna;
- large savanna island: several grazers + one apex predator + scavenger;
- exceptional cold island: mammoth herd + supporting smaller fauna.

## Performance concerns

Audit acceptance must measure:

- active pathfinding count;
- flying-entity cost;
- bird midair spawning behavior;
- microfauna entity density;
- schooling fish;
- herd/pride group sizes;
- portal / dimension transfer;
- persistent tameables;
- weather-response AI overhead.

Naturalist 2.0.3's mob-removal config now suppresses disabled mobs before they consume mob-cap attempts, which supports aggressive curation.

Fowl Play 1.2.1 explicitly reduced non-ambient bird spawn frequency, but Skyforge should still own final bird-density policy.

## Provisional overlap recommendations

### Prefer Fowl Play

- Robin
- Cardinal
- Blue Jay
- Sparrow
- Chickadee
- Duck
- Raven
- Crow
- Hawk
- Gull
- Goose
- Penguin
- Pigeon

### Prefer Naturalist

- large mammals / terrestrial herbivores;
- terrestrial apex predators;
- reptiles;
- amphibious megafauna;
- freshwater food chain;
- broad marine fauna;
- Butterfly/Caterpillar/Firefly;
- Snail;
- Vulture for now;
- Ostrich for now.

### Prefer Critters & Companions

- Otter
- Ferret
- Red Panda
- Koi
- Jumping Spider
- Leaf Insect
- Ladybug
- Stag Beetle
- Roly-Poly
- Stick Bug
- Weevil
- Dumbo Octopus / Sea Bunny if marine habitat exists

### A/B duplicates

- Dragonfly: Naturalist vs C&C
- Shima Enaga vs Fowl cold-forest small-bird role
- C&C Snail vs Naturalist Snail

### Exceptional

- Sky Whale retained with Skyforge population authority and limited biological-lift progression.

## Remaining real gaps

Current stack does **not** obviously require another broad animal mod.

Potential gaps worth revisiting later:

- bats / nocturnal flying insectivores;
- cliff-specialist iconic raptor if Fowl Hawk is too ordinary;
- true pollinator ecosystem if vanilla bees + butterflies/ladybugs prove too shallow;
- large non-avian soaring fauna;
- dimension-specific Nether / End ecology;
- island-specific endemic species that communicate unusual geology or weather.

These are candidates for narrow mods or bespoke Skyforge fauna, not justification for another large catalogue mod.

## Working conclusion

The current four-mod fauna stack is broad enough.

The correct next work is not finding more creatures. It is:

1. disable overlapping species;
2. define Skyforge habitat/niche tags;
3. assign representative species to those niches;
4. enforce low realized density;
5. connect selected flying fauna to authoritative wind/thermals;
6. test performance and persistence.

> Installed biodiversity is a palette. Skyforge decides what actually lives on an island.
