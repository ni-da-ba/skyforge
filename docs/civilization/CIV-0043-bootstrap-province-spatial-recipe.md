# CIV-0043 — Bootstrap Province Spatial Recipe

Status: PRECOMMIT / OWNER-APPROVED BOOTSTRAP CONTRACT

## Purpose

This document turns the accepted first-hours progression and guarantee philosophy into a backend-neutral spatial recipe for the Bootstrap Province.

It does **not** define one fixed map. The province is a generated role graph whose island placement, geology, civilization, and route geometry may vary by seed so long as the capability and recovery invariants below remain true.

The governing rule is:

> **Guarantee the capability at the smallest geographic scale that preserves the intended mobility lesson.**

Two companion experience rules are equally binding:

> **Exploration may delay progression voluntarily, never accidentally.**
>
> **Players may create setbacks. They should not unknowingly delete the game's critical path.**

## Directed-path pacing target

The following times are design targets for a player following the critical path with reasonable competence. They are not deadlines and are not the expected duration for players who choose to explore, build, experiment, crash, or remain at the starting area.

| Beat | Directed-path target |
| --- | ---: |
| Crash, orientation, survival foothold | 0–20 min |
| Wreck salvage, small workshop, basic Create | 20–50 min |
| Glider and local thermal/vertical traversal | 45–90 min |
| First-flight materials and crude powered aircraft | 75–150 min |
| First serious powered journey / specialized resource | 120–210 min |
| Reach civilization/Guild and resolve Bellanca arc | 180–240 min |
| Tutorial closure / first optional freight work | about 3–4 hr |

These are calibration targets only. Exact timing remains a playtest variable.

## Spatial progression

```text
CRASH ISLAND
survival + recovery
        |
        | WALK / BUILD / trivial local access
        v
STARTING GROUP
local traversal + gliding
        |
        | GLIDE closure
        v
STARTING CLUSTER
first powered-flight closure
        |
        | FIRST_FLIGHT
        v
BOOTSTRAP PROVINCE
regional resources + civilization + freight
```

The four scales teach four different lessons. The generator should not collapse them into a single self-sufficient island or spread them so widely that the player must gamble on random exploration.

## Capability-edge vocabulary

The semantic province graph uses capability-labelled edges rather than fixed block-distance assumptions.

### `WALK_BUILD`

Ordinary local traversal, construction, bridges, ropes, stairs, or similarly cheap foothold-scale movement is sufficient. This class should not require the player to understand aviation.

### `GLIDE`

Unpowered aerial traversal is the intended solution. A successful route should have reasonable launch geometry, altitude budget, thermal/updraft support where applicable, and a recoverable landing opportunity.

A `GLIDE` edge may be technically crossable by elaborate player construction, but the recipe should make the intended mobility lesson legible.

### `FIRST_FLIGHT`

A crude powered aircraft is required or strongly favored. The gap, altitude pattern, return requirement, or payload requirement should demonstrate why an engine materially changes regional mobility.

### `FREIGHT`

The route is viable for repeat cargo movement, not merely one-way discovery. It must have usable approaches, landing/service opportunity, and enough economic asymmetry to justify repeated movement.

## Role graph

### A. Crash / home anchor

The province owns exactly one onboarding crash role. It may coexist with other world roles, but its bootstrap responsibilities are explicit.

The crash island or immediate foothold must provide:

- reliable food, wood, stone, and shelter opportunity;
- basic Iron sufficient to avoid an early tool/workshop soft-lock;
- Bellanca wreck access and bounded salvage;
- enough basic Create-entry capability to establish the first workshop, either directly on-island or through a trivial adjacent `WALK_BUILD` hop;
- a viable place to remain and build rather than an artificial "leave immediately or starve" pressure;
- at least one legible path toward the surrounding starting group.

The crash island should feel like a plausible temporary home, not a disposable tutorial platform.

### B. Starting group

