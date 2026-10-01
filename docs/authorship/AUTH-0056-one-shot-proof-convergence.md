# AUTH-0056 — One-Shot Candidate Execution and Proof Convergence

AUTH-0056 is the explicit execution boundary downstream of AUTH-0055.

AUTH-0055 creates a reviewable immutable candidate request.

AUTH-0056 executes that approved complete candidate exactly once, then evaluates the fresh result through AUTH-0054 and AUTH-0053.

It never retries automatically.

## Pipeline

    accepted original request/plan
        -> AUTH-0054 synthesis
        -> AUTH-0055 immutable candidate proposal
        -> explicit approval boundary
        -> AUTH-0056 execute candidate exactly once
            -> planner rejected
            OR
            -> fresh plan
                -> fresh AUTH-0054 synthesis
                    -> incomplete
                    OR
                    -> fresh AUTH-0053 preflight
                        -> reservation rejected
                        OR
                        -> accepted in one pass

AUTH-0056 does not compile world volumes.

Proof-backed world compilation remains downstream of an accepted fresh preflight.

## Complete proposal required

SkyIslandSupportConvergenceExecutor.executeOnce requires a complete AUTH-0055 proposal.

An incomplete proposal has no candidate request by contract and is rejected before planning.

AUTH-0056 does not attempt to repair missing certification.

## Exactly one planner invocation

For a complete proposal, executeOnce performs exactly one:

    SkyIslandArchipelagoPlanner.plan(candidateRequest)

There is no loop around this call.

There is no:

- automatic retry;
- incremental margin increase;
- reservation inflation;
- candidate reconstruction;
- seed change;
- alternative placement search outside the planner's own deterministic algorithm.

Each SkyIslandSupportConvergenceReport therefore has:

    plannerAttemptCount = 1

Calling executeOnce again is a new explicit caller action and produces a new one-attempt report.

## Planner rejection

The deterministic planner may reject a syntactically valid AUTH-0055 candidate because the fresh placement implied by larger spacing no longer fits the provisional outer group reservation.

AUTH-0056 catches deterministic planning-contract exceptions:

- IllegalArgumentException;
- IllegalStateException.

It records stable diagnostic evidence:

- exception type;
- message.

The terminal outcome is:

    PLANNER_REJECTED

No fresh plan exists.

Therefore:

- fresh synthesis is absent;
- fresh preflight is absent.

AUTH-0056 does not catch arbitrary RuntimeException subclasses as planner rejection. Unexpected implementation defects remain visible as failures.

## Fresh synthesis

If planning succeeds, AUTH-0056 immediately runs:

    SkyIslandSupportReservationRequirementSynthesizer.synthesize(
        freshPlan,
        registry)

This is a new AUTH-0054 evaluation over the fresh exact member coordinates/descriptors.

The old synthesis is not inherited.

### Fresh synthesis incomplete

A provider may have certified the original exact descriptor but decline certification for a fresh descriptor.

The canonical proof uses a position-sensitive provider:

- original members are near the group center and certify;
- proof-driven pair spacing moves the fresh members outward;
- the provider returns no support certificate at the fresh positions.

The terminal outcome is:

    FRESH_SYNTHESIS_INCOMPLETE

The report retains:

- fresh plan;
- incomplete fresh synthesis.

Fresh preflight is not run, because the synthesis stage has already established that a complete proof-backed reservation recommendation is unavailable.

## Fresh reservation preflight

If fresh synthesis is complete, AUTH-0056 runs:

    SkyIslandSupportReservationPreflight.evaluate(
        freshPlan,
        registry,
        candidateVerticalReservation)

This is a fresh AUTH-0053 evaluation.

No old preflight result is reused.

### Fresh reservation rejection

A provider may remain certifiable after re-planning while requiring more support than the candidate reservations provide.

The canonical proof uses a position-sensitive provider:

- original support radius = 200;
- fresh spacing moves members outward;
- fresh support radius = 500;
- candidate member reservation remains near the old requirement.

Planning itself succeeds because placement containment uses the declared candidate reservation.

Fresh AUTH-0054 discovers the larger proof requirement.

Fresh AUTH-0053 then rejects the candidate reservation.

The terminal outcome is:

    FRESH_RESERVATION_REJECTED

The report retains:

- fresh plan;
- complete fresh synthesis;
- rejected fresh preflight.

## Accepted in one pass

