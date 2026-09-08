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
