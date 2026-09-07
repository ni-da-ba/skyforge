# Portable Storage and Freight Integrity v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. No vanilla inventory nerf is currently justified.

## Core rule

> Skyforge should make cargo aviation valuable through throughput and capability, not by making the player's pockets artificially miserable.

Minecraft already gives the player substantial carried capacity.

Late-game portable containers multiply that capacity further.

The correct response is not automatically to impose encumbrance.

Instead, freight design should distinguish **manual courier capacity** from **logistics capability**.

## Freight capability classes

~~~text
PERSONAL_LOAD
  ordinary player inventory
  tools / food / valuable items
  small resource haul

PORTABLE_CONTAINER_LOAD
  shulker boxes
  backpacks if selected
  Ender Chest-assisted couriering

BULK_ITEM_FREIGHT
  many containers / repeated resource movement
  quarry / farm / industrial output

FLUID_FREIGHT
  fuels
  petroleum
  water / process fluids where relevant

ENTITY_FREIGHT
  livestock
  villagers
  fauna
  passengers

CONTRAPTION_FREIGHT
  Create machinery
  movable workshops
  large assembled systems
  specialized payloads

NETWORK_THROUGHPUT
  repeated / automated / scheduled movement
~~~

Aircraft progression should increasingly own the lower half of this list rather than merely "more inventory slots."

## Ordinary inventory

Do not reduce the vanilla inventory simply to force use of cargo aircraft.

Reasons:

- ordinary Minecraft play assumes the existing inventory;
- construction already requires moving many block stacks;
- punitive inventory reduction would affect every activity, not only logistics;
- it would create bespoke balance work far outside Skyforge's main value.

A player hand-carrying useful material across a local route is healthy.

## Shulker Boxes

Vanilla Shulker Boxes are a strong manual-courier tool.

They substantially expand item payload while remaining:

- late in normal progression;
- manually carried;
- manually loaded/unloaded unless infrastructure is built;
- incapable of moving entities or assembled contraptions;
- still dependent on the player's own trip.

Current policy:

> Retain vanilla Shulker Boxes provisionally.

Do not nerf them preemptively.

Instead, test whether the late-game combination:

~~~text
high-performance personal glider / Elytra
    + many Shulker Boxes
~~~

causes players to abandon cargo aircraft for ordinary industrial movement.

If it does, first improve the value of aircraft logistics before introducing player encumbrance.

## Ender Chest

The Ender Chest is unusual because it provides secure personal remote storage.

It can also carry Shulker Boxes indirectly.

This is strong for:

- valuable tools;
- emergency supplies;
- high-value resources;
- exploration kits;
- recovery gear.

It is much less naturally suited to:

- continuous farm output;
- fluids;
- mobs;
- contraptions;
- automated route throughput.

Current policy:

> Treat the Ender Chest as a late personal logistics convenience, not a reason to redesign freight.

If a mod turns Ender Chest semantics into arbitrary automation or large shared remote storage, re-audit it.

## Backpack mods

Portable-storage mods require much more scrutiny than vanilla Shulker Boxes.

Risk patterns include:

- very large capacity at P0/P1;
- stack multipliers;
- nested backpacks;
- automated pickup/voiding/feeding;
- remote storage;
- tanks;
- fluid handling;
- inventory access without placement.

These can collapse several progression axes simultaneously.

### Sophisticated Backpacks

Keep as **conditional/optional**, not automatic core.

Admit only if final configuration demonstrates:

- no trivial recursive nesting;
- no enormous early slot/stack multiplier;
- progression tier appropriate to its capacity;
- no early remote-storage behavior;
- no fluid capacity large enough to erase tanker/fuel logistics;
- no combination that makes early cargo aviation obviously irrational.

A modest personal backpack may improve quality of life.

A wearable warehouse is a logistics subsystem and must be treated as one.

## No portable-container recursion

General rule:

> Portable containers should not recursively contain equivalent portable containers.

Vanilla Shulker Boxes already avoid direct Shulker-in-Shulker recursion.

Any selected backpack/storage dependency should preserve the same spirit.

This prevents exponential inventory compression and keeps storage legible.

## Aircraft cargo identity

Aircraft should win through **what they can move and how repeatedly they can move it**.

### Small powered aircraft

Useful for:

- player + ordinary inventory;
- several ordinary containers;
- repair/supply runs;
- modest resource hauling;
- passenger movement.

### Cargo aircraft

Useful for:

- many containers;
- bulk blocks;
- packaged industrial output;
- large fuel stores;
- multi-stop routes;
- repeatable regional hauling.

### Airships / heavy platforms

