# First-Flight Recipe Closure Audit v0.1

**Snapshot:** 2026-09-05  
**Status:** Source-grounded recipe audit against current upstream Create mc1.21.1/dev and Create Aeronautics / Simulated Project main. Exact starter craft geometry remains a playtest decision.

## Objective

Identify the minimum **material and processing closure** required to build a genuinely controllable first Skyforge aircraft without assuming:

- brass;
- petroleum;
- electricity;
- advanced navigation;
- rare structure loot;
- an already-functional aircraft.

This document distinguishes:

1. source-proven recipe dependencies;
2. candidate craft architecture;
3. remaining playtest/mechanical uncertainties.

## Scope boundary: gliding is not first flight

Skyforge may preserve a cheap early personal glider for local starter-group crossings.

That tool does **not** close this milestone.

For this audit, "first flight" means a powered reusable Aeronautics/Simulated craft that demonstrates a real logistics-enabling capability increase:

- powered altitude recovery;
- repeatable route operation;
- ordinary inter-cluster reach;
- modest freight;
- controllable landing/relaunch.

The glider's recipe and traversal closure are tracked separately in [Early Glider Mobility Contract v0.1](early-glider-mobility-contract-v0.1.md).

This distinction prevents local movement convenience from weakening the requirement that powered flight unlock regional economic geography.

## Upstream facts

### Physics Assembler

Current recipe:

~~~text
2 x Create Andesite Alloy
1 x Create Andesite Casing
1 x Lever
-> Simulated Physics Assembler
~~~

The upstream Ponder text states that a Physics Assembler assembles blocks selected with **Super Glue or Honey Glue** into a Simulated Contraption.

Therefore adhesive is a real transitive bootstrap dependency.

### Andesite Alloy

Current Create 1.21.1 recipe:

~~~text
2 x Andesite
2 x Iron Nugget
-> 1 x Andesite Alloy
~~~

Create also supports zinc as an alternative route, but **zinc is not required** for early andesite alloy.

This materially changes the bootstrap assumption: first flight need not be zinc-gated if the selected craft avoids brass components.

### Andesite Casing

Current recipe is Create item application:

~~~text
1 x Stripped Log
1 x Andesite Alloy
-> 1 x Andesite Casing
~~~

No brass/copper/zinc requirement.

### Iron Sheet / Plate

Current Create pressing:

~~~text
1 x Iron Ingot
-> 1 x Iron Sheet
~~~

Aeronautics and Simulated recipes consume the common iron-plate tag, which Create's iron sheet satisfies.

### Mechanical Press

Current Create recipe:

~~~text
1 x Shaft
1 x Andesite Casing
1 x Iron Storage Block
-> Mechanical Press
~~~

This makes iron abundance, not zinc, the major early processing cost.

### Mechanical Saw

Current Create recipe:

~~~text
3 x Iron Sheet
1 x Iron Ingot
1 x Andesite Casing
-> Mechanical Saw
~~~

The press must therefore precede the saw.

### Portable Engine

Current Simulated recipe:

~~~text
1 x Iron Plate
1 x Engine Assembly
1 x Blast Furnace
-> Portable Engine
~~~

The Engine Assembly is produced by an 8-loop Create sequenced assembly starting from one iron plate:

~~~text
CUT
PRESS
(repeated eight loops)
-> Engine Assembly
~~~

No brass or zinc appears in the current recipe.

The upstream Ponder text states that the Portable Engine burns ordinary fuel; coal and charcoal are demonstrated, while Blaze Cake can later superheat it.

Therefore petroleum is **not** required for first powered flight.

### Andesite Propeller

Current Aeronautics path:

~~~text
Create Propeller
+ Wooden Slab
+ Create Shaft
-> Andesite Propeller
~~~

Create Propeller is currently:

~~~text
4 x Iron Sheet
1 x Andesite Alloy
-> Create Propeller
~~~

Again, no brass.

### Propeller Bearing

The current Aeronautics Propeller Bearing recipe **does** require a Create Brass Casing.

Therefore it should not be assumed part of the bootstrap path unless playtesting proves it necessary.

The preferred first-flight prototype should test ordinary Andesite Propellers powered directly from a portable-engine kinetic network before accepting a brass dependency.

### Steering Wheel

Current Simulated recipe:

