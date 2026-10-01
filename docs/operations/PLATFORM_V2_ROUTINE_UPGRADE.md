# Platform-v2 Routine Accepted-Main Upgrade

**Status:** OPT-4 routine-operability command surface
**Authority:** operator/root for execution; read-only by default
**Product impact:** none; DR-70 remains CHANGES REQUIRED / DEFERRED

## Operator view

Use this runbook for a **normal accepted-main Platform-v2 source update** when Platform-v2 is already the production writer.

### Normal path

1. Fetch current `main`.
2. Run the read-only plan.
3. Continue only when the plan says **`READY`** with **no blockers**.
4. Run the privileged upgrade and verify the final target/writer state.

For the operator decision, the command output is machine-readable, but only these fields normally matter:

| Field | What you need to see |
| --- | --- |
| `disposition` | `READY` before execution; `COMPLETE` after execution |
| `blockers` | `[]` |
| `authority` | `V2` on the normal path |
| `target_sha` | the exact fetched/accepted `origin/main` SHA |
| `previous_head_sha` | informational: the checkout being replaced |

If the result is `BLOCKED` or `FAILED_SAFE_LEGACY`, **stop and read `blockers`**. Do not force the transition or manually reconstruct the writer choreography.

### Expected end state

A successful routine upgrade ends with:

- checkout `HEAD == target_sha`;
- Platform-v2 **active + enabled**;
- legacy **inactive + disabled**;
- production execution gate ready;
- production driver running.

Everything below documents why those checks are safe and what the command does internally.

## Purpose

A normal accepted Platform-v2 control-plane update should not require an operator to manually
reconstruct the Release-5 rollback/cutover choreography.

The routine upgrade command composes the already-accepted writer-transition primitives:

```text
V2
 -> NONE
 -> paused/reconciled LEGACY
 -> exact accepted-main checkout
 -> legacy restart/reconciliation at target
 -> regenerate activation template from current projection + portable accepted evidence
 -> NONE
 -> V2
 -> exact target/service/health proof
```

It does not create a second authority model and does not bypass
`platform_v2_operator_cutover.py`.

## Repository work snapshot vs runtime activation

The reviewed production activation SHA identifies the Platform-v2 runtime/deployment baseline. It is
not required to become stale merely because ordinary product or documentation work merges afterward.

Before a fresh standalone objective freezes repository scope, Platform-v2 may model-free fast-forward
its **tracked-clean production repository snapshot** to current GitHub `main` when all of the following
remain true:

- the current checkout descends from the reviewed activation baseline;
- current GitHub `main` is a fast-forward descendant of that checkout;
- the exact fetched `origin/main` matches the GitHub main observation;
- no Platform-v2 runtime, dependency, service, install, staging, operator-cutover, or routine-upgrade
  surface differs from the reviewed activation baseline.

The runtime gate accepts such a newer checkout only while that activation-critical tree remains
equivalent to the reviewed baseline. This lets ordinary repository work become the next worker/context
snapshot without restarting the controller after every merge.

If any activation-critical path changes, automatic repository sync refuses to move the checkout and
the normal reviewed routine-upgrade/staging boundary remains required. Dirty or divergent production
checkouts are never discarded by this sync.

## Commands

Read-only plan against fetched `origin/main`:

```bash
python3 scripts/orchestrator/platform_v2_routine_upgrade.py plan \
  --root /home/skyforge/skyforge
```

An exact target may be supplied explicitly:

```bash
python3 scripts/orchestrator/platform_v2_routine_upgrade.py plan \
  --root /home/skyforge/skyforge \
  --target-sha <accepted-origin-main-sha>
```

After the plan reports `READY`, the privileged operation is:

```bash
sudo python3 scripts/orchestrator/platform_v2_routine_upgrade.py upgrade \
  --root /home/skyforge/skyforge \
  --target-sha <accepted-origin-main-sha> \
  --execute
```

Without `--execute`, no writer transition occurs.

## Admission contract

The normal path requires:

- valid prior production activation evidence;
- clean tracked production checkout;
- exact target equal to fetched `origin/main`;
- target descending from the last accepted activation baseline;
- no unaccepted deployment/dependency-contract changes requiring unit restaging;
- Platform-v2 active/enabled and legacy inactive/disabled before the operation;
- paused durable legacy fallback state with no protected/in-flight/managed authority. A bounded
  startup/periodic `reconcile` wake with no issue/source/signal authority may be present; the routine
  path waits for the paused legacy runtime to drain that model-free noise before proceeding.

The current checkout normally must be an ancestor of the target. One tightly bounded exception exists:
a divergent checkout may be replaced when every current-only changed path is confined to non-runtime
validation plumbing:

- `.github/workflows/**`;
- `scripts/ci/**`;
- `config/ci/**`.

This exception exists for the observed DR validation-maintenance branch case. It never permits
`scripts/orchestrator/**`, deployment, dependencies, or product source to be discarded as portable
validation state.

## Protected authority

Routine upgrade does **not** guess or automatically retire protected authority.

If rollback/restart reveals a protected task, human gate, loop-risk, or other non-quiescent durable
state, routine upgrade stops with legacy authoritative and paused. The existing exact
`transfer-authority` operator primitive remains the recovery boundary.

That transfer:

- binds exact durable event, issue/PR, source comment, and signal kind;
- retires transfer authority without marking work complete;
- proves restart reconciliation;
- never fabricates V2 ingress.

After the exceptional authority condition is explicitly reconciled, run the routine plan again.

## Evidence regeneration

The new activation template is not hand-edited.

The command carries forward only previously accepted long-lived platform capability evidence from the
last valid activation record, including the explicit migration-only DR-70 waiver. It recomputes:

- target accepted-main SHA;
- legacy runtime SHA;
- current legacy projection digest;
- production activation input digest.

The DR-70 waiver remains exactly that: a platform migration/upgrade waiver. It does not convert the
product gate to PASS.

## Failure behavior

The operation preserves the existing single-writer state machine. Failures must end in a provable
legacy writer or no writer; dual-writer operation is never an accepted outcome.

A checkpoint is written before mutation. If re-cutover fails, the command attempts to restore the
previous checkout and healthy legacy writer. Emergency `rollback --execute` remains independently
available.

## Special-path boundary

The routine path intentionally refuses a target that changed:

- `deploy/orchestrator/skyforge-orchestrator.service.in`;
- `deploy/orchestrator/skyforge-orchestrator-v2.service.in`;
- `scripts/orchestrator/requirements.txt`;
- `scripts/orchestrator/stage_platform_v2_cutover.sh`.

Those changes can alter the deployed unit/dependency contract and require the explicit staging/review
path rather than an ordinary source-only upgrade.

## Acceptance

Source acceptance requires failure-injection coverage plus exact-head CI/Orchestrator Smoke.

Operational acceptance additionally requires:

1. read-only planning against a production-shaped stale checkout;
2. one authorized live routine upgrade;
3. final checkout equal to target accepted main;
4. legacy inactive/disabled;
5. Platform-v2 active/enabled;
6. production execution gate ready and driver running without the activation-SHA blocker.
