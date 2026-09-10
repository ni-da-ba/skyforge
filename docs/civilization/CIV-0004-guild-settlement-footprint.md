# CIV-0004: Guild settlement footprint

**Status:** Precommitted authorship strategy
**Stage:** Pre-civilization; implementation deferred
**Date:** 2026-09-10

## Decision

The Skyfarer's Guild should normally present itself to the player as **one concentrated institutional destination per settlement**.

Default hierarchy:

- **minor settlement:** one room, counter, or compact Guild office;
- **ordinary town:** one Guild Hall containing the settlement's Guild-facing services;
- **important town / regional center:** one enlarged hall, attached section, courtyard complex, or contiguous Guild campus;
- **major hub:** one cohesive Guild quarter or campus may contain multiple structures, but it should still read and function as a single destination rather than a set of Guild offices scattered throughout the settlement.

The Guild should not normally distribute banking, registry, contracts, insurance, stores, navigation, freight, and recovery services into unrelated buildings across town merely because those functions are logically distinct.

## Rationale

This rule serves both production and gameplay.

### Production

Concentrating services reduces the number of unique structures required and makes modular authored halls more valuable. A single shell can contain several service modules, and larger variants can grow through wings, courtyards, workshops, freight sheds, docks, or adjacent buildings without requiring a bespoke citywide institutional district.

### Gameplay

The Guild is a major player interface. Concentrating its services minimizes low-value traversal and makes the Hall a reliable destination where the player can resolve several related tasks in one visit.

The intended player intuition is:

> Find the Guild Hall; the Guild business of this settlement is there.

The Hall may therefore become a strong social and navigational anchor even when the surrounding settlement is large or complex.

## Functional composition

Guild functions should be implemented as **service modules within a shared destination**, not automatically as independent buildings. Candidate modules include:

- contract board / contract office;
- registry and certification counter;
- accounts, escrow, and banking counter;
- insurance and claims;
- Guild store / commodity counter;
- navigation, route, and survey desk;
- bonded freight desk;
- salvage / recovery desk;
- inspection and maintenance services;
- warehouse or bonded storage;
- workshop / repair bay;
- air dock / certified berth;
- signal or transponder mast.

Not every Hall exposes every module.

## Scale by capability rather than building count

A settlement's Guild importance should be expressed primarily through **how many capabilities the destination contains, how much freight/aircraft infrastructure surrounds it, and how large or elaborate the compound is**, not by spawning more Guild buildings throughout the town.

Conceptual progression:

**office → hall → expanded hall / courtyard complex → contiguous campus or Guild quarter**

This preserves a recognizable gameplay pattern while allowing major hubs to feel materially more important.

## Relationship to local civilization

Most of the settlement remains non-Guild space: local houses, workshops, markets, civic buildings, private industry, inns, farms, and commercial premises. The Guild Hall is the institutional interface between that local civilization and the wider inter-settlement network.

A local merchant, insurer, mechanic, or warehouse may participate in Guild systems without being Guild-owned. This distinction prevents the Guild from visually or economically swallowing the settlement.

## Siting and settlement association

A Guild Hall belongs to a settlement **institutionally and economically, not necessarily spatially**.

Its placement should be driven primarily by practical access requirements:

- safe and convenient aircraft approach;
- usable landing or mooring terrain;
- freight-handling space;
- route access;
- workshop and storage requirements;
- expansion room;
- local topography and hazards;
- convenient connection to the associated town without requiring physical integration into the town core.

Accordingly, a Hall may be:

- embedded in the settlement where an existing port, square, or edge site is convenient;
- located on the town edge beside a natural landing shelf or freight route;
- positioned a short distance outside the built-up area at a superior landing or logistics site;
- paired with its own purpose-built landing field, mooring apron, dock, or freight court;
- expanded into a compact Guild campus where route importance justifies it.

The associated town and the Guild site should remain recognizably connected through paths, roads, bridges, lifts, signs, traffic, or other settlement infrastructure, but they do not need to occupy the same generated structure footprint.

This decoupling is intentional. It improves world plausibility, allows Guild infrastructure to respond to terrain and aviation requirements, and reduces implementation pressure to splice every Guild Hall directly into a procedurally generated town center.

A useful implementation model is therefore:

**settlement semantic entity**
→ **associated Guild service node**
→ **independently resolved physical site within an allowed relationship envelope**

The civilization system should preserve the relationship even when the Hall and town are physically separated.

## Architectural consequence

Guild Mercantile Functionalism should support **composite buildings** particularly well. A Hall may combine:

- dignified public frontage toward town or its principal access route;
- service counters and administrative rooms in the central body;
- freight court or loading elevation behind it;
- attached workshop / repair shed;
- warehouse wing;
- signal mast;
- one or more air berths.

This makes the public-face / working-face architectural distinction a gameplay tool rather than merely an aesthetic rule.

A detached or edge-sited Guild Hall does not need to imitate the settlement's orientation. Its working side should face the logistics geometry that actually matters, while its public face should address the route by which local people arrive.

## Exceptions

Scattered Guild assets are allowed only when their physical function requires separation. Examples may include remote beacons, route markers, rescue stations, isolated mooring infrastructure, or a heavy industrial facility that cannot plausibly fit at the Hall.

Such assets should be treated as **infrastructure**, not additional player-service destinations. Routine administrative interaction should remain concentrated at the Hall whenever practical.

## Precommitted rules

> **One settlement, one Guild destination. Scale the destination by adding capabilities, attached modules, or a contiguous campus—not by making the player run between scattered Guild offices.**

> **A Guild Hall is associated with a settlement, not required to be embedded in its town center. Site it where landing, logistics, terrain, and access make sense.**

Exact Hall layouts, service-module counts, footprint thresholds, settlement classes, maximum Hall-to-town separation, and site-selection scoring remain civilization-stage implementation decisions.