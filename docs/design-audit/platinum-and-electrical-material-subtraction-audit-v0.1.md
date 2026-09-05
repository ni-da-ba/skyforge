# Platinum and Electrical Material Subtraction Audit v0.1

**Snapshot:** 2026-09-05  
**Status:** Working material-retention decision for Create Crafts & Additions and Create Propulsion: Simulated.

## Governing rule

> An advanced machine may justify an advanced recipe. It does not automatically justify a new ore.

Raw-material geography has a higher burden of proof than manufactured alloys/components because every authored ore adds:

- worldgen authority;
- regional abundance tuning;
- player recognition burden;
- storage/processing clutter;
- another logistics economy that must remain relevant.

# Silver

Current decision remains:

```text
SILVER ORE / GEOLOGY
    EXCLUDE
```

Create Crafts & Additions does not supply a Silver geology itself; Silver enters chiefly as the conditional ingredient for Electrum.

Electrum has a real high-current electrical property, but that does not justify inventing Silver deposits.

Current baseline should allow Gold to carry required electrical storage/transmission where the mod's existing usable-wire/rod tags already permit it.

Electrum remains optional until a representative electrical network proves that Gold's lower transfer ceiling creates a worthwhile high-current engineering problem.

# Platinum

Create Propulsion: Simulated differs from the Silver case because it **does** currently author Platinum ore in the Overworld.

Current source registers:

- Platinum Ore;
- Deepslate Platinum Ore;
- Raw Platinum;
- Platinum Ingot/Nugget/Sheet/Block;
- multiple Platinum ore placed/configured features and Overworld biome modifiers.

Therefore selecting the mod without intervention would introduce another geological material economy.

## Current substantive Platinum consumers

Audited current recipes use Platinum in:

### Cable / relay infrastructure

Platinum Sheet/Nugget participates in:

- cable;
- cable relay;
- associated electrical/control infrastructure.

### Vector Thruster

Current Vector Thruster uses:

- Ion Thruster;
- Platinum Sheets;
- Platinum Nuggets;
- Sable gimbal sensor.

This is a meaningful advanced propulsion component.

However the **capability belongs to the Vector Thruster**, not demonstrably to Platinum as a material.

### Coral Generator

Platinum Sheets are used in the Coral Generator.

Again, the generator's capability is electrical generation from Coral fluid; the audited source does not establish a separate material property that only Platinum can provide.

### Redstone Converter

Platinum Sheet participates in the Redstone Converter.

This is an advanced control recipe, but not an independent reason for geological Platinum.

### Platinum Fluid Tank / Vessel

Current code gives Platinum fluid storage **2x the per-block capacity** of the corresponding ordinary copper storage.

This is the strongest direct Platinum-themed block property in the audited source.

But it remains a quantitative compactness upgrade rather than a new fluid-handling capability.

# Platinum retention test

A raw Platinum resource would need to justify all of this:

```text
NEW ORE
    -> new regional geography
    -> mining route
    -> processing/storage identity
    -> repeat demand
    -> durable engineering reason
```

Current evidence instead looks like:

```text
NEW ORE
    -> advanced recipe ingredient
    -> denser tank
    -> advanced recipe ingredient
```

That is not enough under the current Skyforge material doctrine.

# Current decision

```text
PLATINUM WORLDGEN
    REJECT / DISABLE IF PROPULSION IS RETAINED

PLATINUM AS REQUIRED PRIMARY PROGRESSION MATERIAL
    REJECT

PROPULSION FEATURES THAT CURRENTLY USE PLATINUM
    RETAIN AS CANDIDATE FEATURES
    RECIPE-REBASE ONTO RETAINED MATERIALS IF THE MOD IS SELECTED
```

This does **not** mean Create Propulsion: Simulated is rejected.

It means the mod must earn its propulsion/electrical features independently of its ore.

# Recipe-rebase principles

Do not replace every Platinum ingredient with the same material blindly.

Map the ingredient to the engineering role of the component.

## Cable / relay

Candidate retained inputs:

- Copper for ordinary conduction;
- Gold for higher-current or advanced conduction;
- optional Electrum only if its high-current tier survives testing;
- Brass for connector/control hardware.

Preferred concept:

```text
CONDUCTOR
+ control/structural component
    -> cable / relay
```

not:

```text
rare metal because recipe says rare metal
```

## Vector Thruster

This is a genuinely advanced flight-control component.

Candidate retained material vocabulary:

- Steel for high-load structure;
- Nethersteel if high-temperature/advanced material identity is genuinely useful;
- Brass for precision control;
- electrical conductor/capacitor components;
- Sable gimbal sensor;
- Ion Thruster itself.

The recipe should communicate:

> advanced propulsion + precision vector control

rather than:

> mine Platinum to unlock the next tier.

## Coral Generator

Use electrical/chemical infrastructure components that reflect its actual job.

Potential vocabulary:

