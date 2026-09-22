# Skyforge Machine Roadmap — Deterministically Dressed Exploration Region

**Status:** Project-owner authorized convergence directive  
**Target:** one deterministic, persistent, current-main Skyforge production region worth handing to a human for flight/exploration review.

## Program objective
Produce exactly one reproducible production-region specimen generated from accepted Authorship/Content authority; deterministic from explicit seed/configuration; persistent across save/reload; exact-volume and stacked-volume isolated; populated by production terrain, materials/deposits, authored visible hydrology, caves/interiors, structures, and ecology; free of objective lifecycle/integration failures; and packaged so the project owner can launch directly into it, fly around it, explore it, and judge whether it is a coherent Skyforge place.

This roadmap terminates at human visual/product review. Machines must not decide whether the region is beautiful, exciting, appropriately dense, well paced, or sufficiently "Skyforge."

## Operating principle: one specimen, progressively dressed
All Phase-2 convergence work should target one canonical specimen unless a bounded isolated fixture is necessary to diagnose a distinct technical risk. Local fixtures are allowed during implementation, but acceptance must promote the result back into the canonical specimen.

The specimen must have stable machine-readable identity including specimen ID, world seed, generation/config version, relevant Authorship provenance IDs, exact Skyforge volume identity, production-region bounds, consumed systems, and accepted dressing stage. Use an implementation-owned stable identifier such as `P2_DRESSED_REGION_A`.

## Global invariants
Every milestone inherits determinism of authoritative semantic/owned physical state; idempotent realization; save/reload persistence; exact-volume and stacked/neighbor isolation; provenance identifying admitting authority, realizing system, realization location, and native-vs-Skyforge ownership; fail-closed behavior on missing authority, impossible placement, support failure, unresolved lane ownership, or unavailable accepted semantics; and no aesthetic optimization from machine metrics.

Missing authority or product judgment must produce durable `BLOCKED_AUTHORITY`, `BLOCKED_HUMAN`, or `NO_CHANGE`, never invented policy.

## DR-00 — Canonical specimen lock
Select one deterministic production-geography specimen using accepted machine-valid criteria only. Persist identity, seed/config, volume bounds, morphology/geography provenance, regeneration procedure, and launch/test entry point. Do not rank candidates by subjective beauty.

Completion artifact: `DR00_SPECIMEN_LOCK.json` or equivalent machine-readable state.

Promotion: canonical specimen regenerates deterministically and passes existing production-geography rejection.

## DR-10 — Production geography + base-metals closure
Consumes #491.

Resolve the remaining `STARTING_CLUSTER` / Content guarantee boundary without redispatching completed Iron/Copper/Zinc realization.

Determine exactly one of:
1. accepted Content authority already proves the required starting-cluster condition -> encode/prove and close;
2. accepted authority exists but needs the smallest bounded Content fixture -> dispatch that tranche;
3. a genuinely new product/design choice is required -> stop at authority/human gate.

Canonical acceptance retains the #491 requirements: accepted geological/content eligibility, zero-opportunity rejection, retained vanilla/Create-Zinc identities, exact-volume ownership, stacked-volume isolation, deterministic/idempotent placement, save/reload persistence, noncompetition with broad Create Zinc generation inside Skyforge-owned volumes, explicit Implementation-owned count/grade/accessibility, and accessible production-geography evidence where required.

Completion artifact: `DR10_MATERIALS_EVIDENCE.json` or equivalent.

Promotion: #491 is complete for this convergence path and the canonical specimen is a valid downstream hydrology consumer.

## DR-20 — Visible hydrology
Consumes #492.

Realize authored visible hydrology on the canonical specimen, covering accepted risk-equivalent channel, retained-waterbody, cascade/waterfall or equivalent vertical discharge, and boundary/edge-discharge behavior where authored intent supports them.

Prove deterministic/idempotent realization, no unauthorized leakage, save/reload persistence, cave/native-spring compatibility, no misclassification of vanilla springs as authored visible hydrology, and no unrelated chunk forcing solely for water realization.

Do not invent watershed, water-availability, biome, aesthetic, or gameplay policy.

Completion artifact: `DR20_HYDROLOGY_EVIDENCE.json` or equivalent.

Promotion: canonical specimen contains physically legible authored hydrology and passes lifecycle checks.

## DR-30 — Structure reintegration
Consumes #493.

Reintegrate generic structures against current production geography using only accepted site/support/access evidence and Content roles where available. Exercise only distinct downstream placement risks: ordinary surface, embedded/accommodated, cliff/rim/underside-adjacent, detached, and settlement/infrastructure placement only where accepted Content requirements exist.

Prove geometry/orientation admission, support/clearance, deterministic accommodation, persistence, preservation of player mutation, volume isolation, and no duplicate site/buildability/walkability authority.

At least one structure realization should enter the canonical specimen when accepted Content authority exists. If no role authority exists, emit `CONTENT_ROLE_REQUIRED` and stop rather than inventing one.

