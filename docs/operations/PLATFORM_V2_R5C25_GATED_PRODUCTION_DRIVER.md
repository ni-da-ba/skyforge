# Platform v2 R5C25 — Gated Production Execution Driver

R5C25 closes the functional gap left intentionally by R5C24: the accepted hosted
coordinator advances one durable lifecycle boundary per call, but the production HTTP
entrypoint did not drive that coordinator.

## Accepted design

When and only when the R5C24 production activation gate is READY, the hosted entrypoint
constructs a background `HostedExecutionDriver`. Signed webhook handling remains an
ingress-only HTTP operation: an actionable accepted event signals the background driver,
but no classifier, worker, or GitHub mutation executes synchronously in the request
thread.

The driver advances one durable boundary at a time and continues across safe internal
boundaries only while durable state proves that the previous boundary completed. It
stops at idle, blocked, recovery, and external-wait boundaries. A hard
`max_boundaries_per_wake` bound prevents an unexpected lifecycle loop.

A bounded periodic wake provides model-free recovery from missed webhook delivery.
Periodic wake does not itself authorize provider spend: the R5C24 coordinator and the
durable lifecycle state still decide whether protected work is actually eligible.

## Budget continuity

R5C25 adds `.skyforge-platform-v2/budget.json` with backup recovery through the accepted
JSON state-store adapter. On the first v2 budget observation, if the legacy controller
has counters for the same UTC day, v2 seeds:

- classifier calls;
- Luna worker calls;
- Terra worker calls.

That seed happens once. Restarting v2 cannot re-import or reset same-day legacy usage.
UTC-day rollover starts a fresh v2 ledger.

Luna classifier and Luna worker calls continue to share the accepted Luna local fallback
bucket. Terra worker calls retain the independent Terra bucket. Legacy per-day overrides
remain honored during migration.

Provider quota telemetry remains authoritative when it can be evaluated. If telemetry is
unavailable or unusable, the durable local ledger remains the fail-safe fallback.

Provider attempts are persisted **before** the provider is invoked, including failed
attempts, so a crash or provider exception cannot erase consumed local budget.

## Observability

`/healthz` remains read-only and reports:

- whether production execution is enabled;
- whether the driver is running;
- wake and boundary-advance counts;
- last disposition, reason, durable identity, and advance timestamp;
- last driver error;
- persisted v2 budget snapshot and configured fallback ceilings.

Health reads do not initialize or mutate the budget ledger.

## Safety boundary

R5C25 does not perform production cutover. It does not:

- stop/start/restart systemd services;
- alter Caddy or webhook routing;
- change production writer authority;
- grant sudo/systemd authority to the `skyforge` account;
- execute live providers in tests.

The existing R5C24 startup activation gate remains mandatory. Without accepted activation
evidence, no production driver or mutation-capable dependency factory is started.

DR-70 is operator-deferred after a human re-review found no change. It remains unresolved
bookkeeping and is not machine-passed; it is not a blocker to workflow maturation. It
must be resurfaced before an action whose authority actually depends on that deferred
decision.

## Validation

R5C25 adds targeted coverage for:

- same-day legacy budget seeding and restart persistence;
- UTC-day rollover;
- shared Luna versus independent Terra accounting;
- attempt-before-provider persistence, including provider failure;
- provider-quota mapping and local fallback;
- activation-gate requirement;
- bounded lifecycle draining;
- idle no-spin behavior;
- fail-closed per-wake error behavior.

The full orchestrator smoke sweep is also required. A known pre-existing failure in
`test_completed_authority_without_pr_blocks_node_instead_of_looping` reproduces on the
R5C23/R5C24 base and is not caused by this tranche; it must not be silently attributed
to or repaired inside R5C25 without a separate causal change.
