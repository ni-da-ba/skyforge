# Wave C2 Mobility Integrity Prototype v0.1

**Snapshot:** 2026-09-05  
**Status:** Development scaffold; runtime/human-eye acceptance pending.

## Purpose

Wave C1 asks whether Skyforge's retained engineering mods can become one coherent industrial
economy. Wave C2 asks the complementary mobility question:

> Can Skyforge preserve cheap, expressive personal soaring without allowing vanilla or portal-scale
> shortcuts to erase the economic reason to build aircraft and route infrastructure?

The prototype is deliberately reuse-first and low-bespoke.

It tests four independent interventions:

```text
EARLY PERSONAL GLIDER
    Reliable Gliders 1.4.1
    + ordinary-material recipe override

LOCAL HEAT LIFT
    Reliable Gliders stock updraft behavior
    + no Skyforge tuning in the first pass

ELYTRA ROCKET BYPASS
    No More Elytra Boosting 1.0.0
    + ordinary fireworks otherwise preserved

NETHER DISTANCE COMPRESSION
    separate Minecraft 1.21.1 datapack
    + the_nether coordinate_scale 8.0 -> 1.0 only
```

No item here is a production lock merely because it is easy to prototype.

---

## 1. Version lock

Machine-readable pins live at:

```text
skyforge-neoforge-1211/wave-c2-mods.properties
```

Current top-level C2 pins:

| Component | Pinned version | Role |
|---|---|---|
| Minecraft | 1.21.1 | target game |
| NeoForge | 21.1.249 | current adapter runtime |
| Reliable Gliders | 1.4.1 | early personal soaring |
| No More Elytra Boosting | 1.0.0 | reuse-first rocket-boost suppression |

The integrated comparison additionally reuses Wave C1's already-pinned Create, Sable,
Create Aeronautics, and JEI artifacts. They are not duplicated in the C2 lock.

Reliable Gliders was audited against its 1.21.1 source head
`eb65dfe2159ffb850c631f998cd2149f7383bd47`, whose stock recipe and updraft data are
explicitly part of this prototype contract.

---

## 2. Early-glider recipe prototype

### Upstream problem

Reliable Gliders' stock recipe requires two Phantom Membranes.

That creates the wrong bootstrap semantics:

```text
hostile / insomnia-linked late-night drop
    -> required for basic starter-group traversal
```

Skyforge's early-glider contract explicitly rejects that dependency.

### Wave C2 replacement

Wave C2 preserves the upstream recipe geometry and ordinary frame materials:

```text
C L C
L S L
S   S

C = #minecraft:wool
L = leather
S = stick
```

This is a prototype, not a final balance declaration.

Its purpose is to test the intended material language:

```text
cloth / sail
+ leather / flexible structure
+ wood / frame
    -> simple personal glider
```

The recipe is conditioned on `reliable_gliders` being loaded so the development resource is inert
in ordinary Skyforge runs.

### What is intentionally unchanged

The first pass does **not**:

- add metal;
- require Brass;
- require Create machinery;
- require Phantom loot;
- create a custom Skyforge glider;
- change durability;
- change horizontal speed.

If gameplay shows the glider arrives too cheaply or too late, tune the smallest relevant data
surface after observing the actual bootstrap loop.

---

## 3. Thermal / updraft prototype

Reliable Gliders already supplies a data-driven updraft block tag containing:

```text
#minecraft:fire
#minecraft:campfires
minecraft:lava
minecraft:magma_block
```

Its audited defaults are:

```text
updraft_strength = 0.7
updraft_height   = 15
horizontal_speed = 1.0
```

Wave C2 deliberately does not override that tag or those values.

This is important because the stock behavior implements a useful subset of Skyforge's intended
shared lift language with no bespoke code:

```text
local heat
    -> rising-air proxy
    -> glider climb
    -> personal route opportunity
```

### Player-built lift is allowed

A chain of campfires or other heat sources may make a longer personal glider route possible.

That is not automatically a balance failure.

It is low-throughput route infrastructure comparable to a trail, climbing route, or beacon chain.
The comparison that matters is not maximum personal range. It is capability:

