# Civilization design records

**Status:** current civilization / Skyfarer's Guild conceptual sweep complete on the civilization precommit branch.

This directory contains the durable product-direction records for Skyforge civilization, the Skyfarer's Guild, markets, logistics, vessels, contracts, player industry, latent simulation, sovereignty/allegiance, onboarding, and the implementation boundary.

Start with [`CIVILIZATION-PRECOMMIT-COMPILATION.md`](CIVILIZATION-PRECOMMIT-COMPILATION.md). It is the synthesis/read-order entry point for fresh agents. For executable decomposition, read [`CIV-0041-bellanca-onboarding-and-bootstrap-civilization-integration.md`](CIV-0041-bellanca-onboarding-and-bootstrap-civilization-integration.md), [`CIV-0042-civilization-implementation-and-acceptance-contract.md`](CIV-0042-civilization-implementation-and-acceptance-contract.md), and [`CIV-0043-bootstrap-province-spatial-recipe.md`](CIV-0043-bootstrap-province-spatial-recipe.md) when the task touches Bootstrap geography, mobility, or progression closure. Then inspect only the subsystem records needed by the current bounded task.

Program-level repository authority still applies: current `main`, `AGENTS.md`, the Program Charter, Validation Policy, lane state, Cross-Lane Contracts, executable source/tests, and merged history take precedence over stale summaries.

## Numbering note

The early design sweep produced parallel records with the same numeric prefix (`CIV-0003`, `CIV-0004`, `CIV-0005`). Their **full filenames are canonical**. Do not renumber them merely to make the sequence cosmetically unique.

`CIV-0043` was added after the original compilation/closeout. It narrows the Bootstrap Province spatial/progression contract; it does **not** supersede `CIV-0041` as the Bellanca/onboarding authority or `CIV-0042` as the civilization implementation/acceptance boundary.

## Contract-system quick read

Do not reduce the Guild contract system to a quest board or read `CIV-0001` in isolation. Contracts are downstream of a connected legal, financial, logistical, informational, and world-state system.

For a contract-focused audit, read at minimum:

1. [`CIV-0010-membership-and-delegated-authority.md`](CIV-0010-membership-and-delegated-authority.md) — membership, Standing-derived authorizations, and scoped delegated authority;
2. [`CIV-0011-vessel-registration-certification-and-recertification.md`](CIV-0011-vessel-registration-certification-and-recertification.md) — vessel identity, recognized configuration, and operational eligibility;
3. [`CIV-0012-guild-insurance-and-incident-evidence.md`](CIV-0012-guild-insurance-and-incident-evidence.md) — insurance, restitution, fault, claims, black-box/transponder evidence;
4. [`CIV-0013-guild-loans-and-vessel-financing.md`](CIV-0013-guild-loans-and-vessel-financing.md) — financing and asset acquisition;
5. [`CIV-0014-contract-structure-and-risk.md`](CIV-0014-contract-structure-and-risk.md) — the core contract model: provenance, objective, authorization, hazard, asset exposure, bonds/collateral, liability, advances, completion, and failure/fault;
6. [`CIV-0016-autonomous-freight-and-piracy.md`](CIV-0016-autonomous-freight-and-piracy.md), [`CIV-0017-freight-incident-realization-and-resolution.md`](CIV-0017-freight-incident-realization-and-resolution.md), and [`CIV-0018-route-security-and-piracy-response.md`](CIV-0018-route-security-and-piracy-response.md) — freight incidents, piracy, attribution, route/security consequences;
7. [`CIV-0020-route-topology-and-navigation-network.md`](CIV-0020-route-topology-and-navigation-network.md) and [`CIV-0023-guild-communications-and-information-propagation.md`](CIV-0023-guild-communications-and-information-propagation.md) — the physical/information networks that make contract conditions and knowledge geographically meaningful;
8. [`CIV-0025-physical-logistics-warehouses-manifests-and-custody.md`](CIV-0025-physical-logistics-warehouses-manifests-and-custody.md) — manifests, bonded cargo, custody, and the semantic/physical inventory boundary;
9. [`CIV-0027-local-markets-catalogues-and-price-formation.md`](CIV-0027-local-markets-catalogues-and-price-formation.md) and [`CIV-0037-regional-trade-flow-resolution.md`](CIV-0037-regional-trade-flow-resolution.md) — local prices, supply/demand, procurement, and regional commercial pressure;
10. [`CIV-0039-faction-sovereignty-economic-access-and-allegiance.md`](CIV-0039-faction-sovereignty-economic-access-and-allegiance.md) — sovereignty, jurisdiction, access, and allegiance boundaries around Guild action.

The governing contract principles are:

- contracts are **legal/economic relationships with world state**, not isolated MMO quest rolls;
- risk is multidimensional rather than a single difficulty rank;
- Routine, Bonded, Hazardous, and Discretionary/Sensitive are overlapping classes;
- provenance is first-class: Guild, Chapter, Private, Discretionary, or Illicit work can give similar physical objectives different legal and moral meanings;
- Standing governs entrusted responsibility and authorization; Credit governs financial terms/security; Scrip governs purchasing/payment capacity;
- good-faith failure, negligence, breach, and fraud/theft are distinct;
- evidence and attribution matter; institutions are not omniscient;
- manifests and custody connect contracts to physical cargo, insurance, piracy, recovery, markets, and settlement stock;
- recurring contracts should arise from actual settlement, route, inventory, hazard, and institutional state rather than from FTB Quests as semantic authority.

## Foundation, Guild identity, architecture, onboarding

- `CIV-0001-guild-and-civilization-precommit.md`
- `CIV-0002-guild-architecture-and-donor-strategy.md`
- `CIV-0003-guild-regionalization-and-structural-grammar.md`
- `CIV-0003-open-sky-regional-variants.md`
- `CIV-0004-guild-recognition-and-regional-contexts.md`
- `CIV-0004-guild-settlement-footprint.md`
- `CIV-0005-guild-hall-services.md`
- `CIV-0005-open-sky-emblem-lock.md`
- `CIV-0006-first-guild-claim-and-black-box.md`
- `CIV-0007-dialogue-and-service-conversation.md`
- `CIV-0008-guild-civilization-adapter.md`

## Institutional economy, membership, vessels, finance, contracts

- `CIV-0009-scrip-standing-credit.md`
- `CIV-0010-membership-and-delegated-authority.md`
- `CIV-0011-vessel-registration-certification-and-recertification.md`
- `CIV-0012-guild-insurance-and-incident-evidence.md`
- `CIV-0013-guild-loans-and-vessel-financing.md`
- `CIV-0014-contract-structure-and-risk.md`
- `CIV-0015-scrip-ledger-stores-and-capital-backing.md`

## Freight, incidents, security, routes, information, automation

- `CIV-0016-autonomous-freight-and-piracy.md`
- `CIV-0017-freight-incident-realization-and-resolution.md`
- `CIV-0018-route-security-and-piracy-response.md`
- `CIV-0019-npc-freight-realization-and-simulation-budget.md`
- `CIV-0020-route-topology-and-navigation-network.md`
- `CIV-0021-route-formation-evolution-and-logistics-inspiration.md`
- `CIV-0022-guild-information-ui-and-computer-interface.md`
- `CIV-0023-guild-communications-and-information-propagation.md`
- `CIV-0024-programmable-economic-actions-and-logistics-automation.md`
- `CIV-0025-physical-logistics-warehouses-manifests-and-custody.md`

## Player production, markets, specialization, demand, long-run economy

- `CIV-0026-player-production-and-supply-chain-participation.md`
- `CIV-0027-local-markets-catalogues-and-price-formation.md`
- `CIV-0028-economic-shocks-and-settlement-adaptation.md`
- `CIV-0029-production-chains-and-settlement-specialization.md`
- `CIV-0030-resource-potential-and-nondepleting-baseline.md`
- `CIV-0031-population-and-demand-formation.md`

## Latent simulation, persistence, reconciliation, regional flow

- `CIV-0032-economic-initialization-and-discovery-warm-start.md`
- `CIV-0033-latent-civilization-plan-and-economic-activation.md`
- `CIV-0034-economic-simulation-cadence-and-lazy-reconciliation.md`
- `CIV-0035-economic-dormancy-compaction-and-consequential-persistence.md`
- `CIV-0036-semantic-physical-capability-reconciliation.md`
- `CIV-0037-regional-trade-flow-resolution.md`
- `CIV-0038-prosperity-and-demographic-stability.md`

## Sovereignty, ownership, Bootstrap, implementation boundary

- `CIV-0039-faction-sovereignty-economic-access-and-allegiance.md`
- `CIV-0040-player-economic-principals-and-multiplayer-ownership.md`
- `CIV-0041-bellanca-onboarding-and-bootstrap-civilization-integration.md`
- `CIV-0042-civilization-implementation-and-acceptance-contract.md`
- `CIV-0043-bootstrap-province-spatial-recipe.md`

## Companion handoffs

- `docs/handoffs/CIVILIZATION-PRECOMMIT-HUMAN-GATES.md` — current deferred human/play/visual/manual gates.

## Closeout rule

The current conceptual sweep is complete. The default next action is **not** another CIV design record. Content / Experience and Implementation should decompose the accepted corpus into the smallest executable Bootstrap and runtime milestones. A new conceptual CIV record is warranted only when executable evidence exposes a genuinely unresolved product contract.
