# AUDIT-0040 — Authorship LUNA scope-recovery incident

## Incident

On 2026-09-10 the hosted controller correctly consumed issue #442 task authority and dispatched the bounded Authorship successor for post-review Tableland/Lobed refinement.

The LUNA worker reached handoff with exactly two modified paths:

- `docs/authorship/MORPHOLOGY_CONTROL_ATLAS.md`
- `docs/agent-state/AUTHORSHIP_STATE.md`

The controller safety-paused before commit/push because the classifier-generated LUNA `allowed_paths` contract did not cover every required output of the task. The worker was discarded safely with no remote branch or PR escape; the task authority and pending decision were preserved.

## Root cause

The controller strictly validates LUNA output against classifier-supplied `allowed_paths`. This is correct safety behavior. However, the dispatch path has no deterministic scope-completion step for outputs explicitly required by the authoritative task. In this case the task required both morphology-control changes and Authorship lane-state reconciliation, while the cached dispatch scope was narrower.

Because `/skyforge-discard-worker` intentionally preserves the cached dispatch decision, a subsequent `/skyforge-resume` can replay that same under-scoped decision while `main` is unchanged. A corrected follow-up task comment alone therefore does not guarantee fresh classification.

## Recovery contract

Issue #442 now carries an explicit execution-scope contract:

- any LUNA execution must allow `docs/authorship/MORPHOLOGY_CONTROL_ATLAS.md`;
- any LUNA execution must allow `docs/agent-state/AUTHORSHIP_STATE.md`;
- every other handoff path remains subject to the existing strict safety gate;
- if broader substantive authored/source/runtime edits are actually necessary, prefer TERRA rather than implicitly widening LUNA.

This commit intentionally advances `main` after the stale cached dispatch so `_cached_decision_still_current()` invalidates the old decision on replay. The controller must then classify the preserved #442 authority against the corrected authoritative issue context.

## Acceptance evidence

Recovery passes when live telemetry demonstrates all of the following:

1. the stale #442 cached dispatch is invalidated after this main advance;
2. a fresh Authorship dispatch is produced from the preserved task authority;
3. a worker launches with a complete scope contract (or TERRA is chosen deliberately);
4. handoff creates a controller-managed `codex/authorship-*` branch/PR without a safety pause;
5. ordinary exact-head CI proceeds under normal policy.

If fresh classification still produces an under-scoped LUNA dispatch, the next repair is a protected controller change adding deterministic lane-state scope completion before worker launch. Do not weaken `_handoff_changes()` safety enforcement.