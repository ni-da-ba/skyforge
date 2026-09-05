# CBC / Create: Metallurgy Interoperability and Bespoke Budget v0.1

**Snapshot:** 2026-09-05  
**Status:** Working compatibility/subtraction decision. Create: Big Cannons is retained. Create: Metallurgy remains conditional.

## Question

> Does Create: Metallurgy add enough useful shared heavy-industry capability to Create: Big Cannons to justify its extra machinery, materials, recipe surface, and integration work?

This audit treats **CBC as the baseline industrial system** and asks what Metallurgy must prove above that baseline.

# Executive decision

Current preferred baseline:

```text
KEEP CBC
KEEP CBC Cast Iron / Bronze / Steel / Nethersteel
KEEP Create superheat
DO NOT add Silver, Tin, Wolframite, Tungsten, or Obdurium geology for CBC

Create: Metallurgy
    -> retain only if factory A/B testing proves its foundry materially improves play
```

CBC does not need Create: Metallurgy to be complete.

The burden of proof is therefore on Metallurgy.

# Why CBC changes the material audit

CBC's material ladder is mechanically real rather than cosmetic.

Current big-cannon material properties establish materially different engineering envelopes:

| Material | Safe propellant stress | Weight | Failure | Weldability | Role |
|---|---:|---:|---|---|---|
| Wrought Iron | 1 | 2 | rupture | no | crude/early gun construction |
| Cast Iron | 2 | 3 | fragment | yes | inexpensive low-pressure casting |
| Bronze | 4 | 2 | rupture | yes | light, pressure-tolerant intermediate |
| Steel | 8 | 5 | fragment | yes | mature high-pressure artillery |
| Nethersteel | 10 | 6 | fragment | no | maximum-pressure/heavy top tier with maintenance tradeoff |

CBC therefore gives **Steel** and **Nethersteel** a much stronger retention case than Create: Metallurgy does on its own.

No new geological ore is required for either.

# CBC is already a bulk-metal consumer

CBC cannon casting consumes substantial metal per part:

```text
VERY_SMALL              7 ingots
SMALL                   9 ingots
MEDIUM                 12 ingots
LARGE                  14 ingots
VERY_LARGE             20 ingots
CANNON_END              9 ingots
SLIDING_BREECH          9 ingots
SCREW_BREECH            9 ingots

AUTOCANNON_BARREL       3 ingots
AUTOCANNON_BREECH       4 ingots
AUTOCANNON_RECOIL       4 ingots
```

Steel and Nethersteel built-up cannon construction uses multiple cast layers.

This means artillery is already a legitimate bulk-resource sink.

The player can rationally build:

- iron/coal supply;
- copper/zinc supply;
- metal stockpiles;
- foundry/casting halls;
- water/power for boring;
- ammunition production;
- transport to batteries, ships, airships, or fortifications.

CBC therefore succeeds at the requirement that a retained industrial material create **visible infrastructure and recurring logistics**.

# CBC-native process chain

CBC already supplies a coherent physical factory grammar:

```text
FEEDSTOCK
    -> ALLOY / PREPARE METAL
    -> MELT
    -> CANNON CAST
    -> COOL
    -> BORE
    -> BUILD UP / ASSEMBLE
    -> WELD where material permits
    -> MOUNT
    -> SUPPLY AMMUNITION / PROPELLANT
```

This is enough industrial depth that Skyforge should not add another foundry solely to make cannon making feel industrial.

# Material acquisition remains compact

## Cast Iron

CBC derives Cast Iron from existing Iron + carbon/fuel inputs.

No Cast Iron ore exists or is required.

## Bronze

CBC provides a Tin-free route based on retained materials:

```text
Copper
+ Zinc
+ Cinder Flour
+ HEATED mixing
    -> Bronze
```

Therefore:

```text
TIN GEOLOGY
    NOT JUSTIFIED BY CBC
```

## Steel

Current CBC route:

```text
2 Iron
+ Coal / Charcoal
+ HEATED mixing
    -> 2 Steel
```

Steel is manufactured, not mined.

## Nethersteel

Current high-value route includes:

```text
Netherite Scrap
+ Steel
+ SUPERHEATED mixing
    -> Nethersteel
```