~~~text
1 x Large Cogwheel
1 x Andesite Casing
1 x Shaft
-> Steering Wheel
~~~

Upstream Ponder describes Steering Wheels as providing precise rotational output controlled by right-click + mouse movement.

No brass.

### Swivel Bearing

Current recipe:

~~~text
1 x Wooden Slab
1 x Create Industrial Iron Block
1 x Cogwheel
-> Swivel Bearing
~~~

Create currently stonecuts:

~~~text
1 x Iron Ingot
-> 2 x Industrial Iron Block
~~~

No brass.

Upstream Ponder describes Swivel Bearings as movable substructures that can pass rotation through them, making them a plausible control-surface mechanism.

### Sails / aerodynamic surfaces

Current Simulated Ponder explicitly states:

- regular Create Sails provide lift on Simulated Contraptions;
- enough lift can achieve flight;
- Symmetric Sails provide drag rather than lift;
- angled Symmetric Sails can be used for turning or stabilization surfaces.

Current Simulated Symmetric Sail recipe converts two Create sails into two symmetric sails.

Create regular sails are early andesite/wool/wood components.

This gives a source-supported pre-brass fixed-wing control vocabulary.

### Hot-air envelope

Current Aeronautics white envelope:

~~~text
2 x White Wool
2 x Stick
-> 4 x Hot Air Envelope
~~~

### Adjustable burner

Current Aeronautics recipe consumes:

~~~text
3 x Iron Plate
2 x Andesite Alloy
1 x Redstone Dust
1 x item in aeronautics:burner_fire
~~~

Current burner-fire tag contains:

~~~text
minecraft:coal_block
~~~

Therefore the hot-air path adds:

- redstone;
- a coal block;
- multiple iron sheets.

It is still early-game, but not strictly simpler in material closure than the fixed-wing path.

## Adhesive dependency

### Super Glue

Current Create recipe:

~~~text
2 x Slimeball
1 x Iron Sheet
1 x Iron Nugget
-> Super Glue
~~~

This is the most awkward current first-flight dependency because slimeballs are not automatically guaranteed by ordinary island geology.

### Honey Glue

Current Simulated recipe:

~~~text
1 x Iron Plate
500 mB x common Honey fluid
-> Honey Glue
~~~

using Create filling.

This removes slimeballs but adds:

- honey access;
- fluid handling/filling machinery.

The correct bootstrap abstraction should therefore be:

~~~text
ADHESIVE_PATH
~~~

rather than hard-coding slimeballs into the world recipe before playtesting.

Possible later realizations:

1. guarantee early slime access;
2. guarantee early honey ecology + filling capability;
3. guarantee a tiny instructional adhesive supply through a bootstrap site;
4. add one small pack-level alternative adhesive recipe.

Option 4 is acceptable if it removes disproportionate friction without erasing meaningful progression; it is far cheaper than designing a custom adhesive system.

## Candidate first-flight architectures

### Candidate A — pre-brass fixed-wing craft

Source-supported conceptual components:

~~~text
Physics Assembler
Portable Engine
Andesite Propeller
regular Create Sails for lift
Symmetric Sails for drag/control
Steering Wheel
Swivel Bearing(s)
seat / ordinary structure blocks
adhesive
ordinary furnace fuel
~~~

Likely raw resource families:

~~~text
IRON
ANDESITE
WOOD
WOOL
STONE
FUEL
ADHESIVE_PATH
~~~

Potentially no mandatory:

~~~text
COPPER
ZINC
BRASS
PETROLEUM
ELECTRICITY
GOLD
DIAMOND
NETHER RESOURCE
~~~

This is the leading bootstrap prototype.

### Candidate B — hot-air / buoyant craft

Conceptual components:

~~~text
Physics Assembler
Hot Air Envelopes
Adjustable Burner
steering / thrust mechanism
ordinary structure blocks
adhesive
~~~

Adds:

~~~text
REDSTONE
COAL BLOCK
~~~

and may still require powered propellers/control for reliable lateral navigation.

Therefore hot-air flight is attractive as an early/alternate mobility mode but is **not yet proven to have a smaller reliable-flight closure**.

## Processing closure

Even if raw materials are simple, the player must be able to manufacture the portable engine and propeller.

Source-proven processing needs include:

~~~text
basic Create kinetic source
Andesite Casing
Mechanical Press
Mechanical Saw
iron-sheet production
sequenced CUT/PRESS engine assembly
~~~

