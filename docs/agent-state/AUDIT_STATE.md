# Skyforge AUDIT Agent State

**Lane:** AUDIT
**Status:** Canonical live lane handoff
**Updated:** 2026-09-09 (America/Chicago)
**Current reconciliation base:** `main@2c96be11`
**Highest live-accepted Audit milestone:** **AUDIT-0028**
**AUDIT-0022 through AUDIT-0028:** repository and live acceptance complete; orchestration freeze active

Repository evidence is authoritative. Read current `main`, canonical Audit/program documents, lane
ledgers, issues #349/#369/#378/#424, active PRs, exact-head Actions, and hosted status evidence.

## Accepted durable boundary

**AUDIT-0019 — MERGED / ACCEPTED.** PR #419 merged as
`913c019b3752c8d2f3ec2d902c85493005c5e6ea`. Exact head
`0b4cd809c621875ddcd2a96a094d7ab58fd3d022` passed Orchestrator Smoke `34295782054` and CI
`34295782042`.

**AUDIT-0020 — MERGED / ACCEPTED.** PR #420 merged as
`d590ccdbbfa1f33df2ac297f762732bd1e3080d5`. Exact head
`4d51f8d865485d58ab34ceacdbdb784c0f8f41ef` passed Orchestrator Smoke `34296350432` and CI
`34296350439`.

**AUDIT-0021 — MERGED / LIVE ACCEPTED.** PR #421 merged as
`cbc5bacccccf59d2ff355e89cd6ea040514baead`. Reconciled exact head
`2e09f3ad426db666fbebd0563542e0ec353d577b` passed Orchestrator Smoke `34304533434` and CI
`34304533469`. The live host self-refreshed to that merged runtime. After resume, Luna classified
once at 03:09:59 UTC; model-free statuses at 03:12:29 and 03:18:31 remained at exactly one Luna call
while the same four-event tail remained queued. This accepted durable classifier-decision ownership.

## Post-0021 recovery incident

At 03:21:45 UTC the host safety-paused after an Audit worker changed
`docs/agent-state/AUDIT_STATE.md`. No autonomous commit/push occurred. This revealed a contract
contradiction and left one isolated dirty Audit worker preserved on the host.

The host remains intentionally paused until the current merged controller generation is loaded. Do not
resume the pre-AUDIT-0022 runtime.

## AUDIT-0022 — repository merged / machine-green

PR #423 merged as `61c06a71222c975be62029e9f8461de17efd0221`.
Exact head `bc341678ff94be020dec89031e31c15230ceff4a` passed
Orchestrator Smoke `34308033302` and CI `34308033351`.

It adds:
- exact-scoped Audit-only `AUDIT_STATE.md` handoff;
- restart-safe classifier decision ownership;
- head-quiescence before first classification;
- paused-only runtime refresh and safe worker discard;
- serialized recovery;
- durable HUMAN_GATE posting;
- visible manual-merge gates with auto-merge OFF;
- richer status/recovery telemetry.

## AUDIT-0023 — repository merged / machine-green

PR #425 merged as `e621a15818849b0f29d8075b90295eca28d02fa2`.
Exact head `180a8c22ac5a05a8545dbcfca5b97e0c34250fa1` passed
Orchestrator Smoke `34309585114` and CI `34309585125`.

It adds:
- lossless pending-event pressure handling;
- atomic `state.json` + `state.json.bak` local state mirroring;
- fail-closed double-corruption behavior;
- model-free retry of failed startup reconciliation;
- zero-model queue/recovery telemetry;
- UFW default-deny inbound with only 22/80/443 admitted;
- stale controller-managed PR record retirement after manual merge/closure.

Issue #424 is therefore resolved at the repository boundary.

## AUDIT-0024 — bounded unattended reliability closure

The remaining unattended-mode gap is transport silence while the process stays alive. Startup
reconciliation alone cannot detect a webhook/Caddy/TLS delivery path that stops after startup.

AUDIT-0024 must:

1. Run a model-free remote repository reconciliation every 15 minutes by default while hosted.
2. Compare a stable semantic projection: current main, open PR identity including exact PR head SHA,
   and recent Actions state.
3. Checkpoint only the exact repository projection already shown to a successful classifier, never a
   fresh post-dispatch read that could acknowledge an unseen later change.
4. Defer periodic reconciliation while the controller already owns pending events/decision/worker work,
   avoiding duplicate synthetic wakes behind active work.