This provides a strong Nether/superheat payoff without another ore family.

# CBC ammunition strengthens retained-resource economies

CBC's current propellant/explosive recipes are unusually favorable to Skyforge's subtraction rule.

Guncotton currently combines:

```text
nitrable material (default Paper)
+ Gunpowder
+ nitro acidifier (default Redstone)
+ Water
    -> Guncotton
```

The nitro branch also uses existing materials such as:

- Blaze Powder;
- Magma Cream;
- Gunpowder;
- Guncotton;
- Redstone;
- Water;
- slime-related inputs.

This creates a chemical/ammunition industry without needing Sulfur, Saltpeter, Nitrate, or another bespoke mineral economy.

### Decision

Do **not** add chemistry ores merely for realism.

CBC's abstraction already creates enough production structure.

# What Create: Metallurgy actually adds

The strongest case for Create: Metallurgy is **not** Tungsten.

It is the process machinery:

- general-purpose molten-metal foundry;
- Foundry Basin / Mixer;
- Casting Basin / Casting Table;
- graphite molds;
- Industrial Crucible;
- scalable bulk melting;
- multi-fluid storage;
- heat-scaled throughput;
- ladles / gauges / foundry logistics.

These could make a large artillery works more satisfying if they become a shared upstream metal plant rather than a parallel minigame.

# Interoperability audit

## Item-level Steel is favorable

CBC consumes common Steel item tags such as:

```text
c:ingots/steel
```

Create: Metallurgy provides its Steel through the same general common-material ecosystem.

Therefore item-level recipes can be made coherent without code.

### Risk

Both mods provide a physical Steel item and independent Steel production paths.

That creates potential:

- JEI/EMI clutter;
- player uncertainty about which Steel is canonical;
- conversion/yield loops;
- duplicated storage blocks/forms.

This is a **presentation/integration problem**, not a reason for another raw resource.

## Fluid-level Steel is not seamless by default

CBC cannon casting consumes common molten-fluid tags:

```text
c:molten_cast_iron
c:molten_bronze
c:molten_steel
c:molten_nethersteel
```

Create: Metallurgy registers its molten Steel under its own molten-material system and does not, in the audited source, place it in CBC's `c:molten_steel` tag.

Therefore this desirable route is **not native yet**:

```text
Metallurgy Foundry
    -> molten Steel
    -> pipe directly into CBC cannon cast
```

### Required fix

Add Metallurgy molten Steel source/flowing fluids to:

```text
c:molten_steel
```

Classification:

```text
LOW BESPOKE
    datapack fluid-tag bridge
```

No mixin or custom code should be needed.

## Bronze is partially favorable

Create: Metallurgy has a molten-Bronze compatibility family.

Its generic recipe generation is tag-driven, so CBC's Bronze item tags can make Bronze inputs visible to Metallurgy processing even though Bronze is not a native Metallurgy raw resource.

But CBC cannon casts still require `c:molten_bronze`.

If Metallurgy molten Bronze is to feed CBC casts, add the matching common fluid tag.

Classification:

```text
LOW BESPOKE
    datapack fluid-tag bridge
```

Do not introduce Tin to make this work.

## Cast Iron is not a native Metallurgy metal family

Create: Metallurgy does not presently define a Cast Iron metal family equivalent to CBC's.

Therefore a full route such as:

```text
Metallurgy Industrial Crucible
    -> bulk-melt Cast Iron
    -> CBC cast
```

would require new Metallurgy custom foundry/melting recipes if desired.

Classification:

```text
LOW/MODERATE DATA INTEGRATION
    custom Create: Metallurgy recipe JSON/datapack
```

This is **optional**.

CBC already melts Cast Iron itself.

## Nethersteel is also CBC-native

Create: Metallurgy does not need to own Nethersteel production.

CBC already provides the meaningful superheated alloy route and the material's distinctive weapon properties.

A Metallurgy bulk-melting recipe for Nethersteel could be added only if the shared foundry proves valuable.

Classification:

```text
OPTIONAL LOW/MODERATE DATA INTEGRATION
```

Do not make this an acceptance requirement for CBC.

# The best-case Metallurgy role

