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
- Negative space is an intentional part of the sky-world scale fantasy.

## Current lane snapshot

| Lane | Repository-visible boundary |
| --- | --- |
| Authorship | `AUTH-0088` merged on current `main` (published surface ecology projection) |
| Implementation | `SF-IMP-0080` merged on current `main` (legible forest/taiga ecology showcase) |
| Content / Experience | C16 stock wireless infrastructure envelope accepted; C15 ordinary-player 1:1 Nether portal linking/placement, C14 executable avionics capability, C13 Elytra/firework suppression, C10 registry proof, C9 computing substrate, and C8 glider-maintenance closure also accepted; C11/C12 remain separately in progress/reserved |
| AUDIT | AUDIT-0005 post-ecology convergence audit accepted by the merge that places this update on `main` |

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

### MERGED / ACCEPTED — AUTH-0086 visible authored hydrology

AUTH-0086 / PR #241 merged as `a55e500c86f0baf910af809985956ac398742706`.

Implementation may now consume the accepted exact backend-neutral visible-water intents for:

- coherent naturalized channel paths;
- retained-waterbody footprints with dry margins;
- cascades, waterfalls, and edge discharge.

The adapter owns physical rasterization, block/fluid identity, exact-volume mutation, scheduling, and
fluid lifecycle. It must not substitute native springs or invent a second hydrology planner. Any
backend-neutral physicalization added later must use compiled column authority rather than treating
normalized semantic hydrology potentials as literal world-space Y.

### MERGED / ACCEPTED — AUTH-0087 published authored-realization binding

AUTH-0087 / PR #254 merged as `04fb6dab33cf80e8b82b42fc9d0fc7837cce31c4`.

Downstream consumers may pair an accepted AUTH-0058 publication with an explicit AUTH-0046 association
catalog only through exact root/count/volume-value coverage. Missing, extra, substituted, or foreign-root
sets fail closed; no spatial/seed/order inference is allowed.

Immediate consequences:

- Implementation may use the proven association set to drive accepted AUTH-0049 material composition;
- concrete Minecraft material binding and mutation remain Implementation-owned;
- Authorship may reuse the same exact binding for surface ecology without creating a second publication
  association protocol.

### MERGED / ACCEPTED — AUTH-0088 published surface ecology

AUTH-0088 / PR #262 merged as `844ad6cd09a7cbe60529f1a7d43badc12a84a1e1`.

A downstream adapter may query unchanged AUTH-0003 ecology for one exact published volume id + world X/Z
only through the AUTH-0087 explicit association, strict compiled horizontal support, and current authored
ownership. Unknown volume identity, empty physical support, and physical-but-native-unowned fringe fail
closed. Physical Y, Minecraft biome keys, quart-cell presentation, persistence, and atmospheric biome
envelopes remain Implementation-owned.

### IN PROGRESS — AUTH-0089 island ecological opportunity profile

Authorship is preparing an island-scale placement-free aggregation of AUTH-0003 regime composition,
mean vegetation/saturation/thermal potentials, and horizontal authored-habitat planning area. It does
not assign species, spawn counts, carrying capacity, resources, or backend biome identity.

### MERGED / ACCEPTED — current Implementation capability relevant to Authorship

SF-IMP-0080 is now merged/accepted. The runtime already provides exact-volume ownership/admission,
native population, authored/native cave composition, post-cave interior population, generated-fluid
fencing/persistence, and a separately accepted forest/taiga land-ecology showcase whose machine and
human gates passed. AUTH-0088 remains a distinct producer-side semantic projection; SF-IMP-0080 does
not yet claim ecology-to-Minecraft-biome translation from AUTH-0003.

### MANUAL VERIFICATION REQUIRED — morphology quality

AUTH-0083/AUTH-0084 review machinery is merged, and issue #214 remains open as the broader human gate.
The first SF-IMP-0081 Massif tranche has passed its human silhouette/morphology/underside review; that
does not close #214. Remaining built-in families, seeds/scales, hybrids/providers, regional contexts,
and later material/ecology/hydrology contexts still require review. Issue #267 tracks the first
Massif's non-blocking on-foot traversal/lumpiness observation.

## Active coordination contracts

### Bootstrap Province

Issue **#224** is the organizing Content vertical slice.

The visually legible land-biome prerequisite is now satisfied by accepted SF-IMP-0080; issue **#194** is closed after machine and human review. Bootstrap Province work may consume that accepted legibility without reopening the old gravel/ocean showcase problem. Remaining production-world quality work, including issue #214 morphology review, is tracked separately.

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

SF-IMP-0080 has now passed its automated ecology/showcase/reopen/performance gates and the project-owner human visual review, and is merged on current main. This proves legible forest/taiga land ecology, not AUTH-0003 semantic-biome translation.

Authorship/environment semantics determine viable niches and population opportunity. AUTH-0088 now provides the exact published surface-ecology query; AUTH-0089 is preparing island-scale ecological opportunity evidence without species or spawn decisions.
Content maps retained species into those niches.
Atmosphere may alter behavior (for example thermal soaring) but must not independently create population.

### Nether route scale

C10 / PR #232 proves a 1:1 Nether `coordinate_scale` datapack reaches the live final dimension registry.

C15 / PR #259, merge `764fbb393644fd2de7aa9076f71f29602fa25732`, proves ordinary vanilla
portal destination search and missing-target portal creation consume that live 1:1 scale at non-origin
coordinates with the retained Create/Sable/Aeronautics stack loaded. Same-coordinate linking beats a
deliberate vanilla-8:1 distractor; reverse linking is exact; missing targets are created near the 1:1
destination.

Remaining route-policy gates are assembled contraption/passenger/cargo transfer, authored portal-site
safety/footholds, human terminal usability, and the later permanent-cosmology decision.

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

C14 / PR #256, merge `b4b44c87509ee70b27ddbe20468d2d287cbd79f1`, accepts the existing
Create: Avionics peripheral surface as the baseline programmable avionics sensor/bounded-control
substrate. Real CraftOS computers discover and read live altitude state and drive the retained physical
throttle through its upstream 0..15 clamp. The retained flight stack also initializes without the
computing layer, so computers remain optional for manual and first flight. Other lanes should not add
a duplicate generic aircraft telemetry/control API; thin Skyforge peripherals remain reserved for
Skyforge-owned semantics not exposed upstream.

C16 / PR #264, merge `d63f7c712355fb421c909f7bbdc74465a88522e6`, measures the stock
CC:Tweaked wireless envelope with real CraftOS/modem calls: ordinary Wireless Modems communicate at
48 blocks but not 96 blocks in the accepted low-altitude specimen, while Ender Modems communicate at
512 blocks and across Overworld/Nether. This is accepted measurement, not a mandated recipe change.
Ordinary wireless remains useful bounded infrastructure; any gating/reservation of Ender Modems is a
Content progression decision, not a new Skyforge network authority.

Next contract questions are gameplay/bypass:

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
