# CIV-0030 — Resource Potential and Non-Depleting Baseline

Status: Precommit

## Decision

Skyforge will not ordinarily simulate long-term depletion of NPC resource deposits.

Geological and ecological fields establish persistent regional productive potential. Civilization systems derive coarse extraction or renewable production capacity from that potential plus settlement capability, infrastructure, connectivity, and current disruption.

Physical player extraction remains governed by actual Minecraft world resources and terrain interaction.

## Rationale

A universal depletion model creates substantial complexity for an indefinitely explored procedural world. Newly discovered settlements may be instantiated long after world start, which would require retrospective reconstruction of unknown extraction histories and risks producing inconsistent cases where newly generated mining settlements possess apparently untouched reserves while previously known settlements have depleted.

The gameplay value of universal reserve depletion does not justify that added state, reconciliation burden, and procedural ambiguity.

## Baseline resource model

The default chain is:

```text
world geology / ecology
        ↓
resource suitability / productive potential
        ↓
settlement productive capacity
        ↓
coarse output rate
        ↓
goods, markets, contracts, logistics
```

NPC extraction consumes no continuously decrementing global ore reserve by default.

A mining settlement may therefore expose properties such as:

```text
iron_potential: HIGH
extraction_capacity: 140 / economic period
production_reliability: MODERATE
```

without maintaining a hidden countdown to exhaustion.

## NPC versus player extraction

```text
PLAYER EXTRACTION
actual terrain
finite blocks
physical mining

NPC CIVILIZATION EXTRACTION
semantic regional productive capacity
no block-by-block accounting
no automatic exhaustion
```

The civilization layer and the Minecraft voxel layer are not required to maintain exact mass conservation between every generated ore block and every unit historically produced by NPC settlements.

## Allowed production constraints

NPC resource production may still fall or stop because of meaningful current-state conditions, including:

- route disruption;
- damaged infrastructure;
- missing industrial inputs;
- broader settlement disruption;
- loss of access to required services;
- authored or systemic world events.

These are operational constraints rather than universal resource exhaustion.

## Exceptional depletion and exhaustion

Resource exhaustion, declining accessibility, abandoned workings, newly recognized deposits, and similar conditions remain valid authored or procedurally selected world states where they create meaningful gameplay.

Examples include:

- an old silver district with exhausted accessible seams;
- an abandoned mining settlement;
- a newly discovered commercially important deposit;
- a temporary extraction decline caused by collapse or infrastructure failure.

Such states are explicit conditions or events, not mandatory arithmetic applied to every extraction settlement.

## Renewable resources

Renewable production such as agriculture and forestry is represented primarily as bounded productive capacity derived from appropriate environmental and civilization state.

The baseline simulation does not require soil-nutrient depletion, forest-stock accounting, herd demographics, or other continuously depleted ecological reserves.

Ecological degradation may be integrated later when it creates worthwhile cross-system gameplay and can remain authoritative to Skyforge's ecology layer.

## Industrial capacity

Industrial transformation remains constrained by productive capacity and input availability. Capacity is semantic civilization state and does not imply automatic physical settlement expansion.

## Player commercial boundary

Player-produced goods enter the coarse economy only when they cross a registered commercial custody boundary. Registering a player facility does not automatically grant offline semantic factory production.

## Invariants

> Geological and ecological fields establish persistent productive potential; NPC civilization converts that potential into coarse capacity rather than consuming a universal hidden reserve.

> Physical player extraction remains governed by actual Minecraft resources.

> Resource exhaustion is exceptional and meaningful, not an obligatory background simulation.

> Economic specialization and productive capacity do not require physical settlement reconstruction.
