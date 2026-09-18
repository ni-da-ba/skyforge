# Platform v2 R5C20 — Production activation safety envelope

Status: **pure readiness policy only; current production activation remains blocked**

Parent migration: #767  
Tranche: #890  
Predecessor: R5C19 dormant bounded local handoff commit.

## Purpose

The existing Release-5 cutover policy proves lower-level authority-handoff conditions such
as legacy quiescence, exact accepted-main identity, rollback checkpoint availability,
hosted-runtime acceptance, ingress handoff, and privileged writer revocation.

R5C20 adds the project-level safety conditions that must also be true before anyone may
even review a production v2 activation.

It does not replace the existing cutover decision and it cannot switch writers.

## Outcomes

The policy has only two outcomes:

- `BLOCKED`
- `READY_FOR_OPERATOR_REVIEW`

There is deliberately no `ACTIVATE`, `APPROVED`, or writer-authority transition.

Every decision carries `operator_action_required = true`.

Even a completely green machine evaluation therefore stops at human/operator review.

## Required lower-level cutover state

The supplied `CutoverReadinessDecision` must already be
`READY_FOR_AUTHORITY_SWITCH`.

If it is blocked, R5C20 surfaces each existing blocker with a
`cutover readiness:` prefix rather than hiding or replacing it.

## Additional project-level blockers

All of the following must independently be accepted:

1. **Primary Windows workstation preservation audit PASS.**
   This prevents a cutover while irreplaceable local/unpushed state could still exist on
   the user's primary workstation.

2. **DR-70 migration/human-review hold explicitly cleared.**
   Machines cannot infer this from CI, merge state, or the absence of errors.

3. **Hosted Platform-v2 shadow/parity evidence accepted.**
   The production-facing v2 behavior must have adequate observed parity evidence.

4. **Hosted Platform-v2 execution path accepted.**
   Dormant worker/local-commit primitives existing in source is not equivalent to accepting
   the live hosted execution chain.

5. **Platform-v2 remote-effect path accepted for production.**
   Existing exactly-once effect mechanics are not automatically authorized merely because
   they have unit/integration coverage.

6. **Production cutover/rollback runbook rehearsal accepted.**
   The operator must have an accepted, rehearsed handoff and rollback procedure.

Any false condition produces `BLOCKED`.

## Current project posture

At R5C20 creation, current production activation is intentionally blocked.

Known blockers include:

- the primary Windows workstation preservation audit has not been completed;
- the DR-70 migration/human-review hold has not been explicitly cleared;
- PR #769, the DR-70 repair PR, remains open;
- dormant worker/local-commit code has not been activated in the hosted runtime;
- the production remote-effect path has not been accepted/activated;
- no production writer-authority cutover is being performed by this tranche.

This document does not attempt to predict when those conditions will clear.

## DR-70 authority

R5C20 does not inspect DR-70 content and does not infer gate completion from repository
mechanics.

The `dr70_migration_hold_cleared` input must be supplied as an explicit project-level
fact by the cutover process/operator.

A machine cannot set it true merely because:

- CI passed;
- a PR merged;
- the roadmap reached a terminal node;
- a human-gate visibility record exists;
- ordinary controller state is quiescent.

The existing rule remains: machines must not self-pass DR-70 human re-review.

## Relationship to dormant execution code

R5C17–R5C19 establish a tested dormant path:

`admitted task -> isolated worker -> validated local commit`

Those capabilities remain disconnected from the hosted execution path.

Their presence in source does not satisfy either:

- `hosted_execution_path_accepted`; or
- `remote_effect_path_accepted`.

Those are separate production evidence decisions.

## Relationship to writer authority

R5C20 does not import or call `advance_writer_authority(...)`.

The existing lower-level writer transition still requires:

`LEGACY -> NONE -> V2`

with an observable no-writer interval.

R5C20 sits above that mechanism and says only whether the project has enough accepted
evidence to enter operator review of such a handoff.

## Capability isolation

The activation gate has no access to:

- writer fences;
- subprocess/systemd;
- Git/GitHub mutation;
- hosted runtime execution;
- ordinary remote adapters;
- workers;
- remote effects.

It is deterministic, side-effect free, and canonically digestible for review/audit.

## Next operational boundary

After R5C20, further source-level mutation primitives are not the limiting factor.

The remaining work is evidence and integration:

- preserve/audit the primary workstation;
- resolve and explicitly clear the DR-70 migration hold when human review permits;
- collect/accept hosted parity and execution evidence;
- accept the remote-effect path;
- rehearse cutover and rollback;
- only then consider the operator-controlled writer-authority handoff.

Until those conditions are met, production v2 activation must remain blocked.
