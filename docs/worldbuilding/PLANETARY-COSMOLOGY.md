# Skyforge Planetary Cosmology

**Status:** current worldbuilding direction; intentionally leaves ultimate causes unresolved  
**Scope:** backend-neutral physical cosmology plus Minecraft-backend realization notes  
**Implementation priority:** documentation / future systems guidance; not a Bootstrap blocker

## Design principle

Skyforge should feel physically coherent without requiring that every phenomenon be mundane.

The ordinary world obeys recognizable gravity, atmosphere, weather, hydrology, geology, ecology, logistics, and engineering constraints. A small number of genuinely anomalous phenomena — especially levitic material, the Nether, undeath, and some extreme-upper-atmosphere life — may remain only partly understood.

Scientific observation and supernatural interpretation are allowed to coexist.

## Planetary baseline

The ordinary world is a roughly terrestrial planet with:

- approximately ordinary Minecraft gravity unless a later mechanic requires adjustment;
- one sun;
- one moon;
- ordinary stars;
- conventional seasons / axial geometry;
- a substantial atmosphere with meaningful pressure decline at altitude;
- a global or near-global Lower Sea beneath the principal inhabited sky-island bands;
- a persistent deep cloud deck that commonly obscures the Lower Sea from the inhabited sky;
- extreme upper-atmosphere island fields represented by the Minecraft End backend.

Educated societies can infer the planet's curvature mathematically through navigation, celestial observations, horizon geometry, surveying, and differential solar / stellar measurements. Direct visual knowledge of most of the planet is still incomplete.

## Ancient catastrophe

### Current preferred model: lithospheric spallation / ejection

The floating islands should not exist because an event somehow selected neat island-sized blocks.

Instead, a prehistoric planetary catastrophe imparted an unimaginably violent upward mechanical pulse to large portions of the old crust. Existing tectonic faults, strata, fracture networks, crustal blocks, and structural discontinuities caused the crust to fail across a broad size spectrum:

```text
dust / small debris
-> largely returned, eroded, or was lost

moderate coherent fragments
+ sufficient distributed levitic material
-> remained vertically pinned
-> modern floating islands

enormous slabs / weakly pinned masses
-> insufficiently accelerated, fractured, or returned
-> Lower Sea remnants / Dark Mountains / submerged old crust
```

The catastrophe **launched** the rock. Levitic material did not create the initial uplift.

The present island population is therefore a selection effect: the surviving fragments are the pieces whose size, integrity, emplacement velocity, and levitic composition allowed them to remain aloft.

### Ultimate cause

The ultimate trigger should remain unresolved for now.

Modern science may eventually reconstruct the mechanics from evidence such as:

- globally correlated shocked or disturbed strata;
- immense breccia / catastrophe deposits;
- matching geological units now separated by huge distances;
- fracture surfaces preserved on island undersides;
- anomalous mantle / volcanic provinces;
- magnetospheric or core-linked anomalies;
- drowned old-surface remnants.

Knowing how the event propagated does not require knowing why it began.

## Levitic material and island stability

Floating islands contain a difficult-to-isolate levitic phase distributed through ordinary rock.

The leading interpretation is that this material strongly **resists vertical displacement** under a planetary/core-linked condition rather than producing ordinary upward thrust.

Consequences:

- island altitude preserves ancient catastrophic emplacement rather than being a current buoyant equilibrium;
- ordinary mining does not trivially cause islands to fall because the effect is distributed across very large rock volumes and structural networks;
- small fragments are more vulnerable to losing sufficient levitic-bearing structure;
- catastrophic fracture can theoretically detach masses that then fall normally;
- extremely high ancient ejecta underwent natural long-term selection for unusually strong levitic response, explaining Levitite enrichment in the highest sky / End region;
- modern civilization may enrich tiny quantities by precision separation, but natural geological selection was far more effective.

### Motion

On gameplay and ordinary historical timescales, major islands are effectively stationary.

Over geological timescales they may drift slowly, creep vertically, redistribute stress, fragment, or eventually fall. Small islands should be more vulnerable.

A major inhabited island falling during recorded history should be a named civilizational catastrophe, not routine background behavior.

Skyforge therefore does **not** need to simulate ordinary island motion in the Minecraft backend.

## Atmospheric structure and weather

The atmosphere is one of the defining physical systems of Skyforge.

Primary energy / circulation drivers include:

- solar heating;
- evaporation from the Lower Sea;
- planetary rotation;
- vertical temperature gradients;
- island terrain and rain-shadow effects;
- geothermal / tectonic anomalies left by or continuing after the ancient catastrophe;
- possible unusual core / magnetospheric effects where justified.

### Lower Sea as atmospheric reservoir

