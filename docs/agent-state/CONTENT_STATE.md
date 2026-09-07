# Skyforge Content / Experience Agent State

**Lane:** Content / Experience  
**Status:** Canonical live lane handoff  
**Updated:** 2026-09-06 (America/Chicago)  
**Main snapshot when updated:** `335978905f5c5e235c07a114a653a3be24536c47`

Read first:

- [Program charter](PROGRAM_CHARTER.md)
- [Cross-lane contracts](CROSS_LANE_CONTRACTS.md)
- [Content integration corpus index](../design-audit/README.md)

## MERGED / ACCEPTED

### Highest executable Content milestone: C7

**PR #223**, merge `a3281a0e6f0fe3e6eb4ce86cacc8470bf6006c88`.

C7 closes the player-facing shared-lift proof:

- Reliable Gliders remains owner of glider mechanics;
- A4MC remains atmosphere authority;
- Skyforge applies only trusted post-native vertical lift;
- real Reliable Gliders gliding-state admission is used;
- stronger native/block updraft wins rather than stacking;
- untrusted atmosphere and non-gliding players are inert.

Focused C2/C3/C5/C6/C7 and normal CI were green for the accepted C7 head.

### C6 — real hawk thermal compatibility

Merged before C7.

Accepted machine evidence against a real Fowl Play red-tailed hawk:

```text
joined hawks        1
brain adaptations   1
SOAR transitions    2
steering commands   3
stock schedule      restored
```

Fowl Play hawk is therefore the retained THERMAL_SOARER realization; no bespoke Skyforge hawk is justified.

### C3-C5 supporting boundary

- C3: A4MC core/compat and measurement stack reached dedicated-server ready state.
- C4: shared-lift consumer contract.
- C5: Fowl Play + SmartBrainLib + YACL + A4MC coexistence proved headlessly.

### Latest merged Content design tranche

**PR #238**, merge `0368261e59da1501266427980780848e331e3e7a`.

Merged records:

- `giuseppe-bellanca-aircraft-contract-v0.1.md`;
- `giuseppe-bellanca-b0-engineering-mule-v0.1.md`;
- `portable-engine-redstone-cutoff-compatibility-contract-v0.1.md`.

These documents are merged design state, **not** accepted flight/runtime behavior.

## IN PROGRESS

### Bootstrap Province — issue #224

This is the central Content vertical slice.

It must prove, through one deterministic starting province:

```text
survival -> Create -> cheap glider -> shared thermals/fauna -> first aircraft
-> specialized destination -> regional freight -> mature infrastructure evidence
```

Computing/CC integration is explicitly part of #224.

### Land-biome visibility dependency — issue #194

Implementation must expose a human-visible persistent land-biome specimen with legible soil/grass/trees/plants.
Do not tune ecology from gravel/ocean showcase fixtures.

### Portable Engine cutoff — issue #237

Opt-in engine cutoff/fuel-pause behavior is open and not implemented/accepted.

## PROPOSED

### Computing

Computing is a first-class capability axis, not a first-flight gate.

Leading reuse order:

1. CC:Tweaked;
2. existing 1.21.1 Aeronautics/CC avionics/peripheral integrations;
3. CBC peripheral integrations where heavy-industry automation remains desirable;
4. thin Skyforge peripherals only for Skyforge-owned semantics that retained mods do not expose.

Progression intent:

```text
local display/control -> sensors/peripherals -> wired automation
-> wireless telemetry/networking -> route/fleet/infrastructure control
```

Executable compatibility/bypass tests are still required before locking specific add-ons.

### First powered flight

Preserve pre-Brass, pre-petroleum first powered flight unless actual recipe closure disproves it.
The later Giuseppe Bellanca B0 is Brass-era because its selected Propeller Bearing/governor stack is more mature.

## MANUAL VERIFICATION REQUIRED

### C1 industrial specimen

C1 scaffolding is merged, but do not claim complete gameplay acceptance until the runtime checks are actually run/recorded:

- live Platinum/Wolframite suppression;
- JEI recipe closure/rejected-material leaks;
- Steel/Bronze identity collisions;
- Metallurgy A/B value;
- Gold-vs-Electrum electrical throughput;
- world-side industrial source throughput.

