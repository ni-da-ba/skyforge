# Skyforge always-on orchestration host

This package promotes the accepted AUDIT-0009 local controller to one always-on Linux host without
changing its model-routing, cost ceilings, human gates, or auto-merge policy.

## Architecture

```text
GitHub repository webhook
    -> trusted HTTPS / Caddy
    -> HMAC-SHA256 validation
    -> deterministic event filter
    -> durable AUDIT-0009 event journal
    -> Luna classifier
    -> optional bounded Terra worker
    -> controller-owned GitHub handoff
    -> GitHub Actions
```

The controller itself remains bound to `127.0.0.1:3000`. Caddy is the only public listener.

## Host prerequisites

Use a dedicated Linux host/remote Codex workspace. The service account needs:

- a dedicated clone of `ni-da-ba/skyforge`;
- Python 3 with `venv`;
- `git`;
- authenticated `gh` with permission to push branches, open PRs/comments, and administer this
  repository webhook;
- Codex CLI/SDK authenticated with the intended ChatGPT account;
- Caddy;
- `curl`;
- `sudo` for initial service installation.

The DigitalOcean Codex Universal image is an appropriate starting point. The host may be destroyed
without losing accepted project truth because GitHub remains authoritative; only ignored local
orchestrator state and an interrupted unpushed worker require the host disk.

## Public hostname

Use a hostname whose A/AAAA record resolves to the host. Caddy obtains and renews the public TLS
certificate automatically.

A project-owned domain is preferred. For a temporary pilot, an IP-encoded public DNS name may be used
if its dependency is acceptable; do not weaken GitHub SSL verification.

## One-time authentication

As the service user:

```bash
gh auth login
codex login --device-auth
gh auth status
codex login status
```

Device authentication allows the browser approval to happen on another machine while the credential
is stored on the host. Do not place ChatGPT/Codex tokens or GitHub tokens in the repository.

## Install

Generate a high-entropy webhook secret on the host, for example:

```bash
export SKYFORGE_WEBHOOK_SECRET="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
export SKYFORGE_PUBLIC_HOSTNAME="orchestrator.example.com"

./scripts/orchestrator/install_hosted.sh
```

The installer:

1. creates the ignored Python virtualenv if needed;
2. writes the secret to `/etc/skyforge-orchestrator/env` with mode `0600`;
3. installs/enables the systemd controller unit;
4. installs the dedicated Caddy configuration and enables Caddy;
5. waits for the trusted `/healthz` endpoint;
6. creates or updates one GitHub repository webhook with the exact event allowlist.

It does **not** enable autonomous merge or API-key billing fallback.

## Reboot/offline semantics

Each hosted startup records a compact fingerprint of:

- `origin/main`;
- open PR state;
- recent GitHub Actions state.

If that fingerprint differs from the previous startup baseline, the controller journals one synthetic
`reconcile` wake. The classifier then reasons from current repository truth instead of depending on
every webhook that may have been missed while the host was offline.

This is intentionally conservative. A reboot after ordinary online activity can cause one extra Luna
classification, but repository work is not silently missed.

## Health

```text
GET /healthz
```

returns non-secret operational state including pending-event count, circuit-breaker state, daily call
counters, managed-PR count, and last startup reconciliation time.

Useful commands:

```bash
curl -fsS "https://$SKYFORGE_PUBLIC_HOSTNAME/healthz"
sudo systemctl status skyforge-orchestrator caddy
sudo journalctl -u skyforge-orchestrator -u caddy --since "-30 min"
```

## Acceptance check after deployment

1. `/healthz` returns HTTP 200 over trusted HTTPS.
2. GitHub's webhook delivery log shows a 2xx signed delivery.
3. A manual `/skyforge-orchestrate` comment on issue #349 reaches the host exactly once.
4. Reboot the host.
5. `skyforge-orchestrator.service` and `caddy.service` return active automatically.
6. Verify `/healthz` again and confirm startup reconciliation state is retained.

Do not deliberately trigger a Terra worker merely to prove hosting. The signed ping/manual wake and
normal repository activity are sufficient infrastructure evidence.

## Rollback

Stop the hosted layer without touching project state:

```bash
sudo systemctl disable --now skyforge-orchestrator caddy
```

Then disable/delete the repository webhook in GitHub. The ordinary repository-first workflow, CI, and
Audit watchdog continue unchanged.


## Daily value telemetry

Continuous hosting is not accepted merely because the service stays up. AUDIT-0011 makes the host
publish a **model-free** daily value report at approximately 08:05 America/Chicago.

The timer:

```text
skyforge-value-report.timer
    -> scripts/orchestrator/daily_value_report.py
    -> .skyforge-orchestrator/reports/<period-start-UTC>.{json,md}
    -> controller-marked comment on issue #378
```

The controller marker means the report's own GitHub comment is deterministically filtered and cannot
wake Luna. The reporter imports no Codex SDK and spends zero model turns.

Each report records:

- actual elapsed host hours multiplied by the selected Droplet's configured hourly rate;
- events seen / filtered / actionable / duplicate / signature-rejected;
- manual `/skyforge-orchestrate` wakes separately from Audit/watchdog wakes;
- mean actionable-event-to-classifier dispatch latency;
- Luna attempts and NOOP ratio;
- Terra attempts, handoffs, resumes, and no-change rate;
- human gates, capacity/authentication blocks, dispatch failures, restarts, and reconciliation activity;
- all Skyforge PR activity versus controller-owned `codex/*` PR activity;
- controller PRs created/merged overnight (22:00–08:00 America/Chicago);
- a trailing seven-report cost/yield summary.

The actual hourly Droplet rate is required at install time:

```bash
export SKYFORGE_DROPLET_HOURLY_USD="<actual selected rate>"
```

Do not hard-code an assumed provider price into the reporter when the provisioned size can change.

### Keep / rework / cancel evaluation

The daily advisory is deliberately conservative and cannot destroy infrastructure.

- **KEEP** — repeated merged controller PRs, or repeated information-bearing worker handoffs at
  acceptable yield.
- **REWORK** — the host is active but worker no-change, low handoff yield, dispatch failures, or
  capacity blocking are wasting material runtime.
- **CANCEL_CANDIDATE** — after enough elapsed/activity evidence, Skyforge remains active but paid
  hosted uptime produces no merged controller work and too few useful handoffs.
- **INSUFFICIENT_DATA** — the project or worker sample is too quiet to justify a hosting conclusion.

A quiet Skyforge week is not automatically a failed hosting week. The report compares controller work
with overall project PR activity so lack of opportunity is distinguishable from lack of contribution.

The automated signal is evidence for Audit/Nicholas; it is not a substitute for qualitative review.
In particular, branch races, human-gate bypass, or low-value speculative work can make hosting
unacceptable even when raw PR counts look good.

## Cancellation / decommission

Host-side cancellation is intentionally explicit and reversible:

```bash
./scripts/orchestrator/decommission_hosted.sh --confirm
```

That command:

1. writes/posts one final value report;
2. removes the matching GitHub repository webhook;
3. stops/disables the daily report timer;
4. stops/disables the orchestrator;
5. stops/disables Caddy;
6. leaves the final local JSON/Markdown reports available for export.

It **does not** give the VM credentials to destroy itself. Provider-side destruction remains the final
external step. After host-side teardown, destroy the DigitalOcean Droplet from the provider control
plane and verify that no pilot backup, snapshot, block volume, reserved IP, load balancer, database, or
other separately billable resource remains.

Do not use power-off as cancellation. A powered-off Droplet may still be billable.
