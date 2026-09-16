# Skyforge Nether / Cosmology Strategy

**Status:** worldbuilding and backend strategy; not a Bootstrap implementation gate  
**Scope:** Minecraft backend interpretation of the Nether and End, with backend-neutral separation preserved

## Core decision

Skyforge should **keep the Nether light in the Minecraft backend rather than remove and reconstruct it wholesale**.

The Nether is useful because Minecraft and retained mods already depend on its resources, mobs, structures, progression hooks, and spatial behavior. Rebuilding all of those dependencies would create a large amount of bespoke work for limited gain.

Skyforge therefore treats the Nether as a **Minecraft-backend realization of an anomalous underworld / chthonic frontier**, not as a mandatory piece of backend-neutral Skyforge cosmology.

A future non-Minecraft backend may omit it, reinterpret it radically, or replace its gameplay role entirely.

## Nether interpretation

The Nether is genuinely anomalous. It is associated with phenomena that are already part of the Minecraft-backed Skyforge world:

- souls and soul-bearing materials;
- undeath and Wither phenomena;
- Blazes and other anomalous heat-associated entities;
- Ghasts and other nonordinary fauna/entities;
- Piglin civilization;
- ancient or poorly understood structures;
- distorted spatial correspondence with the ordinary world.

It does **not** need to be explained away as ordinary geology. Skyforge is materially coherent, not materially exclusive: ordinary geology, weather, aerodynamics, ecology, economics, and engineering remain serious physical systems while reproducible supernatural/anomalous phenomena may also exist.

The Nether may be called or culturally interpreted as "Hell" by some peoples without the setting requiring one definitive theological explanation.

## Relationship to the physical world

The Nether should not simply be defined as "farther down than the Lower Sea."

The Lower Sea remains a physically lower region of the ordinary planetary world. The Nether is better treated as an anomalously adjacent or chthonic region whose exact spatial relationship to ordinary geography is incompletely understood.

Researchers may be able to measure access conditions, coordinate correspondences, resource behavior, and entity ecology without possessing a complete theory of what the Nether ultimately is.

## Access strategy

The largest Minecraft-specific integration change should be **portal access**, not wholesale Nether replacement.

Default vanilla behavior:

```text
portable obsidian frame
+ flint and steel
-> arbitrary Nether gateway
```

is too geographically cheap for Skyforge.

Preferred Skyforge behavior:

```text
rare / eligible access site
+ anomalous boundary condition or substantial engineered infrastructure
-> stable Nether gateway
```

Early or ordinary access should therefore be geographically meaningful. Candidate realizations include:

- rare natural or ancient breaches;
- gateways associated with exceptional geological/anomalous sites;
- fortified access stations;
- old ruined gateways;
- Guild- or settlement-controlled crossings;
- later-game artificial gateways requiring meaningful engineering rather than trivial obsidian construction.

The exact access mechanic remains a later Content / Implementation decision.

## Spatial distortion

Vanilla Nether coordinate compression may be retained and reinterpreted as a **measurable anomalous spatial correspondence**.

This is useful because it allows the Nether to support a distinct infrastructure network without making ordinary aviation obsolete, provided access points are scarce and geographically constrained.

Conceptually:

```text
Province A
  |
Nether access
  |
fortified anomalous route
  |
Nether access
  |
Province B
```

The exact ratio need not become backend-neutral canon. Minecraft may keep its native ratio unless later tuning shows a clear reason to change it.

## Nether presentation strategy

Skyforge should transform the Nether through **small integration fixes and selective content composition**, not rebuild it from scratch.

Desired character:

- oppressive enclosed scale rather than open sky;
- larger caverns and stronger vertical relief where practical;
- basalt, blackstone, ash, lava, quartz, fungi, sulfurous/geothermal environments;
- darker route language with civilization expressed through lit tunnels, bridges, rail, stations, bastions, and fortified crossings;
- long quiet or sparsely inhabited stretches rather than constant theme-park mob density;
- preserved strange ecology where it contributes useful gameplay or atmosphere.

Existing mods may be used selectively for terrain, structures, ecology, and presentation if they integrate cleanly with the pinned backend.

The Nether should remain **useful and interesting but not indispensable to Skyforge's identity**.

## Structures

### Bastions

