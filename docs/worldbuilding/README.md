# Skyforge worldbuilding records

**Status:** durable worldbuilding entry point.

This directory contains setting-level records whose subject is broader than one implementation lane or one Minecraft subsystem. Fresh audits of Skyforge's setting should start here before inferring cosmology from implementation-focused design audits.

Program-level repository authority still applies: current `main`, `AGENTS.md`, the Program Charter, Validation Policy, lane state, Cross-Lane Contracts, executable source/tests, and merged history take precedence over stale summaries. Within worldbuilding, later explicit locks refine earlier working direction.

## Required read order for cosmology questions

1. [`PLANETARY-COSMOLOGY.md`](PLANETARY-COSMOLOGY.md) — core physical setting: inhabited sky-island bands, the Lower Sea and old planetary surface, ancient lithospheric catastrophe, levitic geology, planetary atmosphere and hydrology, biogeography, and the End as the extreme upper atmosphere / near-exosphere.
2. [`NETHER-COSMOLOGY-STRATEGY.md`](NETHER-COSMOLOGY-STRATEGY.md) — Minecraft-backend interpretation of the Nether as an anomalous/chthonic adjacent region, portal geography, spatial distortion, strongholds, and its relationship to the physically continuous End cosmology.
3. [`ATMOSPHERE-RENDERING-STRATEGY.md`](ATMOSPHERE-RENDERING-STRATEGY.md) — future realization strategy for semantic weather, the persistent lower cloud deck, Lower Sea revelation, Distant Horizons, shaders, and aviation/weather coupling.

## Planetary model in one diagram

```text
SPACE / EXOSPHERE
        |
        | extreme upper atmosphere; sparse ancient ejecta
        | Minecraft End backend
        v
HIGH SKY / END TRANSITION
        |
        | thinning atmosphere, increasing exposure
        v
INHABITED SKY-ISLAND BANDS
        |
        | ordinary civilization, ecology, aviation, weather
        v
OPEN ATMOSPHERIC DEPTH
        |
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
       LOWER CLOUD DECK
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        |
        | occasional openings / storms / circulation chimneys
        v
LOWER SEA
        |
        | drowned shelves, basins, old mountain chains,
        | sediment fans, catastrophe deposits, Dark Mountains
        v
OLD PLANETARY SURFACE / CRUST
```

The **Nether is not another lower physical layer in this stack**. In the Minecraft backend it is an anomalously adjacent/chthonic region with measurable access and spatial relationships but an unresolved ultimate topology.

## Core cosmology invariants

- Skyforge's ordinary setting is a roughly terrestrial planet with one sun, one moon, conventional stars/seasons, gravity, atmosphere, hydrology, geology, ecology, and engineering.
- The inhabited world consists primarily of ancient crustal fragments suspended at altitude after a prehistoric lithospheric ejection/spallation catastrophe.
- Distributed levitic material did not launch the islands; it helps explain why selected fragments remained vertically pinned after emplacement.
- The Lower Sea is a global or near-global physical ocean beneath the inhabited sky and acts as a major humid thermal reservoir for the atmosphere.
- A persistent deep lower cloud deck usually obscures the Lower Sea, preserving atmospheric scale and making direct views of the old world uncommon.
- The island layer and Lower Sea share one planetary water cycle; waterfalls ultimately return water toward the lower atmosphere/sea rather than falling into an abstract void.
- The End is physically continuous with the planet: an extreme upper-atmosphere / near-exospheric field of ancient ejecta, represented by a separate Minecraft dimension for backend reasons.
- Modern engineering may ultimately reach the End by going **up**; strongholds may provide an ancient anomalous route that goes **through**.
- The deepest Lower Sea, catastrophe trigger, precise levitic mechanism, Nether ontology, post-mortem phenomena, and some upper-atmosphere life remain deliberately unresolved.

## Epistemology

Worldbuilding should distinguish:

1. **directly observed** facts;
2. **historically documented** facts;
3. **scientifically inferred** models;
4. **genuinely unknown** questions.

Do not convert an intentionally unresolved model into omniscient narrator canon merely because a gameplay system needs a working implementation.

## Relationship to civilization records

Planetary geography and atmosphere are upstream of civilization rather than decorative lore. They create the route, weather, resource, settlement, aviation, freight, insurance, and contract conditions modeled under [`../civilization/`](../civilization/).

For Guild contracts and inter-settlement economic/legal systems, start with [`../civilization/README.md`](../civilization/README.md) and its contract-system quick read rather than reading only the initial Guild precommit.

## Realization rule

The Minecraft backend should prioritize **perceptual fidelity and causal consistency** over literal planet-scale block simulation. Full-fidelity playable islands may coexist with semantic weather, distant/LOD geography, rendered Lower Sea surfaces, and backend dimension transitions so long as those realizations preserve the accepted world model.
