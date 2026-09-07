# Skyforge Content / Experience Agent State

**Lane:** Content / Experience  
**Status:** Canonical live lane handoff  
**Updated:** 2026-09-06 (America/Chicago)  
**Main snapshot after C17 merge:** `65bd49a4e349f16bab8b796aa5b1a1e6dd72a7cc`

Read first:

- [Program charter](PROGRAM_CHARTER.md)
- [Cross-lane contracts](CROSS_LANE_CONTRACTS.md)
- [Content integration corpus index](../design-audit/README.md)

## MERGED / ACCEPTED

### Highest accepted executable Content milestone: C17

**C17 / PR #272**, merge `65bd49a4e349f16bab8b796aa5b1a1e6dd72a7cc`; final synchronized runtime head `9373c307ef6bc79a64643ad0d958f5a09ebe1c9c`.

C17 proves the stock CC:Tweaked GPS layer remains infrastructure-dependent under ordinary radio range:

```text
noHostFound=false
normalNearFound=true  normalNear=16,64,16
normalFarFound=false
enderRemoteFound=true enderRemote=512,80,272
```

Accepted interpretation:

- ordinary GPS is KEEP: it requires deployed non-collinear GPS hosts within the bounded Wireless Modem envelope;
- GPS towers/beacons can provide legible aviation/navigation infrastructure without a bespoke Skyforge GPS system;
- Ender-backed GPS inherits C16's mature-bypass status because it removes the ordinary range constraint;
- final tower spacing/placement, Ender progression gating, turtle economics, and autopilot remain separate gameplay decisions;
- Skyforge must not add a duplicate generic GPS/network implementation while the retained CC surface remains sufficient.

Final exact-head verification passed C10/C13/C14/C15/C16/C17, repository CI, all current showcase persistence/reopen jobs including ecology and Massif morphology, and SF-IMP-0070 performance characterization. The branch was synchronized twice around accepted SF-IMP-0081 and AUTH-0089 before merge.

### C16

**C16 / PR #264**, merge `d63f7c712355fb421c909f7bbdc74465a88522e6`; exact synchronized runtime head `03418c4e941bc394989ca4269a1fbff3fc40cab7`.

C16 measures the real CC:Tweaked wireless infrastructure envelope with six real CraftOS computers and stock modem peripherals:

```text
normalNearReceived=true  normalNearDistance=48
normalFarReceived=false
enderRemoteReceived=true enderRemoteDistance=512
enderCrossReceived=true  enderCrossDistance=nil
```

Accepted interpretation:

- ordinary Wireless Modems are a bounded local/regional infrastructure layer and need no Skyforge nerf or replacement;
- Ender Modems remove same-dimension range and cross-dimensional separation and are therefore an explicit mature bypass, not an unexamined early Bootstrap Province default;
- no bespoke Skyforge generic networking API is justified;
- final Ender Modem progression/recipe gating, GPS layout, turtle throughput, and autopilot progression remain separate gameplay decisions.

Exact-head verification before merge passed C2/C3/C5/C6/C7/C9/C10/C13/C14/C15/C16, repository CI, both showcase persistence jobs, and SF-IMP-0070 performance characterization. A first C6 attempt on an earlier synchronized head timed out while the runner was ~40 ticks behind; the exact same C6 job was rerun successfully before later synchronization, and C6 passed again on the final C16 head.

### C15 — ordinary-player 1:1 Nether portal linking/placement

**PR #259**, merge `764fbb393644fd2de7aa9076f71f29602fa25732`; accepted head `8e75e84b800587a6d87676d8e18e4ed974f65c29`.

Vanilla portal search and missing-target creation consume the live C10 1:1 scale at non-origin coordinates. Same-coordinate linking beats a deliberate 8:1 distractor; reverse linking is exact; missing targets are created near the 1:1 destination. Assembled contraption/passenger/cargo transfer, authored foothold safety, terminal UX, and permanent cosmology remain open.

### C14 — executable programmable avionics capability

**PR #256**, merge `b4b44c87509ee70b27ddbe20468d2d287cbd79f1`.

Real CraftOS computers discover upstream Create: Avionics altitude/throttle peripherals, read live altitude, and drive the retained physical throttle through upstream 0..15 clamping. Computing remains optional for manual/first flight; no duplicate generic aircraft telemetry/control API is justified.

### C13 — Elytra/firework bypass suppression

**PR #247**, merge `dcfa4d467bfcd8e64443da84f2f970de6b1ccd7b`.

The pinned No More Elytra Boosting 1.0.0 runtime removes Elytra rocket propulsion while preserving fall-flying and ordinary fireworks. Human clarity/optional feedback remains a UX question only.

### C10 — live 1:1 Nether scale

**PR #232**, merge `5eaff75638cd3f9033f440e67b82f705c999cd51`.

Final live Overworld and Nether `coordinate_scale` are both 1.0. C15 subsequently closes ordinary-player portal linking/creation for that interim policy.

### C9 — CC:Tweaked avionics substrate

**PR #230**, merge `6db44a0f26d244598aa44317abe6c7219eec44c1`.

Accepted retained stack: CC:Tweaked 1.119.0 + Create: Avionics 0.5.2 + Create 6.0.10 + Sable 2.0.5 + Create Aeronautics 1.3.2.

### C8 and earlier retained experience contracts

- C8 / PR #229 closes Phantom-gated glider acquisition/maintenance with ordinary leather/wool repair.
- C7 / PR #223 proves Reliable Gliders consumes trusted shared A4MC lift after native glider physics.
- C6 proves the retained Fowl Play red-tailed hawk can enter/exit thermal SOAR from the same atmosphere authority.
- C3/C5 prove A4MC and retained fauna stack loader/runtime compatibility.
- C1 industrial scaffolding is merged but still has manual/runtime closure gates listed below.