Do not touch #488/#489 or treat manual asset-compiler output as autonomous authority.

Completion artifact: `DR30_STRUCTURE_EVIDENCE.json` or equivalent.

Promotion: generic structure realization is machine-valid and usable by dressed-region assembly.

## DR-40 — Production ecology composition
Consumes #494.

Populate the canonical specimen from accepted ecology opportunity so realized ecology follows terrain, substrate, hydrology/moisture, altitude/isolation, and accepted authored opportunity. Where authority supports it, include at least two materially distinct land ecology expressions plus freshwater/riparian response.

Prove deterministic/idempotent population, persistence, exact-volume isolation, provenance, no competing duplicate ecology authority, diagnostic machine-readable counts, and no arbitrary density escalation to compensate for geography.

Counts remain diagnostic; they are not aesthetic thresholds. Do not decide final density, carrying capacity, predator pressure, population rhythm, biome beauty, or aesthetic distribution.

Completion artifact: `DR40_ECOLOGY_EVIDENCE.json` or equivalent.

Promotion: canonical region expresses accepted ecological differentiation and freshwater response without lifecycle conflicts.

## DR-50 — Integrated dressed region
Consumes #496 and is the machine exit milestone.

The same canonical specimen must compose production morphology/geography; base materials and Iron/Copper/Zinc realization; authored visible hydrology; cave/native interior lifecycle; generic structure realization; and production ecology.

Run objective integration rejection for terrain malformation/disconnection, invalid deposit eligibility/placement/ownership, escaped or invalid water, cave/interior lifecycle conflict, invalid/overwritten structure support, ecology outside accepted opportunity or duplicate population authority, ordering-dependent nondeterminism, subsystem erasure, reload drift, and stacked-volume contamination.

Required reconstruction comparison: fresh generation A vs fresh generation B vs save/reload generation A must agree on authoritative region state.

Stop once objective integration is green. Do not tune aesthetics, terrain-family balance, ecology density, structure beauty, or player pacing.

## DR-60 — Exploration review packet
Prepare the exact specimen for human inspection without machine aesthetic judgment.

Packet must include specimen ID, seed, exact main SHA, config/version, provenance IDs; exact Windows/PowerShell launch command, profile/task, world/run directory, initialization steps; exact spawn/teleport entry coordinate and required player/flight mode; a short feature-presence-derived inspection route with macro flight view, water stop, geology/interior stop, structure stop, ecology stop, and useful boundary/vertical stop; and passed machine invariants, known limitations, intentionally unresolved human/product questions, and nonblocking anomalies.

Coordinates may be chosen because required systems are present, never because the machine claims they are aesthetically best.

Completion artifact: `DR60_HUMAN_REVIEW_PACKET.md`.

## Terminal human gate — Worth flying around
At DR-60, stop autonomous Phase-2 convergence work dependent on subjective region quality and ask the project owner to review coherent-place read versus layered generators; macro landform interest from flight; physical relationship among water, geology, and ecology; procedural artifacts; meaningful exploration variation; emptiness/clutter; geography-motivated ecology; invitation to traversal/investigation; recognizably Skyforge identity; and whether it is worth flying around.

The accepted DR-50 specimen currently contains no completed canonical structure. Structure grounding and architecture are therefore not assessable on this specimen; record that absence as a review limitation and do not substitute the separate DR-30 generic native placement probe as if it were physically present in the canonical region.

Machines must not answer the subjective quality questions.

## DR-60 human review result — CHANGES REQUIRED

The project-owner flight review has now occurred. Preserve this result as product authority rather than re-inferring it from machine metrics.

Accepted observations:
- the top/surface of the island was surprisingly coherent and interesting;
- caves/interiors were also coherent and interesting, even if not yet especially dramatic.

Required repair observations:
1. tall jungle trees could reach the upper production boundary and have their tops visibly chopped off;
2. visible authored hydrology was effectively about one source block spilling directly over the side and therefore failed the intended geographic read despite satisfying machine presence checks;
3. water and lava spilled from island sides far too commonly; ordinary incidental discharge is not desired, although deliberate waterfalls/cascades may remain when compositionally justified.

This result authorizes a narrow repair loop. It does not authorize broad terrain/cave/aesthetic retuning.

## DR-65 — Canonical hydrology Authorship dependency

Consumes AUTH-0104 / #756.

The first bounded #754 Implementation attempt correctly stopped at `TASK_NO_CHANGE`: AUTH-0086 defines general visible-hydrology vocabulary, but the locked canonical specimen does not currently author a coherent retained/channel feature that Implementation is permitted to synthesize. The project-owner DR-60 findings now supply explicit product authority to repair that canonical-instance gap without relocking the specimen.

