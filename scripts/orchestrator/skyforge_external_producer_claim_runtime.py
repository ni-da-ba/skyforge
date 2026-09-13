#!/usr/bin/env python3
"""Durable task-level coordination between hosted and manual Skyforge producers.

A manual/ChatGPT producer claims one issue before beginning repository work. The hosted controller
records that claim as durable local controller state and holds matching issue-backed task authority
*before* classifier or worker spend. The task event/decision is not retired: release of the claim makes
the same durable authority runnable again.

Claims are deliberately issue-scoped rather than lane-scoped so independent Implementation work may
continue in parallel. A claim may optionally bind a PR; closed/merged PRs are pruned model-free.
Explicit release is always available. Claims without a PR fail closed until release or issue closure.
Normal claim rejection is a handled control outcome, never a webhook error/replay loop.
"""

from __future__ import annotations

import re
import shlex
from datetime import datetime, timezone
from typing import Any, Iterable

import skyforge_control_replay_base as _base


core = _base.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_external_producer_claim_runtime.py"
core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

CLAIM_COMMAND = "/skyforge-claim-external"
RELEASE_COMMAND = "/skyforge-release-external"
DEFAULT_HOLD_SECONDS = 900

_ORIGINAL_CLASSIFY_CONTROL_COMMAND = core.classify_control_command
_ORIGINAL_APPLY_CONTROL_PAYLOAD = core.Orchestrator._apply_control_payload
_ORIGINAL_DISPATCH = core.Orchestrator.dispatch
_ORIGINAL_HEALTH_SNAPSHOT = core.Orchestrator.health_snapshot


def _command_line(payload: dict[str, Any]) -> str:
    body = str((payload.get("comment") or {}).get("body") or "")
    return next((line.strip() for line in body.splitlines() if line.strip()), "")


def _parse_claim_options(payload: dict[str, Any]) -> dict[str, Any]:
    line = _command_line(payload)
    try:
        tokens = shlex.split(line)
    except ValueError as exc:
        raise ValueError(f"invalid claim syntax: {exc}") from exc
    if not tokens or tokens[0].lower() != CLAIM_COMMAND:
        return {}

    result: dict[str, Any] = {}
    for token in tokens[1:]:
        if "=" not in token:
            raise ValueError(
                "claim options must use key=value syntax (supported: pr, lane, branch)"
            )
        key, value = token.split("=", 1)
        key = key.strip().lower()
        value = value.strip()
        if key not in {"pr", "lane", "branch"}:
            raise ValueError(f"unsupported claim option: {key}")
        if not value:
            raise ValueError(f"claim option {key} may not be empty")
        result[key] = value

    if "pr" in result:
        try:
            pr_number = int(result["pr"])
        except (TypeError, ValueError) as exc:
            raise ValueError("claim pr must be a positive integer") from exc
        if pr_number <= 0:
            raise ValueError("claim pr must be a positive integer")
        result["pr"] = pr_number
    if "lane" in result:
        lane = str(result["lane"]).strip()
        allowed = {"Implementation", "Authorship", "Content", "Music", "Presentation", "Audit"}
        canonical = next((value for value in allowed if value.lower() == lane.lower()), None)
        if canonical is None:
            raise ValueError(f"unsupported external-producer lane: {lane}")
        result["lane"] = canonical
    if "branch" in result:
        branch = str(result["branch"]).strip()
        if not re.fullmatch(r"[A-Za-z0-9._/-]+", branch):
            raise ValueError("claim branch contains unsupported characters")
        result["branch"] = branch
    return result


def classify_control_command(
    event: str,
    payload: dict[str, Any],
    *,
    trusted_actors: Iterable[str] = core.DEFAULT_TRUSTED_GITHUB_ACTORS,
) -> str | None:
    existing = _ORIGINAL_CLASSIFY_CONTROL_COMMAND(
        event,
        payload,
        trusted_actors=trusted_actors,
    )
    if existing is not None:
        return existing
    if (event or "").strip().lower() != "issue_comment":
        return None
    if str(payload.get("action") or "").lower() != "created":
        return None
    if not core._trusted_actor(payload, trusted_actors):
        return None

    lowered = _command_line(payload).lower()
    if lowered == RELEASE_COMMAND:
        return "release_external"
    if lowered == CLAIM_COMMAND or lowered.startswith(CLAIM_COMMAND + " "):
        # Recognition must not parse/throw: malformed trusted controls are durably rejected by the
        # apply phase, then acknowledged normally so GitHub/webhook reconciliation cannot replay them.
        return "claim_external"
    return None


