# Skyforge event-driven Codex orchestration

This directory contains the accepted low-usage orchestration controller plus both local-development and
always-on hosted transports.

It does not replace the hourly ChatGPT Audit watchdog.

Local development:

```text
GitHub activity
    -> gh webhook forward
    -> local deterministic filter/debounce
    -> Luna orchestration classifier only when useful
    -> Luna bounded worker for low-risk reconciliation when sufficient
    -> Terra bounded worker only for substantive implementation/debugging
    -> controller commits/pushes/opens draft PR
    -> GitHub CI
    -> next completion event

GitHub silence / dead producer
    -> hourly Audit watchdog
    -> Audit RESTART/LOOP-RISK comment
    -> issue_comment webhook
    -> orchestrator wake
```

Hosted production transport:

```text
GitHub repository webhook
    -> trusted HTTPS / Caddy
    -> GitHub HMAC-SHA256 validation
    -> same deterministic filter/debounce
    -> same Luna/Terra dispatch policy
```

The hosted deployment package is documented in `deploy/orchestrator/README.md`.

## Why SDK + App Server

For automation/jobs, the Codex Python SDK is the supported high-level surface. The SDK controls the
local Codex runtime/App Server and exposes persistent thread start/resume semantics without requiring
this repository to implement JSON-RPC itself.

The controller lazily imports `openai_codex`, so ignored GitHub events do not even start the Codex
SDK/runtime.

## Safety model

The pilot intentionally separates model authority from GitHub authority.

**Codex models:**
- Luna classifier: read-only repository access; returns a JSON decision plus worker tier/scope.
- Luna worker: workspace-write local access for tightly scoped docs/state/evidence reconciliation.
- Terra worker: workspace-write local access for substantive implementation/runtime/debugging.
- Neither model is expected to use GitHub/network writes.

**Outer controller:**
- synchronizes the dedicated clone;
- selects/creates a controller-managed branch;
- validates `git diff --check`;
- commits without amend/rewrite;
- pushes without force;
- opens draft PRs;
- optionally merges **only controller-managed PRs** after green checks when auto-merge is explicitly
  enabled.

The controller refuses to run unless `SKYFORGE_ORCHESTRATOR_DEDICATED_CLONE=1` is set. The supplied
runner sets it, but use the runner only in a clone dedicated to this pilot.

Auto-merge is **off by default**. After several clean pilot cycles, it can be enabled with
`SKYFORGE_ORCHESTRATOR_AUTO_MERGE=1` or `--auto-merge`.

## Prerequisites

- Python 3.10+
- Git
- GitHub CLI (`gh`) authenticated for `ni-da-ba/skyforge`
- Codex/ChatGPT authentication available to the Codex SDK
- GitHub CLI webhook-forwarding extension **for local development only**

Review the extension before installing it; GitHub CLI extensions execute local code.

```bash
gh extension install cli/gh-webhook
```

The pilot uses `gh webhook forward` only for local development. It is not a production webhook
receiver and should not be exposed as one.

## Dedicated clone setup

Use a separate clone so an autonomous checkout can never disturb normal interactive work.

```bash
git clone https://github.com/ni-da-ba/skyforge.git skyforge-orchestrator
cd skyforge-orchestrator
git pull --ff-only

# Configure a normal commit identity if this clone does not inherit one.
git config user.name "Skyforge Codex Orchestrator"
git config user.email "<your GitHub commit email>"
```

If the Codex runtime asks whether to trust this repository, trust only this known Skyforge clone after
reviewing `AGENTS.md` and the repository configuration.

## Start the pilot

Cross-platform (recommended, including Windows):

```text
python scripts/orchestrator/run_pilot.py
```

On Unix-like systems the original shell runner remains available:

```bash
./scripts/orchestrator/run_pilot.sh
```

On first start either runner creates an ignored local virtualenv under
`.skyforge-orchestrator/venv` and installs the pinned `openai-codex==0.147.0` SDK. The Python launcher uses argument-array
subprocesses rather than shell quoting, so repository paths containing spaces are supported on Windows
and Unix-like systems.

The process binds only to `127.0.0.1`. Stopping the launcher also terminates the local controller.

### Model routing

Defaults:

```text
classifier/orchestrator      = gpt-5.6-luna / low
low-risk bounded worker      = gpt-5.6-luna / low
substantive bounded worker   = gpt-5.6-terra / medium
```

Routing rule:

```text
docs / lane-state / evidence reconciliation
    -> Luna worker with a narrow controller-enforced path allowlist

source implementation / runtime debugging / substantial test-build integration
    -> Terra worker
```

Override if the current Codex account exposes different model identifiers:

```bash
export SKYFORGE_ORCHESTRATOR_MODEL="<available low-cost Codex model>"
export SKYFORGE_LUNA_WORKER_MODEL="<available low-cost Codex model>"
export SKYFORGE_WORKER_MODEL="<available balanced Codex model>"
```

The Luna worker shares the same 24-call daily Luna ceiling as classifier turns. Terra retains the
separate 4-worker daily ceiling. Do not make Sol the default. Escalate only when cheaper execution
cannot safely retire the stated uncertainty.

