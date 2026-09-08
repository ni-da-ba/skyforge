# Skyforge Codex App Server Orchestrator Pilot

This directory contains the Phase-2 event-driven orchestration pilot described by
`docs/agent-state/ORCHESTRATION_PROTOCOL.md` and issue #349.

## Why this exists

A fixed two-hour Codex heartbeat is intentionally cheap, but it still wakes when nothing happened and
can delay useful follow-up by almost two hours.

The App Server pilot changes the trigger from:

```text
clock -> Codex turn -> inspect whether anything changed
```

to:

```text
GitHub event -> cheap local filter -> Codex turn only if potentially actionable
```

The local webhook/filter process does not use a model. Agentic usage starts only after an event passes
the filter and a Codex turn is submitted.

## Relationship to the hourly Audit watchdog

**Do not replace the watchdog.** The two systems have different failure domains.

### App Server controller — fast path

Owns event-driven continuation:

- relevant PR opened/synchronized/closed/ready-for-review;
- relevant workflow completed;
- `main` advanced;
- human PR comment that may change a worker decision.

It should react quickly, dispatch at most one bounded worker during the pilot, and stop at human gates.

### ChatGPT Audit watchdog — independent safety path

Remains hourly and owns:

- detecting **silence** (there is no webhook for a dead worker doing nothing);
- frozen-session liveness and restart recommendations;
- evidence saturation / low-information rerun detection;
- branch-scope / synchronization / bookkeeping health;
- cross-lane contradictions;
- human strategy/manual-gate escalation;
- hourly development summary for the project owner;
- detecting that this controller itself appears ineffective or offline from repository outcomes.

Conceptually:

```text
GitHub events -----------------------> App Server orchestrator
    |                                         |
    |                                         v
    |                                  bounded Codex work
    |                                         |
    v                                         v
repository history <------------------------- GitHub
    |
    v
hourly Audit watchdog
    |
    +--> liveness / silence / policy / human gates / orchestrator summary
```

The controller is an accelerator. Audit is the circuit breaker and independent observer.

## Usage policy

During the pilot:

- orchestrator model: GPT-5.6 Luna where available;
- one persistent orchestrator thread;
- maximum one bounded worker dispatch per event batch;
- healthy ordinary ChatGPT/manual producer activity => `RUNNING_EXTERNAL`; Codex does not race it;
- Terra is preferred for routine bounded worker work where model routing is available;
- Sol is an escalation tool, not the default;
- no CI polling loops;
- no retry loop after a failed Codex turn;
- event batches are debounced so one logical repository update does not generate multiple turns;
- the hourly Audit watchdog remains enabled.

## Local requirements

- a current Codex CLI with `codex app-server`;
- ChatGPT/Codex authentication already configured for that CLI;
- a local clone of `ni-da-ba/skyforge`;
- Python 3.10+;
- a GitHub webhook secret;
- an HTTPS route from GitHub to the local listener if using GitHub-hosted webhooks directly.

The pilot code uses only the Python standard library.

## First-run safety sequence

### 1. Test locally

From the repository root:

```bash
python -m unittest discover -s tools/orchestrator -p 'test_*.py'
```

### 2. Start in dry-run mode

Set a webhook secret and launch:

```bash
export SKYFORGE_GITHUB_WEBHOOK_SECRET='replace-with-random-secret'
python tools/orchestrator/app_server_controller.py --dry-run
```

On Windows PowerShell:

```powershell
$env:SKYFORGE_GITHUB_WEBHOOK_SECRET = 'replace-with-random-secret'
python tools/orchestrator/app_server_controller.py --dry-run
```

Dry-run mode verifies signatures, filters, deduplicates, debounces, and prints the exact prompt that
**would** have been sent to Codex. It does not start App Server and consumes no Codex agentic usage.

### 3. Configure the GitHub webhook

Repository webhook target:

```text
https://<your-ingress>/github-webhook
```

Use the same random secret as `SKYFORGE_GITHUB_WEBHOOK_SECRET`.

Subscribe only to the event families the pilot understands:

- Pushes;
- Pull requests;
- Workflow runs;
- Issue comments.

