# Platform v2 R5C26 — Privileged operator cutover / rollback

**Status:** production-proven; initial live Platform-v2 writer handoff completed on 2026-09-18
**Parent migration:** #767
**Tranche:** #903
**Predecessor:** R5C25 at **7a878338a8a8b00c4120557f51aec002e2234a4a**

This is the privileged writer-transition runbook. It is **not** the normal accepted-main source-upgrade runbook; routine upgrades use **PLATFORM_V2_ROUTINE_UPGRADE.md**.

**Operator rule:** if the task is only “deploy the latest accepted `main`,” stop here and use the routine-upgrade runbook. This document is for writer transitions, rollback, and explicit protected-authority recovery.

## Operator quick map

| Situation | Use |
| --- | --- |
| Normal accepted-main Platform-v2 source update | platform_v2_routine_upgrade.py / PLATFORM_V2_ROUTINE_UPGRADE.md |
| Initial/re-staged privileged LEGACY → V2 cutover | platform_v2_operator_cutover.py preflight, then cutover --execute |
| Emergency V2 → LEGACY restoration | platform_v2_operator_cutover.py rollback --execute |
| One exact protected legacy authority during upgrade | transfer-authority |
| Two or more exact protected legacy authorities | transfer-authority-batch --batch-manifest ... |
| Matching terminal non-executed V2 ownership after legacy transfer | retire-v2-authority |

All mutating service transitions are root/operator actions. Read-only/default paths do not change writer authority.

### What to read in operator output

The CLI emits structured JSON so automation can consume the same result. For a human operator, read these fields first and ignore the rest unless diagnosing a failure:

| Field | Meaning |
| --- | --- |
| `disposition` | whether the requested step is ready, complete, or blocked |
| `blockers` | the reason to stop; an empty list means no reported blocker |
| `authority` | which writer currently owns mutation authority: `V2`, `LEGACY`, or `NONE` |
| `accepted_main_sha` / `target_sha` | the exact repository revision involved |
| `events` | diagnostic transition history; normally not needed for the go/no-go decision |

**Do not infer success from a long event list.** The operator decision comes from `disposition`, `blockers`, writer authority, and the exact SHA.


## Non-negotiable authority invariant

Forward transition:

~~~text
LEGACY -> NONE -> V2
~~~

Rollback:

~~~text
V2 -> NONE -> LEGACY
~~~

No accepted path permits dual writers. Any ambiguous or failed transition must end with one provable writer or NONE.

## 1. Unit staging

**scripts/orchestrator/stage_platform_v2_cutover.sh** stages:

- **skyforge-orchestrator.service** — legacy rollback writer with startup-reconciliation guard;
- **skyforge-orchestrator-v2.service** — Platform-v2 writer bound to explicit activation evidence.

Activation evidence defaults to:

~~~text
/var/lib/skyforge-orchestrator/platform-v2-activation.json
~~~

Staging requires an exact accepted-main SHA and clean tracked checkout. It refuses to stage while v2 is active, installs unit definitions, runs only daemon-reload, leaves legacy running, and leaves v2 disabled. **Staging does not transfer writer authority.**

The state directory is root-owned and setgid to the service group; finalized activation evidence is mode 0640. Secrets remain outside this evidence.

## 2. Forward cutover

The reviewed activation template contains the complete R5C20 ProductionActivationInput, its canonical digest, accepted-main SHA, and explicit production-execution request. It is a template, not final activation evidence.

Read-only preflight proves:

- production activation input recomputes exactly;
- checkout HEAD equals accepted main and tracked worktree is clean;
- legacy is loaded/active/enabled;
- v2 is loaded/inactive/disabled;
- legacy unit includes --require-startup-reconcile-success;
- v2 unit enables production execution and points at the exact evidence path.

Privileged execution then:

1. repeats preflight;
2. writes a timestamped non-secret checkpoint;
3. stops legacy and proves both writers inactive;
4. disables legacy boot activation;
5. records the explicit NONE boundary;
6. atomically finalizes activation evidence with legacy revocation observed;
7. recomputes the accepted production gate against the actual checkout;
8. enables/starts v2;
9. proves localhost health, production execution, running driver, and exact gate digest.

A stale/blocked gate cannot start v2. Failure recovery never intentionally permits dual writers.

## 3. Checkpoint and rollback

Pre-mutation checkpoints live under the ignored local path:

~~~text
.skyforge-platform-v2/operator-evidence/
~~~

They contain non-secret metadata/hashes for service observations, activation template, legacy durable state, v2 budget state, readable environment-file identity, and loaded unit fragments. Environment/config contents are not copied.

Emergency rollback deliberately does **not** require a still-valid activation template:

1. stop and disable v2;
2. prove both writers inactive;
3. record NONE;
4. retire finalized v2 activation evidence;
5. enable/start legacy;
6. require successful startup reconciliation before legacy is authoritative.

Legacy --require-startup-reconcile-success exits before resume_pending() on reconciliation failure. If legacy cannot prove healthy reconciliation, it is stopped again and the safe result is NONE.

## 4. Protected authority during upgrades

Rollback/redeploy can temporarily restore paused legacy state that rediscovers source comments already owned by Platform-v2. Protected authority must never be guessed, completed, or discarded merely to make an upgrade proceed.

### 4.1 One exact legacy authority — transfer-authority

The singleton primitive preserves its original strict behavior. The operator supplies exact:

- durable event_key;
- issue/PR number;
- source comment ID;
- protected signal_kind (default task).

Preflight requires paused healthy legacy, inactive v2, no legacy worker/decision, one exact matching protected authority, and no conflicting protected authority.

Execution performs:

~~~text
LEGACY(paused) -> NONE -> retire exact authority for V2 transfer -> LEGACY(paused + reconciled)
~~~

At the NONE boundary the operator updates both legacy state.json and state.json.bak while preserving file ownership/mode. The event is removed from pending ownership and added to retired_event_keys; its transfer record preserves source identity plus signal-text digest and has completed=false. It is **not** added to completed_authority_event_keys.

After legacy restarts, reconciliation must prove the transferred key remains retired, remains absent from completed authority, and no protected authority reappears. The command never fabricates Platform-v2 ingress; later V2 ownership still requires a real signed GitHub delivery or a newer signed task revision.

### 4.2 Multiple exact legacy authorities — transfer-authority-batch

Use the atomic batch primitive when concurrent v2 work causes legacy rollback to rediscover more than one protected authority. Singleton semantics remain unchanged.

The manifest is explicit and complete:

~~~json
{
  "schema_version": 1,
  "authorities": [
    {
      "event_key": "sha256:<exact-event-id>",
      "issue_number": 123,
      "source_id": "456789",
      "signal_kind": "task"
    },
    {
      "event_key": "sha256:<exact-event-id-2>",
      "issue_number": 124,
      "source_id": "456790",
      "signal_kind": "task"
    }
  ]
}
~~~

Preflight succeeds only when that enumerated set exactly equals the entire pending protected legacy-authority set. Missing, extra, duplicate, mismatched, completed, or incompatible identities fail closed.

Execution uses one writer transition:

~~~text
LEGACY(paused) -> NONE -> retire the exact full batch atomically -> LEGACY(paused + reconciled)
~~~

All records remain RETIRED_FOR_PLATFORM_V2_TRANSFER with completed=false. Ordinary non-protected pending events are untouched. Replay is idempotent only for the same already-transferred full batch after post-transfer health verifies.

### 4.3 Terminal non-executed V2 ownership — retire-v2-authority

After the exact legacy transfer is durable, this narrower primitive may release matching Platform-v2 ownership only when admission is terminal and **never executed** (BLOCKED, RECLASSIFY, or NOT_DISPATCH).