### Mobility/gameplay closure beyond C7 physics

Machine correctness for shared lift is accepted, but the following experience decisions remain:

- final early glider recipe **and repair** economy;
- practical glider capability envelope;
- Elytra/firework boost suppression in play;
- decision on optional blast/instability feedback;
- 1:1 Nether coordinate-scale policy;
- aircraft-versus-personal-flight freight/logistics comparison.

### Bellanca B0

The merged B0 build specification still requires actual assembly/flight evidence:

- mass/CG;
- propulsion/governor ladder;
- ground handling;
- takeoff/climb/cruise;
- power-off glide/restart;
- atmosphere response;
- payload.

Human-eye aircraft review comes only after mechanical flight credibility.

### Bootstrap Province

Final province acceptance requires visible play in a good land-biome specimen after #194/world-quality convergence.

## Architectural decisions / invariants

- Content source priority: vanilla -> existing mods -> config/datapack/integration -> thin adapter -> bespoke.
- Skyforge owns meaning; do not author duplicate assets without a demonstrated gap.
- Personal mobility is cheap; logistics are not.
- Aircraft must win through payload, repeatability, fluids/entities/contraptions/automation, not blanket nerfs.
- Resource geography creates routes and infrastructure, not repetitive chores.
- Prefer manufactured complexity over redundant ores/material tiers.
- Ecology is niche-first and sparse; atmosphere changes behavior, not population authority.
- Open sky and End-like negative space remain intentionally sparse.
- Do not solve weak geography with mobs/plants/structures.
- First-flight progression should expose aviation before ordinary Minecraft traversal already solves the game.
- A4MC is the leading single atmosphere authority; C6/C7 share it across fauna/player movement.
- Computing extends infrastructure; manual flight and the basic game loop must not depend on Lua/computers.

## Cross-lane dependencies

- **Authorship:** current `main` includes AUTH-0085; Content consumes semantic environment/geography rather than redefining it.
- **Implementation:** current recent accepted work includes SF-IMP-0079; #194 is the immediate Content-visible dependency.
- **Bootstrap Province:** #224 is the shared vertical-slice target.
- **Bellanca cutoff:** #237 requires thin implementation/compat work before powered-soaring acceptance.

## Known hazards / technical debt

- Some old overview/handoff docs lag current `main`; use git/reviews/lane state rather than assuming their stated milestone is current.
- C1 remains partially runtime-gated despite being merged.
- Reliable Gliders stock acquisition/repair uses Phantom Membrane; Bootstrap policy requires closing both acquisition and upkeep, not recipe alone.
- Optional-mod reflection bridges are intentionally version-pinned and fail-closed; upstream API/version changes require rerunning focused workflows.
- Do not promote A4MC, Reliable Gliders, Fowl Play, CC add-ons, or Bellanca tuning from prototype to permanent pack lock solely because current specimens pass.

## Verification shortcuts

Relevant focused workflows/commands:

- `Wave C2 Mobility Preflight`
- `Wave C3 Atmosphere Preflight`
- `Wave C5 Soaring Fauna Preflight`
- `Wave C6 Hawk Thermal Compat`
- `Wave C7 Glider Shared Lift`
- repository `CI`

Detailed commands and evidence live in the corresponding `docs/design-audit/wave-c*.md`, Gradle run definitions,
workflow files, PRs, and tests.

## Ordered next work

1. Close early glider acquisition **and repair** with the smallest data override; verify no Phantom dependency remains.
2. Execute Elytra/firework suppression acceptance and decide whether blast/instability feedback is worth bespoke work.
3. Turn computing from #224 design intent into a pinned executable CC:Tweaked/Aeronautics compatibility specimen.
4. Coordinate #194 and begin Bootstrap Province acceptance as soon as a legible persistent land-biome fixture exists.
5. Implement/accept #237, then build and fly the Giuseppe Bellanca B0.
6. Return to C1 industrial runtime evidence when the focused runtime window is available.

Immediate recommendation for the next fresh Content agent:

> Start with the early-glider recipe/repair executable closure, then the CC computing specimen; keep both subordinate to #224 Bootstrap Province rather than opening another broad audit.
