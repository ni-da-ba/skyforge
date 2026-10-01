# Wave C11 — live pre-Brass first-flight recipe surface

**Status:** ACCEPTED merge candidate  
**Historical draft:** PR #233, closed as reserved historical work by AUDIT dormant-branch cleanup  
**Parent:** #224 Bootstrap Province  
**Downstream:** C12 / issue #239

## Purpose

C11 resumes the valid bounded capability from historical PR #233 on current `main`.

It asks one narrow executable question:

> Does the exact retained Create/Sable/Aeronautics stack expose live recipes for the essential first
> aircraft/workshop component family without directly hard-requiring Brass, petroleum, or other
> advanced families?

C11 is a recipe-surface milestone, not aircraft acceptance.

## Exact runtime

The isolated C11 run reuses the current immutable Wave C1 pins for:

- Create;
- Sable;
- Create Aeronautics, whose retained distribution supplies Simulated.

No CC:Tweaked, Create: Avionics, Diesel Generators, metallurgy candidate, or unrelated optional
engineering mod is added to the C11 source set.

## Required live outputs

```text
simulated:physics_assembler
simulated:engine_assembly
simulated:red_portable_engine
aeronautics:andesite_propeller
simulated:steering_wheel
simulated:swivel_bearing
simulated:white_symmetric_sail
create:mechanical_press
create:mechanical_saw
```

Every output must be registered and have at least one live RecipeManager candidate whose effective
direct ingredients retain an enumerable early alternative.

The fixture handles Create sequenced-assembly recipes conservatively by consulting the live public
`getIngredient()` surface when generic `Recipe#getIngredients()` is empty.

## Advanced-family exclusion

A direct ingredient fails C11 only when **all** enumerable concrete alternatives belong to an advanced
family represented by:

- Brass;
- petroleum/crude oil/gasoline/diesel/fuel oil;
- Netherite;
- Levitite;
- electric motors;
- capacitors.

This is deliberately an alternative-aware rule. A tag such as iron-or-zinc remains admissible if an
early alternative exists.

## Explicit limitations

C11 does **not** prove:

- complete transitive raw-material/BOM closure;
- the exact bootstrap quantities of Iron, Andesite, wool, adhesive, fuel, or food;
- guaranteed starting-world acquisition paths;
- that the aircraft assembles, takes off, handles, lands, or beats gliding for freight;
- Portable Engine powered-soaring cutoff behavior;
- player-facing first-hours timing or guidance.

Those remain C12 / Bootstrap / HS-06 concerns.

In particular, C11 can prove that petroleum is **not a direct first-flight recipe prerequisite**
without making any decision about later R3 petroleum availability.

## Historical-work rule

PR #233 was closed by AUDIT as **reserved historical work, not rejected**.

This recomposition starts from current `main` and carries forward only the still-valid bounded
recipe-runtime capability. It does not extend the stale integration head.

## Acceptance

1. exact current C1 flight-stack pins resolve;
2. all required outputs are registered;
3. every required output has at least one directly bootstrap-safe live recipe;
4. sequenced-assembly inputs are not vacuously accepted;
5. no production dependency is added merely for C11;
6. dedicated C11 workflow passes on the current recomposed head;
7. repository CI passes;
8. Content state records C11 accepted without weakening HS-06 or claiming transitive BOM closure.

No Minecraft human-eye/manual play gate applies to C11 itself.


## Accepted candidate evidence

Exact recomposed code head `8cef137fa0fc617e9c79ef0c3e3c0005137f86c6` passed:

- Wave C11 First Flight Recipe Runtime run `34189656148`;
- repository CI run `34189656149`.

The live RecipeManager emitted nine accepted recipe paths. The critical engine path was:

```text
simulated:engine_assembly
    <- simulated:sequenced_assembly/engine_assembly[create:iron_sheet]

simulated:red_portable_engine
    <- create:iron_sheet
     + simulated:engine_assembly
     + minecraft:blast_furnace
```

A follow-up audit of the upstream Simulated engine-assembly JSON confirmed its sequence contains only
cutting and pressing of the transitional engine assembly after the iron-plate input; it does not hide
a Brass/petroleum deployer ingredient.

C11 therefore accepts the **direct** pre-Brass/pre-petroleum recipe surface only. Transitive BOM,
quantities, guarantees, aircraft viability, and HS-06 remain open.
