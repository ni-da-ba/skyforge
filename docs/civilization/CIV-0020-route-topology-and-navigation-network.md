# CIV-0020 — Route Topology and Guild Navigation Network

## Status
Accepted precommit design direction.

## Core rule
The Guild does not own the sky. It maintains a trusted network through it.

Skyforge models inter-settlement transportation as a semantic graph of nodes and recognized routes. Guild routes provide navigational knowledge, infrastructure, commercial trust, and service guarantees without restricting ordinary free flight through open sky.

## Authoritative layering

```text
Skyforge civilization fields
        ↓
settlement importance / production / demand / connectivity / geography / hazard
        ↓
semantic transport graph
        ↓
Guild interpretation and service network
        ↓
Minecraft realization
```

The Guild must not become the authority that invents settlement importance, population, or economic geography. Those come from civilization semantics; the Guild interprets them into recognized routes and infrastructure.

## Nodes
A transport-network node may represent:
- a settlement;
- a port or Guild Hall associated with a settlement;
- an isolated Guild installation where functionally justified;
- a recognized service or relay point;
- eventually an authorized player base or Guild agency.

## Routes
A recognized route is a semantic connection rather than necessarily a narrow physical corridor.

Illustrative state:

```text
GuildRoute
    origin
    destination
    route_geometry
    travel_cost
    throughput
    reliability
    hazard
    navigation_quality
    operational_status
```

Exact implementation names are deferred.

A route may represent a broad safe passage, known landmark chain, surveyed altitude band, beacon-assisted crossing, difficult approach solution, or other navigationally meaningful connection.

## Open sky versus Guild route
Ordinary flight is not restricted to recognized routes.

Open sky offers freedom and uncertainty.
Guild routes offer information and infrastructure.

Recognized routes may provide:
- surveyed navigation;
- known approach information;
- hazard knowledge;
- distress and recovery coverage;
- stronger route records;
- commercial freight traffic;
- support for automated logistics;
- favorable treatment for bonded or insured operations where appropriate.

Bonded contracts may require use of an approved route unless circumstances justify deviation. Private flight remains free unless a sovereign settlement imposes its own law.

## Navigation infrastructure
Navigation infrastructure exists at three scales.

### Port infrastructure
Potential examples:
- landing/mooring facilities;
- Guild Hall or agency;
- registration/contact point;
- approach beacon;
- cargo infrastructure;
- route/weather information.

### Route infrastructure
Sparse installations only where functionally justified:
- navigation beacon;
- relay or signal station;
- warning marker;
- emergency mooring;
- rescue cache;
- staffed route station on major corridors.

Do not pepper every route with excessive bespoke structures.

### Vessel infrastructure
The future Guild transponder/flight recorder is the vessel-side institutional endpoint.

## Semantic-first infrastructure
Navigation infrastructure should be authoritative semantic state first and physical simulation only where interaction requires it.

Illustrative beacon state:

```text
Beacon
    position
    route_associations
    operational_state
    accuracy_state
    service_region
    maintenance_state
```

No continuous simulated radio propagation is required unless later gameplay justifies it.

This architecture supports the Bellanca opening: a beacon may have had an authoritative calibration fault at incident time, and the black box may preserve evidence of the incorrect guidance without requiring continuous historical physical simulation.

## Transponder / flight recorder role
The future Guild transponder/recorder is reserved as the bridge between a physical aircraft and Guild institutional memory.

Potential responsibilities include:
- vessel identity and registration;
- owner/operator linkage;
- recognized configuration/certification status;
- current or assigned route information;
- navigation interaction;
- distress signaling;
- last-known position;
- departure/arrival events;
- manifest linkage where justified;
- bounded operating telemetry;
- incident chronology and evidence;
- recovery and snapshot association.

This does not imply indefinite every-tick telemetry storage. Detailed retention and hardware behavior remain a later design gate.

## Infrastructure failure
Navigation infrastructure should feed route and economic state.

Examples:

```text
beacon damaged
→ navigation quality falls
→ route reliability falls
→ freight may reduce or divert
→ repair contract may emerge
```

```text
relay outage / severe storm
→ route hazard rises
→ traffic shifts
→ destination stock may decline
→ downstream economic consequences appear
```

## Discovery and recognition
Distinguish:
- DISCOVERED — player knows a location exists;
- SURVEYED — useful navigational information exists;
- GUILD-RECOGNIZED — Guild is willing to integrate the location into its network.

A newly discovered settlement should not automatically become a major route node. Integration may require survey work, negotiated access, infrastructure, an initial freight connection, or Guild agency establishment.

## Player bases as network nodes
A player base may eventually become a recognized node through some combination of:
- certified landing infrastructure;
- Guild agency authorization;
- sufficient connectivity;
- network registration or route establishment.

Once recognized, the base may legitimately receive freight, deliveries, contracts, visitors, route information, or other traffic.

Its actual geography should matter: a remote or hazardous base may have poor throughput; a base on an important corridor may become economically valuable.

## Capacity and realization
Economic throughput and visible vessel count are not the same quantity.

Route capacity remains semantic and may derive from infrastructure, distance, hazard, connectivity, carrier capacity, reliability, and other civilization factors.

Rendered aircraft represent the economy; they do not numerically constitute it.

## Automated Logistics boundary
Candidate layering:

```text
SKYFORGE
civilization topology
route semantics
economic demand
shipment identity
hazard / incident state
        ↓
MINECRAFT CIVILIZATION ADAPTER
selects when/how traffic is physically realized
        ↓
AERONAUTICS AUTOMATED
executes appropriate physical route when required
```

Do not encode civilization topology as authoritative state inside the addon itself.

## Piracy/security implication
Route state provides a natural encounter substrate. A route may carry coarse traffic, security, and piracy pressure without continuously simulating every vessel.

When player observation or interaction requires an encounter, the realization layer selects an authoritative semantic shipment and embodies it physically.

## Locked principles
1. Skyforge models transportation as a semantic graph.
2. Guild routes are trusted navigational/commercial infrastructure, not ownership of the sky.
3. Free flight outside Guild routes remains possible.
4. Navigation infrastructure is semantic first and physically realized only when needed.
5. The future transponder/flight recorder bridges aircraft state to Guild institutional systems.
6. Player-discovered settlements and authorized player bases may become network nodes through actual integration work.
7. Economic route capacity and rendered aircraft count remain decoupled for performance.
