# Vanilla Mobility Bypass Governance v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Vanilla mobility audit begun; Elytra/firework policy has a leading implementation path but is not yet locked.

## Core rule

> Skyforge may allow many ways to move a player. It must not accidentally allow a cheap vanilla mechanic to obsolete flight logistics.

The relevant distinction is not simply:

~~~text
can the player travel far?
~~~

It is:

~~~text
can the player move themselves, freight, or an economic route
farther / faster / more reliably than the intended aviation layer
for trivial cost or infrastructure?
~~~

Personal movement can be permissive.

Regional logistics must remain structurally meaningful.

This document governs vanilla and near-vanilla mobility mechanics that can bypass the intended progression:

- Elytra;
- firework boosting;
- Riptide;
- Ender Pearls;
- Chorus Fruit;
- portal-scale transport;
- any future teleport/recall dependency.

It complements the early glider contract rather than replacing it.

## Mobility capability classes

Audit every traversal mechanic against these capabilities:

~~~text
PERSONAL_REACH
PERSONAL_SPEED
VERTICAL_RECOVERY
ROUTE_REVERSIBILITY
WEATHER_INDEPENDENCE
CARGO_INVENTORY
CARGO_CONTAINER
CARGO_CONTRAPTION
ENTITY_TRANSPORT
ROUTE_THROUGHPUT
INFRASTRUCTURE_COST
ROUTE_PREPARATION
SCHEDULE_RELIABILITY
~~~

A mechanic is dangerous when it acquires several aviation/logistics capabilities simultaneously at negligible cost.

## Elytra policy

### Gliding itself is not the primary problem

Unpowered Elytra flight is compatible with the world in principle.

It is:

- personal;
- freight-poor;
- dependent on altitude or another launch mechanism;
- vulnerable to route geometry;
- unable to move Create contraption/container freight by itself.

It may therefore remain as a later high-performance personal glider or special mobility item.

Its relationship to the cheap starter glider should be evaluated later:

~~~text
starter glider
    -> inexpensive / early / simple

Elytra
    -> later / rarer / higher-performance personal soaring
~~~

Skyforge does not need to delete Elytra merely because a dedicated glider exists.

### Firework boost is the major bypass

Vanilla-style rocket boosting gives Elytra a self-contained propulsion loop:

~~~text
ELYTRA
    + consumable rockets
    -> powered altitude recovery
    -> repeatable long-range personal flight
    -> weak terrain dependence
    -> high route flexibility
~~~

That collapses much of the intended distinction between soaring and powered aircraft.

Even though it still does not provide contraption freight, it can make routine personal aviation so easy that:

- route planning matters less;
- atmospheric reading matters less;
- thermal infrastructure matters less;
- first aircraft construction feels less transformative;
- later aircraft may be perceived primarily as cargo vehicles rather than the backbone of sky travel.

Therefore unrestricted Elytra rocket boosting should not be retained.

## Leading firework solution

### Mechanical rule

Preferred first implementation:

> Firework rockets retain their normal non-Elytra purposes, but do not provide safe sustained propulsion while the player is gliding.

Current low-bespoke candidates exist for Minecraft 1.21.1 NeoForge that disable only firework boosting while leaving fireworks otherwise functional.

This is preferable to globally removing rockets because fireworks may remain useful for:

- signaling;
- celebrations;
- visual navigation;
- redstone/launcher uses;
- cross-mod recipes;
- future flare-like systems.

### Hazardous-feedback option

A stronger Skyforge presentation may make attempted rocket propulsion actively unsafe.

Concept:

~~~text
player ignites rocket while fall-flying
    -> little or no useful thrust
    -> blast / recoil / wing instability
    -> damage and/or dangerous loss of control
~~~

Possible fiction:

> Strapping an explosive pyrotechnic rocket next to a fabric wing is not a propulsion system.

This is attractive because the rule becomes diegetic rather than arbitrary.

However, the hazard is **optional feedback**, not a required bespoke feature.

Implementation order:

~~~text
disable propulsion using an existing server-side dependency
    -> playtest
    -> add warning / feedback if needed
    -> add damage or destabilization only if it improves clarity and humor
~~~

Do not write a custom Elytra physics subsystem merely to punish one vanilla interaction.

### Hazard guardrails

