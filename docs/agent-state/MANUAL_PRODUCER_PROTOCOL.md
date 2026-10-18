# Skyforge Manual Producer Protocol

**Status:** GitHub-native protocol for interactive/manual producers  
**Purpose:** let producers make parallel progress without colliding over the same task.

## 1. Ownership unit

Ownership is **task/issue scoped**, not lane scoped. Two producers may work in the same lane when they have different governing issues and independent semantic scope.

A producer must not take an issue already assigned to another active producer, work on a branch already used by another producer, or duplicate an open PR that is making information-bearing progress on the same scope.

GitHub is the durable handoff record. Conversation state alone is not ownership evidence.

## 2. Producer startup

Before editing:

1. Verify current `main`, relevant lane state and cross-lane contracts, accepted evidence, open issues, and open PRs.
2. Select an authorized bounded issue whose scope is not already covered by an active issue/branch/PR or an unresolved human/product gate.
3. If no governing issue exists, create one with a concrete objective, boundaries, and acceptance criteria.
4. Record ownership on the issue in an ordinary comment before editing:

   ```text
   Taking this issue in branch <branch-name>. Scope: <one-sentence objective>.
   ```

5. Work on a separate ordinary branch and link the issue in the eventual PR.

Do not wait for hosted-controller status, controller-specific issue fields, or controller confirmation to begin a bounded producer task. GitHub issue, branch, and PR state is the ownership record.

## 3. While running

- Keep one bounded acceptance target per issue.
- Preserve lane ownership, current product direction, validation policy, and human-review gates.
- Before expanding scope, check current issue and PR activity for the same semantic area.
- Open a PR linked to the governing issue as soon as the change is reviewable; use draft status when machine or human evidence remains.
- Update the issue with the PR link and any changed boundary or deferred work.
- If abandoning work before opening a PR, add an issue comment that releases the task and explains what remains.

## 4. Ownership conflicts

Treat an issue as owned when it is assigned to another producer, has a current task-ownership comment, or has an open PR/branch advancing the same acceptance target.

When activity is ambiguous, choose a different independent issue or ask the project owner to resolve the overlap. Do not overwrite an active branch, force-update another producer's ref, or close another producer's PR.

## 5. Validation and human gates

- GitHub Actions owns automated builds, tests, and machine-verifiable milestone evidence.
- The local workstation owns interactive Minecraft launch/play, visual review, listening, and other subjective judgments.
- If Actions or a required human review is unavailable, leave that specific evidence pending. Do not substitute a different execution surface.
- Keep each expensive validation tied to the distinct risk it retires and reuse accepted evidence when its dependency surface is unchanged.

## 6. Producer bootstrap

A fresh producer should reconstruct current repository authority from `main`, the relevant issue/PR, lane state, cross-lane contracts, source/tests, and only the merged history needed for the task. Then record issue ownership in GitHub before editing, work on a separate branch, and keep the PR linked to that issue.