If:

- candidate planning succeeds;
- fresh AUTH-0054 is complete;
- fresh AUTH-0053 admits every member/group reservation;

the terminal outcome is:

    ACCEPTED_ONE_PASS

The report retains all three fresh artifacts:

- fresh plan;
- fresh synthesis;
- fresh preflight.

This is the first AUTH-0056 outcome that may proceed to proof-backed world compilation.

AUTH-0056 itself does not perform that compilation.

## Immutable terminal report

SkyIslandSupportConvergenceReport binds:

- original AUTH-0055 proposal;
- terminal outcome;
- optional planner failure;
- optional fresh plan;
- optional fresh synthesis;
- optional fresh preflight.

Its constructor enforces outcome-specific artifact presence.

### PLANNER_REJECTED

Required:

- planner failure.

Forbidden:

- fresh plan;
- fresh synthesis;
- fresh preflight.

### FRESH_SYNTHESIS_INCOMPLETE

Required:

- fresh plan;
- incomplete fresh synthesis.

Forbidden:

- planner failure;
- fresh preflight.

### FRESH_RESERVATION_REJECTED

Required:

- fresh plan;
- complete fresh synthesis;
- rejected fresh preflight.

Forbidden:

- planner failure.

### ACCEPTED_ONE_PASS

Required:

- fresh plan;
- complete fresh synthesis;
- admitted fresh preflight.

Forbidden:

- planner failure.

This prevents terminal labels from drifting away from the evidence they claim.

## No primary/full-volume compilation

Like AUTH-0054/0055 proof planning, AUTH-0056 convergence does not invoke primary/full suspended-volume or world-volume compilation.

Provider support certification can still use its accepted analytical support seam, including lightweight secondary-factor contributions where applicable.

World-volume compilation remains a later action after ACCEPTED_ONE_PASS.

## Determinism

For identical:

- complete AUTH-0055 proposal;
- morphology provider registry;

repeated explicit executeOnce calls produce equal convergence reports.

Each call remains one attempt.

Determinism is not an excuse for hidden retries.

## World-catalog facade

SkyIslandWorldCatalogCompiler exposes:

    executeSupportAwareReplanOnce(proposal, registry)

This is a convenience boundary only.

It delegates to SkyIslandSupportConvergenceExecutor and does not compile a world catalog.

## Visual evidence atlas

AUTH-0056 uses a 1280x720 16:9 atlas.

Six panels are planned:

- ACCEPTED_ONE_PASS;
- PLANNER_REJECTED;
- FRESH_SYNTHESIS_INCOMPLETE;
- FRESH_RESERVATION_REJECTED;
- STAGE_ARTIFACT_MATRIX;
- ONE_SHOT_NO_RETRY.

The terminal panels display which fresh artifacts exist and the key proof/reservation numbers.

The stage matrix makes absence meaningful rather than treating missing files as an error.

The no-retry panel distinguishes:

    one executeOnce call = one planner attempt

from:

    caller explicitly invoking executeOnce twice

which is two separate reports, not an internal retry loop.

## Acceptance gate

Reject AUTH-0056 if:

- incomplete AUTH-0055 proposals are executed;
- candidate planning can occur more than once inside executeOnce;
- planner rejection triggers automatic candidate mutation;
- planner rejection triggers automatic margin inflation;
- fresh synthesis is skipped after successful planning;
- old AUTH-0054 synthesis is reused for the fresh plan;
- preflight runs after incomplete fresh synthesis;
- old AUTH-0053 preflight is reused;
- reservation rejection is labeled accepted;
- accepted outcome lacks a complete fresh synthesis;
- accepted outcome lacks an admitted fresh preflight;
- unexpected runtime defects are swallowed as planner rejection;
- world-volume compilation is hidden inside convergence execution;
- Minecraft or NeoForge types enter the contract.

## Next milestone

AUTH-0056 establishes deterministic one-shot convergence.

A likely AUTH-0057 direction is **accepted-convergence proof-backed compilation handoff**.

It should consume only an ACCEPTED_ONE_PASS report and:

1. verify the fresh plan/preflight provenance;
2. invoke the existing proof-backed world compilation exactly once;
3. bind the resulting AUTH-0052 support bundle to the accepted convergence report;
4. preserve explicit failure if compilation contradicts the accepted preflight.

It must not accept planner-rejected, incomplete, or reservation-rejected reports.
