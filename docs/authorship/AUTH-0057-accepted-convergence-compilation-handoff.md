# AUTH-0057 — Accepted-Convergence Proof-Backed Compilation Handoff

AUTH-0057 is the first world-compilation boundary downstream of AUTH-0056 convergence.

It consumes only an AUTH-0056 report whose terminal outcome is:

    ACCEPTED_ONE_PASS

and compiles that exact fresh plan into the existing AUTH-0052 proof-backed world support bundle.

It does not re-plan or synthesize another candidate.

## Pipeline

    AUTH-0055 candidate
        -> AUTH-0056 executeOnce
            -> ACCEPTED_ONE_PASS
                -> AUTH-0057 reproduce accepted fresh preflight
                    -> exact fresh plan
                    -> compileProofBacked exactly once
                    -> fully certified AUTH-0052 support bundle
                    -> accepted convergence compilation binding

Every non-accepted AUTH-0056 outcome stops before primary/world compilation.

## Accepted-only input

SkyIslandAcceptedConvergenceCompiler.compileOnce requires:

    convergence.accepted() == true

Therefore these outcomes are rejected immediately:

- PLANNER_REJECTED;
- FRESH_SYNTHESIS_INCOMPLETE;
- FRESH_RESERVATION_REJECTED.

The handoff never attempts to repair them.

## Registry/preflight reproduction

An AUTH-0056 report records an accepted fresh preflight, but it does not serialize a provider-registry identity.

AUTH-0057 therefore verifies the supplied registry by reproducing:

    SkyIslandSupportReservationPreflight.evaluate(
        exactFreshPlan,
        suppliedRegistry,
        candidateVerticalReservation)

The reproduced preflight must equal the preflight stored in the accepted convergence report exactly.

This checks more than the final admitted boolean.

It revalidates:

- root seed;
- exact member descriptor seeds;
- provider/morphology identity;
- support envelopes;
- member reservation requirements;
- group requirements;
- vertical reservation;
- admitted state.

If the supplied registry resolves the same provider ID to a different analytical support contract, the reproduced preflight differs and AUTH-0057 rejects before primary compilation.

## Why preflight is reproduced before compilation

AUTH-0056 may have been created under a registry snapshot that is no longer the registry supplied to AUTH-0057.

Without reproduction, an accepted report could be compiled under materially different provider semantics.

AUTH-0057 makes that mismatch explicit.

It does not infer registry equivalence from provider IDs alone.

## Exact fresh plan

AUTH-0057 uses only:

    convergence.freshPlan()

It does not use:

- original plan;
- original request placement;
- a newly reconstructed request;
- another re-plan;
- alternate seeds.

Candidate vertical reservation comes from:

    convergence.proposal().candidateVerticalReservation()

No other vertical value is substituted.

## Proof-backed compilation

After accepted preflight reproduces exactly, AUTH-0057 invokes:

    SkyIslandWorldCatalogCompiler.compileProofBacked(
        exactFreshPlan,
        suppliedRegistry,
        candidateVerticalReservation)

exactly once.

compileProofBacked itself:

1. re-runs AUTH-0053 preflight as its internal safety gate;
2. compiles provider morphology;
3. emits AUTH-0052 world-volume support certificates;
4. requires the resulting bundle to be fully certified.

AUTH-0057 does not add another world compilation around this call.

## Explicit compilation contradiction

Accepted preflight proves reservation/support admission.

It does not prove that provider primary compilation cannot fail for an unrelated implementation defect.

AUTH-0057 therefore treats a runtime failure from compileProofBacked after preflight reproduction as an explicit compilation failure:

    AUTH-0057 proof-backed compilation failed
    after accepted preflight reproduced

The original cause is retained.

There is no fallback provider, retry, or silent downgrade to an ordinary catalog.

## Exact-once primary compilation proof

The canonical test wraps one accepted built-in provider with a primary-compilation counter.

Before AUTH-0057:

    AUTH-0056 accepted convergence
    primary compile count = 0

AUTH-0056 uses analytical support proof only and does not compile primary morphology.

After one AUTH-0057 compileOnce:

    primary compile count = 1

For the one-member fixture, this proves one proof-backed world compilation invocation.

Calling compileOnce again is a second explicit caller action and raises the count to 2.

There is no internal retry loop.

## Accepted convergence compilation binding

SkyIslandAcceptedConvergenceCompilation binds:

- the accepted AUTH-0056 convergence report;
- the reproduced fresh preflight;
- the fully certified AUTH-0052 support bundle.