| Capability | Prepared glider route | Powered aircraft |
|---|---|---|
| One player | yes | yes |
| Inventory-scale cargo | yes | yes |
| Contraption/bulk freight | no | yes |
| Self-contained powered climb | no | yes |
| Route preparation dependence | high | lower |
| Weather/launch geometry sensitivity | high | lower / vehicle-dependent |
| Schedule independence | lower | higher |
| Flexible origin/destination | lower | higher |

Only tune the updraft source tag, strength, or height if actual play makes prepared soaring so easy
that those capability differences stop mattering.

### Natural thermals are later

Wave C2 does not choose Skyforge's authoritative atmosphere implementation.

Natural convection, ridge lift, weather forcing, and soaring-fauna response should eventually enter
through one atmosphere seam:

```text
THERMAL / UPDRAFT AVAILABILITY
    = atmospheric convection
    + terrain / ridge effects
    + weather
    + local heat sources
```

The block-tag behavior is only the local-heat bootstrap term.

---

## 4. Elytra firework-boost suppression

Skyforge does not currently need to remove Elytra.

Unpowered Elytra remains:

- personal;
- freight-poor;
- altitude/launch dependent;
- useful as later high-performance soaring.

The bypass is vanilla rocket boosting, which turns the Elytra into inexpensive self-contained
propulsion.

Wave C2 therefore tests the existing server-side/singleplayer
**No More Elytra Boosting 1.0.0** mod before any Skyforge mixin is justified.

Desired behavior:

```text
ordinary firework use
    PRESERVED

firework rocket while fall-flying
    does not provide sustained propulsion
```

### Explicit non-goal

The first prototype does **not** make the player explode for attempting rocket boost.

Hazardous or unstable feedback remains an optional diegetic treatment after the simple suppression
rule proves mechanically correct. Do not spend bespoke behavior on presentation before the bypass
itself is closed.

---

## 5. 1:1 Nether coordinate-scale prototype

Vanilla Nether distance compression can turn a long Overworld route into a short portal corridor.

That risks erasing the province-scale aviation geography Skyforge is deliberately building.

The first prototype is therefore a separate, standalone Minecraft 1.21.1 datapack:

```text
src/development/wave-c2-nether-scale-datapack
```

It overrides the vanilla Nether dimension type and changes only:

```text
coordinate_scale
    8.0 -> 1.0
```

The other recorded vanilla 1.21 dimension-type properties are retained.

### Why this pack is separate

The 1:1 Nether override is **not** placed in `src/development/resources`.

That source set is attached to many unrelated NeoForge development and SF-IMP acceptance runs.
A global dimension-type override would silently contaminate those proofs.

It is also separate from the mobility-recipe fallback datapack so each axis can be tested
independently.

### What 1:1 is intended to accomplish

It should preserve:

- Nether as a meaningful destination;
- Nether-specific resources/processes;
- hostile corridor engineering;
- portal use for dimension access;
- local portal infrastructure.

It should stop Nether portals from becoming a default eight-times-distance Overworld transport
network that dominates aircraft.

This is an interim transport rule, not a final Nether-authorship commitment.

---

## 6. Development run profiles

### Personal mobility

```bash
./gradlew :skyforge-neoforge-1211:runWaveC2PersonalMobilityClient
```

Loads only the two C2 mobility dependencies plus Skyforge development resources.

Use it for:

- glider recipe closure;
- glider handling;
- stock heat-source updrafts;
- prepared personal lift routes;
- Elytra behavior;
- firework-boost suppression;
- ordinary firework regression.

### Integrated aircraft comparison

```bash
./gradlew :skyforge-neoforge-1211:runWaveC2IntegratedMobilityClient
```

Adds the minimum current aircraft substrate:

```text
Create
Sable
Create Aeronautics
JEI
```

Use it for direct capability comparison:

```text
PERSONAL SOARING
vs.
POWERED AIRCRAFT LOGISTICS
```

CBC, Create: Metallurgy, and Create Propulsion are intentionally absent. They are irrelevant noise
for this question.

### Artifact-resolution preflight

