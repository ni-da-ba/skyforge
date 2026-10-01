# AUTH-0018 — Coherent Visible-Channel Skeleton

AUTH-0018 suppresses spatially redundant disconnected visible-channel components while preserving the accepted watershed routing and every retained component's internal topology.

## Dependency

~~~text
accepted watershed routing
    -> accepted visible channel candidates
    -> accepted hierarchy / geomorphic profiles
    -> AUTH-0017 naturalized reach geometry
    -> AUTH-0018 coherent visible-channel component selection
    -> later migration of riparian / terrain shaping to the coherent skeleton
~~~

## Why this layer exists

AUTH-0017 showed that local spline geometry can remove coarse lattice corners without moving accepted graph nodes. The same evidence also made a different upstream defect obvious: some high-relief representatives contain many disconnected drainage components terminating at neighboring island-edge outlets.

Keys 512 and 811 therefore display dense parallel rake-like systems even after each individual reach is smoothly naturalized.

This is not a spline defect. It is a visible-network selection problem.

## Preservation boundary

AUTH-0018 does not change watershed routing.

Every coarse watershed cell keeps the same downstream index, flow accumulation, spill routing, retained-sink state, and edge-outlet state.

The planner operates only on disconnected components of the already accepted visible channel graph.

Within any retained component, AUTH-0018 preserves every accepted reach, source/downstream identity, stream order, role, discharge, geomorphic profile, and AUTH-0017 geometry.

It removes or retains entire disconnected components; it never cuts a component in half.

## Component identity

Accepted channel profiles are treated as a directed functional graph: each visible source cell has at most one accepted downstream reach.

For coherence selection the graph is viewed weakly/undirected to identify disconnected visible drainage components.

Each component has one terminal downstream node. The component's terminal discharge is the strongest accepted reach entering that terminal node.

## Spatial competition

Components compete in descending order of:

1. terminal relative discharge;
2. maximum stream order;
3. retained reach count;
4. stable terminal cell identity.

The strongest component is considered first. A later component is retained only if its terminal node is at least **4 AUTH-0005 planning-cell spacings** from every already retained terminal.

This is semantic non-maximum suppression over visible drainage outlets, not a change to physical water routing.

The value 4.0 is a first-generation calibration chosen to separate distinct island-scale drainage sectors while suppressing adjacent outlet fans exposed by the 512/811 evidence.

## Evidence

The deterministic authorship-coherent-channel-skeleton-v1 corpus reuses keys 77, 118, 241, 512, 811, and 83.

Each panel uses the accepted AUTH-0016 continuous terrain:

- ALL — all accepted AUTH-0017 naturalized visible channels;
- COHERENT — only components retained by AUTH-0018;
- OVERLAY — retained components in blue and suppressed components in pale red; black circles mark retained terminal nodes.

manifest.csv records source/retained component and reach counts.

components.csv records retained terminal positions, discharge, stream order, and reach count.

## Acceptance gate

Reject AUTH-0018 if:

- ordinary basin/tableland examples lose clearly independent major drainage systems;
- retained components are internally truncated;
- a stronger neighboring component is suppressed in favor of a weaker one;
- retained terminal spacing violates the semantic separation contract;
- keys 512 and 811 remain visually dominated by adjacent parallel rake components;
- channel pruning becomes so aggressive that high-relief islands collapse to one arbitrary visible river;
- retained-water drainage relationships are broken.

## Important scope boundary

AUTH-0018 creates a **coherent candidate skeleton**. It does not yet rewrite the previously accepted downstream riparian, channel-profile, waterfall, or AUTH-0016 terrain-shaping evidence to consume that skeleton.

That migration must be a separate milestone so the before/after consequences of changing the authoritative visible network remain inspectable.

## Deferred

- migrating downstream channel consumers onto the coherent skeleton;
- changing full watershed routing;
- moving accepted graph nodes;
- basin-scale meander evolution;
- literal banks, width, depth, fluids, or blocks;
- province/cluster-scale drainage coordination.