Its constructor revalidates the binding.

### Outcome

The convergence report must still be ACCEPTED_ONE_PASS.

### Preflight

The reproduced preflight must equal the accepted report preflight exactly and remain admitted.

### Certification

The world support bundle must be fully certified.

### Root and count

The compiled catalog must have:

    catalog.rootSeed == freshPlan.rootSeed

and:

    catalog.volumeCount == freshPlan.totalMemberCount

### Plan-order world identities

For every fresh group/member in deterministic plan order, AUTH-0057 reconstructs the expected world-volume ID:

    archipelago root seed
    group identifier
    group ordinal
    member ordinal
    exact member descriptor geometry seed

The compiled catalog volume at the same plan-order position must carry that exact ID.

Every such volume must also have a support certificate.

Thus the handoff is bound to the admitted fresh hierarchy, not merely to a catalog with a matching count.

## Non-accepted rejection proof

The canonical planner-rejected AUTH-0056 fixture is passed into AUTH-0057 with a counted provider.

AUTH-0057 rejects before compilation.

The primary compile count remains:

    0

No primary/world compilation is attempted for a non-accepted convergence report.

## Registry mismatch proof

The canonical accepted report is produced under the standard MASSIF provider.

AUTH-0057 is then supplied another provider under the same stable provider ID whose analytical horizontal support is deliberately changed.

The fresh preflight no longer reproduces exactly.

AUTH-0057 rejects before primary compilation.

The counted primary compile remains:

    0

This proves provider ID equality is not treated as proof-contract equality.

## Primary failure proof

A custom provider exposes a valid analytical support certificate but intentionally throws from compilePrimary.

AUTH-0056 reaches ACCEPTED_ONE_PASS because support/reservation proof is valid.

AUTH-0057 reproduces that accepted preflight successfully.

The subsequent proof-backed compilation throws.

AUTH-0057 surfaces the contradiction explicitly and preserves the original primary failure as the cause.

## Determinism

For identical:

- accepted convergence report;
- provider registry;

two explicit compileOnce calls produce equal:

- reproduced preflight;
- world catalog volumes;
- support certificates.

Each call performs one primary compilation for the one-member canonical fixture.

Again, repeated caller actions are not internal retries.

## World-catalog facade

SkyIslandWorldCatalogCompiler exposes:

    compileAcceptedConvergenceOnce(convergence, registry)

This delegates to SkyIslandAcceptedConvergenceCompiler.

The facade does not:

- re-plan;
- create a new AUTH-0055 proposal;
- run AUTH-0056 again;
- retry compilation.

## Visual evidence atlas

AUTH-0057 uses a 1280x720 16:9 atlas.

The canonical panels are:

- ACCEPTED_HANDOFF;
- NON_ACCEPTED_BLOCKED;
- REGISTRY_MISMATCH_BLOCKED;
- PRIMARY_FAILURE_EXPLICIT;
- PLAN_ID_BINDING;
- EXACT_ONCE_COMPILE.

The atlas displays:

- convergence outcome;
- preflight reproduction state;
- primary compile count;
- catalog volume count;
- certified volume count;
- exact plan/world-ID correspondence;
- explicit failure boundary where applicable.

The atlas is proof/handoff evidence rather than aesthetic morphology evidence.

## Acceptance gate

Reject AUTH-0057 if:

- any non-accepted convergence outcome can compile;
- the supplied registry is not checked against the accepted fresh preflight;
- provider ID equality alone is treated as registry equivalence;
- a different fresh plan can replace the accepted one;
- a different vertical reservation can be substituted;
- proof-backed compilation occurs more than once inside compileOnce;
- compilation failure silently falls back to ordinary compilation;
- the resulting support bundle is partially certified;
- catalog root/count can diverge from the accepted fresh plan;
- world-volume identities can diverge from accepted group/member identity;
- support certificates can be absent from accepted compiled volumes;
- the handoff re-plans or retries;
- Minecraft or NeoForge types enter the contract.

## Next milestone

AUTH-0057 completes the proof-backed world-compilation handoff for accepted convergence.

A likely AUTH-0058 direction is **accepted compiled-world publication/admission identity**.

It should define a stable backend-neutral publication object that distinguishes:

- accepted convergence identity;
- compiled world-catalog identity;
- support certificate set;
- publication/version identity;

and makes clear when a proof-backed compiled regional world is safe to expose to downstream backend adapters.

It should not yet perform Minecraft BlockState mapping or terrain mutation.