If retained, Create: Metallurgy should become a **throughput upgrade**, not a progression gate in front of CBC.

Preferred relationship:

```text
CBC bench / early industry
    heated mixer -> Steel
    CBC foundry lid -> molten cannon metal
    CBC casting -> cannon parts

MATURE SHARED FOUNDRY
    bulk input
    larger molten-metal buffers
    high-throughput melting/alloying
    shared piping/storage
    CBC cannon casts downstream
```

This gives Metallurgy a reason to exist:

> it makes an established metal economy operate at industrial scale.

It does **not** require the player to learn Tungsten/Obdurium before being allowed to make a serious cannon.

# The wrong Metallurgy role

Reject this structure:

```text
CBC needs Steel
    -> must find Wolframite
    -> make Tungsten
    -> make Obdurium
    -> build Industrial Crucible
    -> finally make artillery
```

None of those extra materials explain the weapon's performance.

They are self-gating machinery ingredients.

That is exactly the material bloat the subtraction audit is intended to remove.

# Industrial Crucible recipe if Metallurgy survives

Current stock Industrial Crucible construction requires Tungsten/Obdurium.

Because those materials are rejected from primary progression, the recipe must change if the machine survives.

Leading semantic recipe grammar:

```text
REFRACTORY CONSTRUCTION
+ mature retained structural metal
+ machining / assembly
    -> Industrial Crucible
```

Candidate material choices:

- Steel as the baseline structural metal;
- Nethersteel only if testing shows the crucible should be a later/high-temperature capital machine;
- Sturdy Sheet only if it creates useful Create integration rather than arbitrary cost;
- Brass only for controls/attachments, not as the refractory body.

Exact counts are deliberately not locked yet.

Classification:

```text
LOW BESPOKE
    one datapack recipe override
```

# Steel identity options

If Metallurgy is retained, choose one of these explicitly.

## Option S1 — CBC Steel is canonical

- keep CBC Steel recipe/item as visible canonical Steel;
- use common tags so Metallurgy accepts it;
- suppress/rewrite redundant Metallurgy Steel output where practical;
- bridge CBC Steel/molten Steel into Metallurgy processing only where needed.

### Advantage

CBC remains self-contained and removing Metallurgy later is easy.

## Option S2 — Metallurgy Steel is canonical

- use Metallurgy Steel for mature metalworks;
- rewrite CBC simple Steel recipe to output the common/canonical Steel;
- bridge Metallurgy molten Steel to CBC casting.

### Advantage

Cleaner if Metallurgy becomes a core industrial dependency.

### Cost

Makes CBC more dependent on Metallurgy integration.

## Option S3 — both items remain tag-equivalent

### Advantage

Almost zero work.

### Cost

Worst player-facing coherence.

### Current preference

```text
S1 while Metallurgy remains conditional
```

Do not make a retained mod depend on an A/B candidate.

# Bespoke/config budget

## CBC alone

| Integration | Class | Required? |
|---|---|---|
| Skyforge resource abundance tuning | config/worldgen semantics | yes |
| CBC material property tuning | data | only if playtest shows problem |
| casting-time tuning | data | only if tedious |
| quest/onboarding integration | data/content | later |
| cannon/aircraft mass/recoil validation | runtime test | yes |
| custom code | high bespoke | **no expected need** |

### Assessment

**Low integration burden.**

CBC's industrial chain is practical enough to preserve close to stock until playtesting proves otherwise.

## CBC + Metallurgy minimum viable bridge

| Integration | Class | Required if Metallurgy retained? |
|---|---|---|
| disable Wolframite worldgen | config/datapack | yes |
| replace Industrial Crucible Tungsten/Obdurium gate | recipe override | yes |
| `c:molten_steel` bridge | fluid tag | yes for shared Steel casting |
| `c:molten_bronze` bridge | fluid tag | desirable |
| normalize duplicate Steel recipe/item presentation | recipe/tag/UI data | desirable |
| Cast Iron bulk-melting recipe in Metallurgy | custom data recipe | optional |
| Nethersteel bulk-melting recipe in Metallurgy | custom data recipe | optional |
| custom Java/mixin code | code | **no current justification** |

### Assessment