The local filter ignores feature-branch pushes, bot PR comments, non-Skyforge repositories, and
non-actionable PR/workflow actions.

A tunnel/reverse proxy may be used for the pilot. The repository intentionally does not prescribe a
particular public-ingress vendor or store tunnel credentials.

### 4. Validate dry-run event quality

Let normal development produce several events. Verify that:

- a PR synchronization creates one debounced candidate prompt;
- a completed workflow creates a candidate prompt;
- a `main` push creates a candidate prompt;
- irrelevant PR actions and feature-branch pushes do not;
- a cluster of related GitHub deliveries becomes one event batch.

Do not enable Codex dispatch until the false-positive rate is acceptably low.

### 5. Smoke-test the installed App Server protocol

Pin/record the Codex CLI version used for the pilot. OpenAI's App Server protocol is designed to be
client-friendly/backward-compatible, but this repository should still test the exact installed build
before unattended execution.

The controller uses the documented protocol primitives:

```text
initialize
thread/start or thread/resume
turn/start
turn/completed
```

If the installed Codex build rejects the request shape, stop the pilot and reconcile the adapter with
the JSON schema generated by that installed `codex app-server` rather than adding retries or guessing.

### 6. Enable Codex dispatch

After dry-run quality and App Server smoke are satisfactory:

```bash
export SKYFORGE_GITHUB_WEBHOOK_SECRET='replace-with-random-secret'
export SKYFORGE_REPO='/absolute/path/to/skyforge'
export SKYFORGE_ORCHESTRATOR_MODEL='gpt-5.6-luna'
python tools/orchestrator/app_server_controller.py
```

The controller stores the persistent thread id in:

```text
.skyforge-orchestrator-state.json
```

That file is local state and must not be committed.

## Environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `SKYFORGE_GITHUB_WEBHOOK_SECRET` | required | HMAC-SHA256 webhook verification |
| `SKYFORGE_REPO` | current directory | local Skyforge checkout used by App Server |
| `SKYFORGE_CODEX_BIN` | `codex` | Codex CLI/App Server executable |
| `SKYFORGE_ORCHESTRATOR_MODEL` | `gpt-5.6-luna` | orchestrator model |
| `SKYFORGE_ORCHESTRATOR_HOST` | `127.0.0.1` | listener bind address |
| `SKYFORGE_ORCHESTRATOR_PORT` | `8765` | listener port |
| `SKYFORGE_ORCHESTRATOR_DEBOUNCE_SECONDS` | `20` | combine related GitHub deliveries |

## Event filtering

The current filter intentionally wakes Codex only for:

- pull request `opened`, `reopened`, `ready_for_review`, `synchronize`, `closed`;
- workflow run `completed`;
- push to `refs/heads/main`;
- non-bot comment on a pull request.

This is conservative but still broader than the eventual optimum. Issue #349 should record false
positives and missing useful triggers. Tighten deterministic filtering before adding more agent logic.

## Failure behavior

The controller deliberately **does not repeatedly retry Codex failures**. A failure is logged and the
next independent repository event may produce a new turn. The hourly Audit watchdog remains capable of
noticing that expected progress is absent.

If App Server requests an approval, the unattended turn stops and the approval becomes a human gate.
Do not configure the pilot to auto-approve destructive/external actions merely for continuity.

## Cost comparison

A two-hour heartbeat can create roughly 12 orchestrator turns per day even if nothing changes.

This controller can create **zero Codex turns during an idle day**. During active development it will
create turns only for debounced actionable event batches. Whether that is cheaper in practice depends
on event frequency and how often an event leads to a useful bounded worker; issue #349 is the evidence
record for that decision.

## Pilot success / rollback

Keep the App Server controller if it produces materially faster accepted progress per unit of agentic
usage than the scheduled heartbeat.

Pause it if:

- repository events wake Codex too frequently for little work;
- duplicate worker races appear;
- unattended approvals become common;
- it crosses product/human gates;
- controller maintenance becomes a project of its own;
- the shared Codex allowance drains materially faster without corresponding milestone throughput.

The repository-first agent architecture, `AGENTS.md`, Validation Policy, and hourly Audit watchdog remain
valid whether this pilot succeeds or is removed.
