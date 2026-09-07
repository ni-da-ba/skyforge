# Wave C18 — stock turtle void-freight envelope

**Status:** MERGED / ACCEPTED  
**PR:** #277  
**Merge:** `1c7e24a0e37c4ade8aa5a8943f65017d2300248b`  
**Accepted runtime head:** `9ce39a797ea337fe2f152bc5168ba4d6715c578a`  
**Issue:** #275  
**Parent computing contract:** #227  
**Parent vertical slice:** #224

## Purpose

C18 measures whether retained CC:Tweaked turtles substitute for the aircraft/freight layer strongly
enough to require configuration or progression constraints. It does not assume that void traversal
alone is a balance failure.

Pinned CC:Tweaked mechanics at the retained source revision establish the relevant baseline:

- 16 turtle inventory slots;
- one fuel unit consumed per successful movement block when fuel is enabled;
- movement permits empty/replaceable air without a supporting block;
- turtle movement animation/command cadence is eight ticks per move;
- turtle movement contains an explicit loaded-world guard.

## Black-box specimen

C18 reuses the exact accepted C9 computing runtime and real normal turtles.

### Loaded unsupported-air freight

The fixture explicitly loads a 64-block corridor at Y=250, clears both the travel cells and their
support cells, and places a vanilla barrel one block beyond the remote endpoint.

The turtle receives:

```text
slot 1:      2 coal
slots 2-16:  15 x 64 cobblestone = 960 cargo items
```

CraftOS alone performs refuel, 64 forward moves, fifteen inventory drops into the barrel, and 64
backward moves. Acceptance requires physical return to the start, exactly 960 cobblestone in the
vanilla barrel, and a fuel delta equal to the 128 successful movement blocks. Elapsed server ticks
are measured.

### Externally loaded/ticking route envelope

A second real turtle starts at the eastern edge of one explicitly forced chunk and attempts 48 stock
`turtle.forward()` moves through otherwise unforced sky. The Lua program writes durable progress
after each successful move.

Acceptance requires the turtle to stop or stall short of 48 moves without any Skyforge/CC-specific
chunk-loading support. Fuel delta must equal the number of successful moves. If CraftOS returns a
bounded `STOPPED` result, C18 accepts it only when the error is CC:Tweaked's explicit
`Cannot leave loaded world` condition; unrelated obstruction or command errors fail closed. If the
computer instead leaves Minecraft's ticking envelope and stops receiving turtle command execution,
the fixture detects the stable progress file after 240 server ticks and records that as a measured
`STALLED / NO_TICK_PROGRESS` outcome.

This is deliberately a gameplay-level route test, not an assertion about one internal chunk API.

## Empirical correction

The first two dedicated-server C18 runs tested a stricter source-derived hypothesis: a ticking turtle
could be placed with its current chunk loaded while the immediately adjacent destination chunk was
actually unloaded, causing stock `turtle.forward()` to return `Cannot leave loaded world`.

Runtime showed that setup is not ordinarily constructible through Minecraft tickets:

1. without a ticket, the current turtle chunk itself unloaded before the command ran;
2. forcing the current chunk also caused the adjacent chunk to remain loaded through normal chunk
   ticket propagation.

The underlying source guard remains real, but the useful player-facing question is whether the turtle
can keep itself operating beyond externally loaded/ticking terrain. C18 therefore measures that
directly. Issue #275 records this acceptance refinement explicitly.

## Interpretation boundary

Passing the freight half proves turtles can physically cross unsupported sky when infrastructure keeps
their route loaded. Passing the bounded-route half proves the turtle does not itself provide arbitrary
long-range chunk/ticking infrastructure.

The gameplay decision must therefore consider cargo, fuel, cadence, and route-loading requirements
together. C18 may justify KEEP or later CONFIGURE/progression work, but it does not pre-emptively nerf
turtles.

Still separate: mining/farming throughput, dedicated chunk-loader progression, Ender storage
combinations, autopilot, and final aircraft-versus-turtle economics.
## Accepted result

Final current-main runtime evidence:

```text
outboundMoves=64
returnMoves=64
fuelStart=160
fuelEnd=32
delivered=960
elapsedTicks=1164
boundaryOutcome=STALLED
boundaryMoves=17
boundaryFuelStart=80
boundaryFuelEnd=63
boundaryError=NO_TICK_PROGRESS
```

Decision: **KEEP** the stock turtle movement/freight capability. C18 does not justify a generic
Skyforge turtle nerf or replacement. Route loading is still infrastructure, and the separate
mining/farming, dedicated chunk-loader, Ender-storage, autopilot, and final aircraft-economics
questions remain open.