The starting group is the nearby glider-scale neighborhood. A typical generated recipe should contain roughly **2–4 satellite roles** around the crash anchor, but exact counts remain a generation/playtest knob.

Across the crash anchor and starting group, the recipe must guarantee:

- complete glider material closure;
- replacement material for a failed/lost early glider;
- basic Create/workshop closure if not already satisfied at the crash anchor;
- at least one deliberately legible vertical-traversal or thermal lesson;
- at least one recoverable lower or lateral landing opportunity around a mandatory early glide route;
- optional ecology, ruin, salvage, or scenic roles that reward exploration without becoming progression-critical.

The first glider lesson should use actual geography. A height differential, sheltered launch face, thermal, lower catch island, or comparable formation should make the relationship between terrain and movement understandable without requiring a quest arrow.

### C. Starting cluster / first-flight closure

The starting cluster expands the local neighborhood just far enough that gliding becomes useful for assembling the first aircraft.

A typical recipe may add roughly **1–3 resource-role islands** beyond the immediate starting group. Exact count is tunable.

The cluster must guarantee that:

- every mandatory component/material needed for the first crude powered aircraft exists within the `WALK_BUILD` + `GLIDE` closure of the crash anchor;
- no mandatory first-aircraft ingredient is gated behind `FIRST_FLIGHT`;
- at least one safe launch/landing opportunity exists for initial powered-flight testing;
- the material budget permits recovery from reasonable experimentation or a destroyed first attempt;
- a player who spends resources imperfectly still has a deterministic recovery path rather than depending on lucky loot.

The preferred material reserve is conceptually a multiple-attempt budget, approximately **2–3× the strict first-aircraft minimum** across the closure area. The exact multiplier is not canon and should be calibrated from real aircraft construction/crash playtests.

The cluster is allowed to be a small spatial puzzle: the player may need to glide among several islands to assemble the aircraft rather than finding a complete kit in one place.

### D. Bootstrap Province regional roles

Powered flight opens the actual regional game.

The generated province must contain, within the expected operating envelope of the first crude aircraft:

- at least one Copper-specialized resource zone;
- at least one Zinc-specialized resource zone;
- at least one eligible Guild-connected civilization/settlement able to continue and terminate the Bellanca onboarding arc;
- at least one producer/consumer pairing that can become a meaningful first freight opportunity;
- enough safe or recoverable route geometry that a crude aircraft can reach the intended civilization/resources without one perfect flight being mandatory.

Copper and Zinc should **prefer separate resource zones and/or directions** so regional specialization and Brass acquire a logistical character. They do not need an eternal rule that each metal occupies a unique island; what matters is that the Bootstrap recipe does not routinely collapse post-flight specialization into one trivial stop.

Petroleum is not a Bootstrap guarantee. Levitite is not a Bootstrap guarantee.

## First civilization placement

The first eligible Guild destination is a deliberate province guarantee, not a random discovery outcome.

The recipe must place at least one eligible Guild-connected settlement:

- outside the ordinary reliable walking/building closure of the crash site;
- normally outside the comfortable glider-only solution envelope;
- inside the tested operating envelope of the crude first aircraft;
- on a route with credible emergency/recovery options;
- with enough navigational evidence that a player can infer where civilization lies without blind search.

Useful evidence may include one or more of:

- a distant tower or substantial skyline;
- beacon/light/smoke;
- old route markers or navigation aids;
- Bellanca wreck records, maps, notes, or surviving instrumentation;
- Guild route signs;
- visible traffic or infrastructure where population-tone policy permits it.

The system should prefer observation and world evidence over compulsory quest arrows. FTB Quests may clarify the objective, but geography and infrastructure should make the route intelligible.

Per CIV-0041, another eligible Guild Hall reached first by an unusual player route may satisfy the Bellanca claim. The spatial recipe guarantees a valid destination without requiring one uniquely scripted NPC at one coordinate.

## First freight route

