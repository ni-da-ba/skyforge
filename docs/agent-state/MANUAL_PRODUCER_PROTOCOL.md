# Skyforge Manual Producer Protocol

**Status:** machine-facing companion to `ORCHESTRATION_PROTOCOL.md` and `EXECUTION_BOUNDARIES.md`  
**Purpose:** allow interactive/manual Skyforge agents to add parallel capacity without racing the hosted controller, while preserving the canonical separation between orchestration, automated machine evidence, and human/manual execution.

## 0. Execution surface boundary

`EXECUTION_BOUNDARIES.md` is authoritative for **where** work runs.

- The DigitalOcean `skyforge-orchestrator` droplet is the orchestration control plane, not a project build/test/runtime workstation.
- GitHub / GitHub Actions performs automated project machine work and produces automated verification evidence.
- The project owner's local machine performs manual/human-gated execution: Minecraft play/flight/visual review, listening/taste review, interactive inspection, and other qualitative checks.

A manual producer may author repository changes on its ordinary branch, but required automated machine evidence must still come from GitHub Actions. Local machine results may satisfy an explicitly manual/human gate; they do not silently replace an Actions check required by `VALIDATION_POLICY.md`.

## 1. Ownership unit

Ownership is **task/issue scoped**, not lane scoped.

Two Implementation producers may run concurrently when they own different issues and their repository
scope is not semantically coupled. A manual producer must never edit a controller-owned issue, branch,
worktree, or controller-managed PR.

GitHub remains the durable handoff substrate. Conversation state is never sufficient ownership proof.

## 2. Manual producer startup

Before editing:

1. Read `EXECUTION_BOUNDARIES.md`, reconstruct current `main`, relevant lane state/contracts, open PRs, and issue authority.
2. Read hosted controller status from issue #349.
3. Reject any candidate already represented by:
   - `pending_worker` / active bounded-roadmap issue;
   - a controller-managed PR for the same authority;
   - another active external-producer claim;
   - a healthy manual/external producer PR already making information-bearing progress.
4. Select the highest-value independent machine-ready issue. Prefer work that removes prerequisites or
   prepares future critical-path work without speculatively crossing the active roadmap node.
5. Post this trusted control comment on the governing issue **before editing**:

   ```text
   /skyforge-claim-external lane=<LANE>
   ```

   Optional metadata may be supplied when known:

   ```text
   /skyforge-claim-external lane=Implementation branch=<branch> pr=<number>
   ```

6. Confirm `/skyforge-status` reports the issue under `external_producer_claims` before beginning edits.
   If the controller reports the issue is already owned, choose another task; do not override it.

## 3. While running

- Work on an ordinary non-`codex/*` branch/worktree.
- Do not modify the controller's isolated worktree or state directory.
- Do not post a second task directive for the claimed issue.
- Keep the PR/task bounded and preserve normal validation policy.
- Send automated verification to GitHub Actions; do not use the DigitalOcean orchestrator host as an alternate runner.
- Perform visual/play/listening/interactive human gates on the project owner's local machine only.
- Record the exact PR/head, procedure, specimens/seeds/artifacts, and human pass/fail result back into durable GitHub state when a manual gate is completed.
- After opening the PR, refresh the claim with the exact PR/branch metadata so the controller can
  retire it automatically when that PR closes or merges:

  ```text
  /skyforge-claim-external lane=<LANE> branch=<branch> pr=<number>
  ```

- A claim blocks only the exact issue. Other work in the same lane remains schedulable.

## 4. Completion / abandonment

A PR-bound claim retires automatically when the bound PR merges or closes. Closing the governing issue
also retires the claim. Otherwise release explicitly:

```text
/skyforge-release-external
```

Explicit release is required when abandoning a claim before a PR exists. Do not leave unbound claims
as informal parking locks.

## 5. Hosted-controller behavior

The hosted controller treats an active external claim as `RUNNING_EXTERNAL` ownership for that exact
issue. Matching issue-backed task authority remains durable but dispatch is held **before classifier or
worker spend**. The controller retries model-free after the hold interval and proceeds automatically
once the claim retires.

An external claim must never be interpreted as task completion, roadmap completion, a HUMAN_GATE, or
permission to retire the underlying authority.

If a controller worker or managed PR already owns the issue, a new external claim is rejected. This
makes the race barrier reciprocal: manual producers check controller ownership, and the controller
checks manual ownership.

The controller must also respect execution surfaces while coordinating a manual producer: it may
observe and route Actions evidence, but it must not reproduce those builds/tests on the droplet, and it
must surface rather than simulate a local human gate.

## 6. Manual-agent bootstrap prompt

A fresh manual agent should receive this invariant in addition to its lane-specific instructions:

> Read `EXECUTION_BOUNDARIES.md` first. Reconstruct current repository/controller state before selecting
> work. Treat controller-owned tasks, controller-managed PRs, and active external-producer claims as
> unavailable. Claim exactly one unowned governing issue with `/skyforge-claim-external` and verify the
> claim appears in `/skyforge-status` before editing. Work on a separate ordinary branch. Use GitHub
> Actions for automated project machine verification. Use the project owner's local machine for
> visual/play/listening/interactive human gates and record those results durably. Refresh the claim with
> PR and branch metadata after opening a PR. Release an abandoned unbound claim. Never race the active
> DR critical-path node or another healthy producer.
