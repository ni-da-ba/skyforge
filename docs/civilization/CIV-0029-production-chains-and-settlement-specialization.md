# CIV-0029 — Production Chains and Settlement Specialization

Status: precommit design lock

## Decision

Settlement specialization emerges from resource availability, productive capabilities, infrastructure, connectivity, and historical economic state rather than from rigid settlement archetypes.

Production is represented as a coarse directed graph of economically meaningful transformations. A production stage is modeled separately only when doing so creates meaningful supply, logistics, pricing, or gameplay consequences.

NPC production is resolved semantically with bounded capacity and input constraints. Player factories use physical Minecraft/Create processes but enter the same economic ontology at registered commercial boundaries.

Physical settlement appearance is not required to change when economic specialization changes.

## Semantic production model

A settlement should be described by what it can actually do rather than by a hardcoded label such as `MINING_TOWN`.

Example:

```text
extracts: iron ore, coal
processes: basic metal
consumes: food, tools, machinery
exports: ore, metal
```

Labels such as mining settlement, industrial hub, agricultural district, or port town may be derived for presentation and authored flavor, but they are not the primary simulation state.

This follows the Skyforge design principle: descriptors before labels.

## Production graphs

Economically meaningful transformations form coarse directed chains, for example:

```text
IRON ORE
   ↓ smelting
METAL
   ↓ machining
COMPONENTS
   ↓ assembly
MACHINERY
```

or:

```text
GRAIN
  ↓ milling
FLOUR
  ↓ food production
PROVISIONS
```

The civilization economy should not mirror every Minecraft/Create crafting step. A stage should exist independently only when separating it creates meaningful logistical, pricing, scarcity, strategic, or gameplay consequences.

A suitable default compression is often:

```text
RAW MATERIAL
→ REFINED MATERIAL
→ COMPONENT
→ FINISHED GOOD
```

Intermediate states may be promoted where the world or gameplay justifies it.

## Production capabilities

A settlement may expose bounded production capabilities conceptually similar to:

```text
ProductionCapability {
    input_classes
    output_classes
    capacity
    efficiency
    reliability
    local_resource_dependency
}
```

NPC production should use coarse rates or economic-period capacity rather than block-by-block factory simulation.

Actual output is constrained by available inputs, reserves, disruption, and reasonable substitution. A temporary shortage should not necessarily zero output immediately.

## World-semantic grounding

Primary production should remain downstream of Skyforge world semantics.

Examples:

- geology and ore fields support viable extraction industries;
- climate, moisture, ecology, and terrain support agriculture or forestry;
- settlement scale, infrastructure, connectivity, and history support processing and advanced manufacturing.

The civilization adapter should not assign implausible industries independently of these conditions unless authored history or imported inputs provide a coherent explanation.

## Comparative advantage and specialization

Specialization emerges where resource conditions, infrastructure, labor/population scale, connectivity, import availability, reliability, and historical productive capacity align.

Two settlements with similar resources may therefore diverge:

```text
Settlement A
resource-rich + poorly connected
→ exports raw material

Settlement B
resource-rich + major hub + established workshops
→ produces higher-value machinery
```

Mixed economies should be normal. Primary, secondary, and minor activities may coexist without forcing a settlement into a single economic archetype.

## Input dependence and shock propagation

Production chains should transmit meaningful shortages.

For example:

```text
metal shortage
    ↓
machinery output falls
    ↓
downstream machinery prices rise
    ↓
procurement and freight demand appear
```

Inventories, reserves, partial output, and substitution should prevent excessive brittleness.

## Player production

Registered player facilities participate in the same semantic production vocabulary as NPC civilization.

A player foundry might economically expose:

```text
consumes: METALS_RAW, FUEL
produces: METALS_REFINED
```

while the actual physical Create factory may contain a much deeper recipe chain.

The civilization layer only needs to understand economically relevant inputs and outputs at the registered commercial boundary.

Player production receives no special simulation privilege or penalty: it participates as ordinary economic capacity.

## Item and commodity ontology

A data-driven mapping layer should connect concrete Minecraft/modded items to semantic economic classes.

The ontology may be hierarchical where useful:

```text
METALS
├── IRON
├── STEEL
├── BRASS
└── COPPER
```

Broad categories support strategic reasoning and aggregation, while narrower categories preserve meaningful non-substitutability for actual transactions and production requirements.

## Derived economic properties

The model may derive useful properties such as import dependence, export orientation, production bottlenecks, critical inputs, and chokepoint goods from the underlying graph rather than storing them as arbitrary labels.

These derived properties can influence prices, contracts, logistics, vulnerability, and Guild information presentation.

## Physical settlement realization

Changes in economic specialization do not imply automatic regeneration or expansion of the settlement's physical architecture.

Economic state may evolve independently of visible structures. Any future system for physical settlement adaptation requires a separate design and human gate.

## Invariants

1. Settlement specialization is descriptive and emergent, not a rigid class assignment.
2. Production stages exist only when economically meaningful.
3. NPC production remains coarse and computationally bounded.
4. Player and NPC production enter the same economic ontology.
5. World semantics constrain plausible primary industries.
6. Physical settlement appearance need not track economic-state changes.
7. Civilization economics do not reproduce every Create/Minecraft recipe step.