If explosive feedback is implemented:

1. It should be legible after one mistake.
2. It should not routinely destroy valuable nearby builds.
3. It should not create a new griefing/exploit primitive.
4. The ordinary use of fireworks must remain intact.
5. The punishment should communicate "bad propulsion method," not feel like arbitrary inventory deletion.
6. It should not make accidental rocket use an unavoidable instant death.

A sharp blast, durability damage, temporary instability, or localized player damage is preferable to an enormous destructive explosion.

## Relationship to thermal soaring

Restricting rocket propulsion makes the preserved updraft design substantially stronger.

The personal-flight stack becomes:

~~~text
STARTER GLIDER
    launch height
    thermals
    ridge lift
    player-built heat routes

LATE PERSONAL SOARER / ELYTRA
    better handling and glide performance
    same atmospheric world
    no magic rocket engine

POWERED AIRCRAFT
    engines
    powered climb
    freight
    route independence
    industrial logistics
~~~

This keeps the atmosphere relevant at every personal soaring tier.

A player who wants to cross the world without an aircraft may still do so by:

- reading wind;
- using thermals;
- exploiting ridge lift;
- preparing campfire/heat-source routes;
- accepting route and weather constraints.

That is desirable emergent play.

What should not happen is:

~~~text
craft a stack of cheap rockets
    -> ignore geography
    -> ignore atmosphere
    -> point at destination
    -> hold boost
~~~

## Riptide audit

Riptide is situational rather than automatically disallowed.

Potential behavior:

~~~text
water / rain
    -> burst launch
    -> glider / Elytra transition
~~~

Risks:

- rain may become free regional launch power;
- repeated water stations could become another prepared personal route;
- Riptide plus a high-performance glider may create effective powered flight during storms.

Current direction:

- preserve for prototype testing;
- treat as personal burst mobility;
- allow prepared water-launch sites if they remain low-throughput;
- compare its effective vertical recovery and repeatability against glider thermals and aircraft;
- restrain only if it gives trivial all-weather or nearly continuous propulsion.

A weather-dependent launch trick is much less threatening than cheap universal rocket thrust.

## Ender Pearls

Ender Pearls are compatible with local emergency traversal.

Useful roles:

- short-gap correction;
- recovering from a bad landing;
- boarding a nearby platform;
- local combat movement;
- emergency return to reachable terrain.

They are naturally bounded by:

- finite throw range;
- consumable cost;
- damage/risk;
- inventory-scale use.

They should not be made artificially useless merely to protect aircraft.

### Stasis / remote teleport risk

Any mechanism that turns pearls into reliable long-range recall or remote teleport deserves separate evaluation.

The danger threshold is crossed when the mechanic becomes:

~~~text
remote destination
    + trivial trigger
    -> instant routine travel
~~~

especially if it can move players or freight across provinces while bypassing the authored sky.

Do not forbid clever player infrastructure reflexively, but do not add convenience mods that make this capability cheap and universal.

## Chorus Fruit

Chorus Fruit is low concern.

Its random, short-range teleport behavior is:

- personal;
- unreliable;
- low-throughput;
- unsuitable for planned regional logistics.

Retain unless a mod changes it into deterministic long-range transportation.

## Nether / portal transit — major audit item

Vanilla portal geography is potentially much more dangerous to Skyforge logistics than gliders.

A conventional Nether route can compress Overworld horizontal distance dramatically.

If normal portal coordinate scaling and easy portal chaining remain available, the player may be able to build:

~~~text
Overworld cluster A
    -> portal
    -> short Nether corridor
    -> portal
    -> distant Overworld cluster B
~~~

This can erase:

- inter-cluster aviation routes;
- province-scale distance;
- weather exposure;
- navigation;
- fuel/logistics planning;
- destination visibility.

Unlike Elytra, portals can also participate in entity and item transport.

Therefore Nether transit must receive an explicit design decision before progression lock.

Candidate policies, from least to most invasive:

1. **Accept but heavily gate** portals to a late infrastructure tier.
2. **Retain Nether resource access but remove/alter distance compression.**
3. **Restrict arbitrary portal creation and use authored gateways.**
4. **Treat Nether as a destination/resource dimension rather than a transport shortcut.**
5. **Disable routine Overworld-Nether-Overworld transit if no elegant integration exists.**

