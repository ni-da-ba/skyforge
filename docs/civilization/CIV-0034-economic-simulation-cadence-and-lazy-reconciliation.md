# CIV-0034 — Economic Simulation Cadence and Lazy Reconciliation

Status: PRECOMMIT / LOCKED FOR CIVILIZATION DESIGN

## Basis

This decision extends the repository's existing civilization boundary and CIV-0033 rather than introducing a continuously simulated global economy.

Existing civilization direction remains authoritative:

- civilization is a regional condition and functional network;
- world-level civilization state remains coarse and explainable;
- the cluster is the principal functional unit for settlement-scale planning;
- Minecraft handles loaded local entities and physical interaction;
- detailed economic accounting is activated only where gameplay requires it.

CIV-0033 further establishes:

> Latent civilization carries structure and causality; activated economics carries accounting.

CIV-0034 defines how that accounting advances through time without requiring per-tick simulation of every active or latent settlement.

## Core decision

> Skyforge civilization should use event-driven structural state, coarse/lazy economic reconciliation, and immediate operational transactions. Stable economic conditions are integrated over elapsed time; meaningful discontinuities are recorded as events. Latent clusters retain structural supply/demand relationships rather than continuously updated inventories.

The simulation budget is therefore proportional primarily to relevant change and player-facing consequence, not to world age multiplied by the number of settlements.

## Three cadence classes

### 1. Structural civilization state — rare / event-driven

Examples:

- settlement or cluster role;
- population/activity class;
- faction/control state;
- Guild relationship;
- route recognition/topology;
- major infrastructure capability;
- persistent abandonment/restoration;
- major route or civic-asset destruction.

Structural state changes when a meaningful event changes the support story of the region. It should not be recomputed every game tick.

### 2. Activated economic state — coarse periodic or lazy catch-up

Examples:

- local commodity stocks;
- production/consumption balance;
- market pressure and quotes;
- known supplier/customer relationships;
- bounded store catalogues;
- scheduled or committed shipment effects;
- credit/account obligations where time matters.

An activated cluster records a last reconciled simulation time. When economic state is next needed, Skyforge advances that state from the last reconciled time to the requested time.

Periodic background reconciliation may be used for currently relevant clusters when profiling justifies it, but it is not the semantic requirement.

### 3. Operational gameplay state — immediate / event-driven

Examples:

- player purchases or sales;
- accepting or completing a contract;
- actual shipment departure/arrival;
- piracy/interception;
- cargo loss;
- repayment;
- civic infrastructure destruction;
- recovery or restitution;
- player-owned automation changing inventory/capacity.

These actions affect authoritative state immediately because they are directly observable, transactional, or custody-sensitive.

## Lazy reconciliation

Preferred conceptual state:

```text
ActivatedEconomy {
    clusterId
    lastReconciledTime
    detailedStocks
    marketState
    supplierCustomerState
    committedShipments
    consequentialHistory
    ...
}
```

When the state is needed at time `T1`:

```text
T0 = lastReconciledTime
elapsed = T1 - T0

reconcile(T0 -> T1)
lastReconciledTime = T1
```

No requirement exists to execute one simulation step for every tick, second, minute, or in-game hour in the interval.

## Analytical integration where safe

Stable conditions should be integrated directly where the model permits it.

Conceptually:

```text
stock += productionRate * elapsed
stock -= consumptionRate * elapsed
```

subject to:

- bounded capacity;
- minimum/maximum stock constraints;
- resource-input limitations;
- route availability;
- known commitments;
- saturation or shortage behavior;
- other intentionally modeled nonlinearities.

Do not force analytical integration where the economic rule is materially state-dependent. The requirement is to avoid unnecessary small timesteps, not to pretend all dynamics are linear.

## Discontinuities segment the interval

Meaningful changes inside a lazy interval are represented as sparse semantic events.

Example:

```text
T0 ---------------- X ---------------- T1
                    ^
               route destroyed
```

Reconciliation becomes:

```text
integrate T0 -> X under old conditions
apply route-destruction event
integrate X -> T1 under new conditions
```

Potential discontinuities include:

- route opening/closure;
- infrastructure destruction/restoration;
- major shipment arrival/loss;
- contract-backed demand;
- player facility entering/leaving service;
- major hazard or security transition;
- faction/control change;
- authored incident;
- communications outage where information-dependent behavior actually matters.

Only events that can materially alter the later state need to be retained.

## Event log is not tick history

The consequential event record must remain sparse.

Do not persist:

- every market quote observed;
- every implied NPC purchase;
- every abstract worker action;
- every nominal production cycle;
- every intermediate integration step.

Persist only discontinuities, custody-sensitive records, player-caused consequences, or other facts that cannot safely be reconstructed from current authoritative state and elapsed time.

## Latent clusters do not run detailed clocks

A wholly latent cluster does not maintain per-commodity stocks or continuously advance a detailed economy.

It retains or derives structural information such as:

```text
RAW_MATERIAL = EXPORT
FOOD = IMPORT
MANUFACTURED_GOODS = IMPORT
TRANSPORT = SUPPLY
tradeIntensity = moderate
routeImportance = high
```

This describes economic pressure and support relationships without requiring exact quantities.

Detailed commodity stocks, quotes, and shipment accounting are created only at the activation boundary defined in CIV-0033.

## Latent regional flows

Do not model one explicit transfer for every implied inter-cluster transaction.

Where both sides are latent, commerce may remain aggregate route/flow pressure derived from:

