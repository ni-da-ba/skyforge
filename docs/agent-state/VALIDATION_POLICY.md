# Skyforge Validation and Evidence Economy Policy

**Status:** Canonical program validation policy  
**Updated:** 2026-09-07 (America/Chicago)

## Objective

Skyforge should maximize defect-detection value per unit of developer/CI/runtime cost.

Correctness and robustness remain mandatory. The project must also avoid **evidence saturation**:
repeating expensive evidence after the material uncertainty has already been retired.

The governing question for every expensive test is:

> What materially distinct failure mode can this test still falsify?

If no concrete answer exists, prefer moving to the next integration risk.

## Core rule: exhaustive cheap evidence, representative expensive evidence

### Tier 0 — exhaustive static / deterministic contract evidence

Run broadly and automatically wherever cheap.

Examples:

- compilation and unit tests;
- schema/manifest/source identity;
- deterministic descriptor compilation;
- exact IDs/seeds/scales/provider identity;
- support/bounds/topology;
- deterministic digests;
- pure-translation and ownership invariants;
- cheap parameter/property checks.

For finite review corpora, Tier 0 should normally remain exhaustive.

### Tier 1 — routine integration regression

Automatically recurring and merge-gating.

Examples:

- ordinary repository CI;
- focused integration smoke tests;
- retained compatibility tests that are reasonably fast and stable.

Operational target:

- routine jobs should normally complete within roughly **15 minutes**;
- a recurring job exceeding roughly **20 minutes** requires an explicit reason, optimization, split, or
  promotion to a deliberate characterization tier.

These are governance triggers, not claims that a technically necessary 21-minute test is invalid.

### Tier 2 — milestone characterization

Deliberately triggered and representative.

Examples:

- Minecraft world preparation of large authored volumes;
- save/reload or actual-client reopen;
- realistic exploration-scale performance;
- representative multi-family/provider lifecycle runs;
- high-cost compatibility characterization.

Default target:

- choose **risk-equivalence representatives**, not every parameter point;
- keep an individual deliberate characterization within roughly **30 minutes** where technically
  practical;
- runs expected to exceed that budget should be manual-only and must state the uncertainty they retire.

### Tier 3 — human / soak / release matrix

Manual, scheduled, or release-gated rather than normal PR fan-out.

Examples:

- large visual atlases;
- long multiplayer/server soak;
- exhaustive cross-seed presentation review;
- broad release-candidate matrices;
- long stress/performance runs.

Tier 3 may be expensive because it is deliberately infrequent.

## Risk-equivalence sampling

Two specimens may share one expensive evidence class when:

- they traverse the same backend/runtime path;
- cheap deterministic evidence proves their parameter/geometry differences;
- the expensive test is validating lifecycle behavior rather than the parameter value itself;
- no known defect suggests that the omitted parameter dimension changes the runtime risk.

A representative set should cover:

- each materially distinct backend path;
- boundary values;
- worst observed footprint/vertical span/work estimate where relevant;
- known problematic families/cases;
- at least one ordinary/non-pathological case;
- previously failed cases after repair.

Do not sample solely for convenience. Document why the selected specimens span the risk.

## Automatic widening rule

Sampling is not a one-way reduction.

If any representative reveals:

- a parameter-dependent failure;
- nondeterminism;
- persistence/reopen disagreement;
- family/provider-specific lifecycle behavior;
- scale-specific performance pathology;
- a defect whose domain is not yet bounded;

then expand the expensive matrix to the affected equivalence class until the failure domain is
understood.

This rule preserves robustness while avoiding default Cartesian-product testing.

## Evidence portability across synchronization

Expensive evidence does **not** automatically expire because main moved.

A prior expensive characterization may remain valid after synchronization when Audit/producer can show:

1. the code/config/contracts governing the tested behavior are unchanged;
2. intervening changes are orthogonal (for example docs/state or unrelated lane work);
3. cheap exact-head CI on the synchronized candidate is green;
4. no retained compatibility contract affecting the evidence changed.

In that case:

~~~text
reuse prior expensive evidence
+ run synchronized cheap/current-head gates
+ record why evidence remains applicable
~~~

Do not rerun a 20–50 minute world/client characterization merely to obtain a newer timestamp after an
orthogonal documentation merge.

Rerun when the relevant dependency surface changed or the old result cannot be shown portable.

## Diagnostic versus acceptance runs

A stale/diverged branch may run an expensive test when the run is explicitly diagnostic and the result
will guide a repair.

A final acceptance characterization should normally be run from a current or demonstrably equivalent
integration candidate.

Never confuse:

~~~text
DIAGNOSTIC PASS ON STALE HEAD
~~~

with:

~~~text
MERGE-READY ACCEPTANCE EVIDENCE
~~~

Evidence portability may bridge the two only under the rule above.

## Synchronization economy

A producer branch being numerically behind `main` is not by itself a defect.

Synchronize/recompose when one of these is true:

- an intervening change touches the producer's dependency or contract surface;
- GitHub reports a real merge conflict that must be resolved;
- the branch is approaching an acceptance/merge boundary;
- Audit identifies stale state that materially changes the claim being tested;
- a relevant retained regression failed on newer main.

Do **not** merge/rebase current main into an active branch merely to erase a behind-count when the
intervening commits are orthogonal docs/state or unrelated lane work. Preserve portable expensive
evidence and continue the bounded feature until the next meaningful integration boundary.

