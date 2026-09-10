# CIV-0032 — Economic Initialization and Discovery Warm Start

Status: PRECOMMIT / LOCKED FOR CIVILIZATION DESIGN

## Decision

Skyforge does not simulate the complete economic history of every unobserved settlement from world creation. Settlements may remain in a cheap latent procedural state until detailed economic state becomes necessary.

When a settlement first becomes economically active, Skyforge performs a deterministic, context-sensitive warm start derived from authoritative civilization and world semantics, including population, prosperity, productive capabilities, resource potential, settlement role, regional connectivity, nearby established markets, and current regional conditions.

Once initialized, settlement economic state is persisted and thereafter evolves through the normal coarse simulation. It is not regenerated from the world seed on later visits.

## Distinct state concepts

These states must not be conflated:

- civilization existence;
- Guild/network knowledge of a settlement;
- player knowledge/discovery;
- detailed economic activation;
- Minecraft physical realization.

A Guild-connected settlement may exist and participate semantically in regional civilization before the player discovers it.

## Warm-start derivation

Initialization should proceed approximately as:

```text
world / civilization semantics
        +
settlement population / prosperity / role
        +
resource potential and productive capabilities
        +
regional logistics and current conditions
        +
nearby established markets
        ↓
market capabilities / catalogue
        ↓
desired stock and consumption pressures
        ↓
initial stock
        ↓
local prices
        ↓
candidate logistics relationships
```

Initialization is deterministic for the same authoritative inputs and initialization version.

## Bounded disequilibrium

Newly activated settlements should not initialize at perfect equilibrium. Starting stocks and local market conditions may vary within plausible bounds so that shortages, surpluses, and trade opportunities can exist immediately.

Such variation must remain downstream of settlement semantics and regional conditions rather than independent random inventory generation.

## Historical maturity without fabricated bookkeeping

Initialization may encode aggregate historical maturity, such as:

- established versus marginal route connectivity;
- ordinary versus thin reserves;
- mature versus shallow commercial catalogues;
- typical local import dependence;
- established productive specialization.

It must not fabricate detailed historical incidents, insurance claims, named freight losses, transaction records, or other recoverable event history merely to justify aggregate state.

Persistent detailed records begin where records become gameplay-relevant, except where authored lore intentionally establishes older records.

## Current regional state matters

Warm starts consume current authoritative regional state.

A newly activated settlement in a region suffering sustained route disruption, elevated piracy pressure, or degraded infrastructure should not initialize as though those conditions do not exist.

This avoids unexplored settlements existing in disconnected temporal bubbles.

## Route initialization

New settlement logistics should use the existing sparse route-formation logic rather than universal direct connectivity.

Candidate links may consider:

- economic complementarity;
- distance and travel cost;
- hazard and reliability;
- network value and hub access;
- existing infrastructure;
- institutional/Guild relationship.

An old established port may initialize with mature recognized connections even when newly discovered by the player, because player discovery is not settlement creation.

## Hierarchical activation

Preferred architecture:

```text
WORLD / REGION SEMANTICS
very coarse authoritative descriptors
        ↓
SETTLEMENT LATENT STATE
cheap deterministic civilization descriptors
        ↓ when detailed economics are required
SETTLEMENT ECONOMIC STATE
stock, production, demand, markets, logistics
        ↓ when observation/interactivity requires it
MINECRAFT REALIZATION
stores, NPCs, aircraft, physical cargo
```

The exact boundary between latent and initialized civilization state is deferred to the existing civilization architecture and implementation constraints.

## Catalogue, stock, and price ordering

Store initialization should derive in this order:

```text
settlement semantics
→ market capabilities
→ catalogue
→ desired stock
→ initial stock
→ prices
```

Do not infer settlement commercial identity from independently randomized inventories.

## No retroactive full simulation

Initialization must not require generating the complete unseen history of:

- production cycles;
- freight movements;
- Scrip transactions;
- insurance claims;
- individual carriers;
- prior NPC incidents.

Aggregate state is sufficient until specific history becomes gameplay-relevant.

## Persistence invariant

Settlement activation is conceptually one-way:

```text
LATENT → INITIALIZED → PERSISTENT
```

Returning after an absence advances/reconciles the persisted state; it does not reroll the settlement.

## Design invariants

> Unobserved civilization has a plausible past, not a fully simulated past.

> The player discovers settlements; discovery does not create their civilization.

> Detailed persistent history begins where detail becomes gameplay-relevant.

> Lazy initialization must consume current regional state, not exist in an isolated procedural bubble.