- structural import/export complementarity;
- route availability and reliability;
- broad trade intensity;
- transport capability;
- persistent disruption/security state;
- regional civilization context.

This aggregate state may influence traffic plausibility, route importance, settlement support stories, and later economic warm-starting without generating persistent shipment objects.

## Crossing from activated into latent civilization

Player-visible or otherwise authoritative actions may affect a destination that is still latent.

Example:

```text
ACTIVE PLAYER FACTORY
        |
        | actual committed commercial effect
        v
LATENT RECEIVING CLUSTER
```

Skyforge should not activate every downstream market merely to acknowledge that consequence.

Instead, record a compact aggregate effect that can survive until the receiving cluster is next evaluated.

Conceptual example:

```text
ExternalEconomicEffect {
    targetClusterId
    category = MANUFACTURED_GOODS
    direction = SUPPLY
    magnitudeClass = SIGNIFICANT
    sourceClass = PLAYER_OPERATION
    effectiveTime
    persistencePolicy
}
```

The exact representation remains implementation-sensitive. The invariant is that consequential external actions can cross into latent civilization without forcing detailed activation.

## Crossing from latent into activated civilization

When a latent supplier/customer relationship begins affecting an activated cluster, Skyforge may refine the relevant portion of the latent relationship into a concrete economic relationship or shipment only as needed.

The refinement should preserve the structural cause:

```text
latent mining exporter
        |
        | activated market now needs concrete supply
        v
supplier relationship / shipment realization
```

Do not invent unrelated commodity history merely because the destination became active.

## Committed shipments are authoritative discontinuities

Once Skyforge creates a concrete shipment because custody, contract, player interception, or detailed market accounting requires it, the shipment is no longer an aggregate background flow.

Its departure, incident state, delivery, loss, diversion, or recovery must settle exactly once against the relevant economic state.

This preserves the existing freight invariant:

> One authoritative semantic shipment settles once, regardless of whether a physical vessel was ever realized.

## Player-owned automation

Player-owned logistics can participate in lazy reconciliation when the physical system is not currently observed, but player inventory/custody must remain authoritative.

Do not fabricate production or delivery that could conflict with loaded physical machinery.

A player-owned operation may therefore require a stronger persistence boundary than ordinary NPC background commerce.

Detailed execution rules for player-owned autonomous routes remain downstream implementation work.

## Information systems

Guild information interfaces and ComputerCraft-style clients must read reconciled or explicitly stale cached economic products; they must not cause a full civilization simulation step per query.

Conceptual sequence:

```text
query arrives
    -> determine whether requested economic product is current enough
    -> reconcile relevant activated state only if required
    -> update/read bounded Guild information cache
    -> return provenance + freshness
```

Polling must not scale simulation cost linearly with Lua query frequency.

## Determinism

Given the same:

- prior authoritative state;
- elapsed time;
- ordered consequential event set;
- deterministic procedural inputs;

reconciliation should produce the same semantic result.

Where stochastic economic variation is desirable, use deterministic keyed randomness or predeclared semantic events rather than wall-clock-dependent randomness during catch-up.

## Reconciliation ordering

Implementation must define a stable ordering for same-time or overlapping effects.

At minimum, the design must not permit outcome changes caused solely by hash-map iteration, chunk load order, client query timing, or incidental thread scheduling.

Exact ordering rules remain an implementation contract, but deterministic resolution is mandatory.

## Bounds and stability

Lazy integration must not permit arbitrarily large elapsed intervals to create absurd state.

Models should use meaningful bounds such as:

- storage capacity;
- minimum operating stocks;
- finite production capacity;
- demand saturation;
- route capacity;
- disruption ceilings/floors;
- decay of temporary external effects where appropriate.

If a long interval contains no meaningful discontinuity, the reconciled result should approach a plausible bounded state rather than diverge indefinitely.

## Persistence policy

Persist:

- `lastReconciledTime` for detailed activated state;
- authoritative detailed state that cannot be regenerated safely;
- committed shipments;
- player-caused or custody-sensitive discontinuities;
- unresolved external effects crossing latent/active boundaries;
- persistent structural changes.

Do not persist periodic intermediate integration states solely because time passed.

## Relationship to physical realization

Economic reconciliation is not equivalent to Minecraft realization.

A cluster may have reconciled detailed economic state while no physical aircraft or NPC worker is loaded.

Likewise, local Minecraft entities may be realized for presentation while some surrounding regional commerce remains aggregate.

Physical simulation is required when observation, collision, player interaction, or another concrete mechanic makes it necessary.

## Explicit non-goals

CIV-0034 does not define:

- exact production-rate formulas;
- price elasticity constants;
- universal update intervals;
- exact commodity units;
- detailed company/household simulation;
- per-NPC consumption;
- a global optimal-transport solver;
- deactivation/collapse policy for turning detailed active state back into a safe latent representation;
- exact save-file schema.

Those are downstream only where gameplay or profiling demonstrates a need.

## Computational invariants

> Stable conditions are integrated; discontinuities are recorded.

> Structural civilization changes by meaningful events, not ticks.

> Detailed economic state advances lazily or at coarse cadence, not continuously world-wide.

> Operational transactions settle immediately against authoritative state.

> Latent-to-latent commerce is aggregate unless concrete custody or gameplay requires a shipment.

> Consequential actions may cross into latent civilization as compact aggregate effects without forcing activation.

> Computer/UI polling reads cached or selectively reconciled products; it never drives one economy simulation per query.

> Simulation cost should scale primarily with relevant state changes and active gameplay, not with total generated civilization multiplied by world age.