Useful for:

- very large payloads;
- mobile workshops;
- contraption-scale systems;
- entities;
- construction material in bulk;
- slow but persistent logistics.

### Automated mature routes

Useful for:

- continuous transfer;
- regular schedules;
- industrial throughput;
- cross-province specialization;
- low-attention operation.

A glider carrying Shulker Boxes can compete with a small aircraft on **one-off personal item shipment**.

It should not compete with a mature aircraft network on **system throughput**.

## Fluid logistics

Fluids are a particularly useful way to preserve freight identity.

Strategic petroleum/fuel geography may create payloads that are awkward to reduce to ordinary inventory stacks.

Where existing Create/Diesel Generators mechanics already provide:

- tanks;
- pipes;
- fluid containers;
- movable contraptions;

prefer those mechanics over bespoke freight units.

A tanker aircraft or fuel airship should solve a genuinely different problem from a player carrying a few buckets.

Do not make every fluid trivially item-compressible if doing so erases the logistics game.

## Contraption and machinery transport

This is likely the strongest unique aviation niche.

Create/Aeronautics can make aircraft valuable for moving:

- machinery;
- mobile drilling/mining equipment;
- assembled workshop sections;
- cranes;
- construction platforms;
- specialized payload contraptions.

These cannot be replaced by stuffing item stacks into a backpack without first dismantling the system.

That distinction should be preserved.

## Construction logistics

Skyforge is still Minecraft.

Players should be able to build absurd bridges or carry stacks of blocks manually if they want.

The logistics layer should make large projects **easier and more interesting**, not declare alternate construction styles invalid.

Example:

~~~text
manual builder
    10 glider trips with Shulker Boxes

cargo pilot
    1–2 aircraft loads

industrial operator
    scheduled freight route
~~~

All three can be valid.

The progression value lies in scale and convenience.

## Personal soaring and cargo

Do not add glider encumbrance initially.

Possible future mass model:

~~~text
carried payload
    -> lower climb rate
    -> poorer glide performance
    -> greater stall / landing burden
~~~

This could be elegant if the selected glider/atmosphere APIs make it cheap.

But it is **not justified yet**.

Only add such a rule if manual couriering actually suppresses aircraft use in playtesting.

## Loot and resource geography implication

Regional specialization should create both:

- high-value low-volume goods suitable for personal couriering;
- high-volume bulk goods that reward freight infrastructure.

Examples:

~~~text
HIGH VALUE / LOW VOLUME
  rare components
  maps
  specialist tools
  small electronics

BULK
  ores
  fuel
  construction material
  food surplus
  industrial feedstocks
  machinery
~~~

This keeps glider/Elytra expeditions useful without making cargo craft pointless.

## Acceptance tests

### FRT-1 — no artificial pocket tax

The baseline pack remains comfortable to play using ordinary Minecraft inventory assumptions.

### FRT-2 — early backpack restraint

No P0/P1 wearable storage option provides warehouse-scale capacity or stack multiplication that makes starter cargo aviation pointless.

### FRT-3 — no recursive compression

Selected portable storage cannot nest recursively into effectively unbounded carried storage.

### FRT-4 — manual courier remains viable

A player can intentionally hand-carry valuable goods between locations without being forced into a vehicle for every trip.

### FRT-5 — cargo aircraft advantage

For representative bulk item routes, an aircraft materially reduces trip count or handling burden.

### FRT-6 — fluid advantage

Representative strategic fluid/fuel transport is materially easier with freight infrastructure than with ordinary player inventory.

### FRT-7 — contraption advantage

At least one meaningful mature payload class cannot be reproduced by personal portable storage without dismantling the payload.

### FRT-8 — throughput advantage

For sustained industrial output, a route/network outperforms repeated player courier trips in attention and practical throughput.

### FRT-9 — late portable storage coexistence

Shulker Boxes and any accepted backpack do not make mature aviation economically irrational across most cargo categories.

## Manual evidence required

During gameplay validation compare:

~~~text
A. player + ordinary inventory
B. glider + ordinary inventory
C. glider/Elytra + Shulker Boxes
D. first powered aircraft
E. cargo aircraft / airship
F. automated mature route
~~~

for:

- common ore hauling;
- high-value component transport;
- construction blocks;
- fuel/fluid;
- moving machinery;
- emergency recovery;
- repeated industrial output.

Record:

- trip count;
- active player time;
- loading/unloading friction;
- infrastructure investment;
- failure risk;
- whether the player feels compelled to choose one method for every task.

## Acceptance principle

> Personal storage should make exploration pleasant. Freight infrastructure should make civilization possible.
