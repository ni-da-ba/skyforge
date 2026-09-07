# Skyforge Cross-Lane Contracts

**Status:** Canonical concise coordination state  
**Updated:** 2026-09-06 (America/Chicago)  
**Repository snapshot when updated:** `d63f7c712355fb421c909f7bbdc74465a88522e6`

## Program-wide invariants

- Backend-neutral modules remain free of Minecraft/NeoForge ontology unless a neutral abstraction is justified.
- Authorship owns world meaning; Implementation owns realization/lifecycle; Content owns game integration/experience.
- Exact three-dimensional ownership and deterministic identity remain fundamental.
- Existing mods/content are asset and capability libraries under Skyforge semantic authority.
- Do not solve poor morphology or ecological legibility by increasing content density.
- Negative space is intentional.

## Current lane snapshot

| Lane | Repository-visible boundary |
| --- | --- |
| Authorship | `AUTH-0088` accepted: published surface-ecology projection; AUTH-0089 in progress only |
| Implementation | `SF-IMP-0080` accepted: legible forest/taiga ecology showcase; SF-IMP-0081 / #214 morphology review in progress |
| Content / Experience | `C16` accepted: bounded Wireless Modem / universal Ender Modem envelope; C11/C12 remain separately in progress/reserved |
| AUDIT | AUDIT durable reconstruction/reconciliation remains the program-state review layer |

Use each lane state file and merged history for detail.

## Authorship / Implementation realization contracts

### AUTH-0085 — native spring admission — ACCEPTED

Water spring candidates require authored cave interior plus accepted aquifer support. Molten/lava candidates fail closed until explicit geothermal/volcanic authorship exists. Existing exact-volume generated-fluid provenance/fencing remains authoritative.

### AUTH-0086 — visible authored hydrology — ACCEPTED

Implementation may consume exact backend-neutral channel, retained-waterbody, cascade/waterfall, and edge-discharge intents. Implementation owns physical rasterization, block/fluid identity, mutation, scheduling, and lifecycle; normalized semantic potentials must not be treated as literal world-space Y.

### AUTH-0087 — published authored-realization binding — ACCEPTED

Published volumes may bind to authorship only through the explicit exact association catalog/root/count/coverage contract. No seed/order/spatial inference may substitute for the explicit binding.

### AUTH-0088 — published surface ecology — ACCEPTED

For one exact published volume ID + world X/Z, downstream may query unchanged AUTH-0003 ecology only through AUTH-0087 association, compiled horizontal support, and current authored ownership. Native-unowned fringe fails closed. Physical Y, Minecraft biome keys, quart-cell presentation, persistence, and atmospheric biome envelopes remain Implementation-owned.

### AUTH-0089 — island ecological opportunity profile — IN PROGRESS

Authorship is aggregating placement-free ecology regime composition and broad environmental potentials. It must not assign species, spawn counts, carrying capacity, resources, or backend biome identity.

### SF-IMP-0080 — visible land ecology — ACCEPTED

The forest/taiga showcase passed machine persistence/reopen gates and project-owner human visual review. This proves legible Minecraft land ecology, not yet AUTH-0003-to-Minecraft-biome semantic translation. Issue #194 is closed; #261 is a non-blocking short-distance ambience/presentation follow-up.

### SF-IMP-0081 / issue #214 — morphology quality — MANUAL GATE

Implementation must preserve deterministic handoff IDs for above/approach/below views and flight routes. The project-owner review determines whether current underside morphology is sufficient or requires additional authored vocabulary.

## Active coordination contracts

### Bootstrap Province — issue #224

Organizing Content vertical slice:

```text
spawn -> survival foothold -> Create workshop -> cheap glider -> shared thermals/fauna
-> first powered aircraft -> regional specialization -> freight/infrastructure
-> evidence of mature skyborne civilization
```

The previous legible-land dependency is cleared by SF-IMP-0080. Content may proceed with ecology-visible province work while treating #261 as non-blocking. First powered flight should remain pre-Brass/pre-petroleum unless executable recipe closure disproves it.

### Atmosphere and lift

Aerodynamics4MC remains the single accepted atmosphere authority.

- aircraft consume it through retained Aeronautics compatibility;
- C6 proves retained Fowl Play hawk thermal SOAR behavior;
- C7 proves Reliable Gliders can consume trusted post-native lift;
- C8 closes Phantom-gated glider maintenance;
- C13 removes Elytra rocket propulsion while preserving fall-flying and ordinary fireworks.

Other lanes must not introduce an independent wind/thermal authority without reopening this contract.

### Ecology

Authorship/environment semantics determine niches/opportunity. Content maps retained species into those niches. Atmosphere may alter behavior but must not independently create population. AUTH-0088 is the semantic query seam; SF-IMP-0080 is the current visible Minecraft ecology seam. Do not conflate them.

### Nether route scale

- C10 proves live Nether `coordinate_scale=1.0`.
- C15 proves ordinary vanilla portal search/linking and missing-target creation consume that live 1:1 scale at non-origin coordinates with retained Create/Sable/Aeronautics loaded.

Remaining gates: assembled contraption/passenger/cargo transfer, authored portal foothold/site safety, human terminal usability, and eventual permanent cosmology decision.

### Structures / civilization

Content defines gameplay roles/reuse strategy; Authorship provides site/environment semantics; Implementation owns realization modes/lifecycle. Progression-critical structures must remain obtainable.

### Computing

Computing is a first-class infrastructure axis but **not** a first-flight prerequisite.

- **C9 / PR #230:** CC:Tweaked 1.119.0 + Create: Avionics 0.5.2 coexist with retained Create/Sable/Aeronautics.
- **C14 / PR #256:** real CraftOS computers discover/read retained avionics sensors and drive the physical throttle through its upstream 0..15 clamp. No duplicate generic aircraft telemetry/control API is justified.
- **C16 / PR #264**, merge `d63f7c712355fb421c909f7bbdc74465a88522e6`: real stock CC networking proves ordinary Wireless Modems are bounded local/regional infrastructure while Ender Modems erase range and dimension separation.

C16 exact runtime evidence:

```text
normalNearReceived=true  normalNearDistance=48
normalFarReceived=false
enderRemoteReceived=true enderRemoteDistance=512
enderCrossReceived=true  enderCrossDistance=nil
```

Cross-lane consequences:

- do not add a duplicate generic Skyforge networking authority/API;
- ordinary Wireless Modems may remain unnerfed as infrastructure;
- Ender Modems are a mature bypass and should be progression-conscious rather than an unexamined early default;
- final Ender gating, GPS layout, turtle throughput, and autopilot progression remain Content gameplay decisions;
- thin Skyforge peripherals remain reserved for genuinely Skyforge-owned semantics absent upstream.

Bootstrap computing remains tracked in #224.

### Bellanca / mature utility aircraft

The Bellanca B0 must be a real Sable/Create Aeronautics contraption with useful power-off flight. Issue #237 / draft PR #240 tracks the Portable Engine cutoff. Stationary evidence exists, but assembled-Sable, save/reload, two-engine behavior, and human ergonomics remain unaccepted. C12 remains reserved for the executable B0.

## Handoff discipline

When one lane changes a contract another lane relies on, update this file with:

- changed invariant;
- owning lane;
- concrete issue/PR/doc;
- accepted / in-progress / proposed status.

Keep detailed milestone history in lane-specific state/docs rather than here.