Bastions may remain living or recently occupied Piglin strongholds / settlements. Piglins can represent a civilization adapted to enclosed anomalous space rather than the open sky.

### Nether Fortresses

Fortresses may remain ancient, dangerous, and incompletely explained. Their builders and original purpose need not be solved immediately. Blazes and Wither Skeletons may remain strongly associated with them.

### Strongholds

Strongholds may remain in the ordinary world even if the primary Skyforge interpretation of the End changes.

Their portal rooms can remain an **alternate anomalous route to the End** rather than the only canonical way to reach it.

This preserves expensive vanilla content while turning the old progression into archaeology rather than obligation.

## End strategy

The current End reinterpretation remains coherent and should be preserved as the leading Skyforge model.

The End is best understood as an **extreme upper-atmosphere / near-exospheric region** containing some of the highest surviving ejecta from the ancient planetary catastrophe.

Important consequences:

- air pressure becomes progressively lower with altitude;
- ordinary aviation becomes increasingly expensive and eventually inadequate;
- civilization thins with altitude and ultimately disappears;
- the highest surviving rock is naturally biased toward unusually strong levitic content;
- Levitite therefore makes sense as a highly enriched expression of the same distributed material/phase that helps vertically pin floating islands;
- the planet's ancient catastrophe may have thrown some fragments almost to the exosphere, where strongly pinned material remained.

Primary Skyforge route:

```text
ordinary aviation
-> advanced high-altitude flight
-> extreme ascent technology / infrastructure
-> End / uppermost sky
```

Alternate Minecraft-backend route:

```text
stronghold
-> ancient portal mechanism
-> same End region
```

The stronghold route may be interpreted as an ancient spatial shortcut to a physically extreme region that modern engineering can also reach by ascent.

This preserves both the altitude-based cosmology and useful vanilla archaeology.

## Floating-island levitic material model

The current leading model is:

- floating islands contain a difficult-to-isolate levitic material or mineralogical phase distributed through their rock;
- the material reacts to a planetary/core-linked condition and strongly resists vertical displacement rather than simply producing upward thrust;
- island altitude therefore preserves some combination of ancient emplacement history and levitic concentration;
- ordinary mining does not trivially destabilize islands because the response is distributed through enormous geological volumes and structural networks;
- nature separated/enriched the strongest material over geological time much more effectively than civilization can;
- extreme upper-atmosphere rock is consequently a plausible natural source of concentrated Levitite.

The precise physics, catastrophe mechanism, and industrial isolation process remain open for later refinement.

## Relationship to future Deep gameplay

A future backend may implement large-scale Deep cave systems, drilling, massive ore bodies, extreme cave biomes, and terrestrial industrial gameplay without requiring those systems to exist in the Minecraft backend immediately.

For Minecraft, the Nether already provides a useful inverse spatial experience and can absorb some of that gameplay value with comparatively low integration cost.

Therefore:

> Do not require a bespoke Deep dimension or full Deep implementation merely to replace the Nether in the current backend.

## Backend-neutral principle

Skyforge should retain the following semantic separation:

```text
CORE SKYFORGE WORLD
- floating-island civilization
- altitude / pressure / weather
- ecology / logistics / engineering
- Lower Sea
- levitic geology / ancient catastrophe
- extreme upper-sky progression

MINECRAFT-BACKEND OPTIONAL COSMOLOGY
- Nether as anomalous chthonic frontier
- Nether-specific mobs/resources/structures
- stronghold shortcut to End
- vanilla/modded spatial anomalies
```

The Minecraft backend may use the Nether extensively enough to feel coherent, but **future backends must not be required to reproduce Minecraft's Nether merely to remain faithful to Skyforge**.

## Development guidance

1. Keep the Nether in the Minecraft backend unless concrete dependency or gameplay evidence argues otherwise.
2. Prefer small integration fixes over wholesale replacement.
3. Restrict / reinterpret portal access before changing large amounts of Nether content.
4. Preserve useful mobs, resources, structures, and progression dependencies unless they actively conflict with Skyforge.
5. Use selected existing mods where they produce the desired scale, ecology, or structure language without seizing Skyforge semantic authority.
6. Keep the Nether secondary to the main sky world.
7. Preserve the End-as-uppermost-sky interpretation as the leading model, with strongholds as an optional ancient shortcut.
8. Do not make any of this a Bootstrap blocker.
