# Platform v2 Release 3 — Live Shadow Acceptance

Status: **ACCEPTED FOR RELEASE 4 CANARY**

Parent migration authority: issue #767  
Acceptance tranche: issue #825  
Release-3 acceptance main before this record: `db1715b6d8a8cd5819e118f78d57dec9c7f44504`

## Exit criterion

The durable migration plan requires Release 3 to show sustained live agreement across:

- ordinary activity;
- recovery cases;
- human gates;
- manual/external producer transitions;
- roadmap progression.

Release 3 satisfies that criterion. The shadow did not merely agree: it exposed several real legacy/v2 discrepancies, those discrepancies were repaired or explicitly classified, and the repaired behavior was re-demonstrated against live production state.

## Accepted Release-3 tranches

| Tranche | PR | Accepted merge |
| --- | ---: | --- |
| R3A — read-only shadow runner and capability guard | #808 | `054622bef604173cf8174514ba70a95e6f33c9a2` |
| R3B — live managed-PR read collector | #810 | `8954e477ab18372992d583db2ebd383881769e71` |
| R3C — deterministic live parity analyzer | #812 | `d09e80f2970b805909bf8d6437f0ea2896ea9740` |
| R3D — host-ready read-only shadow cycle | #814 | `c6653774e91e0a60f1b273c0bb8b02fb03fb0f2d` |
| R3E — accepted-main identity correction | #816 | `b45dbaa9fd749400d03277f86ea0427d9c9af265` |
| R3F — roadmap recovery live shadow | #818 | `388d6f0d4570a4435c348df52bda4bc1f74291a7` |
| R3G — terminal-gate deadlock repair | #820 | `c7ff98a3b6a1250f1ae7f349239ac0c25dd13ea7` |
| R3H — external producer live parity | #822 | `c0c08590fee10ea3f778aeb039e8bbcc38ab6e51` |
| R3I — terminal-gate ordinary-event parity | #824 | `db1715b6d8a8cd5819e118f78d57dec9c7f44504` |

Every tranche passed its exact-head normal CI and Orchestrator Smoke before merge.

## Live evidence

### 1. Hosted read-only execution

The first foreground live shadow cycle ran accepted code against the production controller state without mutation.

- accepted main: `b45dbaa9fd749400d03277f86ea0427d9c9af265`
- managed lanes observed: 0
- cycle digest: `723a903ec6b7ab718ec433bff977a9581c0889f8d53fe4d97f1ed85ffb672c62`

This proved the live read path/capability boundary. It was infrastructure evidence, not a decision-parity sample, because the managed-PR set was empty.

### 2. Accepted-main identity recovery

Live preflight found the production checkout on active DR-70 work while both local `main` and `origin/main` refs were stale. R3D's original local-`HEAD` freshness source would therefore have been wrong.

R3E changed shadow current-main identity to the exact read-only GitHub lookup. Live verification showed:

- GitHub accepted main: `b45dbaa9fd749400d03277f86ea0427d9c9af265`
- host product checkout remained independently on DR-70 work.

This disagreement was a **shadow implementation defect**, fixed before mutation authority.

### 3. Roadmap progression + human gate

R3F evaluated the live durable roadmap and current GitHub issue truth.

It projected model-free retirement of:

- `dr-65-canonical-hydrology-authorship`;
- `dr-70-human-review-repair`;

and selected:

- `dr-human-exploration-rereview`;
- disposition: `HUMAN_GATE`;
- decision digest: `732c93eb8757b6090ab332f02ea8fdfb5ce34e8b34ad092eb92d9049aeeb55df`.

Production initially did not make that transition.

### 4. Recovery divergence discovered and repaired

The live disagreement above exposed a real legacy composition bug.

Terminal-gate quiescence could latch on an older blocked human gate while later task nodes were still durably blocked. Because quiescence returned before the roadmap wrapper ran, closed blocked tasks could never be reconciled and a new human gate remained hidden.

R3G changed terminal quiescence to fail open to roadmap reconciliation whenever any blocked task remains.

After #820 merged:

1. production was paused through the trusted control path;
2. the built-in paused-only runtime refresh synced the controller to `c7ff98a3b6a1250f1ae7f349239ac0c25dd13ea7`;
3. systemd restarted it through the normal accepted restart mechanism;
4. production resumed;
5. its first pre-dispatch reconciliation:
   - completed DR-65 at run count 1;
   - completed DR-70 at run count 2;
   - surfaced `dr-human-exploration-rereview`;
   - latched terminal-gate quiescence only after the roadmap became genuinely terminal.

This converted a real `DIVERGENCE` into demonstrated live agreement.

### 5. Manual/external producer transitions

R3H first found two pure-policy gaps against accepted legacy behavior:

- v2 kept CLOSED-unmerged bound PR claims; legacy retires them;
- v2 kept all unbound claims indefinitely; legacy retires an unbound claim when its governing issue closes.

Both were corrected and regression-locked.

Accepted R3H then ran against live production state:

- active #613 / PR #762 OPEN -> v2 `KEEP` -> **AGREE**;
- active #754 / PR #769 OPEN -> v2 `KEEP` -> **AGREE**;
- recent auto-retired #692 / PR #760 MERGED -> v2 `RETIRE` -> **AGREE**.

Live external-claim cycle digest:

`ae72d3f101efd757c27eb399f3794a20e6e44ba22c6c6944e598dd6e8ac3b80d`

Summary: `AGREE: 3`, `DIVERGENCE: 0`.

### 6. Ordinary live repository activity

Production was deliberately paused while R3I ran so real ordinary repository lifecycle events remained durable.

Four live events accumulated:

1. Orchestrator Smoke completion for PR #824 head;
2. PR #824 close/merge lifecycle event;
3. push of accepted main `db1715b6d8a8cd5819e118f78d57dec9c7f44504`;
4. CI completion for that accepted main.

Accepted R3I predicted `QUIESCE_ORDINARY` for the exact durable identities:

- `sha256:c723840045d99932b575d7dfd234ba74556d8f43c7361b2ca001242f816edd47`
- `sha256:1275257e9fa3993a3354e41d12b5862cc71a85cb7b96e60f9a734943c5e25586`
- `sha256:8218b4dc652e0f5bfcbb837fac00bbe201ffc9aadc8e7f8c066659205e99fdfc`
- `sha256:980912dc0f04992b28384df6fba09bd8d94c4fc03254f5f98ba9c6bc0de07083`

v2 terminal-gate decision digest:

`c7989dfaf7377befaf36f9dfdaab2f9336414ff27f83c60f9d566921cf16b630`

live collector digest:

`4cb8b04a9f08b1d85d30fbe3f13e7c3f8fa38e171ab283ebb4a6617a63b2fb00`

Before legacy resumed, `classifier_calls_today = 1`.

After resume:

- legacy retired exactly 4 ordinary events;
- pending event count became 0;
- terminal-gate quiescence recorded `retired_events = 4`;
- `classifier_calls_today` remained **1**.

This is exact ordinary-live agreement with zero classifier/model spend.

## Disagreement ledger

### Resolved defects

1. **R3E — wrong accepted-main source**
   - local checkout/ref identity was not accepted-main identity;
   - fixed by exact read-only GitHub accepted-main lookup.

2. **R3G — terminal-gate/roadmap composition deadlock**
   - quiescence could hide closed blocked tasks and a new gate;
   - fixed in legacy, runtime-refreshed, and re-demonstrated live.

3. **R3H — external claim terminal semantics**
   - CLOSED-unmerged bound PR and unbound issue-closure retirement were missing in v2;
   - fixed in v2 and regression-locked.

No unresolved semantic divergence discovered by Release 3 remains.

## Intentional stricter-v2 gap carried into Release 4

Legacy managed-PR state does not durably prove all exact acceptance identities required by Platform v2:

- reviewed SHA;
- frozen task-spec hash;
- accepted task-spec hash.

Therefore R3 live managed-PR shadow correctly leaves an otherwise green legacy PR at `RECONCILE` rather than manufacturing `MERGE_ELIGIBLE`.

This is **not** accepted as a permanent compatibility relaxation. Release 4 must exercise a canary whose frozen task/spec/attempt identity and exact-SHA evidence exist natively so v2 can complete a true `MERGE_ELIGIBLE` lifecycle.

## Operational note: persistent shadow timer

R3D includes hardened systemd oneshot/timer templates, but the remote shell used during this acceptance intentionally blocks `sudo`, while the service account has no user-systemd lingering session.

A cron fallback was deliberately rejected because it would weaken the R3D OS-level read-only sandbox.

Release-3 live evidence therefore used repeated foreground executions of accepted-main shadow code against the production state. This does not block Release 4 because the required live categories were directly observed and recorded. Persistent shadow scheduling may be installed later through the documented privileged path.

## DR-70 boundary

Per project-owner direction, further DR-70 repair after the re-reviewed gate is deferred until Platform v2 migration/optimization work is complete. The human gate remains human and does not block Platform v2 migration.

## Release decision

Release 3 exit criterion is satisfied.

Release 4 may begin only as the migration plan specifies:

- narrowly scoped low-risk mutation authority;
- old controller excluded from that authority;
- writer fencing active;
- exact frozen task/spec/attempt identity;
- exact-SHA evidence/acceptance;
- restart, reconciliation/outage, stale-event, and head-movement recovery exercised before broadening authority.

No broader v2 mutation authority is granted by this acceptance record.
