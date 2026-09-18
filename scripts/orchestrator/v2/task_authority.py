"""Trusted typed task-authority hydration for Platform v2 R5C7.

Legacy task wakes may remain prose-only and visible, but Platform v2 executes ordinary
work only when a trusted task comment carries one explicit typed authority marker.
Issue title/body remain bounded context and never widen executable permissions.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
from typing import Any, Callable, Mapping, Sequence

from .dispatch_admission import RepositoryTaskAuthority
from .identity import canonical_digest


TASK_AUTHORITY_MARKER = "[SKYFORGE TASK AUTHORITY]"
ISSUE_FIELDS_ENDPOINT = "issue"
COMMENTS_ENDPOINT = "comments"


def _required_text(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


def _safe_scope(path: Any, label: str) -> str:
    text = _required_text(path, label).replace("\\", "/")
    if text.startswith("/") or re.match(r"^[A-Za-z]:/", text):
        raise ValueError(f"{label} must be repository-relative")
    parts = PurePosixPath(text).parts
    if ".." in parts:
        raise ValueError(f"{label} may not traverse upward")
    lowered = text.lower().lstrip("./")
    if lowered.startswith(".git/") or lowered == ".git":
        raise ValueError(f"{label} may not target .git")
    if lowered.startswith(".skyforge-orchestrator/") or lowered == ".skyforge-orchestrator":
        raise ValueError(f"{label} may not target legacy controller state")
    if lowered.startswith(".skyforge-platform-v2/") or lowered == ".skyforge-platform-v2":
        raise ValueError(f"{label} may not target controller state")
    return text


def _json_object_after_marker(body: str) -> Mapping[str, Any]:
    if body.count(TASK_AUTHORITY_MARKER) != 1:
        raise ValueError("task authority marker must appear exactly once")
    tail = body.split(TASK_AUTHORITY_MARKER, 1)[1].strip()
    fence = chr(96) * 3
    if tail.startswith(fence):
        lines = tail.splitlines()
        if not lines:
            raise ValueError("typed authority JSON is missing")
        lines = lines[1:]
        if lines and lines[-1].strip() == fence:
            lines = lines[:-1]
        tail = "\n".join(lines).strip()
    start = tail.find("{")
    end = tail.rfind("}")
    if start < 0 or end < start:
        raise ValueError("typed authority JSON object is missing")
    try:
        value = json.loads(tail[start : end + 1])
    except json.JSONDecodeError as exc:
        raise ValueError("typed authority JSON is malformed") from exc
    if not isinstance(value, Mapping):
        raise ValueError("typed authority JSON must be an object")
    return value


class TaskAuthorityDisposition(str, Enum):
    EXECUTABLE_V2 = "EXECUTABLE_V2"
    NOT_EXECUTABLE_V2 = "NOT_EXECUTABLE_V2"
    BLOCKED = "BLOCKED"


@dataclass(frozen=True)
class TaskIssueObservation:
    issue_number: int
    state: str
    title: str
    body: str
    comment_id: int
    comment_author: str
    comment_body: str

    def __post_init__(self) -> None:
        _positive_int(self.issue_number, "issue_number")
        _positive_int(self.comment_id, "comment_id")
        if self.state not in {"OPEN", "CLOSED"}:
            raise ValueError("issue state must be OPEN or CLOSED")
        _required_text(self.title, "issue title")
        _required_text(self.comment_author, "comment author")
        _required_text(self.comment_body, "comment body")

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "issue_number": self.issue_number,
                "state": self.state,
                "title": self.title,
                "body": self.body,
                "comment_id": self.comment_id,
                "comment_author": self.comment_author.lower(),
                "comment_body": self.comment_body,
            }
        )


@dataclass(frozen=True)
class TypedTaskAuthority:
    observation_digest: str
    comment_id: int
    authority: RepositoryTaskAuthority

    @property
    def revision_id(self) -> str:
        return canonical_digest(
            {
                "observation_digest": self.observation_digest,
                "comment_id": self.comment_id,
                "authority_digest": self.authority.digest,
            }
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "revision_id": self.revision_id,
            "observation_digest": self.observation_digest,
            "comment_id": self.comment_id,
            "authority": self.authority.as_dict(),
        }


@dataclass(frozen=True)
class TaskAuthorityHydrationResult:
    disposition: TaskAuthorityDisposition
    reason: str
    observation_digest: str
    typed: TypedTaskAuthority | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "observation_digest": self.observation_digest,
                "typed_revision_id": self.typed.revision_id if self.typed else "",
            }
        )


def hydrate_task_authority(
    observation: TaskIssueObservation,
    *,
    trusted_actors: Sequence[str],
) -> TaskAuthorityHydrationResult:
    trusted = {
        str(actor).strip().lower()
        for actor in trusted_actors
        if str(actor).strip()
    }
    digest = observation.digest

    if observation.comment_author.strip().lower() not in trusted:
        return TaskAuthorityHydrationResult(
            TaskAuthorityDisposition.BLOCKED,
            "task directive author is not trusted",
            digest,
        )
    if observation.state != "OPEN":
        return TaskAuthorityHydrationResult(
            TaskAuthorityDisposition.BLOCKED,
            "governing task issue is not open",
            digest,
        )

    body_lower = observation.comment_body.lower()
    first_line = next(
        (line.strip() for line in body_lower.splitlines() if line.strip()),
        "",
    )
    legacy_task_signal = (
        "audit" in first_line
        and "new " in first_line
        and " task" in first_line
    )
    if not legacy_task_signal:
        return TaskAuthorityHydrationResult(
            TaskAuthorityDisposition.BLOCKED,
            "comment is not an explicit legacy-compatible AUDIT NEW TASK directive",
            digest,
        )

    marker_count = observation.comment_body.count(TASK_AUTHORITY_MARKER)
    if marker_count == 0:
        return TaskAuthorityHydrationResult(
            TaskAuthorityDisposition.NOT_EXECUTABLE_V2,
            "trusted legacy task directive lacks typed Platform-v2 authority",
            digest,
        )
    if marker_count != 1:
        return TaskAuthorityHydrationResult(
            TaskAuthorityDisposition.BLOCKED,
            "task authority marker must appear exactly once",
            digest,
        )

    try:
        raw = _json_object_after_marker(observation.comment_body)
        allowed_keys = {
            "lane",
            "objective",
            "stop_boundary",
            "allowed_paths",
            "protected_paths",
            "auto_merge_eligible",
        }
        unknown = sorted(set(raw) - allowed_keys)
        if unknown:
            raise ValueError(f"unknown typed authority keys: {unknown}")

        lane = _required_text(raw.get("lane"), "lane")
        objective = _required_text(raw.get("objective"), "objective")
        stop_boundary = _required_text(
            raw.get("stop_boundary"),
            "stop_boundary",
        )
        allowed_raw = raw.get("allowed_paths")
        protected_raw = raw.get("protected_paths") or []
        if not isinstance(allowed_raw, list) or not allowed_raw:
            raise ValueError("allowed_paths must be a non-empty list")
        if not isinstance(protected_raw, list):
            raise ValueError("protected_paths must be a list")
        allowed = tuple(
            _safe_scope(value, "allowed path") for value in allowed_raw
        )
        protected = tuple(
            _safe_scope(value, "protected path") for value in protected_raw
        )
        auto_merge = raw.get("auto_merge_eligible", False)
        if not isinstance(auto_merge, bool):
            raise ValueError("auto_merge_eligible must be boolean")
    except ValueError as exc:
        return TaskAuthorityHydrationResult(
            TaskAuthorityDisposition.BLOCKED,
            str(exc),
            digest,
        )

    task_id = f"issue-{observation.issue_number}-comment-{observation.comment_id}"
    context = (
        f"Issue #{observation.issue_number}: {observation.title}\n\n"
        f"{observation.body}"
    )[:18000]
    authority = RepositoryTaskAuthority(
        task_id=task_id,
        authority_key=f"issue:{observation.issue_number}",
        issue_numbers=(observation.issue_number,),
        lane=lane,
        objective=objective,
        stop_boundary=stop_boundary,
        allowed_paths=allowed,
        protected_paths=protected,
        context_text=context,
        auto_merge_eligible=auto_merge,
    )
    typed = TypedTaskAuthority(
        observation_digest=digest,
        comment_id=observation.comment_id,
        authority=authority,
    )
    return TaskAuthorityHydrationResult(
        TaskAuthorityDisposition.EXECUTABLE_V2,
        "trusted typed task authority is executable by Platform v2",
        digest,
        typed=typed,
    )


class TaskAuthorityRemoteUnavailable(RuntimeError):
    pass


class GitHubTaskAuthorityHydrator:
    """Read-only exact GitHub observation for one issue/comment authority revision."""

    def __init__(
        self,
        *,
        root: Path,
        repo: str,
        trusted_actors: Sequence[str],
        runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
    ) -> None:
        self.root = Path(root).resolve()
        self.repo = _required_text(repo, "repo")
        if self.repo.count("/") != 1:
            raise ValueError("repo must be owner/name")
        self.trusted_actors = tuple(trusted_actors)
        self.runner = runner

    def _allowed(
        self,
        *,
        issue_number: int,
    ) -> set[tuple[str, ...]]:
        issue = str(_positive_int(issue_number, "issue_number"))
        return {
            ("gh", "api", f"repos/{self.repo}/issues/{issue}"),
            (
                "gh",
                "api",
                f"repos/{self.repo}/issues/{issue}/comments?per_page=100",
            ),
        }

    def _run(self, args: Sequence[str], *, issue_number: int) -> Any:
        command = tuple(str(part) for part in args)
        if command not in self._allowed(issue_number=issue_number):
            raise ValueError("command is outside exact task-authority read allowlist")
        try:
            result = self.runner(
                list(command),
                cwd=self.root,
                check=True,
                text=True,
                capture_output=True,
                timeout=60,
            )
        except subprocess.SubprocessError as exc:
            raise TaskAuthorityRemoteUnavailable(str(exc)) from exc
        try:
            return json.loads(result.stdout)
        except json.JSONDecodeError as exc:
            raise TaskAuthorityRemoteUnavailable(
                "task-authority GitHub observation returned malformed JSON"
            ) from exc

    def observe(
        self,
        *,
        issue_number: int,
        comment_id: int,
    ) -> TaskIssueObservation:
        issue_number = _positive_int(issue_number, "issue_number")
        comment_id = _positive_int(comment_id, "comment_id")
        issue = self._run(
            ["gh", "api", f"repos/{self.repo}/issues/{issue_number}"],
            issue_number=issue_number,
        )
        comments = self._run(
            [
                "gh",
                "api",
                f"repos/{self.repo}/issues/{issue_number}/comments?per_page=100",
            ],
            issue_number=issue_number,
        )
        if not isinstance(issue, Mapping) or int(issue.get("number") or 0) != issue_number:
            raise TaskAuthorityRemoteUnavailable("issue identity mismatched")
        if not isinstance(comments, list):
            raise TaskAuthorityRemoteUnavailable("issue comments response is malformed")
        matches = [
            item
            for item in comments
            if isinstance(item, Mapping) and item.get("id") == comment_id
        ]
        if len(matches) != 1:
            raise TaskAuthorityRemoteUnavailable(
                "exact task authority comment is missing or ambiguous"
            )
        comment = matches[0]
        author = (
            comment.get("user") if isinstance(comment.get("user"), Mapping) else {}
        )
        state = str(issue.get("state") or "").upper()
        return TaskIssueObservation(
            issue_number=issue_number,
            state=state,
            title=str(issue.get("title") or ""),
            body=str(issue.get("body") or ""),
            comment_id=comment_id,
            comment_author=str(author.get("login") or ""),
            comment_body=str(comment.get("body") or ""),
        )

    def hydrate(
        self,
        *,
        issue_number: int,
        comment_id: int,
    ) -> TaskAuthorityHydrationResult:
        return hydrate_task_authority(
            self.observe(issue_number=issue_number, comment_id=comment_id),
            trusted_actors=self.trusted_actors,
        )
