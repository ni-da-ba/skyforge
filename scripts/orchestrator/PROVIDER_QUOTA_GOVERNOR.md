# Provider quota governor

Skyforge uses the authenticated Codex provider allowance as its primary pacing signal whenever the
provider exposes a complete, current weekly quota window. LUNA and TERRA remain worker capability
choices; they do not receive independent hard budgets in provider-governed mode.

## Default policy

- Weekly reserve: **10%**.
- Weekly burst margin: **5 percentage points** ahead of a perfectly linear weekly burn curve.
- Five-hour reserve: **20%**.
- Meter tolerance: **0.25 percentage points** to avoid oscillation on rounded provider readings.
- The provider quota is read immediately before each attempted model turn. The read itself uses
  `account/rateLimits/read` and creates no model turn.

The weekly target remaining quota is:

```text
target_remaining = weekly_reserve
                 + (100 - weekly_reserve) * fraction_of_week_remaining
                 - weekly_burst_margin
```

The target is never allowed below the weekly reserve.

Examples with the defaults:

```text
week just began          target ~= 95% remaining
half the week remains    target ~= 50% remaining
week almost complete     target approaches 10% remaining
```

If actual weekly remaining quota is above the target, Skyforge may continue working. Capacity saved
earlier in the week remains available later; there is no artificial per-day tranche. If actual
remaining quota falls below the target, model work is deferred until elapsed time moves the target
curve down to the current remaining percentage. Repository reconciliation, webhook ingestion, status,
and quota reads remain model-free during that wait.

The five-hour window is not independently budgeted. It only prevents a burst from consuming the final
20% of the active short window. When that reserve is reached, model work defers until the short window
resets.

## Fail-safe fallback

The old local call counters remain in state and continue to record attempts. They are not authoritative
when provider telemetry is valid. They become authoritative fallback ceilings when:

- quota telemetry cannot be read;
- the provider response has no usable weekly window;
- the returned weekly window is stale or incomplete; or
- `SKYFORGE_PROVIDER_QUOTA_GOVERNOR=0` explicitly disables provider pacing.

This means a provider/API-format failure cannot accidentally create unlimited local dispatch.

## Configuration

```text
SKYFORGE_PROVIDER_QUOTA_GOVERNOR=1
SKYFORGE_WEEKLY_RESERVE_PERCENT=10
SKYFORGE_WEEKLY_BURST_MARGIN_PERCENT=5
SKYFORGE_FIVE_HOUR_RESERVE_PERCENT=20
SKYFORGE_QUOTA_METER_TOLERANCE_PERCENT=0.25
SKYFORGE_QUOTA_LIMIT_ID=<optional explicit provider limit id>
```

`SKYFORGE_QUOTA_LIMIT_ID` is normally unnecessary. Without it, the governor prefers a limit bucket
with a weekly window, then a five-hour window, then a Codex-looking limit id. Use the explicit override
only if live telemetry exposes multiple independent provider buckets and the automatic choice is not
the allowance consumed by Skyforge.

## Telemetry

`/skyforge-quota` posts both the normalized provider snapshot and the current governor evaluation.
`/skyforge-status` and `/healthz` expose:

- `quota_governor_enabled`;
- `quota_governor_mode` (`provider`, `fallback_local`, or `disabled_local_fallback`);
- `quota_governor_settings`;
- `last_quota_governor_decision`;
- `last_codex_quota_snapshot`;
- `last_codex_quota_error`.

The persisted governor decision identifies the attempted call kind only for telemetry. Call kind does
not receive a separate provider budget.

## What this does not change

The governor does not change:

- single-worker concurrency;
- classifier/worker routing policy;
- minimum dispatch spacing;
- classifier failure circuit breakers;
- stale-worker/replay protection;
- protected worker paths;
- human/product gates;
- auto-merge (still off unless separately authorized);
- paid API fallback or reset-credit use (still absent).

## Rollout check

After deployment, while the controller is paused:

1. run `/skyforge-quota`;
2. require `governor.authoritative=true` before trusting provider pacing;
3. compare the reported five-hour/weekly percentages and reset times with the visible Codex usage UI;
4. run `/skyforge-quota` again without model work and confirm the meter does not move materially;
5. resume the controller;
6. after the first classifier/worker attempt, inspect `/skyforge-status` and confirm
   `quota_governor_mode=provider` and a fresh `last_quota_governor_decision`.

If the live provider shape is incomplete or unexpected, leave the governor enabled or disable it; in
either case the controller safely falls back to the existing local limits instead of guessing.
