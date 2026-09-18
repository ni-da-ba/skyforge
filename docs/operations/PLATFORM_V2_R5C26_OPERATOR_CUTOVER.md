# Platform v2 R5C26 — Privileged operator cutover / rollback

Status: **source/test package only; no live production writer transition performed**

Parent migration: #767
Tranche: #903
Predecessor: R5C25 merged at `7a878338a8a8b00c4120557f51aec002e2234a4a`.

## Purpose

R5C26 packages the first production-capable operator handoff around the accepted R5C24/R5C25 hosted runtime. It deliberately keeps systemd authority outside the `skyforge` service account.

Forward authority remains:

`LEGACY -> NONE -> V2`

Rollback remains:

`V2 -> NONE -> LEGACY`

The default CLI path is read-only. Mutating service operations require both the explicit `--execute` flag and a root operator.

## Unit staging

`scripts/orchestrator/stage_platform_v2_cutover.sh` stages two unit definitions without restarting or starting either writer:

- the legacy `skyforge-orchestrator.service`, now carrying the rollback startup-reconciliation guard;
- the separate `skyforge-orchestrator-v2.service`, bound to an explicit activation-evidence path.

Activation evidence lives by default at `/var/lib/skyforge-orchestrator/platform-v2-activation.json`. Staging creates that state directory as root-owned, setgid to the service group, and non-writable by the service account; the finalized evidence is `0640`. This keeps `/etc/skyforge-orchestrator` private for secrets while allowing the unprivileged v2 runtime to read root-authorized activation state.

The staging helper requires an exact accepted-main SHA and a clean tracked checkout. It refuses to run while v2 is active, installs the unit files, runs only `daemon-reload`, leaves legacy running, and leaves v2 disabled.

Staging therefore does **not** change writer authority.
## Activation-template boundary

The operator supplies a reviewed activation template containing the complete R5C20 `ProductionActivationInput`, its canonical digest, the accepted main SHA, and an explicit production-execution request.

The template is intentionally **not** the final R5C24 activation evidence.

Preflight verifies:

- the production activation input recomputes cleanly;
- the checkout HEAD equals the accepted main SHA;
- the tracked worktree is clean;
- legacy is loaded, active, and boot-enabled;
- v2 is loaded, inactive, and boot-disabled;
- the legacy unit contains `--require-startup-reconcile-success`;
- the v2 unit invokes `platform_v2_hosted_runtime.py` with production execution enabled and the exact requested evidence path.

Only after legacy has been stopped and observed inactive does the operator tool create the final evidence with:

- `legacy_writer_revoked_observed = true`;
- `writer_authority = NONE`.

The tool then invokes the accepted R5C24 gate loader against the actual checkout. A blocked or stale gate cannot start v2.

## Forward cutover

The privileged sequence is:

1. re-run read-only preflight;
2. capture a timestamped pre-cutover checkpoint;
3. stop legacy;
4. prove legacy inactive and v2 inactive;
5. disable legacy boot activation;
6. record the explicit `NONE` boundary;
7. atomically finalize activation evidence;
8. recompute the R5C24 activation gate;
9. enable and start v2;
10. verify localhost health reports Platform v2, production execution enabled, a running driver, and the exact gate digest.

Any failure before legacy revocation leaves legacy authoritative. Any failure after legacy is dead removes/retire stale activation evidence, disables/stops v2 when possible, and returns to a provable `NONE` or legacy-only state. The tool never intentionally permits dual writers.
## Checkpoint evidence

Before the first writer mutation, the tool records an ignored local checkpoint under:

`.skyforge-platform-v2/operator-evidence/`

The checkpoint contains non-secret metadata and hashes for the relevant service observations, activation template, legacy durable state, v2 budget state, environment file when readable, and loaded unit fragments.

Environment/config contents are not copied into the report.

## Rollback

Rollback does not depend on the activation template remaining valid. This is deliberate: emergency restoration of the legacy writer must not be prevented by a damaged or missing review artifact.

The rollback sequence is:

1. stop v2 if active;
2. disable v2 boot activation;
3. prove v2 and legacy are inactive;
4. record `NONE`;
5. retire the finalized v2 activation-evidence file so a stale v2 boot cannot reuse it;
6. enable and start legacy;
7. require successful startup reconciliation;
8. only then regard legacy as restored.

The legacy runtime now supports `--require-startup-reconcile-success`. When set, a failed startup reconciliation exits before `resume_pending()`; systemd may retry the process, but durable work cannot dispatch before reconciliation succeeds.

If rollback starts legacy but its reconciliation/health proof fails, the operator tool stops legacy again and returns to `NONE`.

## DR-70

DR-70 remains **deferred**, not passed.

The R5C26 source package, unit staging design, dry-run preflight, and tests may mature while DR-70 is deferred. The live activation template still passes through the accepted R5C20 production gate, which requires the explicit DR-70 clearance fact.

Therefore the current deferred value is expected to block an actual production cutover. That is intentional. No code in R5C26 infers or fabricates a DR-70 PASS.
## Operator commands

Stage units from the accepted clean checkout:

```bash
export SKYFORGE_ACCEPTED_MAIN_SHA=<accepted-main-sha>
./scripts/orchestrator/stage_platform_v2_cutover.sh
```

Read-only preflight:

```bash
python3 scripts/orchestrator/platform_v2_operator_cutover.py preflight \
  --root /home/skyforge/skyforge \
  --activation-template /path/to/reviewed-activation-template.json
```

A future explicitly authorized live cutover uses the same command surface with:

```bash
sudo python3 scripts/orchestrator/platform_v2_operator_cutover.py cutover \
  --root /home/skyforge/skyforge \
  --activation-template /path/to/reviewed-activation-template.json \
  --execute
```

Rollback uses `rollback --execute`. Do not run either mutating command merely to test the package.

## Acceptance meaning

R5C26 acceptance proves the privileged handoff package, failure-injection state machine, service separation, and rollback reconciliation guard at source/test level.

It does **not**:

- clear DR-70;
- restart/stop the live production controller;
- enable the live v2 writer;
- change Caddy or the GitHub webhook;
- grant sudo to the controller account;
- perform provider calls or live GitHub mutations for validation.

A future live cutover remains an explicit human/operator action after the optimized workflow is considered mature and all then-applicable activation facts are reviewed.
