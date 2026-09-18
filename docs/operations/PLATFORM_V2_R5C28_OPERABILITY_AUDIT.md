# Platform v2 R5C28 — Final operability and migration-debt audit

Status: **READY FOR FINAL NONPRODUCTION ACCEPTANCE REHEARSAL**

R5C28 audits the accepted Release-5 implementation after R5C27. It does not transfer production writer authority.

## Roadmap regression disposition

The lingering full-sweep failure was a test-isolation defect, not a controller defect. Its fixture created an active roadmap node for historical issue #284, marked its authority completed, and expected the node to remain blocked, but did not mock issue state. Real issue #284 is now closed, so the accepted closed-blocked-task reconciler correctly promoted the newly blocked fixture node to completed during the same call.

The test now fixes the backing issue as open. This preserves both intended behaviors: completed authority without a PR blocks an open objective, while the separately tested closed-objective path retires the block as complete.

## Accepted architecture

The current source has the required pre-cutover mechanisms:

- production v2 execution requires reviewed activation evidence;
- the v2 unit conflicts with the legacy unit and uses the same localhost port, making simultaneous systemd ownership fail closed;
- staging requires exact accepted HEAD and a tracked-clean checkout;
- the v2 unit is separately named and remains disabled during staging;
- rollback retires stale v2 activation evidence before restoring legacy;
- legacy restoration requires successful startup reconciliation before durable work resumes;
- controller service authority remains separate from privileged service-management authority;
- Caddy can remain on 127.0.0.1:3000 while writer ownership changes behind the stable endpoint.

Historical R5A documentation still records blockers that were true when R5A was accepted, including the then-absent hosted v2 runtime and undefined ingress/revocation. Those statements remain historical evidence. R5C24-R5C28 supersede those implementation blockers.

## Read-only live observation

Production remains deliberately pre-cutover:

- live checkout: c7ff98a3b6a1250f1ae7f349239ac0c25dd13ea7;
- accepted main at audit: 7f6411c3e0e722cecf8f560ef73bc93562b39734;
- legacy systemd ExecStart still uses skyforge_control_plane_runtime.py as user skyforge;
- v2 service is inactive and its production unit is not installed;
- Caddy still proxies to 127.0.0.1:3000;
- the established no-noninteractive-sudo boundary for skyforge remains part of the migration contract and has not been relaxed.

Checkout lag and the unstaged v2 unit are expected pre-cutover conditions. R5C28 does not alter the running writer to remove them.

## Remaining path

Only final operational handoff work remains:

1. accept R5C28;
2. synchronize to the final accepted migration head while legacy remains authoritative;
3. stage the separate v2 unit from that exact clean head;
4. generate and validate final activation facts and run the complete nonproduction preflight/rehearsal;
5. freeze migration code if that rehearsal is clean;
6. resurface DR-70 when production activation actually requires its authority;
7. with explicit operator authorization, execute LEGACY -> NONE -> V2;
8. observe v2 in production, retain rollback initially, then retire legacy and migration scaffolding after the observation gate.

No additional architecture tranche is justified unless the final rehearsal exposes a defect.

## Safety

R5C28 performs no service restart, unit installation, Caddy mutation, live provider call, writer transition, sudo grant, or DR-70 pass. Production authority remains LEGACY.