5. If changed state still has active Actions, defer the synthetic wake until a quiescent observation.
6. If an uncheckpointed quiescent change exists, journal one synthetic periodic `reconcile` event.
7. Recover new trusted issue/PR comments model-free as well: first-upgrade pre-start history is seeded
   without replay, subsequent scans paginate from the prior scan boundary, and Audit
   `RESTART RECOMMENDED` / `LOOP RISK` signals plus trusted `/skyforge-*` controls use the same
   deterministic classifiers as webhook delivery.
8. Persist periodic/comment reconciliation failures and expose them model-free.
9. Surface persistent authentication/classifier-failure safety states durably rather than failing
   silently when GitHub comment authority remains available.
10. Reconcile pinned Python runtime requirements via a fingerprinted helper on service start; controller
    Python, `requirements.txt`, and the sync helper are runtime-refresh triggers so dependency-only
    updates do not require another bespoke package-install visit.
11. Make the hosted installer idempotent: preserve daily telemetry history and reuse durable host
    hostname/secret/cost/report configuration during maintenance redeploy.
12. Keep privileged installer/systemd/Caddy/UFW changes as deliberate Audit deployment work; do not let
    autonomous workers rewrite their own authority.
13. Preserve all existing safety boundaries and run only exact-head Audit Smoke + CI.

Safety remains unchanged: **24 total Luna calls per UTC day, 4 Terra worker attempts per UTC day,
auto-merge OFF, API-billing fallback OFF.**

## Combined live acceptance boundary — COMPLETE

After AUDIT-0024 merged, one host maintenance refresh was performed with no earlier intermediate restart:

1. synchronize the stable dedicated clone to merged current `main`;
2. refresh/install the accepted systemd/runtime dependency contract;
3. verify GitHub auth, Codex auth, main protection, UFW, Caddy, service, timer, and HTTPS health;
4. verify `state.json` and `state.json.bak`;
5. load the merged runtime while still paused;
6. discard only the superseded isolated uncommitted Audit worker via the trusted recovery control;
7. inspect model-free status;
8. resume once;
9. verify stable status across more than one dispatch interval with no duplicate Luna spend,
   protected-path loop, queue-loss/state-recovery fault, startup/periodic reconciliation fault, or
   producer race;
10. record AUDIT-0022/AUDIT-0023/AUDIT-0024 live acceptance durably.

The trusted live acceptance signal `5603451890` records AUDIT-0026, AUDIT-0027, and AUDIT-0028 PASS
on `main@2c96be11`, with healthy repeated stale-batch observations. Main CI and Orchestrator Smoke
are green. This completes the combined live acceptance boundary for AUDIT-0022 through AUDIT-0024
and confirms the subsequent reliability fixes.

## AUDIT-0025 through AUDIT-0028 — ACCEPTED

**AUDIT-0025 — ACCEPTED.** PR #427 merged as `ec69a600`; it removes the `gh --slurp` dependency and
parses paginated JSON compatibly with Ubuntu 24.04 `gh`.

**AUDIT-0026 — LIVE ACCEPTED.** PR #428 merged as `3a7c874c`; it normalizes superseded queue history,
blocks non-open dispatch targets, and prevents controller-repair worker loops.

**AUDIT-0027 — LIVE ACCEPTED.** PR #429 merged as `49d8e3c5`; it adds durable retired/completed event
ledgers so stale webhook history cannot re-enter the queue after successful retirement.

**AUDIT-0028 — LIVE ACCEPTED.** PR #430 merged as `2c96be11`; it makes replay ledgers authoritative
over `pending_events` and purges suppressed physical queue copies before dispatch/status reads.

Signal `5603451890` records AUDIT-0026/0027/0028 PASS on the current main, including healthy repeated
stale-batch observations. No producer evidence was rerun for this reconciliation.

## Current orchestration boundary — FROZEN

The orchestration infrastructure is frozen at `main@2c96be11`. Do not extend or revisit it absent a
demonstrated operational defect or an explicit product-policy change. Ordinary Skyforge work should
not require infrastructure changes.

## Current program gates

Implementation #358 remains at its morphology human gate. Presentation #385 remains at a human
communication gate. Authorship is intentionally dormant after AUTH-0101. Content and Music retain their
accepted/current lane boundaries. Audit must not race healthy producers or cross human/product gates.