AUTH-0104 must keep the exact locked specimen identity/seed while establishing at least one deterministic multi-position retained-water footprint and/or naturalized channel path, retaining exact source/drop provenance and preserving AUTH-0085's fail-closed molten-fluid rule. No new geothermal/lava authority is implied.

Do not use DR-65 to retune morphology, caves, ecology density, beauty, pacing, or final visual identity. Automated proof remains GitHub Actions-only.

Promotion: #756 closes with accepted canonical-instance hydrology authority on `main`; only then may #754 retry.

## DR-70 — Post-platform human-review repair tranche

The original DR-60 repair tranche and subsequent hydrology-geomorphology work have already established machine-qualified canopy/fluid fixes and an actual visibly legible channel. The project-owner key-2885 review on #754 comment `5752994015` nevertheless returned **CHANGES REQUIRED / PRODUCT WORK DEFERRED**.

The accepted restart boundary after the pre-Bootstrap platform gate is therefore the later key-2885 finding, not a replay of the original DR-60 repair:

- preserve the proven real channel geomorphology;
- preserve the accepted tall-canopy, cave/interior, exterior-fluid, determinism, and exact-volume improvements;
- resolve the unacceptable ocean-biome/presentation context for intended island hydrology using already-accepted world semantics;
- improve the visible water realization and surrounding bank/substrate/material dressing so the channel reads coherently in-world;
- qualify a deterministic representative review specimen large and legible enough to demonstrate the integrated system.

The key-2885 specimen is evidence, not a sacred lock. A different deterministic specimen may be selected only when current accepted semantics naturally exercise the system better; do not fabricate hydrology merely to make a review image.

Automated proof remains GitHub Actions-only. Reuse accepted expensive evidence wherever the changed dependency surface permits it. If existing Authorship/Content/Presentation semantics are insufficient to resolve the biome/presentation context or provide a representative specimen, stop at that precise dependency instead of inventing product policy in Implementation.

Do not broadly retune morphology, caves, ecology density, pacing, final visual identity, structure architecture, or #488/#489.

Promotion: #754 reaches machine-green evidence for the post-platform material delta and registers a new qualified artifact ready for project-owner inspection.

## DR-70 human re-review gate

After the post-platform repair is machine-qualified, stop again and ask the project owner to verify specifically that:

- the hydrology is presented in an appropriate island context rather than the failed ocean-biome presentation;
- the water, channel banks/substrate, and surrounding material dressing read coherently;
- the selected specimen is representative enough to demonstrate the integrated system;
- the previously proven real channel geomorphology remains legible;
- the accepted canopy, cave/interior, exterior-fluid, determinism, and exact-volume improvements have not regressed.

Machines must not self-pass this re-review.

## Dispatch priority until DR-70 re-review
1. blockers preventing the #754 canonical repair;
2. cross-system defects directly caused/exposed by canopy-headroom or fluid/hydrology composition repair;
3. deterministic evidence needed to qualify the repaired canonical specimen;
4. cheap preparation for the second human review;
5. unrelated work only when it does not consume scarce capacity needed by the repair;
6. reserve work only when the primary path is genuinely blocked.

Do not dispatch low-priority AAL/computing, optional polish, speculative infrastructure, or unrelated capability expansion merely because Codex quota exists.

## Codex-efficiency policy
Before every worker dispatch, deterministic orchestration should assemble the exact issue, canonical specimen identity, current main SHA, relevant accepted contracts, failing/missing invariant, relevant files/tests, expected output, and explicit stop boundary.

Prefer `prepare -> one bounded meaningful worker tranche -> deterministic CI/evidence -> reconcile` over repeated tiny classify/patch loops.

A worker discovering a human gate, missing authority, already-complete state, or dependency mismatch must persist the reason and suppress equivalent redispatch until authoritative state changes.

## Progress states
Allowed convergence states: `LOCKED`, `READY`, `ACTIVE`, `VERIFYING`, `BLOCKED_AUTHORITY`, `BLOCKED_HUMAN`, `NO_CHANGE`, `ACCEPTED`. Only `ACCEPTED` promotes dependent milestones.

## Critical path
`DR-00 -> DR-10 -> DR-20 -> DR-30 -> DR-40 -> DR-50 -> DR-60 -> HUMAN REVIEW (CHANGES REQUIRED) -> DR-65 AUTH-0104 -> DR-70 -> HUMAN RE-REVIEW`

Parallel work is permitted only when it does not corrupt this dependency model or disproportionately consume worker budget.

## Definition of machine success
The orchestrator succeeds when current main contains one exact, reproducible Skyforge production region whose terrain, materials, deposits, authored water, caves/interiors, structures, and ecology coexist under accepted authority; the region survives deterministic reconstruction and reload without lifecycle conflicts; objective integration failures are green; and a human can launch directly into the specimen and fly a documented inspection route.

After a human review returns required corrections, machine success becomes narrower: implement only those explicitly authorized corrections, requalify the affected canonical dependency surfaces, and stop at the next human gate.