The first freight opportunity should emerge from geography and comparative advantage rather than existing as tutorial theater.

At least one guaranteed producer/consumer pair should satisfy all of the following:

- producer and consumer have an authored economic reason to exchange goods;
- the route is materially easier or more valuable with powered cargo movement than with personal gliding;
- repeated round trips are physically plausible for early aircraft;
- the route exposes at least one mature Skyforge concept: specialization, Guild information, local prices, cargo custody, infrastructure, or route risk;
- completing or ignoring this route does not determine whether the player is allowed to leave the tutorial.

A preferred early pattern is that one resource-specialized regional node creates a natural supply relationship with the first settlement or another nearby settlement. The exact commodity pair remains Content tuning.

## Recovery and anti-soft-lock contract

Mandatory early capability always requires a recoverable failure path.

The recipe and gameplay systems together must account for at least:

- lost or destroyed glider -> deterministic replacement material;
- destroyed or badly designed first aircraft -> deterministic rebuild supply;
- exhausted critical first-flight resource during experimentation -> alternate guaranteed supply within the appropriate capability closure;
- misplaced Bellanca recorder -> recoverable evidence semantics or authoritative reacquisition path;
- dismantled progression-relevant structure -> repair, replacement, alternate service, or system state that does not permanently delete the critical path;
- failed first regional flight -> a credible way to land, recover, rebuild, or try again.

Failure should cost time, material, and inconvenience. It should not require world regeneration unless the player deliberately performs extraordinary destructive actions beyond the intended progression budget.

## Resource-guarantee hierarchy

| Capability / resource | Minimum guarantee scope | Design intent |
| --- | --- | --- |
| Food, wood, stone, basic shelter | Starting island | reliable survival |
| Basic Iron | Starting island / immediate foothold | no early tools/workshop soft-lock |
| Basic Create entry | Starting island or trivial adjacent hop | engineering before aviation |
| Glider materials | Starting island/group | first mobility unlock locally achievable |
| First-flight aircraft materials | Starting cluster | gliding helps assemble aircraft |
| Safe launch/landing opportunity | Starting cluster | credible flight test site |
| Copper | Bootstrap Province, post-flight reachable | regional specialization |
| Zinc | Bootstrap Province, post-flight reachable | regional specialization |
| Eligible civilization/Guild Hall | Bootstrap Province | guaranteed onboarding destination |
| First freight producer/consumer pair | Bootstrap Province | guaranteed sandbox opening |
| Petroleum | Not guaranteed in Bootstrap | mature strategic node |
| Levitite | Not guaranteed in Bootstrap | late/extreme material |

Trade or salvage may satisfy a hard guarantee only when the **path itself** is guaranteed: destination, service/merchant, stock, and affordability must all be deterministic enough to preserve closure. Random chest contents do not satisfy a mandatory guarantee.

For the first Copper/Zinc lesson, direct regional geology is preferred. Trade is a recovery/backup path unless later playtesting demonstrates a better teaching pattern.

## Mobility envelopes, not block constants

This contract intentionally does not canonize block distances before real glider and aircraft behavior is measured.

Implementation/Content should calibrate semantic placement against empirical envelopes such as:

```text
R_walk
R_glide_safe
R_glide_edge
R_first_flight
R_freight
```

A role is placed relative to the capability envelope it is meant to teach. Actual block ranges may then vary with altitude, prevailing wind, route geometry, craft performance, and backend realization.

The critical requirement is tier separation:

```text
mandatory survival/workshop roles <= WALK_BUILD closure
mandatory glider roles            <= local closure
mandatory first-flight materials  <= GLIDE closure
first regional specialization     <= FIRST_FLIGHT closure
first Guild destination           <= FIRST_FLIGHT closure
first freight loop                <= FREIGHT closure
```

Do not encode "first city = N blocks from spawn" as the semantic contract.

## Procedural generation order

Preferred backend-neutral generation flow:

```text
province seed / regional intent
        ↓
assign required Bootstrap role nodes
        ↓
construct capability-labelled role graph
        ↓
place role candidates inside their mobility envelopes
        ↓
assign island morphology / altitude / approach geometry
        ↓
assign geology, resources, ecology, structures, civilization
        ↓
validate capability and recovery invariants
        ↓
retry/backtrack boundedly if invalid
        ↓
realize through backend
```

Required progression roles must be solved before optional decoration can crowd out their spatial budget.

## Validation invariants

A generated Bootstrap Province is invalid if any of the following fail:

1. **Spawn survival:** the player can establish the intended survival foothold from the crash anchor without random loot.
2. **Workshop closure:** basic engineering can begin before powered aviation.
3. **Glider closure:** glider construction is available within the starting island/group guarantee.
4. **Glide recovery:** at least one mandatory early glide lesson has a credible recovery/landing margin rather than a single lethal perfection check.
5. **First-aircraft closure:** every mandatory first-flight ingredient lies in the `WALK_BUILD` + `GLIDE` transitive closure.
6. **Rebuild path:** reasonable early experimentation cannot exhaust all deterministic first-flight recovery supply.
7. **Tier separation:** the regional lesson is not accidentally collapsed so every post-flight objective is trivial before powered flight.
8. **Guild reachability:** at least one eligible Guild settlement is reachable within the crude-aircraft envelope.
9. **Guild legibility:** the player receives sufficient world evidence to infer a civilization route without blind random search.
10. **Regional specialization:** Copper and Zinc are available post-flight without both being routinely collapsed into the same trivial pickup.
11. **Freight opening:** at least one economically motivated producer/consumer pair forms a viable early freight edge.
12. **No lucky closure:** no mandatory Bootstrap capability depends on a random chest, random merchant appearance, or other unbounded chance event.
13. **No premature mature resource:** Petroleum and Levitite are not required to close Bootstrap progression.

These are semantic invariants. Minecraft-specific validation may add block-space, Aeronautics, Create, persistence, or multiplayer checks without replacing the neutral contract.

## Tunable, deliberately not frozen

The owner-approved direction leaves the following as playtest/configuration variables rather than canon:

- exact number of starting-group satellites;
- exact number of first-flight closure islands;
- exact block distances and altitude differences;
- exact rebuild-reserve multiplier;
- whether Copper and Zinc are on separate islands in every seed versus merely separate regional resource zones;
- exact commodity chosen for the first freight loop;
- exact strength and form of civilization navigation cues;
- exact split of first-flight ingredients between crash island and glider-scale satellites;
- exact directed-path completion times within the target bands above.

Changing these values to improve play does not reopen the strategy decision unless the change violates the capability hierarchy or mobility lessons.

## Recommended executable specimen

Before coupling this contract deeply to production terrain, Content/Experience should prove a deterministic backend-neutral province-role specimen that outputs:

- province seed;
- role nodes;
- capability-labelled edges;
- resource/civilization role assignments;
- mobility-envelope classifications;
- invariant/validation report.

The specimen should demonstrate both valid generation and deterministic rejection/backtracking of invalid graphs. It does not need Minecraft terrain integration to retire the first topology risk.

After that specimen is stable, Authorship/Implementation can bind the semantic graph to real island geometry and backend traversal evidence.

## Relationship to existing civilization contracts

This document narrows the Bootstrap spatial/progression problem. It does not replace the broader civilization system.

In particular:

- CIV-0041 remains authoritative for the Bellanca onboarding state and tutorial terminus;
- CIV-0042 remains authoritative for civilization implementation/acceptance boundaries;
- existing resource, market, route, freight, Guild, and semantic/physical reconciliation contracts remain authoritative within their domains.

Where a future implementation cannot satisfy this recipe without violating an accepted cross-lane contract, stop and surface the conflict rather than silently weakening the Bootstrap guarantee.