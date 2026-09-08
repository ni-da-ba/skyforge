# Skyforge event-driven Codex pilot

This directory contains a **local development pilot** for low-usage autonomous Skyforge orchestration.

It does not replace the hourly ChatGPT Audit watchdog.

```text
GitHub activity
    -> gh webhook forward
    -> local deterministic filter/debounce
    -> Luna orchestration classifier only when useful
    -> Terra bounded worker only when actionable
    -> controller commits/pushes/opens draft PR
    -> GitHub CI
    -> next completion event

GitHub silence / dead producer
    -> hourly Audit watchdog
    -> Audit RESTART/LOOP-RISK comment
    -> issue_comment webhook
    -> local orchestrator wake
```

## Why SDK + App Server

For automation/jobs, the Codex Python SDK is the supported high-level surface. The SDK controls the
local Codex runtime/App Server and exposes persistent thread start/resume semantics without requiring
this repository to implement JSON-RPC itself.

The controller lazily imports `openai_codex`, so ignored GitHub events do not even start the Codex
SDK/runtime.

## Safety model

The pilot intentionally separates model authority from GitHub authority.

**Codex models:**
- Luna classifier: read-only repository access; returns a JSON decision.
- Terra worker: workspace-write local access; edits/tests one bounded objective.
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
- GitHub CLI webhook-forwarding extension

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

```bash
./scripts/orchestrator/run_pilot.sh
```

On first start the runner creates an ignored local virtualenv under
`.skyforge-orchestrator/venv` and installs `openai-codex`.

The process binds only to `127.0.0.1`.

### Model routing

Defaults:

```text
classifier/orchestrator = gpt-5.6-luna / low
bounded worker          = gpt-5.6-terra / medium
```

Override if the current Codex account exposes different model identifiers:

```bash
export SKYFORGE_ORCHESTRATOR_MODEL="<available low-cost Codex model>"
export SKYFORGE_WORKER_MODEL="<available balanced Codex model>"
```

Do not make Sol the default. Escalate difficult work manually or through future policy only when the
lower-cost worker fails to produce information-bearing progress.

### Local usage guardrails

The pilot also has hard call-count ceilings independent of the Codex account's own allowance:

```text
classifier attempts / UTC day = 48
worker attempts / UTC day     = 8
```

Override them only deliberately:

```bash
export SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY=48
export SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY=8
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
- an Audit/restart/loop-risk/human-gate comment;
- a manual `/skyforge-orchestrate` issue/PR comment.

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
producer PR, its remote branch is fetched for comparison, but stale history is not blindly merged.

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
`/skyforge-orchestrate` is itself an actionable webhook event.

If Codex reaches a human gate, the local controller posts a
`[skyforge-orchestrator] HUMAN_GATE` comment. Its own comment is ignored by the webhook filter to
prevent recursion; the hourly watchdog/user-facing GitHub notifications remain the escalation layer.

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
- a durable, deduplicated pending-event journal;
- a cached classifier decision tied to the event batch it consumed;
- interrupted worker branch identity;
- quota/rate/authentication/controller retry state;
- daily call-budget counters and pilot metrics.

Actionable webhooks are journaled **before** the HTTP handler returns success. A process crash or Codex
failure therefore cannot silently consume the wake.

If Luna succeeds but Terra is interrupted, the classifier decision and worker branch are retained so
the next attempt can continue the bounded objective without paying to rediscover it. New repository
events are retained separately and are classified after the interrupted objective reaches its handoff.

The parent thread rotates after 24 useful turns by default. The new thread reconstructs from GitHub and
`AGENTS.md`, preventing an indefinitely growing orchestration conversation from becoming another
source of context drag.

Terra workers are intentionally fresh/bounded threads.

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

## Production phase, only if pilot succeeds

Do **not** use `gh webhook forward` as permanent infrastructure.

A later production controller should use a real HTTPS webhook receiver with GitHub signature
verification and the same deterministic event filter. The model-facing portion can remain the Codex SDK
and resumable parent thread.

Track pilot outcomes in issue #349.
