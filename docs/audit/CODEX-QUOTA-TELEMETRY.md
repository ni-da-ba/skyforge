# Codex quota telemetry

## Purpose

Skyforge currently protects hosted Codex usage with controller-local daily Luna/Terra ceilings. Those
ceilings are intentionally retained during this change, but they are only local accounting and cannot
observe Codex/Work usage outside the orchestrator.

Codex app-server exposes the authenticated account's provider-owned rate-limit snapshot through the
model-free JSON-RPC request `account/rateLimits/read`. Skyforge now has a read-only adapter for that
signal so a later governor can pace the orchestrator against the real account allocation rather than
fixed worker-tier buckets.

## Safety boundary

This milestone is **observability only**.

- No model turn is created by a quota read.
- No Luna/Terra routing changes.
- No local budget reset or ceiling change.
- No automatic resume, merge, credit consumption, or provider-side mutation.
- No API-key billing fallback.
- Auto-merge remains OFF.
- Account IDs, auth/session material, upsell payloads, and opaque reset-credit detail rows are not
  persisted or posted to GitHub.

The only account metadata surfaced is whether an account id was present, plus the provider-owned quota
fields needed for pacing.

## Machine-readable source

`scripts/orchestrator/codex_quota.py` launches the same authenticated local `codex app-server` available
to the hosted service account and performs:

1. `initialize`;
2. `initialized` notification;
3. `account/rateLimits/read` with reset-credit detail rows excluded.

The normalized snapshot records:

- capture timestamp;
- `ordinary_usage_allowed`;
- reset-credit available count, when supplied;
- every returned limit id;
- plan/rate-limit metadata safe for telemetry;
- every returned primary/secondary window as:
  - used percent;
  - remaining percent;
  - duration in minutes;
  - provider reset epoch;
  - a duration-derived window kind.

Window semantics are derived from duration rather than primary/secondary position:

- 240–360 minutes -> `five_hour`;
- 9,000–11,000 minutes -> `weekly`;
- other known durations -> `rolling_<minutes>m`;
- missing duration -> `unknown`.

No unknown bucket is silently promoted to a five-hour or weekly allowance.

## Remote command

After the quota-aware runtime is deployed, a trusted owner may post exactly:

```text
/skyforge-quota
```

The controller replies on the same issue/PR with one self-marked, non-recursive comment:

```text
[skyforge-orchestrator] QUOTA
```

followed by normalized JSON. The last successful safe sample is also included in `/healthz` and
`/skyforge-status` as `last_codex_quota_snapshot`. A safe probe error is retained separately as
`last_codex_quota_error`.

The command is model-free and does not enter the orchestration event queue.

## Standalone probe

The adapter can be validated independently under the same Unix account/HOME as the orchestrator:

```bash
python scripts/orchestrator/codex_quota.py
```

Optional overrides:

```bash
python scripts/orchestrator/codex_quota.py --codex-bin /path/to/codex --timeout 20
```

The CLI prints only the normalized safe snapshot or a redacted error.

## Hosted runtime integration

`deploy/orchestrator/skyforge-orchestrator.service.in` now starts:

```text
scripts/orchestrator/skyforge_quota_runtime.py
```

The extension imports the accepted core controller, adds the quota command and safe state fields, and
then calls the ordinary controller `main()`. It also extends `CONTROLLER_RUNTIME_PATHS` at runtime so
subsequent `/skyforge-refresh-runtime` operations recognize changes to both quota files.

Rollback is intentionally simple: restore the service `ExecStart` to
`scripts/orchestrator/skyforge_orchestrator.py`, daemon-reload, and restart. Durable controller state is
compatible because the added fields are optional telemetry.

## First live acceptance after deployment

Do not replace the current local budget policy merely because the probe compiles. First:

1. deploy the merged quota-aware service while the controller is safely paused;
2. verify `/skyforge-status` reports the expected loaded runtime;
3. issue `/skyforge-quota`;
4. compare the returned five-hour and weekly percentages/reset times with the visible Codex usage UI;
5. issue a second `/skyforge-quota` without starting model work and verify the meter does not materially
   move from the read itself;
6. complete one known worker attempt and sample again to establish an initial cost delta;
7. only then design/enable the shared pacing governor.

Until that live acceptance is complete, the production 72 Luna / 12 Terra local policy remains the
active guardrail.

## Intended successor

Once the provider signal is validated, the next control-plane milestone should replace model-tier hard
ceilings with a shared account governor based on:

- provider weekly remaining percentage and reset time as the long-run resource;
- provider five-hour remaining percentage and reset time as the burst resource;
- an explicit owner reserve;
- bounded same-task retry/circuit-break rules;
- LUNA/TERRA retained only as capability/routing choices.

That pacing change is deliberately outside this telemetry milestone.