### Local usage guardrails

The pilot also has hard call-count ceilings independent of the Codex account's own allowance:

```text
total Luna calls / UTC day   = 24
  (classifier + Luna worker)
Terra worker attempts / day   = 4
```

Override them only deliberately:

```bash
export SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY=24
export SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY=4
```

These are safety ceilings rather than dollar accounting. The local state also records attempt,
NOOP/handoff, retry-block, restart-replay, and recovery counters so issue #349 can evaluate accepted
progress against model usage before any broader deployment.

## Event policy

No Codex turn is created for:

- producer branch pushes;
- PR synchronize events;
- ordinary comments;
- PR-open events before their first CI completion;
- workflow events that have not completed;
- controller-authored comments.

Codex may be woken by:

- pushes to `main`;
- meaningful PR lifecycle changes;
- a completed workflow **after all runs on that exact head are quiescent**;
- an Audit/restart/loop-risk/human-gate comment **from a trusted GitHub actor**;
- a manual `/skyforge-orchestrate` issue/PR comment **from a trusted GitHub actor**.

Hosted issue-comment wake authority defaults to `ni-da-ba` and may be explicitly configured with
`SKYFORGE_TRUSTED_GITHUB_ACTORS`. Fork/external PR and workflow payloads are ignored before Codex.

Events are debounced (default 25 seconds) and Codex dispatch has a minimum interval (default 120
seconds). A workflow completion does not wake Codex while another run for the same head is still active.

## Worker ownership

The classifier must return one of:

- `NOOP`
- `DISPATCH`
- `HUMAN_GATE`
- `MERGE`

During the pilot, healthy ordinary ChatGPT/manual producers are `RUNNING_EXTERNAL` and should not be
duplicated.

A new Codex worker branch is created from current `origin/main`. If the objective refers to an old
producer PR, the controller also inventories that PR's changed paths and tells the worker to treat them
as durable existing work rather than recreating them merely to copy evidence onto the controller branch.
Luna reconciliation workers additionally receive a controller-enforced narrow allowed-path scope.

Subsequent events may continue a controller-managed branch recorded in the ignored local state file.

## Human gates and watchdog

The hourly ChatGPT watchdog remains the independent supervisor because **absence of events cannot be a
webhook**.

It should continue to:

- send the hourly orchestrator brief;
- detect UI-frozen + repository-silent producers;
- detect evidence saturation/loop risk;
- surface human visual/play/listening/strategy gates;
- post material Audit interventions to GitHub.

An Audit comment containing terms such as `RESTART RECOMMENDED`, `LOOP RISK`, or
`/skyforge-orchestrate` is itself an actionable webhook event. Trusted Audit directives are preserved
as structured classifier input, including their exact bounded objective and signal timestamp; they are
not reduced to a generic "Audit signal." A `RESTART RECOMMENDED` directive is authoritative that the
prior producer was stale at that instant. PR/issue `updatedAt` is never sufficient evidence of producer
recovery because comments and bookkeeping mutate it; only information-bearing post-signal evidence
such as a new producer head/commit, attributable Actions movement, or an already-managed recovery may
suppress the fresh-worker dispatch. If Luna nevertheless returns NOOP while the target remains open at
the signal-time head, the controller performs one guarded reclassification. A second unsupported NOOP
is converted to a human gate rather than silently consuming the restart. Classifier-policy changes
automatically rotate the persistent Luna parent thread while preserving budgets, pending events, and
worker state.

If Codex reaches a human gate, the local controller posts a
`[skyforge-orchestrator] HUMAN_GATE` comment. Its own comment is ignored by the webhook filter to
prevent recursion; the hourly watchdog/user-facing GitHub notifications remain the escalation layer.

## Remote control

Trusted GitHub actors can control and inspect the hosted dispatcher without spending a model turn:

```text
/skyforge-pause
/skyforge-resume
/skyforge-status
```

Pause preserves incoming actionable events in the durable journal but starts no new classifier/worker
dispatch. Resume schedules the retained batch. Status posts a controller-marked, non-secret snapshot
to the issue or PR containing the command, including the loaded runtime head, checkout head, pause /
breaker state, queued-event count, worker state, and daily Luna/Terra counters. The status comment is
ignored by the webhook filter and therefore cannot recurse. Untrusted commenters cannot invoke these
controls.

## Manual wake

On issue or PR #349:

```text
/skyforge-orchestrate
```

This is useful for testing the event path without manufacturing a code change.

## Local state and thread rotation

Ignored local state lives under:

```text
.skyforge-orchestrator/state.json
```

It stores:
- persistent Luna parent thread id;
- parent turn count;
- last dispatch time;
- controller-managed branch/PR ownership;
- durable human-gate surfacing records keyed to current PR state;
- a durable, deduplicated pending-event journal;
- a cached classifier decision tied to the event batch it consumed;
- interrupted worker branch identity;
- quota/rate/authentication/controller retry state;
- daily call-budget counters and pilot metrics.

Actionable webhooks are journaled **before** the HTTP handler returns success. A process crash or Codex
failure therefore cannot silently consume the wake.

