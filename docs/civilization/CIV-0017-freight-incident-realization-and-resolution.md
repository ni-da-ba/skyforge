# CIV-0017 — Freight Incident Realization and Resolution

## Status
Locked precommit decision.

## Decision
Freight incidents are authoritative semantic records by default, not continuously simulated physical events. Physical realization occurs only when the incident becomes directly relevant to a player or another high-value gameplay system.

### Core rule

> Unobserved history is semantic. Observed history becomes physical. Player-altered history becomes persistent.

### Incident lifecycle

```text
semantic incident
    ↓ player approaches / investigates / intercepts
physical realization
    ↓ player modifies scene / salvages / fights / rescues
persistent interactive aftermath
```

The civilization/logistics layer records only the facts required to preserve causality and downstream effects. It does not simulate detailed vessel physics, combat, debris, NPC behavior, or insurance processing for unrelated NPC-only incidents.

## Semantic incident record

A coarse incident may retain fields such as:

- `incident_id`
- `route_id`
- `vessel_id`
- incident time
- cause
- state
- approximate location / route segment
- cargo outcome
- vessel outcome
- survivor state where relevant
- evidence state
- scene seed
- economic consequences

Illustrative incident states:

- `INCIDENT_PENDING`
- `DISTRESS`
- `DELAYED`
- `DIVERTED`
- `DAMAGED`
- `ATTACKED`
- `LOST`
- `RECOVERY_PENDING`
- `RECOVERED`
- `CLOSED`

Possible causes include weather, dragons, illagers, player piracy, mechanical failure, navigation failure, collision, and infrastructure outage.

Cause and consequence remain separate. Different causes may produce the same loss state while generating different follow-up contracts, security responses, salvage opportunities, and narrative consequences.

## Realization policy

When an incident is unobserved, Skyforge resolves it semantically. If the player becomes relevant before or during resolution, the backend may realize an encounter or investigation scene physically.

For NPC freight, Aeronautics Automated is the likely execution layer when physical route or vessel behavior is required. Skyforge remains authoritative over the semantic route and incident state.

An incident may also enter `INCIDENT_PENDING` briefly, allowing the runtime to determine whether it should be realized because a player is nearby, tracking the route, responding to distress, or otherwise engaged. If not, the incident resolves coarsely.

## Deterministic scene reconstruction

Investigation scenes should preferentially be reconstructed from authoritative facts and a deterministic seed rather than permanently cached before discovery.

Potential reconstruction inputs include:

- vessel snapshot / recognized configuration
- incident seed
- approximate location
- cause
- severity
- cargo state
- survivor state
- evidence state
- damage profile

The generated scene need only be consistent with those facts. Exact tick-by-tick physical history is not authoritative unless the player was present for it.

Once the player changes the scene materially — for example by removing components, rescuing survivors, opening cargo, moving wreckage, or otherwise interacting — the altered state becomes persistent and should no longer be regenerated from the pristine semantic record alone.

## Player piracy

Player piracy is expensive only while the player is actually participating in or observing it. A nearby freighter can be physically simulated because the encounter is already relevant; afterward its outcome collapses back into semantic civilization state.

NPC piracy and other distant hazards resolve at the same coarse level as ordinary autonomous logistics.

## Insurance boundary

The world does **not** need to instantiate or process detailed insurance claims for random NPC-to-NPC freight losses that never involve the player or another system that consumes claim state.

For unrelated NPC commerce, insurance may be represented implicitly in aggregate economic resolution if needed, or omitted entirely when it has no gameplay consequence.

Detailed claim workflows are reserved for player assets, player-carried bonded cargo, authored incidents, Guild liability cases, or other situations where the claim itself is gameplay-relevant.

## Rationale

This design preserves causal world history while keeping civilization simulation computationally cheap. It supports piracy, distress, salvage, investigation, route disruption, and recovery without requiring thousands of persistent physical wrecks or permanently simulated aircraft.

It also preserves the principle:

> Simulate physically when observed; simulate semantically when unobserved.