The Lower Sea acts as an enormous humid thermal reservoir beneath the inhabited sky.

Large-scale circulation transports heat and moisture upward into the island bands. This supports persistent storm tracks, convection, cloud systems, and a strong vertical water cycle.

### Persistent lower cloud deck

A broad, deep lower cloud layer commonly separates inhabited sky from the Lower Sea.

It should read visually as an atmospheric floor rather than scattered vanilla cloud puffs:

- broad stratiform / stratocumulus fields;
- slowly varying cloud-top elevation;
- large shadowed regions;
- embedded storms and lightning;
- rain shafts disappearing downward;
- occasional towering convection penetrating upward;
- rare or regional large openings.

The deck need not be continuously opaque. Large gaps are important because they occasionally reveal the frightening depth beneath the player.

### Atmospheric chimneys and openings

Some persistent or semi-persistent cloud holes may correspond to strong vertical circulation.

Powerful moist updraft:

```text
Lower Sea
   ^
warm humid air
   ^^^
cloud opening / convective tower
   ^
storm activity in island layer
```

Persistent dry downdraft:

```text
cold / dry upper air
   vvv
cloud suppression / opening
   v
clearer view toward the Lower Sea
```

These features can become recognizable climatological geography for pilots.

### Horizontal atmospheric geography

Useful large-scale phenomena include:

- jet streams;
- storm belts;
- convergence zones;
- thermal corridors;
- rain shadows behind large islands;
- dry subsidence regions;
- lower-sea evaporation hotspots;
- volcanic / geothermal convection regions;
- long-lived but not necessarily permanent atmospheric anomalies.

Aviation routes should therefore be shaped by winds and weather as well as geometric distance.

## Hydrological cycle

The island layer and Lower Sea participate in one planetary water cycle:

```text
Lower Sea
-> evaporation
-> upward atmospheric transport
-> clouds / storms
-> island precipitation
-> soil / lakes / aquifers
-> springs / streams / rivers
-> waterfalls from island edges
-> lower atmosphere / cloud deck
-> Lower Sea
```

Some falling water re-evaporates or becomes mist before reaching the sea.

This makes edge waterfalls part of a closed planetary cycle rather than water disappearing into an abstract void.

## Ecology and biogeography

Current preferred direction: most living things share ancestry with pre-catastrophe planetary life.

The catastrophe violently fragmented ecosystems and then isolated populations across island chains, altitude bands, the Lower Sea, and extreme high sky.

This naturally produces island biogeography:

- related species on separated islands;
- endemic island species / subspecies;
- strong cliff, wind, thin-air, and open-sky adaptations;
- easier dispersal for flying organisms and spores;
- strong isolation for terrestrial specialists;
- fossil evidence linking current island life to old terrestrial / marine environments;
- Lower Sea lineages isolated from former surface relatives.

A useful ecological rule is:

> Most strange organisms should still have relatives, ecological history, or recognizable niches.

Not every extraordinary species must be reduced to mundane biology. Dragons, sky whales, Nether entities, and extreme-upper-atmosphere organisms may retain genuinely anomalous or unresolved aspects where useful.

Marine fossils suspended miles above the current Lower Sea are especially valuable environmental evidence of the old world.

## Lower Sea and old surface

The Lower Sea is not assumed to be uniformly abyssal.

Broad scientific expectations may include:

- drowned continental remnants;
- vast submerged shelves;
- abyssal basins;
- old mountain chains;
- enormous sediment fans beneath long-lived waterfall regions;
- volcanic provinces;
- catastrophe deposits;
- Dark Mountains and other rare exposed old-surface remnants.

Some continental remnants may lie near the water surface or intermittently break it. At sufficient scale they should not read as picturesque islands, but as unnervingly large pieces of the old world — barren rock, black water, weather, and horizons that do not reveal obvious boundaries.

Most of this geography remains poorly surveyed. The existence of likely broad classes of feature does not imply precise maps or detailed knowledge of what lives, lies, or survives there.

## Lower cloud deck as visual boundary

From most inhabited regions the typical vertical view should be:

```text
island / inhabited sky
       |
       | open atmospheric depth
       v
===============================
       LOWER CLOUD DECK
===============================
       | occasional gaps
       v
       LOWER SEA
  + old-surface remnants
```

The sea should not be constantly visible. Controlled revelation preserves scale and unease.

The cloud deck can also provide a practical backend boundary: it can be rendered convincingly even when detailed Lower Sea terrain is not instantiated at full block fidelity.

## Celestial presentation

Default astronomical presentation should remain comparatively restrained:

- one sun;
- one moon;
- ordinary stellar field;
- conventional day / night and seasons.

Skyforge's spectacle should primarily come from **atmospheric depth and weather**, not constant exotic celestial decoration.