If Luna succeeds but a worker is interrupted, the classifier decision, worker branch, and linked
worker-worktree path are retained so the next attempt can continue the bounded objective without paying
to rediscover it. The service/controller checkout stays on `main`; bounded workers execute in ignored
linked worktrees under `.skyforge-orchestrator/worktrees/`. A worker safety pause therefore cannot pin
the running controller to stale branch code or dirty the controller checkout.

Once a worker completes, the controller persists a separate `handoff` stage before commit/push/PR
operations. That handoff is idempotent: an existing local commit, remote branch, or already-open PR is
reused rather than rerunning the worker or creating a duplicate. The linked worktree is retired only
after the consumed event and pending-worker state have been durably cleared; a crash before that point
leaves the worktree available for replay. New repository events are retained separately and are
classified after the interrupted objective reaches its handoff.

The parent thread rotates after 24 useful turns by default. The new thread reconstructs from GitHub and
`AGENTS.md`, preventing an indefinitely growing orchestration conversation from becoming another
source of context drag.

Bounded Luna and Terra workers are intentionally fresh threads; only the lightweight classifier parent is persistent.

Before controller handoff, worker changes are rejected if they touch the orchestration/control plane:
`scripts/orchestrator/**`, `deploy/orchestrator/**`, `.github/**`, `AGENTS.md`, or canonical
governance/Audit documents. Scoped workers are also rejected if they edit outside their explicit
allowlist. Either condition creates a durable safety pause with no autonomous commit/push. Safety pauses
do not also create a transient retry circuit-breaker, preventing a misleading controller-error loop.

### Codex-limit and failure recovery

Model-call failures are classified conservatively:

- quota/usage-limit failures: default one-hour circuit-breaker backoff;
- rate/capacity failures: default five-minute backoff;
- authentication failures: default one-hour backoff;
- unknown model/controller failures: default five-minute fail-closed backoff;
- local daily call-budget exhaustion: retry after the next UTC-day reset.

Backoff durations can be overridden with the corresponding
`SKYFORGE_ORCHESTRATOR_*_BACKOFF_SECONDS` variables.

While blocked, incoming actionable events are still journaled but **do not start Codex**. When the
breaker expires, the controller reconstructs against current repository state. After a process restart,
pending non-worker decisions are deliberately reclassified; an actual interrupted worker is resumed on
its recorded branch so partial work is not discarded.

Human-gate comments are once-per-current-PR-head by default. Before posting, the controller checks
durable local gate state and can seed that state from an existing controller gate comment posted after
the current PR head commit. Rewording the same gate therefore does not repeatedly notify the owner; a
new PR head may legitimately surface the gate again.

When `sync_main()` advances across a changed `scripts/orchestrator/skyforge_orchestrator.py` runtime, the running process
durably records a runtime-refresh request and exits non-zero. The hosted systemd unit's
`Restart=on-failure` then reloads the synchronized Python from stable `main`, and the replacement
process replays the retained event journal. Documentation-only movement does not restart the process.
Dependency/installer changes remain an explicit deployment concern rather than an automatic package
installation.

The hourly Audit watchdog remains the independent path for a prolonged outage or a human decision that
should not wait for the retry timer.

## Auto-merge pilot stage

Auto-merge is disabled initially.

After several clean cycles, enabling it permits **only** a PR recorded in local controller state to
merge, and only when all visible status checks are completed with success/skipped/neutral conclusions.

The controller never force-pushes and does not delete branches.

## Stop / rollback

Press Ctrl-C. There is no cloud daemon to clean up.

Pause the pilot if:
- idle classifier calls dominate useful worker calls;
- Codex usage rises disproportionately to accepted progress;
- it races a healthy normal producer;
- it invents speculative work;
- it repeatedly opens low-value PRs;
- it crosses a human/product gate;
- local webhook forwarding is unreliable.

The repository-first workflow, Audit watchdog, validation policy, and ordinary producer chats continue
to work without this controller.

## Always-on hosted mode

AUDIT-0010 promotes the accepted controller to an always-on host without changing its model-facing
policy. Hosted mode requires:

- `--require-webhook-secret` / `SKYFORGE_WEBHOOK_SECRET`;
- `--startup-reconcile` so repository changes across host downtime become one synthetic current-state
  wake;
- trusted HTTPS in front of the localhost controller;
- systemd restart-on-boot;
- the exact repository webhook allowlist.

Use `deploy/orchestrator/README.md` and `scripts/orchestrator/install_hosted.sh`.

The hosted installer refuses root execution and refuses activation unless GitHub server-side protection
for `main` is verifiably requiring pull requests + status checks while blocking force pushes and
branch deletion **and applying the protection to administrators**. The pinned Python SDK is also the authentication surface; after the virtualenv exists,
run:

```bash
.skyforge-orchestrator/venv/bin/python scripts/orchestrator/codex_auth.py --device-login
```

The device URL/code may be completed on another device. No API-key billing fallback is introduced.

The local `gh webhook forward` transport remains useful for development but is not permanent
infrastructure. Hosted mode still leaves auto-merge disabled and does not enable API-key billing
fallback.

Track local-pilot economics in issue #349 and hosted activation in issue #369.
