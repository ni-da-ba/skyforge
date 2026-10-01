# CIV-0038 — Prosperity and Demographic Stability

Status: PRECOMMIT / LOCKED FOR CIVILIZATION DESIGN

## Core decision

> Skyforge does not ordinarily simulate demographic births, deaths, or migration. Population scale remains a slow structural civilization property and changes only through explicit high-level conditions or events where demographic change itself matters to gameplay.

The civilization/economic layer may track a slowly changing coarse economic condition or prosperity state, but it must not turn ordinary market fluctuations into automatic settlement growth, depopulation, or physical reconstruction.

## Population as structural state

Population remains an authoritative civilization descriptor at coarse scale rather than an explicit count of continuously simulated residents.

Existing qualitative population/settlement scale concepts remain suitable, for example:

```text
OUTPOST
HAMLET
VILLAGE
TOWN
HUB
CAPITAL
```

or equivalent existing authoritative population-intensity descriptors.

Population state is intended to answer questions such as:

- how much ordinary consumption pressure exists;
- what scale of services and infrastructure is plausible;
- what settlement role and activity level are coherent;
- what broad civilian traffic and local realization are plausible.

It is not intended to simulate:

- individual households;
- births and deaths;
- age structure;
- individual migration;
- wages or household budgets;
- exact population accounting.

## Population transitions

Ordinary economic variation does not automatically change population class.

A temporary food shortage, profitable trade period, price spike, or one successful player delivery must not silently cause demographic growth or decline.

Population change is reserved for explicit high-level civilization conditions/events where the demographic transition itself is meaningful, for example:

- authored abandonment;
- catastrophic or sustained conflict;
- deliberate colonization;
- establishment of a genuinely new settlement;
- explicit evacuation or depopulation;
- a major persistent world-state transition.

Such transitions remain structural civilization events rather than incidental arithmetic from the economic solver.

## Prosperity / economic condition

Settlements may maintain a slowly changing coarse economic-condition state derived from persistent economic circumstances.

A minimal conceptual presentation may be qualitative:

```text
STRAINED
STABLE
PROSPEROUS
```

The implementation may internally use a bounded scalar, moving average, banded score, or other deterministic representation, but exact representation is deferred.

Candidate causal inputs include sustained:

- essential supply adequacy;
- trade reliability;
- productive utilization;
- infrastructure/capability availability;
- route connectivity;
- commercial activity;
- persistent disruption or conflict.

Prosperity must change with hysteresis/inertia. A single transaction or incident is insufficient to move a settlement into a structurally different economic condition.

## Prosperity is a consequence, not a currency

The player does not directly earn or spend prosperity.

Do not implement a loop such as:

```text
deliver goods
-> +prosperity points
```

Player action affects underlying causes:

```text
restore reliable freight route
-> shortages reduce
-> supply reliability improves
-> commercial throughput stabilizes
-> over sustained time economic condition may improve
```

Likewise repeated piracy, infrastructure destruction, or route failure may degrade those causes and eventually worsen local economic condition.

Prosperity therefore summarizes sustained economic reality rather than acting as a separate progression meter.

## Demand consequences

Prosperity primarily affects the composition and elasticity of demand rather than multiplying every commodity equally.

A strained settlement tends to prioritize:

```text
FOOD
FUEL
REPAIR GOODS
BASIC MATERIALS
CRITICAL INDUSTRIAL INPUTS
```

A prosperous settlement may support more demand for:

```text
FINISHED GOODS
SPECIALIZED COMPONENTS
LUXURY GOODS
COMMERCIAL SERVICES
HIGHER-VALUE EQUIPMENT
```

Exact commodity mappings remain data-driven and downstream of the demand model.

## Shortages and demographic consequences

Temporary or even serious shortages produce economic pressure first:

```text
scarcity
-> price pressure
-> procurement pressure
-> emergency / freight contracts
-> reduced discretionary activity
```

Do not automatically translate zero food stock or other commodity shortages into simulated civilian death or population decrements.

If famine, evacuation, abandonment, or another demographic consequence is desired, it must be represented as an explicit structural event or authored/systemic high-level transition with suitable presentation and persistence.

## Civilization identity versus current condition

Prosperity/economic condition is orthogonal to the broad civilization role/state.

Examples:

```text
INDUSTRIAL + PROSPEROUS
INDUSTRIAL + STRAINED
FRONTIER + STABLE
SETTLED + STRAINED
```

An `INDUSTRIAL`, `FRONTIER`, `SETTLED`, `CONTESTED`, or similar regional/cluster descriptor answers what the civilization network is. Prosperity answers how its economy is currently performing.

Prosperity must not directly rewrite those structural identities.

## Physical-settlement boundary

Economic improvement or decline does not automatically place, remove, ruin, or replace buildings.

In particular:

```text
STABLE -> PROSPEROUS
```

must not imply automatic physical expansion.

Likewise:

```text
STABLE -> STRAINED
```

must not automatically ruin generated structures.

Any physical settlement transformation remains a separate, explicitly justified realization concern and is not required by this precommit.

## Player-visible information

Ordinary player/Guild interfaces should prefer qualitative or contextual economic reporting over exposing raw internal prosperity variables.

Examples:

```text
Commercial conditions are strong.
Imported necessities remain under pressure.
Trade conditions have stabilized.
```

Computer-facing interfaces may expose structured status where appropriate, subject to existing permissions, freshness, and information-provenance constraints, but should not reveal hidden internal optimization weights.

## Computational constraints

This design must not require:

- household agents;
- population cohorts;
- per-capita birth/death updates;
- continuous migration models;
- physical NPC counts matching semantic population;
- automatic settlement geometry mutation.

Prefer:

```text
slow structural population state
+
slow coarse economic-condition state
+
existing market / supply / infrastructure causes
```

## Design invariants

> Population is slow structural civilization state, not a continuously simulated demographic ledger.

> Prosperity is a slowly changing consequence of sustained economic conditions, not an XP bar or currency.

> Temporary scarcity produces economic pressure before demographic consequences.

> Demographic growth, decline, abandonment, and colonization are explicit structural events when they matter to gameplay.

> Economic prosperity does not automatically change population class or physical settlement geometry.