High-value sky presentation includes:

- vast lower cloud ocean;
- cloud shadows;
- towering anvils and storm walls;
- distant island silhouettes at multiple altitude bands;
- rain curtains;
- crepuscular rays;
- moonlit cloud tops;
- stars becoming more visible at high altitude;
- auroral phenomena;
- meteors / upper-atmospheric events;
- darkening sky as pressure and atmospheric column decline.

Aurorae are particularly compatible with a planet whose core / magnetosphere may have unusual properties after the catastrophe, without requiring additional moons or planetary rings.

## End / extreme upper atmosphere

The End is canonically part of the same physical planetary environment even though Minecraft realizes it as a separate dimension.

It represents an extreme upper atmospheric / near-exospheric island region characterized by:

- very low pressure;
- sparse ancient ejecta;
- unusually high levitic enrichment;
- strong radiation / exposure;
- little ordinary weather;
- a visibly darker sky approaching space;
- organisms that either evolved under extreme-altitude conditions or may have arrived from outside the planet.

Minecraft dimension separation is an implementation boundary, not cosmological separation.

Primary route:

```text
ordinary sky
-> high-altitude flight
-> extreme ascent technology
-> upper-atmospheric transition
-> End-region backend
```

Strongholds may remain as an ancient anomalous shortcut to the same physical region.

Modern engineering reaches it by going **up**. Ancient mechanisms may reach it by going **through**.

## Nether and post-mortem phenomena

The Nether remains a Minecraft-backend-specific anomalous chthonic frontier. It should not be treated simply as physically below the Lower Sea.

The existence of undead makes a real anomalous post-mortem phenomenon useful to the setting.

Current preferred epistemic model:

- living organisms sustain some information-bearing anomalous state;
- after death this usually dissipates;
- under certain conditions some residual state remains coupled to matter;
- the Nether appears strongly associated with conditions that preserve, intensify, or distort this phenomenon;
- undead animation, soul-bearing materials, Withering, and related phenomena may therefore share an observable family resemblance.

This does **not** prove that the measured remnant is a complete conscious person or establish one objectively correct theology.

Religious traditions may call it the soul. Researchers may describe residual informational structure or post-mortem persistence. Neither vocabulary need settle the ultimate metaphysics.

## Catastrophe age

Current recommended order of magnitude: **hundreds of thousands of years ago**, provisionally around 100,000–500,000 years before the present.

This remains deliberately approximate.

That interval is useful because it is:

- far beyond reliable cultural memory;
- geologically recent enough that catastrophe scars remain obvious;
- long enough for substantial island endemism and ecological adaptation;
- short enough that major ancestral relationships remain recognizable;
- compatible with much strange life predating the event rather than requiring implausibly rapid evolution after it.

Do not lock an exact date until history, geology, and ecological design require one.

## Epistemic categories

Worldbuilding should distinguish:

1. **directly observed** — routinely measurable phenomena;
2. **historically documented** — surviving credible records;
3. **scientifically inferred** — models strongly supported by geology, ecology, navigation, or experiment;
4. **genuinely unknown** — unresolved areas where the setting should preserve mystery.

This is especially important for:

- the ultimate cause of the catastrophe;
- the deepest Lower Sea;
- the precise nature of the levitic field;
- the ultimate meaning of post-mortem persistence;
- the full nature of the Nether;
- the origin of some extreme-upper-atmosphere life.

## Minecraft-backend realization guidance

The Minecraft backend should prefer perceptual fidelity over literal planetary-scale simulation.

Likely implementation split:

- real floating-island block terrain in the playable sky bands;
- GPU / renderer-driven local clouds and persistent lower cloud deck;
- semantic weather fields rather than CFD;
- LOD / atmospheric silhouettes for very distant islands and old-surface forms;
- rendered or impostor Lower Sea where visible through distant gaps;
- dimension transition for the extreme upper-atmosphere / End while preserving continuous fiction;
- no requirement to instantiate an always-active planet-sized ocean beneath every sky chunk.

The world can therefore feel many kilometers deep without allocating every apparent meter as full-fidelity Minecraft terrain.

## Non-goals / cautions

- Do not make the catastrophe intentionally produce neat island sizes; surviving islands are a selection effect.
- Do not turn Levitite into ordinary upward-thrust floatstone.
- Do not require routine island-motion simulation.
- Do not overpopulate the Lower Sea merely because hidden space exists.
- Do not solve the ultimate catastrophe trigger prematurely.
- Do not laboratory-prove one final theology of souls / afterlife.
- Do not make every spectacular sky effect a celestial object; atmospheric scale is the primary visual language.
- Do not make literal planetary-scale rendering a prerequisite for delivering the cosmology in Minecraft.
