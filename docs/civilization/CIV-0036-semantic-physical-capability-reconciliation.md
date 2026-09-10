# CIV-0036 — Semantic / Physical Capability Reconciliation

Status: PRECOMMIT / LOCKED FOR CIVILIZATION DESIGN

## Core decision

> Realized civilization capabilities are reconciled through explicit functional anchors and registered contributors rather than continuous block-volume inspection.

Skyforge distinguishes between a settlement's intended semantic role and the current operational state of realized infrastructure.

A player may modify ordinary Minecraft structures freely. Civilization semantics only need to react when that modification affects a gameplay-relevant tracked capability.

## Intended role versus operational capability

A settlement or island may semantically remain:

```text
BEACON_NAVIGATION
AIRFIELD
STORAGE
INDUSTRIAL
DOCK_PORT
```

even when one or more currently realized services are degraded or offline.

Example:

```text
INTENDED ROLE
BEACON_NAVIGATION

CURRENT CAPABILITY
NAVIGATION = OFFLINE
```

Destroying an operational beacon does not erase the site's historical or planned role. It changes the present capability state and permits repair, replacement, maintenance contracts, degraded route state, or other downstream consequences.

## Capability bindings

When a semantic capability is concretely realized in Minecraft, Skyforge may establish a thin binding between the semantic service and the small set of realized components that actually enable it.

Conceptually:

```text
CapabilityBinding {
    owner / cluster / facility
    capability
    required anchors
    optional contributors
    current status
}
```

This is a semantic contract, not a commitment to this exact code shape.

Examples:

```text
NAVIGATION
    -> navigation controller / beacon anchor

FUEL_SERVICE
    -> registered fuel-transfer/service anchor

CARGO_TRANSFER
    -> registered cargo interface(s)

GUILD_INFORMATION
    -> Guild terminal / communications endpoint
```

Most decorative blocks, walls, roofs, pipes, furniture, roads, and ordinary structure fabric do not need bindings.

## Block significance classes

For civilization reconciliation purposes, realized content can be thought of in three broad classes:

```text
REPRESENTATIONAL / ORDINARY FABRIC
    no direct semantic accounting

SUPPORTING INFRASTRUCTURE
    may contribute to capacity or quality

CAPABILITY ANCHOR
    enables, disables, or validates a gameplay-relevant service
```

This classification is functional rather than visual.

Do not make every block in an airfield or warehouse semantically critical.

## Event-driven invalidation

The common path should be event-driven.

Example:

```text
player removes beacon anchor
        ↓
Minecraft block/change event
        ↓
relevant CapabilityBinding invalidated
        ↓
NAVIGATION changes to DEGRADED or OFFLINE
        ↓
route / Guild / information systems may react
```

Skyforge should not continuously rescan an entire settlement volume to determine whether services still exist.

Validation may also occur when:

- a replacement anchor is installed;
- a service operation requires confirmation;
- a saved binding is migrated across a schema/version change;
- corruption/inconsistency recovery requires a targeted audit.

## Capability state

Capabilities need not be universally binary.

Candidate conceptual states include:

```text
OPERATIONAL
DEGRADED
OFFLINE
UNKNOWN
```

Some capabilities may aggregate multiple contributors.

For example:

```text
3 cargo interfaces -> high transfer capacity
2 cargo interfaces -> reduced capacity
1 cargo interface  -> low but operational capacity
0 cargo interfaces -> offline
```

The exact capacity model is implementation/content dependent.

## Shared validation grammar

Generated civilization and player-built registered facilities should use the same functional vocabulary wherever practical.

The same kind of rule that can establish:

```text
NPC facility supports CARGO_TRANSFER
```

should also be usable to establish:

```text
player facility supports CARGO_TRANSFER
```

when both satisfy the same gameplay-relevant functional requirements.

This preserves Skyforge's broader infrastructure principle:

> Civilization should demonstrate a language of engineering and logistics that the player can reproduce rather than using a separate NPC-only ruleset.

Certification validates capability, not blueprint conformity.

## Material consequence

Once a semantic service has been physically realized and tracked, the physical state must matter.

Examples:

```text
remove beacon controller
-> navigation capability can fail

remove cargo interface
-> cargo transfer capacity can decline

remove registered Guild terminal
-> local Guild information/service access can fail

remove fuel transfer infrastructure
-> fuel service can fail
```

Do not preserve a hidden operational service merely because the original latent cluster plan said that service should exist.

The semantic plan remains the intended/historical role; operational capability reflects current reality.

## Ordinary destruction does not require settlement accounting

Skyforge does not need to calculate a generic settlement-damage percentage when a player removes houses, walls, paths, decoration, or ordinary blocks.

Those changes remain Minecraft world state unless they affect a recognized functional capability or an explicitly tracked gameplay consequence.

This avoids continuous settlement scanning and allows unrestricted Minecraft-style modification to remain the default.

## Unload and dormancy

When a realized area unloads, persist the latest relevant capability state and binding state as needed.

No continuous validation is required while unloaded.

On later load, previously validated anchors should remain trusted unless an event, migration, targeted service check, or recovery audit requires revalidation.

## Relationship to latent civilization

The latent plan may say that a settlement supports or intends a service before Minecraft realization.

Lifecycle:

```text
latent semantic capability
        ↓ realization
bound operational capability
        ↓ player/world modification
operational / degraded / offline state
```

Once realized and materially altered, reconciliation must respect that history rather than regenerating the original latent assumption unchanged.

## Performance constraints

Do not require:

- continuous whole-settlement scans;
- per-block semantic ownership for ordinary world fabric;
- per-tick capability recomputation;
- exact tracking of decorative/support geometry that cannot affect gameplay;
- separate validation systems for equivalent NPC and player infrastructure.

Prefer:

```text
small explicit anchor set
+ event-driven invalidation
+ bounded contributor aggregation
+ persisted consequence
```

## Design invariants

> Intended role and current operational capability are distinct.

> Civilization capability is tracked through meaningful functional interfaces, not arbitrary percentages of surviving buildings.

> Ordinary Minecraft modification remains ordinary unless it affects a tracked service.

> Destroyed realized infrastructure must be capable of producing real semantic consequences.

> Generated and player-built infrastructure should share capability validation wherever practical.
