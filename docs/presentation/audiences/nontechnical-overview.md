# Skyforge at a Glance

**Audience:** nontechnical reviewers, friends/family, generalist recruiters, project introductions  
**Claim freshness checked:** 2026-09-07 against current `main`, README, Implementation state, and cross-lane contracts.

## One-sentence explanation

**Skyforge is a world-building engine that starts with what a place is supposed to *mean* - its landform, geology, water, ecology, and role in a larger region - and turns that meaning into a deterministic three-dimensional world that can be reproduced and tested. Minecraft is its first working realization, not the definition of the system.**

## The central idea

Most procedural terrain systems begin by asking where to put terrain. Skyforge begins one level higher:

> What kind of place is this, and what consequences should follow from that identity?

A Massif should not merely have a different silhouette from a Tableland. Its shape, internal volume, local terrain character, geology, water opportunity, ecological opportunity, and later gameplay role should be able to derive from the same authored world meaning.

That produces a simple mental model:

```text
MEANING
  -> LAND + INTERNAL STRUCTURE
  -> WATER / GEOLOGY / ECOLOGY OPPORTUNITY
  -> EXACT 3-D WORLD VOLUME
  -> MINECRAFT REALIZATION
  -> REPRODUCIBLE EVIDENCE
```

## What is already real

Skyforge is pre-release, but its core architecture is working and accepted across several layers.

### 1. It can author worlds as systems, not disconnected decoration

The backend-neutral engine already represents five primary floating-island landform families - **Massif, Tableland, Spine, Basin, and Lobed** - along with higher-order composition machinery and authored evidence for hydrology, ecology, geology, regional relationships, and site conditions.

### 2. It builds finite three-dimensional terrain, not just a painted surface

A Skyforge island has exact three-dimensional ownership, an upper surface, an underside, internal space, and explicit boundaries. Separate islands can even occupy the same horizontal coordinates at different heights without becoming one global terrain column.

### 3. The same authored terrain survives contact with a real game engine

Minecraft 1.21.1 / NeoForge is the first runtime backend. Accepted implementation work already brings Skyforge terrain through real game lifecycle concerns such as biome/surface population, caves, lakes, ores, underground decoration, springs, structures, save/reload, deferred generation, and actual-client reopen while preserving Skyforge ownership rules.

### 4. The result is reproducible rather than merely visually plausible

Skyforge treats determinism as an engineering contract. Authored descriptors, procedural graphs, sampled geometry, exact ownership, and evidence identities can be regenerated and compared. Screenshots are useful, but they are not the sole proof that the system behaved correctly.

## Why that is unusual

The project is not trying to become a larger collection of random terrain tricks. Its architectural wager is that world generation becomes more coherent when many visible consequences come from a shared semantic model.

Instead of independently deciding:

- where mountains go;
- where water goes;
- where resources go;
- where forests go;
- where settlements could fit;

Skyforge is being built so those decisions can increasingly become consequences of one world description.

## What not to claim yet

Skyforge does **not** yet claim:

- a finished player-facing world or stable public API;
- multiple accepted production backends;
- complete Minecraft realization of every authored geology, hydrology, ecology, resource, and structure system;
- a completed Bootstrap Province or final game experience.

Those are roadmap boundaries, not current capability.

## Where the roadmap is going

The current program is converging toward a deterministic **Bootstrap Province**: a coherent playable region where terrain morphology, geology/materials, hydrology, caves, ecology, resources, structures, mobility, industry, and progression reinforce one another rather than being generated as unrelated layers.

If that vertical slice succeeds, a player should be able to look at a region, travel through it, exploit it, build infrastructure across it, and gradually discover that the world's geography has consequences.

## Thirty-second spoken version

Skyforge is a procedural world-synthesis engine. Instead of starting with random blocks, it starts with the meaning of a place - whether it is a massif, basin, tableland, what its geology and water systems imply, and how it fits into a region. That description compiles into deterministic three-dimensional terrain with exact ownership and reproducible evidence. Minecraft is the first real backend, and the engine already survives real game systems like caves, vegetation, structures, persistence, and save/reload. The long-term goal is a world where geography, resources, ecology, travel, and civilization all feel like consequences of the same underlying world rather than separate random generators.

## Evidence pointers

- Root `README.md` - current project definition, principles, capabilities, and engineering-proof summary.
- `docs/agent-state/IMPLEMENTATION_STATE.md` - accepted Minecraft realization through SF-IMP-0082 and current SF-IMP-0083 frontier.
- `docs/agent-state/CROSS_LANE_CONTRACTS.md` - current accepted Authorship, Content, Implementation, and program boundaries.
- `docs/agent-state/PROGRAM_CHARTER.md` - long-range Minecraft realization objective and lane ownership.

Presentation artifacts are subordinate to those authorities and should be refreshed when a producer milestone materially changes an external claim.