**Low-to-moderate datapack burden; no code burden currently justified.**

That is acceptable *only if* the foundry is more fun/useful than CBC alone.

# Practical/fun A/B test

Build the same representative artillery works twice.

## Factory A — CBC native

Target production:

```text
1 useful Cast Iron/Bronze gun
1 mature Steel built-up gun
1 Steel autocannon
representative ammunition batch
```

Measure:

- footprint;
- number of machines;
- active attention;
- passive wait;
- metal handling friction;
- throughput;
- visual/industrial satisfaction;
- how much easier the second gun is than the first.

## Factory B — CBC + stripped Metallurgy

Produce the same output with:

- Wolframite/Tungsten/Obdurium removed from progression;
- revised Industrial Crucible recipe;
- shared molten-Steel/Bronze tags;
- otherwise minimal changes.

Measure the same dimensions.

## Retention rule

Keep Metallurgy only if Factory B provides at least one substantial benefit such as:

- genuinely useful bulk throughput;
- simpler large-volume metal handling;
- a satisfying foundry layout not already achieved by CBC;
- reusable casting/melting infrastructure that benefits non-CBC industry;
- meaningful automation improvement.

Do **not** keep it merely because the factory looks more complicated.

# Aircraft/artillery interaction gate

CBC is especially valuable in Skyforge because weapon selection should interact with vehicle engineering.

Manual integrated tests should compare:

```text
Cast Iron gun
Bronze gun
Steel gun
Nethersteel gun
```

on representative Sable/Aeronautics vehicles.

Record:

- assembled mass;
- center-of-mass effect;
- recoil behavior;
- structural/contraption stability;
- ammunition mass/volume;
- reload ergonomics;
- propulsion/fuel penalty;
- firing while maneuvering;
- whether heavier material actually changes aircraft architecture.

Desired result:

> A better cannon should often require a better vehicle, not merely more ingots.

# Current material conclusions after CBC audit

```text
SILVER
    raw resource rejected

TIN
    rejected unless another independent consumer emerges

WOLFRAMITE / TUNGSTEN / OBDURIUM
    rejected from current progression

CAST IRON
    keep: real CBC engineering role, no new ore

BRONZE
    keep: real CBC engineering role, no Tin required

STEEL
    keep: major recurring CBC industrial material

NETHERSTEEL
    keep: meaningful Nether/superheat artillery material

SUPERHEAT
    keep: independently justified by Create + petroleum + CBC

CREATE: METALLURGY
    A/B candidate as process machinery only
```

# Acceptance tests

## CBC-MET-1 — CBC independence

CBC's useful material/cannon/ammunition progression works without Create: Metallurgy installed.

## CBC-MET-2 — no orphan ore

No new geological material is introduced solely to satisfy CBC or Metallurgy machinery recipes.

## CBC-MET-3 — Steel coherence

The player experiences one coherent Steel economy even if multiple mods register Steel items.

## CBC-MET-4 — shared molten Steel

If Metallurgy is retained, its molten Steel can feed CBC cannon casting without manual bucket/item conversion.

## CBC-MET-5 — shared Bronze where retained

If Metallurgy is retained, Bronze processing does not require adding Tin and can feed CBC casting coherently.

## CBC-MET-6 — optional exotic-fluid integration

Cast Iron and Nethersteel bulk-foundry recipes are added only if the shared foundry produces measurable gameplay value.

## CBC-MET-7 — throughput payoff

A mature foundry makes repeated cannon manufacture meaningfully easier, not merely more elaborate.

## CBC-MET-8 — no code without demonstrated gap

No mixin/custom Java integration is written unless datapack tags/recipes demonstrably cannot solve a required interaction.

## CBC-MET-9 — artillery logistics

Representative cannon production creates enough metal/ammunition demand to justify transport and stockpiling without artificial recipe inflation.

## CBC-MET-10 — vehicle engineering payoff

Material choice changes practical aircraft/airship weapon integration through mass, recoil, pressure tolerance, maintenance, or ammunition requirements.

# Acceptance principle

> CBC earns its industry by making metal choice, manufacturing method, weapon performance, and logistics agree with one another. Any extra metallurgy mod must improve that loop rather than merely sit in front of it.