- Brass control casing;
- Gold/Copper electrical conductor;
- Sturdy Sheet/Steel structure;
- existing docking/electrical components.

No new ore is needed unless runtime behavior reveals a real missing material role.

## High-capacity fluid storage

The doubled-capacity Platinum tanks are useful, but the value is **compact fluid storage**.

If retained, candidate approaches are:

### P-TANK-A — re-recipe as reinforced advanced tank

```text
ordinary fluid tank
+ Steel / Sturdy Sheet / retained structural material
    -> high-capacity tank
```

### P-TANK-B — keep Platinum-branded block but obtain through a manufactured advanced component

Possible but less coherent if Platinum is otherwise absent.

### Current preference

P-TANK-A conceptually.

The block's visual/name integration may determine whether a resource-pack/name adjustment is worthwhile later.

# Worldgen integration burden

Disabling Propulsion's native Platinum ore should be a low-bespoke pack operation.

Current source exposes explicit Overworld biome modifiers / placed features for Platinum.

Preferred implementation order:

1. datapack/config disable/override if supported cleanly;
2. suppress biome modifiers/placed-feature injection;
3. do not let native Platinum generation coexist with Skyforge-authoritative Overworld geology merely because the mod ships it.

No custom Skyforge ore authoring is planned.

# Electrum versus Platinum

These cases should not be conflated.

## Electrum

Has a direct hard-coded electrical distinction:

```text
higher transfer capacity
```

Therefore the manufactured material may survive even though Silver geology does not.

## Platinum

Current audited uses mostly gate machines and increase tank compactness.

The source does not presently establish a broad intrinsic material behavior comparable to CBC Steel/Nethersteel or CC&A high-current Electrum.

Therefore Platinum has a **weaker retention case as a material** despite being used in more recipes.

This is an important rule:

> Recipe count is not the same thing as material identity.

# Cross-system material vocabulary after subtraction

The current preferred engineering vocabulary is becoming deliberately compact:

```text
IRON
    basic structure / machinery

COPPER
    fluids / conductors

ZINC
    Brass / capacitors / Levitite

BRASS
    precision control / logistics / mature machinery

GOLD
    advanced conductor / vanilla strategic value

ELECTRUM (optional)
    explicitly high-current conductor if needed

CAST IRON
    inexpensive CBC pressure vessel / cannon material

BRONZE
    light pressure-tolerant CBC material

STEEL
    heavy structural / high-pressure industrial material

NETHERSTEEL
    severe high-pressure / top artillery material with weight/maintenance tradeoff

END-DERIVED LEVITITE
    passive lift support
```

A future material must explain what it adds that this vocabulary cannot.

# Bespoke/config budget if Propulsion is retained

| Change | Expected class | Necessity |
|---|---|---|
| Disable Platinum worldgen | datapack/config | required |
| Rebase Cable/Relay recipes | recipe data | likely required |
| Rebase Redstone Converter | recipe data | likely required |
| Rebase Coral Generator | recipe data | likely required if retained |
| Rebase Vector Thruster | recipe data | required if retained |
| Rebase high-capacity fluid storage | recipe data | required if retained |
| Hide obsolete Platinum ore/raw forms | recipe-viewer/resource presentation | desirable |
| Java/mixin changes | code | no current justification |

Assessment:

```text
LOW-TO-MODERATE DATA INTEGRATION
NO CURRENT CODE REQUIREMENT
```

The burden is acceptable only if Propulsion's thrusters/controls survive their own gameplay audit.

# Acceptance tests

## MAT-ELEC-1 — no Silver geology

Required electrical progression works without Silver worldgen.

## MAT-ELEC-2 — Electrum earns survival

Electrum remains only if representative networks benefit materially from its high-current transfer tier.

## MAT-ELEC-3 — no Platinum geology

Selecting Create Propulsion: Simulated does not automatically add Platinum ore to Skyforge's Overworld.

## MAT-ELEC-4 — propulsion capability survives material subtraction

Vector/ion/reaction propulsion remains craftable through retained materials without losing meaningful progression structure.

## MAT-ELEC-5 — compact storage is not mistaken for geology

High-capacity fluid tanks may survive as an engineering capability without requiring a dedicated ore.

## MAT-ELEC-6 — recipe semantics

Replacement ingredients communicate conductor, structure, control, heat, or propulsion roles rather than arbitrary rarity.

## MAT-ELEC-7 — minimal code

Recipe/worldgen data solves the integration; custom Java is introduced only if an actual required behavior cannot be expressed otherwise.

# Current decision summary

```text
SILVER
    exclude raw resource

ELECTRUM
    optional manufactured high-current material

PLATINUM
    exclude raw resource / worldgen

PROPULSION
    remains R&D candidate
    rebase useful machines onto retained material vocabulary if selected
```

# Acceptance principle

> Keep the capability. Keep the material only when the capability is meaningfully a property of the material itself.
