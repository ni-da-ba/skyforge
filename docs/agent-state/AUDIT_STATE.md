# Skyforge AUDIT Agent State

**Lane:** AUDIT  
**Status:** Canonical live lane handoff  
**Updated:** 2026-09-08 (America/Chicago)  
**Current reconciliation base:** `main@61c06a71222c975be62029e9f8461de17efd0221`  
**Highest live-accepted Audit milestone:** **AUDIT-0021**  
**AUDIT-0022:** repository boundary merged/machine-green; combined live recovery gate pending  
**AUDIT-0023:** pending-event journal coalescing hardening in progress

Repository evidence is authoritative. Read the canonical program files, current lane ledgers, current
`main`, issues #349/#369/#378/#424, active PRs, source/tests, merged history, and exact-head Actions.

## Durable Audit boundary

AUDIT-0001 through AUDIT-0018 established repository-first supervision and the hosted orchestration
pilot. Detailed evidence remains in merged Audit PRs and issues #349/#369/#378.

**AUDIT-0019 — MERGED / ACCEPTED.** PR #419 merged as
`913c019b3752c8d2f3ec2d902c85493005c5e6ea`. Exact head
`0b4cd809c621875ddcd2a96a094d7ab58fd3d022` passed Orchestrator Smoke `34295782054` and CI
`34295782042`. Three consecutive classifier failures fail closed with the durable batch preserved.

**AUDIT-0020 — MERGED / ACCEPTED.** PR #420 merged as
`d590ccdbbfa1f33df2ac297f762732bd1e3080d5`. Exact head
`4d51f8d865485d58ab34ceacdbdb784c0f8f41ef` passed Orchestrator Smoke `34296350432` and CI
`34296350439`. Dispatch acquires its single lock before reading the durable queue.

**AUDIT-0021 — MERGED / ACCEPTED.** PR #421 merged as
`cbc5bacccccf59d2ff355e89cd6ea040514baead`. Reconciled exact head
`2e09f3ad426db666fbebd0563542e0ec353d577b` passed Orchestrator Smoke `34304533434` and CI
`34304533469`. The preserved pre-reconciliation head
`fb188f64e8d9da91af5a12bd55e064bbc50b058e` had already passed CI `34298969341` and
Orchestrator Smoke `34298969384`.

The live host self-refreshed to merged `cbc5bac...`. After the paused-only local counter reset and
03:09:45 UTC resume, Luna classified once at 03:09:59. Model-free statuses at 03:12:29 and 03:18:31
both remained at exactly one Luna call while the same four-event tail remained queued, with zero Terra
workers, zero classifier failure streak, and no retry blocker. This bounded observation accepts the
AUDIT-0021 durable-decision ownership repair.

### Post-0021 recovery-contract incident

At 03:21:45 UTC the hosted controller correctly safety-paused after its Audit worker changed
`docs/agent-state/AUDIT_STATE.md`. No autonomous commit/push occurred. The incident exposed a
control-contract contradiction: the classifier allowed bounded Audit lane-state/evidence reconciliation
while the generic protected-path gate forbade the Audit lane's own durable state file.

**AUDIT-0022 — REPOSITORY MERGED / MACHINE-GREEN; LIVE GATE PENDING.** PR #423 merged as
`61c06a71222c975be62029e9f8461de17efd0221`. Exact head
`bc341678ff94be020dec89031e31c15230ceff4a` passed Orchestrator Smoke `34308033302` and CI
`34308033351`.

AUDIT-0022 repairs:
- exact-scoped Audit-only `AUDIT_STATE.md` handoff while authority-defining governance remains protected;
- classifier decision ownership across controller/process restart;
- pre-classification quiescence for every represented event head;
- paused-only model-free runtime refresh and safe isolated-worker discard controls;
- serialized recovery against dispatch;
- durable HUMAN_GATE posting/retry semantics;
- visible manual-merge gate when auto-merge remains OFF;
- richer model-free recovery/status telemetry.

The host remains intentionally safety-paused on the pre-0022 loaded runtime with the isolated dirty
worker preserved. Do not resume that runtime. Because the old process cannot know the new recovery
commands, one explicit host maintenance refresh is required after all current controller hardening is
merged. The live AUDIT-0022 gate will be satisfied on the combined refreshed runtime rather than
restarting the host twice.

## AUDIT-0023 — pending-event durability pressure

Issue #424 identified the final silent-loss path in the event journal: old code retained only the
newest 100 unique pending events with `[-100:]`.

AUDIT-0023 replaces that silent truncation with a soft-cap/coalescing contract:

1. Trusted Audit/manual signals are never evicted.
2. Event keys owned by a cached classifier decision are never evicted.
3. Ordinary repository transitions first coalesce by semantic subject.
4. If ordinary history still exceeds the soft cap, the newest bounded sample is retained and one
   synthetic `reconcile | queue_compaction` event preserves current-repository reconstruction.
5. If protected authority itself exceeds the cap, preserve it above the soft cap and expose the
   pressure model-free instead of dropping it.
6. Status and the daily zero-model value report expose queue high-water/compaction/overflow evidence.
7. Run only cheap exact-head Audit gates; do not recreate producer evidence or race producer lanes.

After AUDIT-0023 merges, perform one combined host recovery:
- synchronize the stable controller checkout to current merged `main`;
- restart the service onto the new runtime;
- discard only the superseded isolated uncommitted Audit worker through the new trusted control;
- verify durable queue/decision state with model-free status;
- resume once;
- verify no protected-path loop, duplicate Luna spend, queue-loss signal, or unexpected producer race.

Safety remains unchanged: **24 total Luna calls per UTC day, 4 Terra worker attempts per UTC day,
auto-merge OFF, API-billing fallback OFF.**

## Current program snapshot

Implementation #358 remains at its machine-ready morphology human gate. Presentation #385 remains at a
human communication gate. Authorship is intentionally dormant after AUTH-0101; Content retains its
accepted boundaries; Music retains MUS-0005 with source/listening work remaining. Audit must not race
these producer/human-gated lanes.

## Next Audit work

1. Complete AUDIT-0023 exact-head Smoke + CI and merge when green.
2. Keep the host safety-paused until the combined merged controller is loaded.
3. Perform the one-time maintenance refresh and safe isolated-worker recovery.
4. Resume the durable queue once and verify model-free stability.
5. Record AUDIT-0022/AUDIT-0023 live acceptance durably on issue #349/#424.
6. Continue issue #378 value review and ordinary liveness/race/evidence-saturation supervision.
