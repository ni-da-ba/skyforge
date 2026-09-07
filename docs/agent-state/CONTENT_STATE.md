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

### Highest executable Content milestone: C10

**C10 / PR #232**, merge `5eaff75638cd3f9033f440e67b82f705c999cd51`.

C10 proves the standalone 1:1 Nether datapack reaches Minecraft's live final dimension registry:

```text
Overworld coordinateScale = 1.0
Nether    coordinateScale = 1.0
```

This accepts the runtime mechanism, not yet the final portal-route policy.

### C9 — CC:Tweaked avionics substrate

**PR #230**, merge `6db44a0f26d244598aa44317abe6c7219eec44c1`.

The exact retained flight stack loads with:

```text
CC:Tweaked 1.119.0
Create: Avionics 0.5.2
Create 6.0.10
Sable 2.0.5
Create Aeronautics 1.3.2
```

Accepted claim: the reuse-first computing/avionics substrate is runtime-compatible and no bespoke
Skyforge computer/peripheral is required merely for aircraft instrumentation/control.

Production lock, autopilot balance, turtle/mining roles, and Skyforge-specific peripherals remain open.

### C8 — bootstrap glider maintenance closure

**PR #229**, merge `d2f9ee69c55f10da32f9da3802945e390f5168cf`.

C2 already replaced Phantom-gated crafting with wool/leather/sticks. C8 also replaces the Reliable
Gliders repair tag with leather + `#minecraft:wool` using `replace: true`, so neither acquisition
nor maintenance requires Phantom Membrane.

### C7 — player shared lift

**PR #223**, merge `a3281a0e6f0fe3e6eb4ce86cacc8470bf6006c88`.

C7 closes the player-facing shared-lift proof:

- Reliable Gliders remains owner of glider mechanics;
- A4MC remains atmosphere authority;
- Skyforge applies only trusted post-native vertical lift;
- real Reliable Gliders gliding-state admission is used;
- stronger native/block updraft wins rather than stacking;
- untrusted atmosphere and non-gliding players are inert.

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

### Computing beyond the accepted C9 substrate

Computing is a first-class capability axis, not a first-flight gate.

C9 accepts CC:Tweaked + Create: Avionics as a viable runtime substrate. Next computing decisions are
capability/bypass questions rather than basic loader compatibility:

- autopilot must not erase route planning/navigation gameplay;
- turtles must not trivialize resource geography/freight;
- wireless/rednet/GPS should support telemetry/navigation without making infrastructure irrelevant;
- prefer existing avionics/peripheral integrations before bespoke Skyforge APIs;
- thin Skyforge peripherals are reserved for genuinely Skyforge-owned semantics not otherwise exposed.

Progression intent:

```text
local display/control -> sensors/peripherals -> wired automation
-> wireless telemetry/networking -> route/fleet/infrastructure control
```

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

- practical glider capability envelope after C8's accepted cheap acquisition/maintenance closure;
- Elytra/firework boost suppression in play;
- decision on optional blast/instability feedback;
- C10 portal linking/placement usability and retained-mod assumptions before final 1:1 Nether policy lock;
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
- C8 removes the stock Phantom dependency; keep future recipe/tag changes from reintroducing it.
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

1. Execute Elytra/firework suppression acceptance and decide whether blast/instability feedback is worth bespoke work.
2. Extend C9 from loader compatibility to computing capability/bypass acceptance (autopilot, telemetry, turtles, networking).
3. Exercise C10 portal linking/placement and retained-mod compatibility before locking 1:1 Nether as final route policy.
4. Coordinate #194 and begin Bootstrap Province acceptance as soon as a legible persistent land-biome fixture exists.
5. Implement/accept #237, then build and fly the Giuseppe Bellanca B0.
6. Return to C1 industrial runtime evidence when the focused runtime window is available.

Immediate recommendation for the next fresh Content agent:

> Start with the Elytra/firework executable bypass closure, then the C9 computing capability/bypass specimen; keep both subordinate to #224 Bootstrap Province rather than opening another broad audit.
