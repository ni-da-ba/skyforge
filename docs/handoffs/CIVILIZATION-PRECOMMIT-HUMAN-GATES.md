# Civilization precommit: human-gates checklist

**Status:** Active design-gate record
**Date:** 2026-09-10
**Scope:** Human decisions that can be cleared before the dedicated civilization implementation stage

## Purpose

This checklist separates decisions that can be locked now from choices that should remain deferred until prototype evidence exists. It is intended to prevent future civilization agents from reopening settled conceptual questions while also preventing premature commitment to implementation-sensitive parameters.

## A. Gates cleared now

### A1. Civilization semantic authority — CLEARED

Decision:

- settlements possess authoritative semantic state independent of loaded Minecraft chunks;
- loaded Minecraft entities are a realization of that state, not the sole source of truth;
- unloaded settlements advance coarsely and reconcile on observation.

Canonical rule:

> Simulate physically when observed; simulate semantically when unobserved.

### A2. Guild role — CLEARED

Decision:

The Skyfarer's Guild is the principal inter-settlement commercial / aviation institution connecting the player to freight, registry, insurance, recovery, accounts, standards, routes, stores, arbitration, and professional certification.

Its fundamental commodity is trust.

### A3. Guild sovereignty boundary — CLEARED

Decision:

The Guild is powerful but not sovereign. Its authority comes from delegated settlement authority, contract, and network effects. It does not automatically own criminal law, taxation, elections, ordinary policing, local government, land, or domestic trade.

### A4. Guild origin model — CLEARED IN PRINCIPLE

Decision:

Use an early Interport Charter / Compact model rather than creation by one sovereign. The Guild grows from pilot associations, merchants, shipwrights, insurers, freight brokers, and mutual-aid organizations coordinating long-distance sky commerce.

Deferred: exact charter language, dates, founders, and named historical events.

### A5. Settlement relationship classes — CLEARED IN PRINCIPLE

Decision:

Support multiple degrees of Guild recognition rather than universal membership. Initial conceptual classes:

- full member;
- associate;
- independent / non-charter;
- hostile / excluded.

Exact labels may change during authorship, but the systemic distinction should remain.

### A6. Player opening — CLEARED

Decision:

The player begins at the crashed Bellanca B0-A, reaches civilization with registration/transponder evidence, encounters the Guild through the first claim, and thereby enters the account / Scrip / insurance / registry systems.

Preferred claim structure includes a path that lets an engineering-focused player keep and rebuild the wreck.

### A7. Certified aircraft philosophy — CLEARED

Decision:

The Guild certifies a small number of standardized aircraft families. Standardization supports parts, insurance, blueprints, performance expectations, training, and asset reuse. Custom player aircraft remain legal/playable but require more certification/insurance effort.

### A8. Contract provenance model — CLEARED

Decision:

Contracts are not all morally or legally equivalent. Preserve distinct provenance classes such as:

- Guild Contract;
- Chapter Contract;
- Private Commission;
- Discretionary Contract;
- Illicit Contract.

This enables jurisdiction disputes and chapter-level misconduct without making the entire Guild monolithic.

### A9. Routine vs exceptional contract split — CLEARED

Decision:

Routine contracts should derive from settlement/economic state. Exceptional contracts should expose politics, danger, disaster, or unusual world events and may perturb actual settlement state.

Canonical rule:

> Routine contracts sustain the economy. Exceptional contracts reveal the world.

### A10. Blueprint contract philosophy — CLEARED

Decision:

Construction/repair contracts validate desired state rather than forcing one building sequence. Preserve flexible requirement classes analogous to:

- exact block;
- block tag;
- material class;
- optional;
- functional interface.

### A11. Currency / trust separation — CLEARED

Decision:

Maintain distinct concepts:

- Guild Scrip = money;
- Guild Standing = professional trust;
- Financial Credit = banking/financial reliability.

Do not collapse these into one reputation number.

### A12. Emerald boundary — CLEARED

Decision:

Emeralds are not the serious foundation of the Guild economy. They may remain part of local Minecraft retail/trading, but Guild settlement-state economics uses its own accounting/value system.

### A13. Bank MVP scope — CLEARED

Decision:

Initial banking scope is intentionally narrow:

- account balance;
- escrow;
- insurance billing/claims;
- financial record used by other Guild systems.

Advanced finance remains deferred.

### A14. Trader career viability — CLEARED

Decision:

Players must be able to profit through spatial/informational arbitrage without owning factories.

