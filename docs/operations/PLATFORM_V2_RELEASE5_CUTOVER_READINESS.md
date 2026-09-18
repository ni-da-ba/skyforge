# Platform v2 Release 5 — Cutover Readiness

Status: **R5A READINESS CONTRACT COMPLETE — PRODUCTION CUTOVER BLOCKED**

Parent migration: #767  
Release-4 acceptance: #845 / PR #846  
R5A authority: #847

## Purpose

Release 5 begins by proving that production authority can move from the legacy hosted controller to Platform v2 without ambiguity, without losing current operational state, and without relying on conversation history.

R5A does **not** perform that authority switch. It creates the machine-enforced readiness and rollback boundary that a later R5B/R5C switch must satisfy.

## Live starting observation

At R5A start:

- accepted main: `4f5ce6ecab91e86b6bc4ff346a31d07e8ce04314`;
- legacy service: active;
- legacy runtime checkout: `c7ff98a3b6a1250f1ae7f349239ac0c25dd13ea7`;
- legacy state SHA-256: `7c77fe615a02a2c18426e043a49c1c66a43c33ad750a11fb37a4d663d40d596e`;
- pending worker: none;
- pending decision: none;
- managed PRs: none;
- pending events: none;
- controller block: none.

The legacy controller is therefore operationally quiescent, but its runtime checkout does not yet equal the accepted Release-5 cutover head.

## Authority that must survive cutover

Active external producer claims are project truth, not cleanup debris:

- #613 / PR #762 / `platform/613-aero-moment-contract`;
- #754 / PR #769 / `implementation/754-dr70-human-visible-repair`.

The roadmap is `skyforge-dressed-region-convergence-v3`, with no active machine node. The blocked node `dr-human-exploration-rereview` remains a human/product gate. Platform migration must preserve it and must not machine-pass it.

R5A projects these authorities deterministically into v2 state. Their existence alone does not authorize retirement, merge, closure, or human-gate completion.

## Machine readiness predicate

`v2.cutover.evaluate_cutover_readiness` returns `READY_FOR_AUTHORITY_SWITCH` only when all of the following are true:

1. Release 4 is accepted.
2. The bounded Release-4 mutation gate is disabled.
3. Legacy operational state is quiescent.
4. The legacy state projection is complete.
5. The running legacy runtime SHA equals the accepted cutover main SHA.
6. A fresh pre-v2-cutover rollback checkpoint is PASS.
7. A hosted v2 production runtime has been separately accepted.
8. Webhook/control ingress handoff is defined.
9. Old-writer revocation/rollback sequencing is defined.
10. A privileged mechanism exists that can actually revoke the legacy hosted writer before v2 activation.

R5A remains BLOCKED because the legacy runtime SHA is behind accepted main, the hosted v2 runtime is not yet accepted, ingress handoff is undefined, and the controller service account cannot itself stop/revoke the production systemd service.

## Writer authority handoff

The only accepted forward sequence is:

`LEGACY -> NONE -> V2`

The only accepted rollback sequence is:

`V2 -> NONE -> LEGACY`

The pure handoff contract rejects direct `LEGACY -> V2` activation. This makes the no-dual-writer interval explicit and testable.

## Fresh checkpoint — PASS

R5A captured `pre-v2-cutover-20260917T2115CDT`.

Evidence:

- accepted main at checkpoint: `4f5ce6ecab91e86b6bc4ff346a31d07e8ce04314`;
- live legacy runtime: `c7ff98a3b6a1250f1ae7f349239ac0c25dd13ea7`;
- quiescent primary/backup state SHA-256: `64ff62f20063d9d8873e1a13d27345cdfcc182e590e6aecc41d41d27094a2f37`;
- trusted paused rollback-state SHA-256: `ec368a4d7cd730452cea154d009c9cac38639132c9d3bfc00b117fd222cf2e57`;
- checkpoint archive SHA-256: `1245ed5976e3c82e5616757b1756188adae2b680d0cb5dee38ba1045c4a6e6e7`;
- all-refs Git bundle SHA-256: `bdfed8f699478db8f2edbe27c34896fa39c559dceb94be8b9ec2666039c9019e`;
- bundle size: 10,009,116 bytes;
- bundle verification: PASS;
- restore drill: PASS, restored `main` exactly `4f5ce6ec...`;
- whole-host DigitalOcean snapshot: image `245929672`, action `3415118353`;
- public Git bundle copied off-Droplet to an unpublished draft release asset.

The paused rollback-state copy was captured with `paused=true`, no pending worker, no pending decision, no managed PR, no controller block, and zero pending events. Legacy was then explicitly resumed and returned to a zero-event quiescent state.

The checkpoint contains at minimum:

- exact accepted main;
- exact running legacy runtime SHA;
- `.skyforge-orchestrator/state.json` and `.bak`;
- non-secret service/config identities;
- dependency fingerprint;
- external-producer claims;
- roadmap state;
- Platform-v2 state;
- SHA-256 manifest;
- restorable repository identity/bundle evidence.

Secrets remain outside repository evidence.

## Privileged revocation blocker

The `skyforge` service account does not have permission to invoke `sudo systemctl stop skyforge-orchestrator.service`. The attempted R5A stop failed before changing service state.

That is useful cutover evidence: Release 5 must establish a separately accepted privileged revocation mechanism. v2 must never be activated merely because a low-privilege process *intended* to stop legacy. The old writer must be observably revoked first.

## Current architectural blocker

There is no accepted hosted v2 production runtime yet. Current v2 capabilities include pure policies/core, read-only shadow collectors/runners, and the bounded Release-4 canary executor. None of those is a general hosted replacement for `skyforge_control_plane_runtime.py`.

A later R5B tranche must build and validate that runtime before any authority switch.

## Non-impact boundary

R5A does not:

- stop or replace the legacy production service;
- acquire production v2 writer authority;
- change webhook routing;
- modify DR-70;
- retire #613/#754 external authority;
- pass the DR-70 human gate;
- migrate to SQLite.

R5A therefore completes its readiness/checkpoint purpose while Release 5 remains fail-closed for R5B runtime/ingress/revocation work.