It requires exact matching event/provenance/plan/admission, consume_attempt=false, no frozen task/attempt/worker, and no worker run, concurrency history, scheduler record, handoff commit, ordinary execution pipeline, or hosted completion.

It writes durable PREPARED evidence, retires only the exact V2 event, removes only its non-executed admission/plan, then records COMPLETE with completed=false and executed=false. Task/classifier provenance remains durable.

Exceptional recovery order:

~~~text
legacy transfer-authority
  -> retire-v2-authority
  -> routine upgrade/cutover
~~~

## 5. DR-70 boundary

DR-70 remains **CHANGES REQUIRED / DEFERRED**.

The accepted migration/upgrade path carries an explicit migration-only DR-70 waiver through reviewed activation evidence. That waiver exists only to keep the platform migration/upgrade operable; it does **not** convert DR-70 to PASS or authorize product milestone advancement.

No R5C26 command infers DR-70 acceptance.

## 6. Command reference

Stage from a reviewed clean checkout:

~~~bash
export SKYFORGE_ACCEPTED_MAIN_SHA=<accepted-main-sha>
./scripts/orchestrator/stage_platform_v2_cutover.sh
~~~

Initial/re-staged cutover preflight:

~~~bash
python3 scripts/orchestrator/platform_v2_operator_cutover.py preflight \
  --root /home/skyforge/skyforge \
  --activation-template /path/to/reviewed-activation-template.json
~~~

Execute cutover only after clean preflight:

~~~bash
sudo python3 scripts/orchestrator/platform_v2_operator_cutover.py cutover \
  --root /home/skyforge/skyforge \
  --activation-template /path/to/reviewed-activation-template.json \
  --execute
~~~

Emergency rollback:

~~~bash
sudo python3 scripts/orchestrator/platform_v2_operator_cutover.py rollback \
  --root /home/skyforge/skyforge \
  --execute
~~~

Singleton protected-authority preflight:

~~~bash
sudo python3 scripts/orchestrator/platform_v2_operator_cutover.py transfer-authority \
  --root /home/skyforge/skyforge \
  --event-key sha256:<exact-durable-event-id> \
  --issue-number <issue-number> \
  --source-id <github-comment-id> \
  --signal-kind <task-or-other-protected-kind>
~~~

Add --execute only after that preflight is clean.

Atomic batch preflight:

~~~bash
sudo python3 scripts/orchestrator/platform_v2_operator_cutover.py transfer-authority-batch \
  --root /home/skyforge/skyforge \
  --batch-manifest /path/to/exact-authority-batch.json
~~~

Add --execute only after the exact-full-set preflight is clean.

Terminal non-executed V2 retirement is likewise preflight-first:

~~~bash
sudo python3 scripts/orchestrator/platform_v2_operator_cutover.py retire-v2-authority \
  --root /home/skyforge/skyforge \
  --event-key sha256:<exact-durable-event-id> \
  --issue-number <issue-number> \
  --source-id <github-comment-id>
~~~

Add --execute only after preflight is clean.

For ordinary accepted-main updates, do **not** reconstruct this choreography manually. Use:

~~~text
docs/operations/PLATFORM_V2_ROUTINE_UPGRADE.md
scripts/orchestrator/platform_v2_routine_upgrade.py
~~~

Do not run mutating operator commands merely to test the package.

## 7. Acceptance meaning

R5C26 source acceptance proved the privileged handoff package, fail-safe writer state machine, service separation, activation-evidence boundary, and rollback reconciliation guard.

The later live cutover proved the package operationally: Platform-v2 became the production writer and legacy became the inactive/disabled rollback path.

That does **not** mean:

- DR-70 passed;
- every later accepted-main commit is already deployed to the live checkout;
- human acceptance can be inferred from CI/merge status;
- controller code receives sudo authority;
- protected authority may be silently completed or discarded.

Current accepted-main movement after cutover belongs to the separate routine-upgrade contract.