At final acceptance, synchronize once, run cheap exact-head/current-contract gates, and rerun only the
expensive evidence whose dependency surface actually changed.

## Branch-convergence rule

If branch synchronization/test maintenance begins consuming more effort than the feature:

1. stop extending scope;
2. checkpoint the bounded valid delta;
3. recompose onto current main;
4. use portable expensive evidence where justified;
5. run required cheap exact-head gates;
6. merge the smallest coherent milestone;
7. continue new work from the new boundary.

Do not make perpetual synchronization part of the feature.

## Integration-first bias

After a subsystem's standalone risks are credibly retired, the next preferred test is usually the
first integration with another major system.

For production world work, risk increasingly lives in interactions such as:

~~~text
morphology
x geology/materials
x hydrology
x structures/caves
x ecology
x civilization
x Minecraft lifecycle
~~~

Do not completely exhaust an isolated parameter corpus before discovering whether the next system
composes with it.

## Human-gate economy

Human review should answer qualitative questions machines cannot resolve:

- visual identity and grandeur;
- traversal feel;
- gameplay pacing;
- comprehensibility;
- music/listening quality;
- whether the world feels sparse, inhabited, threatening, coherent, or fun.

Machines should prefilter obvious defects but should not replace human judgment with an ever-growing
proxy-metric matrix.

## SF-IMP-0083 application

The project owner has approved layered/risk-driven certification for the current morphology tranche.

### Keep exhaustive

All remaining AUTH-0083 built-in specimens should retain cheap deterministic evidence for:

- exact member ID;
- family, seed, scale, provider;
- deterministic compilation;
- exact/tight support and bounds;
- build-range/translation feasibility;
- relevant morphology/roughness diagnostics;
- deterministic geometry identity/digest where cheap.

### Reduce full lifecycle

Do **not** require all 20 remaining parameter specimens to repeat full Minecraft
prepare/persistence/actual-client-reopen merely because they exist.

Select a representative full-runtime set that, together with the already accepted SMALL/seed-skyforge
five-family carriers, covers:

1. every built-in family at least once at a new scale;
2. seed-min, seed-zero, and seed-skyforge somewhere in the representative set;
3. MEDIUM scale across the family set;
4. at least two LARGE stress representatives selected from the largest footprint / largest vertical
   span / highest observed work cases;
5. both Massif and Tableland because #267/#283 identify them as active quality questions;
6. any previously failing/pathological fixture needed to prove a specific repair.

A natural starting target is roughly **7 full-runtime new specimens rather than 20**. The exact seven
should be selected from the cheap support/profile evidence, not hard-coded before that evidence is
read.

This is expected to reduce the heavy new runtime matrix by about two thirds while preserving:
- full deterministic coverage;
- all family coverage;
- all seed classes;
- all relevant scales;
- worst-case stress coverage;
- targeted expansion if a representative fails.

### Human morphology review

Human #214 review need not wait for 20 repeated persistence proofs.

Use:
- the complete cheap/offline morphology corpus for broad seed/scale comparison;
- representative in-engine Minecraft cases for cross-checking presentation/traversal;
- targeted additional in-engine cases only where #267/#283 or human inspection reveals uncertainty.

The exhaustive 20-member in-engine atlas may remain a later/manual regression or presentation asset if
useful; it should not block the next production-world system once the carrier risk is retired.

## Documentation/state fast path

Routine CI should distinguish repository prose/state changes from executable changes.

- Changes confined to `docs/**` and selected top-level documentation files use a lightweight integrity
  gate rather than provisioning Java/Gradle and regenerating the complete evidence corpus.
- Code, assets, build configuration, workflow definitions, and all other executable-affecting paths
  continue through the full CI suite.
- If change-impact classification is uncertain, fail safe to full CI.
- Documentation-only validation must still reject merge-conflict markers and require the canonical
  program/state entry points to exist.

This reduces state-ledger/Audit/design-document merge latency without weakening executable coverage.

## Retained regression trigger policy

Accepted historical evidence remains available, but its trigger should match the risk it protects.

- **Obsolete milestone-specific suites** that are superseded by newer aggregate coverage remain
  `workflow_dispatch`-only. They must not create skipped checks on every modern PR.
- **Large showcase / actual-client / exploration-performance suites** run on a deliberate/manual basis
  and on a low-frequency retained schedule rather than on every NeoForge PR.
- **Accepted Content compatibility waves** retain pre-merge PR triggers for their own fixture, policy,
  dependency-pin, or adapter files.
- Broad cross-cutting files such as the shared NeoForge build script or central mod registration do
  not fan every retained compatibility proof out across every PR synchronization. Those changes run
  the affected retained wave once after they land on `main`, while normal CI remains the pre-merge
  gate.
- A producer may still deliberately invoke any retained suite when a milestone's dependency surface
  or prior failure makes that evidence relevant.

This preserves direct pre-merge protection for the code that owns each contract while removing
historical-capability-complete fan-out from ordinary development.

Issue #319 tracks the first repository implementation of this policy.

## Audit enforcement

Audit should flag:

- repeated expensive runs with no named new uncertainty;
- unnecessary heavy reruns after orthogonal main movement;
- growing Cartesian matrices over one backend path;
- acceptance branches spending more effort synchronizing/testing than implementing;
- isolated-system polishing delaying first integration.

Audit may recommend splitting/narrowing gates under this policy. A human decision is required only when
the proposed change alters a substantive product/acceptance philosophy not already covered here.
