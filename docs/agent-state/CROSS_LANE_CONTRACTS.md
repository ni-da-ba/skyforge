# Skyforge Cross-Lane Contracts

**Status:** Canonical concise coordination state  
**Updated:** 2026-09-06 (America/Chicago)  
**Repository snapshot when updated:** `cab1be65f370f3f81a6d8019ed3db23bcff6f49f`

## Program-wide invariants

- Backend-neutral modules remain free of Minecraft/NeoForge ontology unless a neutral abstraction is justified.
- Authorship owns world meaning; Implementation owns realization/lifecycle; Content owns game integration/experience.
- Exact three-dimensional ownership and deterministic identity remain fundamental.
- Existing mods/content are asset and capability libraries under Skyforge semantic authority.
- Do not solve poor morphology or ecological legibility by increasing content density.
- Negative space is an intentional part of the sky-world scale fantasy.

## Current lane snapshot

| Lane | Repository-visible boundary |
| --- | --- |
| Authorship | `AUTH-0085` merged on current `main` (native spring semantic admission) |
| Implementation | `SF-IMP-0079` merged before current Authorship work (post-cave vegetal routing) |
| Content / Experience | C13 Elytra/firework bypass suppression accepted; C10 live 1:1 Nether-scale proof, C9 CC:Tweaked avionics substrate, and C8 glider-maintenance closure also accepted; C11/C12 remain separately in progress/reserved |
| AUDIT | AUDIT-0001 repository-state reconstruction / durable audit handoff accepted on `main` |

Use each lane's own state file and git history for detail. Do not infer acceptance solely from an old
architecture summary; some older runtime overview docs lag current `main`.

## Authorship / Implementation world-realization contracts

### MERGED / ACCEPTED — AUTH-0085 native spring admission

AUTH-0085 merge: `335978905f5c5e235c07a114a653a3be24536c47`.

Implementation's native `FLUID_SPRINGS` path should enforce:

- water candidates require authored cave interior plus accepted AUTH-0023 `AQUIFER_BODY` support;
- no adapter-local second aquifer threshold;
- molten/lava candidates fail closed until explicit geothermal/volcanic authorship exists;
- existing exact-volume generated-fluid provenance/fencing remains authoritative after admission.

### IN PROGRESS — AUTH-0086 visible authored hydrology

PR #241 projects accepted coherent naturalized channels, retained-waterbody footprints/margins, and
accepted channel-drop events into exact backend-neutral visible-water intents.

Implementation should not build authored channel/waterfall runtime behavior against AUTH-0086 until
that PR is merged/accepted.

### MERGED / ACCEPTED — current Implementation capability relevant to Authorship

SF-IMP-0079 is the latest merged Implementation milestone. The runtime already provides exact-volume
ownership/admission, native population, authored/native cave composition, post-cave interior
population, and generated-fluid fencing/persistence. Authorship should target those seams rather than
inventing a parallel backend lifecycle.

### MANUAL VERIFICATION REQUIRED — morphology quality

AUTH-0083/AUTH-0084 review machinery is merged, but issue #214 remains the human gate. Implementation
must preserve deterministic handoff IDs when producing above/approach/below views and flight routes.
The gate determines whether current underside morphology is sufficient or needs an explicit
underside-secondary vocabulary.

## Active coordination contracts

### Bootstrap Province

Issue **#224** is the organizing Content vertical slice.

Final content acceptance depends on a visually legible land-biome specimen, currently tracked by
Implementation issue **#194**. Content must not judge ecology from gravel/ocean showcase fixtures.

Required progression shape:

```text
spawn -> survival foothold -> Create workshop -> cheap glider -> shared thermals/fauna
-> first powered aircraft -> regional specialization -> freight/infrastructure
-> evidence of mature skyborne civilization
```

First powered flight should remain pre-Brass/pre-petroleum unless executable closure disproves it.

### Atmosphere and lift

Aerodynamics4MC is the leading single atmosphere authority.

- Aircraft consume it through the retained Aeronautics compatibility path.
- C6 proves the retained Fowl Play red-tailed hawk can enter/exit thermal SOAR from the same field.
- C7 proves Reliable Gliders can consume trusted vertical lift after native glider physics.
- C8 closes Phantom-gated glider maintenance with ordinary leather/wool repair.
- C13 / PR #247 proves the exact pinned No More Elytra Boosting 1.0.0 runtime removes firework propulsion while preserving Elytra fall-flying and ordinary block-launched fireworks.
- Blast/damage/instability feedback for attempted boost is optional UX, not required for mobility-integrity acceptance.
- Other lanes must not introduce a second independent wind/thermal authority without reopening this contract.

### Ecology

Authorship/environment semantics determine viable niches and population opportunity.
Content maps retained species into those niches.
Atmosphere may alter behavior (for example thermal soaring) but must not independently create population.

### Nether route scale

C10 / PR #232 proves a 1:1 Nether `coordinate_scale` datapack reaches the live final runtime registry.
Final route policy still requires portal linking/placement and retained-mod compatibility evidence.

### Structures / civilization

Content defines gameplay roles and reuse-first asset strategy.
Authorship provides site/environment semantics.
Implementation owns realization modes and lifecycle safety.

Leading realization modes remain:

- surface-supported;
- settlement/network;
- subsurface;
- cliff/underside;
- detached;
- structure-seeded terrain.

Progression-critical structures must remain obtainable.

### Computing

Computing is a first-class capability axis but is **not** a first-flight prerequisite.

C9 / PR #230 accepts the runtime coexistence of CC:Tweaked 1.119.0 + Create: Avionics 0.5.2 with
the retained Create/Sable/Aeronautics stack.

Next contract questions are gameplay capability/bypass:

- autopilot versus route/navigation gameplay;
- turtles versus resource/freight geography;
- wireless/rednet/GPS versus infrastructure value;
- thin Skyforge peripherals only for genuinely Skyforge-owned semantics not already exposed.

Bootstrap computing requirements remain tracked in **#224**.

### Bellanca / first mature utility aircraft

Merged design contracts define the Giuseppe Bellanca / B0 engineering mule.
The aircraft must be a real Sable/Create Aeronautics contraption and support useful power-off flight.

Issue **#237** and draft PR **#240** track the opt-in Portable Engine cutoff needed to conserve active fuel during
intentional engine-off soaring. The PR has stationary retained-stack runtime evidence, but assembled-Sable,
save/reload, two-engine aircraft behavior, and human ergonomics remain unaccepted.

## Handoff discipline

When one lane changes a contract another lane relies on, update this file with:

- the changed invariant;
- the owning lane;
- the concrete issue/PR/doc;
- whether the change is accepted, in progress, or proposed.

Keep detailed history out of this file.

## Durable-state namespace

The canonical live agent-state namespace is `docs/agent-state/`.

Parallel lane-state work should add its unique lane file there and reuse this charter/contracts layer. Do not
establish a second canonical program charter or cross-lane contract set under `docs/handoffs/`; that directory
may continue to contain historical milestone handoffs.