Canonical rule:

> Manufacturers create margin through production efficiency. Traders create margin through spatial and informational differences.

### A15. Procurement source neutrality — CLEARED

Decision:

A routine contract generally cares whether valid goods arrive, not whether the player personally manufactured them. Buying, hauling, assembling, manufacturing, and automating are all valid economic paths.

### A16. Coarse autonomous logistics — CLEARED

Decision:

Autonomous logistics persists through semantic route state while unloaded. Do not require physical ticking of every aircraft across the world.

### A17. External logistics-mod audit — CLEARED AS REQUIRED RESEARCH

Decision:

During civilization implementation, explicitly audit Create Aeronautics: Automated Logistics before implementing equivalent route/station/transponder/materialization systems from scratch.

This is a required compatibility / buy-vs-build investigation, not an adoption commitment.

### A18. Lost-asset recovery institutional relationship — CLEARED

Decision:

The Guild is the civilization-facing institution for many recovery interactions. Recovery should feel like salvage, insurance, registry, and controlled restoration rather than magical save rollback.

### A19. Architecture production strategy — CLEARED

Decision:

Guild architecture is a design system, not a list of bespoke buildings. Use shared material roles, proportions, motif families, modules, interface standards, regional material resolution, and authored exceptions.

Production invariant:

> No new systemic feature may assume a unique physical asset for every instance.

### A20. Donor-asset strategy — CLEARED

Decision:

Community builds may be used as whole-build donors, module donors, systems donors, or composition references where rights permit. Search by morphology as well as literal function. Every production derivative receives a Skyforge standardization pass and rights/provenance review.

### A21. Rights classification — CLEARED

Decision:

Track donor design quality separately from legal usability:

- GREEN = explicit reuse/adaptation permission supports intended use;
- YELLOW = permission/redistribution scope needs verification;
- RED = reference-only until permission/provenance is resolved.

Do not discard excellent RED references; do not canonize weak GREEN assets merely because they are easy to license.

### A22. District ownership hierarchy — CLEARED

Decision:

Settlements should contain local vernacular and local industry in addition to Guild structures. The Guild should be visually legible without making every building Guild-owned.

### A23. Architecture standardization level — CLEARED IN PRINCIPLE

Decision:

Standardize through material roles, proportions, roof language, repeated motifs, freight/service dimensions, signage, and functional modules rather than forcing every branch to use identical blocks or blueprints.

### A24. Standard freight interfaces — CLEARED IN PRINCIPLE

Decision:

The Guild should standardize freight geometry/hardpoints strongly enough that warehouses, cranes, containers, contracts, airships, and autonomous logistics interoperate.

The provisional GFU-3 concept is preserved as a candidate standard; exact dimensions remain open.

### A25. Specialized Guild functions — CLEARED

Decision:

Guild architecture may include specialized navigation, weather, survey, recovery, underwriting, academy, signal, and exchange facilities. The institution should not collapse into repeated generic halls.

## B. Gates intentionally NOT cleared yet

These require implementation or visual evidence and should not be guessed now.

### B1. Exact economic equations — DEFER

Need prototype evidence for:

- stock consumption rates;
- production rates;
- price elasticity / response curves;
- contract generation thresholds;
- supply recovery behavior;
- unloaded simulation interval / catch-up strategy.

### B2. Exact starting economy — DEFER

Do not yet freeze:

- first claim payout;
- replacement Bellanca cost;
- starting account balance;
- basic freight margins;
- insurance premiums;
- early-game contract rewards.

These must be tuned against actual playtime and progression.

### B3. Standing and rank thresholds — DEFER

Do not yet freeze rank names, score ranges, certification requirements, or unlock thresholds.

### B4. Central Guild governance — DEFER

The Guild's non-sovereign / compact origin is settled, but the exact General Council, voting system, chapter representation, corporate participants, and constitutional mechanics belong to civilization authorship.

### B5. Charter text and historical chronology — DEFER

Write exact lore only once the civilization stage has enough settlement/political context to avoid locking incompatible history.

### B6. Certified aircraft catalog — DEFER

Bellanca B0-A remains the anchor, but do not freeze the full certified fleet until aircraft gameplay and donor/authoring tests establish useful classes.

### B7. GFU final dimensions — DEFER

The standard freight-unit idea is accepted; final dimensions must be tested against actual warehouse bays, cranes, vehicle balance, Minecraft movement clearances, and Create/Aeronautics constraints.

