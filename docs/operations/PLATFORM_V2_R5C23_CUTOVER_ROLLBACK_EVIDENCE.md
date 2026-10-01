# Platform v2 R5C23 — Cutover / rollback rehearsal evidence

Status: **nonproduction rehearsal accepted pending exact-head repository CI/Smoke**

Parent migration: #767  
Tranche: #897  
Candidate branch: `platform/v2-r5c23-cutover-rollback-rehearsal`  
Live rehearsal code head: `81469766fd9a2d1ed9ffa5b407669cc9b35340c2`

## Purpose

R5C23 rehearses the required writer-authority transitions using disposable fixture child
processes only:

`LEGACY -> NONE -> V2 -> NONE -> LEGACY`

It does not stop, restart, reload, reconfigure, or signal the production
`skyforge-orchestrator.service`.

## Process-level regression coverage

The focused rehearsal/cutover/activation suite passed **33 tests**.

The process-level cases include:

- normal forward cutover and rollback;
- failed legacy revocation -> v2 never starts;
- abort after legacy revocation -> explicit NONE -> legacy rollback;
- failed v2 activation -> explicit NONE -> legacy rollback;
- dual live fixture writer rejection;
- production hosted runtime isolation from the rehearsal module;
- accepted writer-authority transition invariants.

These tests use real disposable subprocesses rather than mocked process liveness.

## Live disposable rehearsal

The live rehearsal was executed on the orchestrator host from the candidate branch with:

`python3 -m v2.cutover_rehearsal --root /tmp/skyforge-r5c23-live-rehearsal`

The report disposition was `ACCEPTED`.

Observed ordered events:

1. `PROCESS_READY` — disposable LEGACY fixture PID `417059`;
2. `WRITER_NONE` — legacy fixture observably dead before any v2 activation;
3. `V2_READY` — disposable V2 fixture PID `417060`;
4. `ROLLBACK_WRITER_NONE` — v2 fixture observably dead before rollback;
5. `ROLLBACK_LEGACY_READY` — disposable LEGACY rollback fixture PID `417061`.

The report asserted:

- `forward_v2_observed = true`;
- `rollback_legacy_observed = true`;
- `no_overlap_proven = true`;
- `recovery_from_none_proven = true`;
- final authority = `LEGACY`.

Transition digests:

- LEGACY -> NONE:
  `106ec25c006038130a40edeb2d805e94111e97e8e54351ca45ca33c1f133aa36`
- NONE -> V2:
  `e9292065424eb5b4b91d154895f43adf8bec683eba3c7110acdb53eec8f03cb6`
- V2 -> NONE:
  `37edd594136f4553827200ec070c5699ad9cb3c32c5084cebc11f67f2b840d2b`
- NONE -> LEGACY:
  `700e49754f45546e3709d101ebb5f99bf71fd36c6c43d9fe237fc0c3c767d106`

The raw rehearsal report SHA-256 was:

`e1a8688237459740ad240bb92f9a2fa85d5f23527c759e4b023691906e04d43f`

No fixture child process remained after rehearsal cleanup.

## Production-service before / after proof

Read-only production evidence was captured immediately before and immediately after the
live disposable rehearsal.

Both snapshots were identical on the authority-relevant process identity:

- service: `skyforge-orchestrator.service`;
- `ActiveState=active`;
- `SubState=running`;
- `MainPID=400145`;
- `ExecMainStartTimestampMonotonic=263628208170`;
- service unit:
  `/etc/systemd/system/skyforge-orchestrator.service`;
- unit SHA-256:
  `864b5591af6389314f67a27442fafa22a3b55262e6449885795ae41de702e009`;
- command:
  `skyforge_control_plane_runtime.py --root /home/skyforge/skyforge --repo ni-da-ba/skyforge --bind 127.0.0.1 --port 3000 --require-webhook-secret --startup-reconcile`;
- `127.0.0.1:3000` remained bound by PID `400145`.

The production process PID and monotonic start timestamp being unchanged proves the
production controller was not restarted or replaced during this rehearsal. The unchanged
unit hash proves the systemd service definition was not modified.

## Capability boundary

The R5C23 rehearsal module contains no:

- `systemctl`;
- production service name;
- production port binding;
- hosted production runtime import;
- writer fence;
- Git/GitHub mutation;
- network client;
- Caddy control.

The production hosted runtime does not import the rehearsal.

## Acceptance meaning

After exact-head CI and Orchestrator Smoke accept the final R5C23 branch, this evidence is
sufficient to mark the R5C20 input:

`cutover_rollback_rehearsal_accepted = true`

It does **not** by itself clear:

- primary Windows workstation preservation;
- DR-70 migration/human-review hold;
- lower-level live cutover-readiness conditions;
- the final required operator review.

No production writer switch was performed.