No candidate is locked yet.

The reuse-first principle still applies: prefer datapack/config/mod behavior over bespoke portal code.

## Teleport / Waystone dependencies

Default policy:

> Do not add general-purpose convenience teleportation unless it is deliberately framed as a very late, expensive logistics technology with strong constraints.

Waystone-like systems are especially risky because they can make authored distance decorative.

If a future dependency is considered, audit:

- player-only versus freight;
- power/fuel cost;
- endpoint construction cost;
- throughput;
- cooldown;
- progression tier;
- whether the route still requires physical discovery;
- whether aviation remains economically useful after activation.

## Infrastructure versus bypass

Skyforge should distinguish **earned infrastructure** from **bypass mechanics**.

Good emergent infrastructure can include:

- bridges;
- elevators;
- rope routes;
- glider launch towers;
- thermal/campfire chains;
- rail on suitably built structures;
- landing fields;
- beacons;
- fuel depots;
- air routes.

These are visible investments in the world.

A mechanic becomes suspicious when it provides similar or better capability with very little:

- construction;
- route exposure;
- material cost;
- operating cost;
- planning;
- maintenance.

The world should reward players for building networks rather than merely consuming one cheap universal movement item.

## Acceptance tests

### VMB-1 — rocket boost suppression

Elytra + ordinary firework rocket does not provide vanilla-style sustained powered flight.

### VMB-2 — firework preservation

Fireworks remain usable for ordinary non-propulsion purposes.

### VMB-3 — feedback clarity

If rocket boosting is blocked or hazardous, a player can understand the rule without needing external documentation.

### VMB-4 — no accidental catastrophic punishment

A single accidental attempted boost does not routinely cause unavoidable death or major base destruction.

### VMB-5 — soaring preserved

Unpowered glider/Elytra flight, thermals, ridge lift, and prepared personal soaring routes remain useful.

### VMB-6 — aircraft transition preserved

First powered aircraft still gives an obvious increase in:

- powered vertical recovery;
- route flexibility;
- freight;
- schedule reliability;
- industrial throughput.

### VMB-7 — Riptide bounded

Riptide does not become a trivial always-available replacement for powered flight.

### VMB-8 — portal geography audited

Before pack progression is locked, Nether/portal travel has an explicit policy demonstrating that ordinary province-scale aviation is not made irrelevant.

### VMB-9 — no convenience-teleport leak

No included mod silently adds cheap general-purpose long-range teleportation that invalidates authored geography.

## Leading implementation candidates

### No More Elytra Boosting

Current external audit:

- Minecraft 1.21.1;
- NeoForge;
- server-side;
- purpose-built to disable firework boosting without altering other firework behavior;
- license is restrictive / all-rights-reserved, so treat as pack dependency candidate rather than code source.

This is mechanically close to the desired first prototype.

### Disable Elytra Outside The End

Current external audit:

- supports Minecraft 1.21.1 NeoForge;
- server-side capable;
- LGPL-3.0-or-later;
- can disable firework boosting via configuration;
- can also gate Elytra behavior by dimension if future progression needs that.

This is the stronger reuse candidate if its dependency/config footprint is acceptable.

### Elytra Tuning

Current external audit:

- supports Minecraft 1.21.1 NeoForge;
- LGPL-3.0-or-later;
- can tune firework boost strength/duration and Elytra speed.

Retain as a tuning candidate if complete boost removal feels too binary.

Do not add several overlapping Elytra-control mods simultaneously.

## Manual evidence required

When the pack prototype exists, test:

- unpowered starter glider;
- thermal-assisted starter glider;
- unpowered Elytra;
- attempted firework boosting;
- Riptide launch into gliding;
- first powered Aeronautics aircraft;
- representative cargo route.

Record whether the player would rationally choose each tool for:

~~~text
local scouting
emergency recovery
long personal expedition
repeated commute
resource hauling
contraption freight
poor-weather travel
scheduled regional route
~~~

The desired answer should not be "aircraft wins every category."

The desired answer is:

~~~text
gliders / Elytra
    -> elegant personal movement

aircraft
    -> dependable sky logistics
~~~

## Acceptance principle

> Preserve clever ways to move through the world. Remove cheap universal propulsion that turns the world into empty space between coordinates.