```bash
./gradlew :skyforge-neoforge-1211:waveC2ResolvePinnedMods
```

This should resolve both focused profiles before interactive testing.

---

## 7. Standalone datapacks

### Mobility recipe fallback

```text
src/development/wave-c2-mobility-datapack
```

Purpose:

- deterministic higher-priority fallback for the glider recipe if development mod-resource ordering
  does not win.

### Nether scale

```text
src/development/wave-c2-nether-scale-datapack
```

Purpose:

- isolated `coordinate_scale = 1.0` world test.

Both target Minecraft 1.21.1 datapack format 48.

---

## 8. Runtime acceptance sequence

Do not collapse all axes into one test immediately.

Recommended order:

```text
1. waveC2ResolvePinnedMods
2. waveC2PersonalMobilityClient
3. recipe proof
4. stock updraft proof
5. Elytra + ordinary-firework proof
6. disposable-world 1:1 Nether datapack proof
7. waveC2IntegratedMobilityClient
8. glider-vs-aircraft capability comparison
```

Stop at the first loader or behavioral incompatibility and change one variable at a time.

---

## 9. Required human-eye evidence

### Glider

Record:

- actual crafting ingredients shown by JEI/recipe book;
- no Phantom Membrane requirement;
- takeoff/handling feel;
- useful glide distance from representative bootstrap terrain;
- durability burden;
- failure/recovery behavior.

### Updrafts

Record:

- campfire/fire/lava/magma activation;
- approximate practical climb;
- whether default 15-block scan height feels legible;
- whether repeated heat sources enable meaningful route construction;
- whether chaining is interesting preparation or trivial infinite flight.

### Elytra / rockets

Record:

- unpowered Elytra still functions;
- rocket use while fall-flying provides no sustained boost;
- normal launched fireworks still function;
- fireworks remain usable for recipes/signaling/celebration.

### Nether scale

In a disposable/new world with the standalone datapack enabled, record paired portal coordinates.

Acceptance expectation:

```text
Overworld X/Z
    ~= Nether X/Z

not

Overworld X/Z / 8
```

Also test:

- return mapping;
- portal collision/placement sanity;
- whether 1:1 removes the major transport bypass without making dimension access confusing.

### Integrated logistics

Compare a prepared glider route and a simple powered aircraft route for:

- outbound travel;
- return travel;
- carried inventory;
- bulk/container freight;
- route preparation;
- departure flexibility;
- arrival flexibility;
- repeatability;
- weather/launch sensitivity;
- recovery after error.

The glider may remain excellent at personal movement. It fails the design only if it starts
substituting for industrial logistics.

---

## 10. Acceptance outcomes

### Reliable Gliders

```text
KEEP NEAR-STOCK
    if handling + updrafts make good personal traversal

TUNE DATA/CONFIG
    if source strength, height, speed, or durability is the only problem

A/B ALTERNATIVE GLIDER
    only if core handling is wrong
```

### Elytra suppression

```text
KEEP EXISTING MOD
    if it cleanly blocks boost and preserves fireworks

THIN SKYFORGE HOOK
    only if a small interoperability gap exists

BESPOKE MIXIN
    only if reuse-first options fail
```

### Nether scale

```text
KEEP 1:1 DATAPACK
    if it preserves aviation geography cleanly

REVISE TRANSPORT RULE
    if portal usability/recovery creates a worse problem

BESPOKE PORTAL LOGIC
    only after the datapack-level rule demonstrably fails
```

---

## 11. Explicitly deferred work

Wave C2 does not yet implement:

- authoritative wind;
- natural thermals;
- ridge lift;
- soaring-fauna flight AI;
- Riptide tuning;
- Ender Pearl/Chorus Fruit tuning;
- cross-dimension Aeronautics contraption transfer;
- hazardous rocket explosions;
- final Nether terrain;
- production quest guidance.

Those remain later acceptance or atmosphere/dimension work.

## Acceptance principle

> Personal mobility may be cheap, expressive, and surprisingly far-reaching; industrial mobility
> earns its place through payload, repeatability, flexibility, and infrastructure-scale logistics.