def _issue_number(payload: dict[str, Any]) -> int:
    issue = payload.get("issue") or {}
    try:
        number = int(issue.get("number") or 0)
    except (TypeError, ValueError):
        number = 0
    if number <= 0:
        raise ValueError("external-producer controls require a concrete GitHub issue number")
    return number


def _active_claims(self: core.Orchestrator) -> dict[str, dict[str, Any]]:
    with self._state_lock:
        values = self.state.data.get("external_producer_claims")
        if not isinstance(values, dict):
            return {}
        return {
            str(key): dict(value)
            for key, value in values.items()
            if isinstance(value, dict) and str(value.get("state") or "active") == "active"
        }


def _controller_owns_issue(self: core.Orchestrator, issue_number: int) -> dict[str, Any] | None:
    """Return concrete controller ownership, failing closed on managed-PR visibility uncertainty."""
    with self._state_lock:
        pending = self.state.data.get("pending_worker")
        pending = dict(pending) if isinstance(pending, dict) else None
        record = self._decision_record() or {}
        task_issues = {
            int(value)
            for value in (record.get("task_issue_numbers") or [])
            if str(value).isdigit()
        }
        managed = {
            str(lane): dict(value)
            for lane, value in (self.state.data.get("managed") or {}).items()
            if isinstance(value, dict)
        }
        roadmap = self.state.data.get("roadmap") or self.state.data.get("bounded_roadmap") or {}
        active = roadmap.get("active") if isinstance(roadmap, dict) else None
        active = dict(active) if isinstance(active, dict) else None

    # An active roadmap node is already durable controller authority even if its worker has not yet
    # launched (or is temporarily paused). Manual work may not steal that issue in the race window.
    if active is not None:
        active_issue = active.get("issue_number")
        if str(active_issue).isdigit() and int(active_issue) == issue_number:
            return {
                "kind": "active_roadmap",
                "lane": active.get("lane"),
                "node_id": active.get("node_id"),
                "pr_number": active.get("pr_number"),
            }

    if pending is not None:
        authority_issue = pending.get("authority_issue")
        if (
            str(authority_issue).isdigit()
            and int(authority_issue) == issue_number
        ) or issue_number in task_issues:
            return {
                "kind": "pending_worker",
                "branch": pending.get("branch"),
                "stage": pending.get("stage"),
                "lane": pending.get("lane"),
            }

    for lane, value in managed.items():
        authority_issue = value.get("authority_issue")
        if not str(authority_issue).isdigit() or int(authority_issue) != issue_number:
            continue
        # Retire stale lane records through the already-accepted current-truth validator. If GitHub
        # visibility itself fails, fail closed: uncertainty is never permission to race a PR.
        try:
            live = self._validated_managed_branch(lane)
        except Exception:
            return {
                "kind": "managed_pr_visibility_uncertain",
                "lane": lane,
                "pr_number": value.get("pr_number"),
                "branch": value.get("branch"),
            }
        if isinstance(live, dict):
            live_issue = live.get("authority_issue")
            if str(live_issue).isdigit() and int(live_issue) == issue_number:
                return {
                    "kind": "managed_pr",
                    "lane": lane,
                    "pr_number": live.get("pr_number"),
                    "branch": live.get("branch"),
                }
    return None


def _record_claim_rejection(
    self: core.Orchestrator,
    *,
    issue_number: int | None,
    actor: str,
    reason: str,
    owner: dict[str, Any] | None = None,
) -> None:
    with self._state_lock:
        self.state.data["last_external_producer_claim_rejection"] = {
            "at": core._utc_now(),
            "issue_number": issue_number,
            "actor": actor,
            "reason": reason[:1000],
            "controller_owner": owner,
        }
        self.state.save()
    self._metric("external_producer_claim_rejections")