### B8. Guild architectural palette — DEFER UNTIL VISUAL TEST

Do not pick a final block palette from screenshots or prose. Import high-value donors into an authoring world and compare standardized variants first.

### B9. Final reusable module dimensions — DEFER

Window, bay, floor-height, hangar-door, dock, and roof standards should be derived from successful prototypes, not invented numerically in advance.

### B10. Donor acceptance — DEFER PER ASSET

The current donor list is intentionally inclusive. Actual acceptance requires:

1. in-game visual inspection;
2. architecture/composition audit;
3. compatibility check;
4. rights/provenance verification;
5. standardization test;
6. determination of whether the derivative meaningfully saves production work.

### B11. Automated schematic standardizer — DEFER

The semantic-material-role concept is promising, but do not implement a custom transformer until manual Axiom/WorldEdit prototype work demonstrates repetitive labor worth automating.

### B12. NPC granularity — DEFER

Do not decide yet whether persistent individual NPC identities are required for every economic process. Aggregate settlement state remains authoritative; authored named NPCs can be added where gameplay demands them.

### B13. Advanced banking — DEFER

Loans, interest, collateral, securities, insolvency, bank runs, or investment products require explicit gameplay justification.

### B14. Exact jurisdiction enforcement mechanics — DEFER

The political concept is accepted, but the exact mechanics for warrants, contract disputes, settlement hostility, asset seizure, fines, appeals, or Guild sanctions require civilization-stage design.

## C. Human-eye gates that can be prepared now but require manual review later

### C1. Guild architecture comparison board

Prepare/import the strongest current donor candidates and compare them after a first-pass common palette. Minimum useful comparison set:

- Warehouse Row;
- Harbour Storehouse;
- The Market;
- Koin Train Station;
- Koin Victorian Factory;
- Koin Factory Yard;
- Medieval Manor;
- House + Stable;
- one observatory/signal reference;
- Survival-Friendly Airship.

Human question:

> Do these sources converge into one institutional culture after standardization, or do they still read as unrelated community builds?

### C2. First Guild branch prototype

Build one test branch containing:

- signal / transponder landmark;
- Guild hall;
- Guild store / commodity counter;
- warehouse and freight yard;
- crane / loading apron;
- maintenance bay;
- light air dock;
- certified light aircraft.

Human question:

> Does the branch read immediately as a functioning aviation/commercial institution rather than a themed village set?

### C3. Bellanca opening playthrough

Once civilization systems exist, manually test the crash → registration → first claim → return-to-flight loop.

Human questions:

- Is the Guild introduction diegetic rather than tutorial-heavy?
- Does retaining/rebuilding the wreck feel viable?
- Does the first claim create stakes without trivializing loss?
- Is the player given meaningful agency in how they re-enter aviation?

### C4. Trader-only progression test

Run a player path that deliberately avoids industrial manufacturing.

Human question:

> Can a player become economically successful through discovery, buying, hauling, timing, route choice, and risk management alone?

### C5. Coarse-vs-physical settlement reconciliation test

Leave a settlement unloaded through meaningful economic change, return, and inspect the realized result.

Human question:

> Does the world appear to have continued without producing obvious discontinuities or fake simulation artifacts?

## D. Required civilization-stage research queue

Before implementation begins in earnest, the civilization agent should explicitly investigate:

1. Create Aeronautics: Automated Logistics source/API/behavior and compatibility implications.
2. Actual donor-schematic licenses and derivative/redistribution rights for selected production candidates.
3. Current Create/Aeronautics vessel persistence and docking APIs relevant to registry, recovery, and logistics.
4. Axiom / WorldEdit / Litematica workflow for rapid palette and morphology normalization.
5. Viable persistence schema for settlement state and autonomous routes.
6. Existing Minecraft merchant/economy mod hooks that should be integrated rather than duplicated.
7. Performance envelope for lazy economic catch-up across many settlements.

## E. Definition of ready for civilization implementation

The civilization stage may begin without reopening the cleared conceptual gates when all of the following are true:

- world/terrain authoring provides stable settlement-placement inputs;
- Minecraft backend structure-placement and persistence hooks are mature enough for settlement prototypes;
- the selected Aeronautics stack is stable enough to support route/registry experiments;
- at least one Guild branch visual prototype can be built;
- at least one settlement-state persistence prototype can be tested;
- open implementation-sensitive gates above can be resolved from evidence rather than speculation.

Until then, the records in `docs/civilization/` are the authoritative precommit for Guild/civilization direction.