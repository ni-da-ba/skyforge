# CIV-0028 — Economic Shocks and Settlement Adaptation

Status: precommit / human-approved direction

## Decision

Settlement economies respond to shocks in stages. Inventories, prices, procurement, rerouting, and substitution handle short-term disturbances. Only persistent changes in supply, demand, connectivity, productive capacity, or reliability justify structural economic adaptation.

Player industry participates as ordinary economic capacity rather than through special-case bonuses. Sustained player supply may displace existing trade, reduce scarcity, redirect NPC logistics, and alter regional sourcing or specialization. As scarcity falls, extraordinary margins should fall naturally rather than through arbitrary production caps.

## Response ladder

A settlement may respond progressively through:

1. **Absorb** — draw down reserves or tolerate temporary imbalance.
2. **Price** — adjust local transaction prices as scarcity or surplus changes.
3. **Procure** — seek additional supply through contracts or autonomous logistics.
4. **Reroute** — use alternate suppliers or transport paths.
5. **Substitute** — use acceptable alternative goods where semantics permit.
6. **Expand economically** — increase viable productive capacity or supplier relationships.
7. **Restructure economically** — change long-term sourcing, specialization, or route dependence after sustained evidence.

Short-lived shocks should not trigger long-lived restructuring.

## Hysteresis and persistence

Economic adaptation should use accumulated evidence and retention thresholds rather than reacting to every transaction.

- Temporary deviation → operational response.
- Repeated deviation → adaptive response.
- Sustained new equilibrium → structural economic response.

This prevents oscillation and preserves institutional inertia.

## Player industry

Registered player production should appear to the economy as another supplier characterized by factors such as available quantity, price, reliability, route access, and delivery cost.

A sufficiently large and well-connected player factory may genuinely dominate a regional commodity or component market. The design should allow this. Natural consequences provide balance:

- local stock rises;
- scarcity premiums fall;
- imports decline;
- displaced suppliers seek other destinations or reduce relevance;
- further growth requires broader distribution, additional customers, and freight capacity.

Logistics and market depth, not arbitrary production nerfs, should become the limiting factors.

## Reliability and delivered cost

Supplier selection should consider more than nominal purchase price. Relevant semantic pressures may include purchase price, transport cost, expected loss, delay penalty, route reliability, and service capacity.

A more expensive but reliable nearby supplier may be preferred to a cheaper source across a dangerous or unstable route.

## Resilience

Settlement resilience should emerge from existing underlying state rather than necessarily becoming a single explicit gameplay statistic. Contributing factors include reserves, supplier diversity, route redundancy, warehousing, infrastructure, and Guild connectivity.

The same route outage may therefore be trivial for a major hub and severe for a remote single-route settlement.

## Surpluses

Sustained abundance matters as much as shortage. Large new supply can lower local prices, increase outbound freight, redirect suppliers, and potentially make downstream production viable where appropriate.

The civilization model should allow persistent surpluses to alter economic flows without requiring household-level simulation.

## Physical settlement change — deferred

This decision does **not** commit Skyforge to automatically adding, replacing, or regenerating physical town buildings in response to economic adaptation.

Economic state, contracts, prices, routes, and supplier relationships may change without any architectural change at all.

If physical settlement growth or modification is later desired, it must be designed as a separate system with strong constraints around persistence, player modifications, authored settlements, generation safety, and performance. Examples such as new warehouses, docks, workshops, or Guild facilities are therefore possibilities only, not requirements of this model.

## Computational constraint

Settlement adaptation remains coarse and event-driven or periodically reconciled. A settlement should inspect its own stock/production/demand state, existing supplier and route relationships, and only a bounded candidate set when persistent stress warrants adaptation.

The model must not require household simulation, per-NPC economic accounting, continuous global optimization, or all-pairs supplier search.

## Invariants

> Short disruptions change operations. Sustained pressures can change economic structure.

> Player industry is ordinary economic capacity and may reshape regional supply chains if it is sufficiently productive, reliable, and connected.

> Economic adaptation does not imply automatic physical town expansion.