def _claim_external(self: core.Orchestrator, payload: dict[str, Any], *, actor: str) -> bool:
    try:
        issue_number = _issue_number(payload)
    except ValueError as exc:
        _record_claim_rejection(
            self,
            issue_number=None,
            actor=actor,
            reason=str(exc),
        )
        return False

    try:
        options = _parse_claim_options(payload)
    except ValueError as exc:
        _record_claim_rejection(
            self,
            issue_number=issue_number,
            actor=actor,
            reason=str(exc),
        )
        return False

    owner = _controller_owns_issue(self, issue_number)
    if owner is not None:
        _record_claim_rejection(
            self,
            issue_number=issue_number,
            actor=actor,
            owner=owner,
            reason=f"issue already controller-owned ({owner.get('kind')})",
        )
        return False

    comment = payload.get("comment") or {}
    with self._state_lock:
        claims = self.state.data.setdefault("external_producer_claims", {})
        key = str(issue_number)
        prior = claims.get(key) if isinstance(claims.get(key), dict) else None
        claimed_at = (prior or {}).get("claimed_at") or core._utc_now()
        claim = {
            "state": "active",
            "issue_number": issue_number,
            "claimed_by": actor,
            "claimed_at": claimed_at,
            "updated_at": core._utc_now(),
            "source_comment_id": comment.get("id"),
            "lane": options.get("lane") or (prior or {}).get("lane"),
            "branch": options.get("branch") or (prior or {}).get("branch"),
            "pr_number": options.get("pr") or (prior or {}).get("pr_number"),
        }
        claims[key] = claim
        self.state.data["last_external_producer_claim"] = dict(claim)
        self.state.save()
    self._metric("external_producer_claims")
    return True


def _release_external(
    self: core.Orchestrator,
    payload: dict[str, Any],
    *,
    actor: str,
    reason: str = "explicit_release",
) -> bool:
    try:
        issue_number = _issue_number(payload)
    except ValueError:
        return False
    removed = None
    with self._state_lock:
        claims = self.state.data.setdefault("external_producer_claims", {})
        value = claims.pop(str(issue_number), None)
        if isinstance(value, dict):
            removed = dict(value)
        self.state.data["last_external_producer_release"] = {
            "at": core._utc_now(),
            "issue_number": issue_number,
            "released_by": actor,
            "reason": reason,
            "claim": removed,
        }
        if self.state.data.get("blocked_kind") == "external_producer":
            self.state.data["blocked_until_epoch"] = 0.0
            self.state.data["blocked_kind"] = None
            self.state.data["blocked_reason"] = None
        self.state.save()
    self._metric("external_producer_releases")
    if not self.is_paused() and self._pending_events():
        self._schedule_pending(1)
    return removed is not None


def _prune_external_claims(self: core.Orchestrator) -> int:
    """Retire claims only on durable remote closure evidence; API failure keeps the claim active."""
    claims = _active_claims(self)
    if not claims:
        return 0

    retire: dict[str, str] = {}
    for key, claim in claims.items():
        issue_number = int(claim.get("issue_number") or int(key))
        pr_number = claim.get("pr_number")
        if str(pr_number).isdigit() and int(pr_number) > 0:
            try:
                pr = core._json_cmd(
                    [
                        "gh", "pr", "view", str(int(pr_number)),
                        "--repo", self.repo,
                        "--json", "state,mergedAt,headRefName",
                    ],
                    cwd=self.root,
                    timeout=60,
                )
            except Exception:
                # A bound PR whose state cannot be proven terminal retains ownership.
                continue
            if str(pr.get("state") or "").upper() != "OPEN":
                retire[key] = "bound_pr_merged" if pr.get("mergedAt") else "bound_pr_closed"
            # While a bound PR remains OPEN it is authoritative ownership even if the issue itself is
            # manually closed. Do not release the claim until that producer PR reaches a terminal state.
            continue

        try:
            issue = core._json_cmd(
                ["gh", "api", f"repos/{self.repo}/issues/{issue_number}"],
                cwd=self.root,
                timeout=60,
            )
        except Exception:
            continue
        if str(issue.get("state") or "").lower() == "closed":
            retire[key] = "issue_closed"

    if not retire:
        return 0
    with self._state_lock:
        values = self.state.data.setdefault("external_producer_claims", {})
        retired_records = []
        for key, reason in retire.items():
            value = values.pop(key, None)
            if isinstance(value, dict):
                retired_records.append({**value, "retired_at": core._utc_now(), "reason": reason})
        self.state.data["last_external_producer_auto_retire"] = {
            "at": core._utc_now(),
            "retired": retired_records,
        }
        if self.state.data.get("blocked_kind") == "external_producer":
            self.state.data["blocked_until_epoch"] = 0.0
            self.state.data["blocked_kind"] = None
            self.state.data["blocked_reason"] = None
        self.state.save()
    self._metric("external_producer_auto_retires", len(retire))
    return len(retire)


