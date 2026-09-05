# First-Flight Bootstrap BOM v0.1

**Snapshot:** 2026-09-05  
**Status:** Parameterized source-derived BOM. Not a finished aircraft blueprint or accepted balance target.

## Purpose

Convert the first-flight recipe closure into a quantity model without selecting one exact aircraft.

Leading prototype:

~~~text
Physics Assembler
Portable Engine
Andesite Propeller(s)
Create Sails for lift
Symmetric Sails for control/stability
Steering Wheel
Swivel Bearing(s)
ordinary structure blocks
adhesive
stationary Create workshop
~~~

## Parameters

~~~text
P = Andesite Propeller count
S = total regular + symmetric sail blocks
B = Swivel Bearing count
~~~

Actual aerodynamic size and control geometry must be established in-game.

## Fixed workshop

Source-backed machinery includes:

- Mechanical Press: shaft + andesite casing + iron block.
- Mechanical Saw: 3 iron sheets + iron ingot + andesite casing.
- Iron-sheet production through pressing.
- Portable-engine sequenced assembly: CUT then PRESS, eight loops.

Keep two requirements abstract until a playable proof exists:

~~~text
WORKSHOP_POWER_PATH
SEQUENCED_ASSEMBLY_HANDLING
~~~

Do not force water-wheel geography or dried-kelp/belt access unless the real minimal workshop proves them necessary.

## Fixed vehicle overhead

Physics Assembler:

~~~text
2 Andesite Alloy
1 Andesite Casing
1 Lever
~~~

Portable Engine:

~~~text
1 Iron Sheet
1 Engine Assembly
1 Blast Furnace
~~~

Steering Wheel:

~~~text
1 Large Cogwheel
1 Andesite Casing
1 Shaft
~~~

Assembly also requires:

~~~text
ADHESIVE_PATH
~~~

Current direct choices are Super Glue (slime + iron) or Honey Glue (honey + filling).

## Variable propulsion cost

Each Aeronautics Andesite Propeller requires:

~~~text
1 Create Propeller
1 Wooden Slab
1 Shaft
~~~

Each Create Propeller requires:

~~~text
4 Iron Sheets
1 Andesite Alloy
~~~

So each added propulsion unit is mostly iron-sheet cost plus one alloy and one shaft.

## Variable sail cost

Every two Create Sails require:

~~~text
1 Wool
2 Wooden Rods
1 Andesite Alloy
~~~

Therefore:

~~~text
Andesite Alloy += ceil(S / 2)
Wool            += ceil(S / 2)
Wooden Rods     += 2 * ceil(S / 2)
~~~

Symmetric Sails are converted 1:1 from ordinary sails, so the same material model covers both.

## Swivel control cost

Each Swivel Bearing requires:

~~~text
1 Wooden Slab
1 Industrial Iron Block
1 Cogwheel
~~~

Current Create stonecutting converts one iron ingot into two Industrial Iron Blocks, so this is an inexpensive pre-brass control path.

## Shaft batching

Current Create recipe:

~~~text
2 Andesite Alloy -> 8 Shafts
~~~

Do not charge two alloys per individual shaft in the planner. Compute total shaft demand and round by batches.

## Provisional quantity bands

A representative small one-propeller craft/workshop appears to land in the low-to-mid teens of Andesite Alloy before optional machinery, depending mostly on sail count and shaft reuse.

That implies a first tuning band around:

~~~text
32-48 accessible Andesite blocks
~~~

for bootstrap testing.

Major iron costs include the press iron block, saw, portable engine/blast furnace, propeller sheets, industrial-iron/control path, adhesive, and the nugget share of Andesite Alloy.

A representative minimum is around thirty iron-ingot equivalents before normal tools, mistakes, extra control hardware, or retry margin.

Therefore a first bootstrap test band around:

~~~text
48-64 accessible iron-ingot equivalents
~~~

is reasonable.

These are **engineering estimates only**. They must not become accepted worldgen constants until a real craft is tested.

## Renewable guarantees

Prefer capability guarantees over exact piles for:

~~~text
WOOD
WOOL_OR_FIBER_PATH
COMMON_OR_RENEWABLE_FURNACE_FUEL
ADHESIVE_PATH
WORKSHOP_POWER_PATH
~~~

Wool demand depends on proven aerodynamic area. Wood is already a survival resource. Charcoal can satisfy early portable-engine fuel without making coal or petroleum mandatory.

## Resources currently absent from the leading closure

Current source evidence does not show a mandatory dependency on:

~~~text
Copper
Zinc
Brass
Petroleum
Electricity
Gold
Diamond
Nether materials
~~~

This preserves those materials for meaningful post-flight progression.

## Future verifier input

~~~text
FirstFlightPrototype {
    propellerCount
    regularSailCount
    symmetricSailCount
    swivelBearingCount
    shaftCount
    frameMaterialCount
    adhesivePath
    workshopPowerPath
    assemblyHandlingPath
}
~~~

The verifier should derive raw resource demand, then add ordinary tool/workshop overhead and one meaningful retry margin.

## Required playable proof

The BOM becomes authoritative only after a real specimen demonstrates:

1. assembly;
2. takeoff;
3. controlled forward motion;
4. stable steering/control;
5. safe landing;
6. modest cargo;
7. ordinary-fuel operation;
8. disassembly/reassembly;
9. recovery after a failed craft;
10. no hidden brass/zinc/petroleum dependency.

## Acceptance principle

> Bootstrap quantities should be derived from a proven vehicle/workshop BOM with experimentation margin, not from ore-rarity intuition.
