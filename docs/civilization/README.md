# Civilization design records

**Status:** current civilization / Skyfarer's Guild conceptual sweep complete on the civilization precommit branch.

This directory contains the durable product-direction records for Skyforge civilization, the Skyfarer's Guild, markets, logistics, vessels, contracts, player industry, latent simulation, sovereignty/allegiance, onboarding, and the final implementation boundary.

Start with [`CIVILIZATION-PRECOMMIT-COMPILATION.md`](CIVILIZATION-PRECOMMIT-COMPILATION.md). It is the synthesis/read-order entry point for fresh agents. For executable decomposition, read [`CIV-0041-bellanca-onboarding-and-bootstrap-civilization-integration.md`](CIV-0041-bellanca-onboarding-and-bootstrap-civilization-integration.md) and [`CIV-0042-civilization-implementation-and-acceptance-contract.md`](CIV-0042-civilization-implementation-and-acceptance-contract.md), then inspect only the subsystem records needed by the current bounded task.

Program-level repository authority still applies: current `main`, `AGENTS.md`, the Program Charter, Validation Policy, lane state, Cross-Lane Contracts, executable source/tests, and merged history take precedence over stale summaries.

## Numbering note

The early design sweep produced parallel records with the same numeric prefix (`CIV-0003`, `CIV-0004`, `CIV-0005`). Their **full filenames are canonical**. Do not renumber them merely to make the sequence cosmetically unique.

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

## Sovereignty, ownership, Bootstrap, terminal contract

- `CIV-0039-faction-sovereignty-economic-access-and-allegiance.md`
- `CIV-0040-player-economic-principals-and-multiplayer-ownership.md`
- `CIV-0041-bellanca-onboarding-and-bootstrap-civilization-integration.md`
- `CIV-0042-civilization-implementation-and-acceptance-contract.md`

## Companion handoffs

- `docs/handoffs/CIVILIZATION-PRECOMMIT-HUMAN-GATES.md` — current deferred human/play/visual/manual gates.

## Closeout rule

The current conceptual sweep is complete. The default next action is **not** another CIV design record. Content / Experience and Implementation should decompose the accepted corpus into the smallest executable Bootstrap and runtime milestones. A new conceptual CIV record is warranted only when executable evidence exposes a genuinely unresolved product contract.