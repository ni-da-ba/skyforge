# Wave C19 — stock mining-turtle extraction envelope

**Status:** MERGED / ACCEPTED  
**PR:** #292  
**Merge:** `97c9e1accf0a243ddc892fbf1fc424825b0c244e`  
**Accepted runtime head:** `4c6bfe67f07e5c80650c23d8a697615588a02a9a`  
**Issue:** #291  
**Parent computing contract:** #227  
**Parent vertical slice:** #224

## Purpose

C19 measures whether retained CC:Tweaked mining turtles bypass Skyforge resource geography strongly
enough to require configuration or progression constraints.

C18 already closed the base freight/ticking question: stock turtles can carry substantial payloads
through unsupported air when external infrastructure keeps the route loaded/ticking, but do not
supply arbitrary long-range ticking themselves.

C19 therefore asks the narrower extraction question before any mining-specific nerf is considered.

The retained upstream turtle contract supplies the stock mechanism: a normal turtle can equip a
diamond pickaxe with `turtle.equipLeft()`, use `turtle.dig()` to break blocks, store recovered
items in its 16-slot inventory, and consume fuel only on physical movement.

## Black-box specimens

### Loaded deterministic deposit

The fixture constructs a 96-block vanilla iron-ore corridor at Y=250 and explicitly keeps the
corridor loaded for the bounded test.

A real normal turtle receives:

```text
slot 1: 3 coal
slot 2: 1 vanilla diamond pickaxe
```

CraftOS alone:

1. refuels;
2. equips the stock diamond-pickaxe upgrade;
3. performs 96 `turtle.dig()` calls;
4. advances through all 96 mined cells;
5. returns 96 blocks through the cleared route;
6. turns toward a vanilla home barrel;
7. drops the recovered raw iron.

Acceptance requires:

- exactly 96 successful digs;
- exactly 96 outbound and 96 return moves;
- movement fuel delta exactly equal to 192;
- exactly 96 raw iron delivered into the vanilla barrel;
- no accepted iron-ore cell remaining;
- physical return of the turtle to its start;
- measured server-tick elapsed time.

This is deliberately a deterministic extraction specimen, not a claim about natural ore density.

### Otherwise-unforced mining route

A second real mining turtle begins at the eastern edge of one explicitly forced chunk. A 48-block
iron-ore corridor is prepared ahead of it, but no route chunk beyond the start is force-loaded.

The Lua program records durable dig/move/fuel progress after every successful dig-and-move cycle.

Acceptance requires it to stop or stall before all 48 steps unless independent loading
infrastructure exists. A bounded explicit stop is accepted only when movement surfaces
CC:Tweaked's loaded-world guard; unrelated dig/tool/block errors fail closed. A no-result path may
become `STALLED / NO_TICK_PROGRESS` only after the bounded progress-stall window.

## Interpretation boundary

Passing the loaded specimen proves stock turtles can automate extraction and return physical
material when the deposit and route are available and ticking.

Passing the boundary specimen proves the mining tool does not change the C18 infrastructure fact:
the turtle still does not create arbitrary long-range ticking for itself.

C19 should therefore judge the combined envelope:

```text
physical deployment
+ real block extraction
+ fuel
+ finite inventory / physical delivery
+ command cadence
+ loaded/ticking infrastructure
```

rather than treating "can automate digging" as sufficient evidence for a nerf.

Still separate:

- farming throughput;
- quarry-scale or branch-mining patterns;
- dedicated chunk-loader progression;
- Ender-storage combinations;
- ore-generation abundance;
- mature fleet automation;
- final aircraft-versus-turtle economics.
## Accepted result

Final current-main runtime evidence:

```text
digs=96
outboundMoves=96
returnMoves=96
fuelStart=240
fuelEnd=48
delivered=96
elapsedTicks=2377
boundaryOutcome=STALLED
boundaryPhase=NO_TICK_PROGRESS
boundaryDigs=17
boundaryMoves=17
boundaryFuelStart=80
boundaryFuelEnd=63
boundaryError=NO_TICK_PROGRESS
```

Decision: **KEEP** the stock mining-turtle base capability. The specimen proves useful local physical
extraction automation, but it does not show a geography-erasing capability: the turtle still requires
physical access to the deposit, fuel, loaded/ticking execution, and physical material return. Farming,
quarry/branch-mining scale, chunk-loader progression, Ender-storage combinations, ore abundance,
fleet automation, and final aircraft economics remain separate audits.