## IN PROGRESS / RESERVED

### Bootstrap Province — issue #224

Central Content vertical slice:

```text
survival -> Create -> cheap glider -> shared thermals/fauna -> first aircraft
-> specialized destination -> regional freight -> mature infrastructure evidence
```

SF-IMP-0080 / PR #248 is now merged/accepted and the project-owner human ecology gate passed, so legible forest/taiga land ecology is no longer blocked by issue #194. Issue #261 is only a non-blocking short-distance biome ambience/presentation follow-up.

### C11 — live pre-Brass first-flight recipe surface — draft PR #233

Already owned by parallel work. Do not duplicate C11.

### C12 — executable Bellanca B0 — issue #239

Reserved for the real assembled Sable/Create Aeronautics Bellanca engineering mule. It still depends on C11 closure and the Portable Engine cutoff path.

### Portable Engine cutoff — issue #237 / draft PR #240

Stationary retained-stack evidence exists. Assembled-Sable, save/reload, two-engine behavior, and human ergonomics remain unaccepted.

### Authorship / Implementation dependencies

- AUTH-0088 is accepted: exact published-volume/world-XZ surface-ecology projection, with no Minecraft biome/Y policy.
- AUTH-0089 is accepted: deterministic island ecological opportunity aggregation without species/spawn/resource/backend policy.
- SF-IMP-0080 is accepted: legible forest/taiga ecology showcase.
- SF-IMP-0081 is accepted for the Massif carrier/human tranche; SF-IMP-0082 / #269 is expanding the remaining built-in morphology atlas, while #267 separately tracks Massif traversal lumpiness.

## PROPOSED / OPEN CONTENT QUESTIONS

### Computing after C17

C9/C14/C16/C17 now establish substrate, real avionics capability, wireless-envelope behavior, and GPS infrastructure topology. Remaining questions are gameplay/bypass questions only:

- turtles versus resource geography and freight value;
- GPS tower spacing/placement and how visibly navigation infrastructure appears in #224;
- Ender Modem progression gating;
- autopilot versus route-planning/navigation gameplay;
- thin Skyforge peripherals only for genuinely Skyforge-owned semantics absent upstream.

Computing is a first-class infrastructure axis, **not** a first-flight prerequisite.

### Nether after C15

Ordinary 1:1 portal mechanics are closed. Remaining questions:

- assembled Aeronautics/Sable contraption portal transfer;
- passengers/cargo if transfer is possible;
- authored portal foothold/site safety;
- human portal-terminal usability;
- eventual permanent route-scale/cosmology decision.

## MANUAL VERIFICATION REQUIRED

### C1 industrial specimen

Still requires recorded runtime/play evidence for material suppression/recipe closure, identity collisions, Metallurgy A/B value, electrical throughput, and world-side industrial source throughput.

### Mobility / aircraft

- practical cheap-glider envelope;
- blocked Elytra-boost clarity in ordinary play;
- aircraft-vs-personal-flight freight/logistics comparison;
- Bellanca B0 mass/CG, propulsion, ground handling, takeoff/climb/cruise, power-off glide/restart, atmosphere response, and payload;
- human-eye aircraft review only after mechanical credibility.

### Morphology

Issue #214 remains a project-owner visual gate for underside/approach quality. Do not treat machine evidence as a substitute for the required view/flight-route review.

## Architectural invariants

- Reuse priority: vanilla -> existing mods -> config/datapack/integration -> thin adapter -> bespoke.
- Skyforge owns meaning; retained mods provide assets/capabilities unless a demonstrated gap justifies more.
- Personal mobility is cheap; logistics are not.
- Aircraft win through payload, repeatability, fluids/entities/contraptions/automation, not blanket nerfs.
- Resource geography should create routes/infrastructure rather than repetitive chores.
- A4MC remains the single atmosphere authority for accepted aircraft/fauna/player lift behavior.
- Computing extends infrastructure; manual flight and the basic loop must not depend on Lua/computers.
- Ordinary wireless is bounded infrastructure; Ender Modems are a mature bypass and should be progression-conscious.

## Verification shortcuts

- `Wave C2 Mobility Preflight`
- `Wave C3 Atmosphere Preflight`
- `Wave C5 Soaring Fauna Preflight`
- `Wave C6 Hawk Thermal Compat`
- `Wave C7 Glider Shared Lift`
- `Wave C9 Computing Avionics`
- `Wave C10 Nether Scale Runtime`
- `Wave C13 Elytra Bypass`
- `Wave C14 Avionics Capability`
- `Wave C15 Portal Linking`
- `Wave C16 Wireless Envelope`
- `Wave C17 GPS Infrastructure`
- `Skyforge Showcase Acceptance`
- repository `CI`

## Ordered next work

1. Do not duplicate active C11/C12 work; consume their results into #224 when accepted.
2. Check whether C18 is already claimed before reserving any new milestone.
3. Prefer the next narrow #227 executable bypass specimen: turtles versus resource geography/freight value. Do not nerf turtles without measured substitution evidence.
4. Begin more of Bootstrap Province acceptance now that SF-IMP-0080 has cleared land-ecology visibility and C17 has closed the GPS topology question; treat final GPS tower placement as province design, not a substrate uncertainty.
5. Complete/accept #237 / PR #240 before C12 powered-soaring closure.
6. Keep assembled-aircraft Nether transfer separate from accepted ordinary-player C15 mechanics.
7. Return to C1 industrial runtime evidence when a focused runtime window is practical.