def _apply_control_payload(
    self: core.Orchestrator,
    control: str,
    payload: dict[str, Any],
) -> None:
    actor = str((((payload.get("comment") or {}).get("user") or {}).get("login")) or "")
    if control == "claim_external":
        _claim_external(self, payload, actor=actor)
        return
    if control == "release_external":
        _release_external(self, payload, actor=actor)
        return
    _ORIGINAL_APPLY_CONTROL_PAYLOAD(self, control, payload)


def _dispatch(self: core.Orchestrator, events: list[core.EventDecision]) -> None:
    # Refresh bounded external ownership before buying a classifier or worker turn. Closed/merged
    # bound PRs and closed issues retire model-free; API uncertainty keeps the claim fail-closed.
    _prune_external_claims(self)
    task_issues = {
        issue
        for event in events
        if (issue := core._task_issue_number(event)) is not None
    }
    if task_issues:
        claims = _active_claims(self)
        held = sorted(issue for issue in task_issues if str(issue) in claims)
        if held:
            retry = core._env_int(
                "SKYFORGE_EXTERNAL_PRODUCER_HOLD_SECONDS",
                DEFAULT_HOLD_SECONDS,
                minimum=60,
            )
            with self._state_lock:
                self.state.data["last_external_producer_hold"] = {
                    "at": core._utc_now(),
                    "issue_numbers": held,
                    "claims": {str(issue): claims[str(issue)] for issue in held},
                    "retry_after_seconds": retry,
                }
                self.state.save()
            self._metric("external_producer_dispatch_holds")
            raise core.RetryBlocked(
                "external_producer",
                retry,
                "manual/external producer owns issue(s) "
                + ", ".join(f"#{issue}" for issue in held)
                + "; durable task authority retained without classifier/worker spend",
            )
    _ORIGINAL_DISPATCH(self, events)


def _claim_age_seconds(claim: dict[str, Any]) -> int | None:
    raw = claim.get("claimed_at")
    if not raw:
        return None
    try:
        at = datetime.fromisoformat(str(raw).replace("Z", "+00:00"))
    except ValueError:
        return None
    return max(0, int((datetime.now(timezone.utc) - at).total_seconds()))


def health_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    _prune_external_claims(self)
    snapshot = dict(_ORIGINAL_HEALTH_SNAPSHOT(self))
    claims = _active_claims(self)
    snapshot["external_producer_claim_count"] = len(claims)
    snapshot["external_producer_claims"] = {
        key: {**value, "age_seconds": _claim_age_seconds(value)}
        for key, value in claims.items()
    }
    with self._state_lock:
        snapshot["last_external_producer_claim"] = self.state.data.get(
            "last_external_producer_claim"
        )
        snapshot["last_external_producer_release"] = self.state.data.get(
            "last_external_producer_release"
        )
        snapshot["last_external_producer_claim_rejection"] = self.state.data.get(
            "last_external_producer_claim_rejection"
        )
        snapshot["last_external_producer_hold"] = self.state.data.get(
            "last_external_producer_hold"
        )
        snapshot["last_external_producer_auto_retire"] = self.state.data.get(
            "last_external_producer_auto_retire"
        )
    return snapshot


def install_extension() -> None:
    if getattr(core, "_skyforge_external_producer_claim_extension_installed", False):
        return
    core.classify_control_command = classify_control_command
    core.Orchestrator._apply_control_payload = _apply_control_payload
    core.Orchestrator.dispatch = _dispatch
    core.Orchestrator.health_snapshot = health_snapshot
    core.Orchestrator.claim_external_producer = _claim_external
    core.Orchestrator.release_external_producer = _release_external
    core.Orchestrator.prune_external_producer_claims = _prune_external_claims
    core._skyforge_external_producer_claim_extension_installed = True


install_extension()