A water wheel or hand-crank path can provide early stationary rotational power without strategic fuel.

The exact minimum sequenced-assembly transport arrangement (manual/depot versus belt line) should be validated in-game before the bootstrap recipe freezes dried-kelp/belt requirements.

## Provisional bootstrap resource set

### Hard source-backed provisional guarantees

The starting cluster should currently assume access to:

~~~text
WOOD / LOGS / PLANKS / STICKS
STONE / COBBLESTONE / SMOOTH-STONE PATH
ANDESITE
IRON
WOOL
ORDINARY FURNACE FUEL
ADHESIVE_PATH
~~~

### Conditional on selected first-flight craft

Potential:

~~~text
REDSTONE
COAL BLOCK
~~~

for hot-air burner path.

### Not currently demonstrated as mandatory

~~~text
COPPER
ZINC
BRASS
PETROLEUM
GOLD
DIAMOND
ELECTRICITY
NETHER MATERIAL
~~~

This is a significant simplification relative to the earlier generic resource assumptions.

## Bootstrap world implications

### Andesite must become a real authored bootstrap resource

Because Create's entry material directly consumes vanilla andesite, Skyforge cannot accidentally omit andesite from all early island material palettes.

Options:

- map an ordinary authored lithologic family to andesite strongly enough that starting-cluster closure can prove access;
- provide a recipe/tag-compatible semantic alternative only if later material design warrants it.

Prefer preserving actual Create recipe semantics first.

### Iron must be generous enough for infrastructure, not merely tools

The first-flight chain consumes iron through:

- alloy nuggets;
- press iron block;
- saw;
- iron sheets;
- blast furnace;
- portable engine;
- propeller;
- swivel/control components.

Therefore the bootstrap requirement is not:

> one small iron vein exists.

It is:

> enough accessible iron exists to build an early Create workshop and first craft with reasonable failure/retry margin.

Exact quantity belongs to the recipe BOM/playtest pass.

### Wool must be renewable or sufficiently available

Fixed-wing and hot-air paths both use wool-based aerodynamic material.

The starting cluster should guarantee a wool/fiber path through:

- sheep;
- compatible ecological substitute/tag if intentionally supported;
- civilization trade only if that civilization is guaranteed.

Prefer a renewable ecological path.

### Adhesive is a first-class bootstrap capability

Do not leave slime/honey to chance.

The starting recipe needs an explicit adhesive solution.

## Suggested starting-cluster teaching layout

Not a fixed map, but semantic opportunities:

~~~text
spawn island:
  wood / food / stone / initial iron

nearby resource island:
  deeper iron + andesite

ecological opportunity:
  wool/fiber + adhesive path

workshop opportunity:
  enough safe surface/water for basic Create processing

first-flight build area:
  open approach / recovery space

post-flight horizon target:
  clearly visible regional destination
~~~

Any morphology/cluster arrangement satisfying these is acceptable.

## Civilization/quest interaction

The bootstrap region may optionally include a guaranteed:

- abandoned workshop;
- route station;
- frontier homestead;

to demonstrate:

- Create processing;
- adhesive/assembly;
- simple aerodynamic parts;
- navigation.

However the hard material closure should not require looting one unique advanced block.

FTB Quests/advancements can guide the player through the source-backed chain:

~~~text
Andesite Alloy
-> Casing / basic kinetics
-> Press
-> Iron Sheets
-> Saw
-> Engine Assembly
-> Portable Engine
-> Physics Assembler
-> aerodynamic/control components
-> first craft
~~~

## Required in-game proof

This source audit is not sufficient to call Candidate A accepted.

A future playable bootstrap proof must build a minimal craft using only the proposed pre-brass closure and demonstrate:

1. assembly;
2. takeoff;
3. controlled forward motion;
4. controlled turning;
5. controlled vertical/pitch behavior appropriate to craft type;
6. safe landing;
7. disassembly/reassembly;
8. loss/recovery path;
9. modest cargo;
10. no hidden brass/zinc/petroleum dependency.

If ordinary Andesite Propellers cannot be used as assumed on the first craft, revisit the closure before changing worldgen guarantees.

## Acceptance principle

> Freeze the starting-region resource guarantee from a tested aircraft bill of materials, not from intuition about which Create resources seem early game.
