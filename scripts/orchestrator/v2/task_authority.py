"""Trusted repository task-authority hydration for Platform v2 R5C7.

Classifier prose may propose execution details, but it cannot create repository authority.
One exact trusted GitHub issue comment supplies the typed authority revision.  The comment
is re-read from GitHub before conversion to the existing R5C4 RepositoryTaskAuthority.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
import re
import subprocess
from typing import Any, Iterable, Mapping, Sequence

from .dispatch_admission import RepositoryTaskAuthority
from .identity import canonical_digest


TASK_AUTHORITY_MARKER = "[SKYFORGE TASK AUTHORITY]"
_REPO_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")
_MAX_ISSUE_TITLE = 512
_MAX_ISSUE_BODY = 16_000


def _required_text(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} must be non-empty text")
    return value.strip()


def _required_body(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} must be non-empty text")
    return value


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


def _repo(value: Any) -> str:
    text = _required_text(value, "repo")
    if not _REPO_RE.fullmatch(text):
        raise ValueError("repo must be owner/name using GitHub-safe characters")
    return text


def _task_path(value: Any, label: str) -> str:
    text = _required_text(value, label).replace("\\", "/")
    if (
        text.startswith("/")
        or text.startswith("./")
        or re.match(r"^[A-Za-z]:/", text)
        or any(part in {"", ".", ".."} for part in text.split("/"))
    ):
        raise ValueError(f"{label} must be a normalized repository-relative path")
    if "*" in text and not text.endswith("/**"):
        raise ValueError(f"{label} supports only a trailing /** scope wildcard")
    if text.endswith("/**") and "*" in text[:-3]:
        raise ValueError(f"{label} contains unsupported wildcard syntax")
    return text


def _path_tuple(value: Any, label: str, *, require_nonempty: bool) -> tuple[str, ...]:
    if not isinstance(value, list):
        raise ValueError(f"{label} must be a JSON array")
    values = tuple(_task_path(item, f"{label} item") for item in value)
    if require_nonempty and not values:
        raise ValueError(f"{label} must not be empty")
    return values


def _bounded(value: Any, limit: int) -> str:
    text = str(value or "").strip()
    if len(text) <= limit:
        return text
    return text[:limit] + "\n...[truncated by Platform v2 task-authority hydrator]"


def _legacy_task_wake_form(body: str) -> bool:
    lower = body.lower()
    return "audit" in lower and "new " in lower and " task" in lower


class TaskAuthorityDisposition(str, Enum):
    EXECUTABLE_V2 = "EXECUTABLE_V2"
    NOT_EXECUTABLE_V2 = "NOT_EXECUTABLE_V2"
    REJECTED = "REJECTED"


@dataclass(frozen=True)
class TypedTaskDirective:
    lane: str
    objective: str
    stop_boundary: str
    allowed_paths: tuple[str, ...]
    protected_paths: tuple[str, ...] = ()
    auto_merge_eligible: bool = False

    @classmethod
    def from_mapping(cls, raw: Any) -> "TypedTaskDirective":
        if not isinstance(raw, Mapping):
            raise ValueError("task authority payload must be a JSON object")
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
            raise ValueError(
                "task authority contains unsupported field(s): " + ",".join(unknown)
            )
        required = {"lane", "objective", "stop_boundary", "allowed_paths"}
        missing = sorted(required - set(raw))
        if missing:
            raise ValueError(
                "task authority is missing required field(s): " + ",".join(missing)
            )
        auto_merge = raw.get("auto_merge_eligible", False)
        if not isinstance(auto_merge, bool):
            raise ValueError("auto_merge_eligible must be boolean")
        return cls(
            lane=_required_text(raw.get("lane"), "lane"),
            objective=_required_text(raw.get("objective"), "objective"),
            stop_boundary=_required_text(raw.get("stop_boundary"), "stop_boundary"),
            allowed_paths=_path_tuple(
                raw.get("allowed_paths"),
                "allowed_paths",
                require_nonempty=True,
            ),
            protected_paths=_path_tuple(
                raw.get("protected_paths", []),
                "protected_paths",
                require_nonempty=False,
            ),
            auto_merge_eligible=auto_merge,
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "lane": self.lane,
            "objective": self.objective,
            "stop_boundary": self.stop_boundary,
            "allowed_paths": list(self.allowed_paths),
            "protected_paths": list(self.protected_paths),
            "auto_merge_eligible": self.auto_merge_eligible,
        }


@dataclass(frozen=True)
class TaskDirectiveParseResult:
    disposition: TaskAuthorityDisposition
    reason: str
    directive: TypedTaskDirective | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "directive": self.directive.as_dict() if self.directive else None,
            }
        )


def parse_typed_task_directive(body: str) -> TaskDirectiveParseResult:
    text = _required_text(body, "task directive body")
    count = text.count(TASK_AUTHORITY_MARKER)
    if count == 0:
        return TaskDirectiveParseResult(
            TaskAuthorityDisposition.NOT_EXECUTABLE_V2,
            "legacy task directive has no typed Platform-v2 authority marker",
        )
    if count != 1:
        return TaskDirectiveParseResult(
            TaskAuthorityDisposition.REJECTED,
            "task directive must contain exactly one Platform-v2 authority marker",
        )
    if not _legacy_task_wake_form(text):
        return TaskDirectiveParseResult(
            TaskAuthorityDisposition.REJECTED,
            "typed authority marker is not attached to a legacy-compatible AUDIT NEW TASK wake",
        )

    tail = text.split(TASK_AUTHORITY_MARKER, 1)[1].lstrip()
    if not tail:
        return TaskDirectiveParseResult(
            TaskAuthorityDisposition.REJECTED,
            "typed authority marker is not followed by JSON",
        )

    decoder = json.JSONDecoder()
    try:
        value, offset = decoder.raw_decode(tail)
    except json.JSONDecodeError:
        return TaskDirectiveParseResult(
            TaskAuthorityDisposition.REJECTED,
            "typed task authority JSON is malformed",
        )
    if tail[offset:].strip():
        return TaskDirectiveParseResult(
            TaskAuthorityDisposition.REJECTED,
            "typed task authority must be the only content after the marker",
        )
    try:
        directive = TypedTaskDirective.from_mapping(value)
        # Reuse the accepted R5C4 path normalizer/validator without manufacturing
        # an executable authority yet. This rejects absolute/traversal/malformed scope.
        RepositoryTaskAuthority(
            task_id="validation-only",
            authority_key="validation-only",
            issue_numbers=(1,),
            lane=directive.lane,
            objective=directive.objective,
            stop_boundary=directive.stop_boundary,
            allowed_paths=directive.allowed_paths,
            protected_paths=directive.protected_paths,
            auto_merge_eligible=directive.auto_merge_eligible,
        )
    except ValueError as exc:
        return TaskDirectiveParseResult(
            TaskAuthorityDisposition.REJECTED,
            str(exc),
        )

    return TaskDirectiveParseResult(
        TaskAuthorityDisposition.EXECUTABLE_V2,
        "trusted typed task authority syntax is valid",
        directive,
    )


@dataclass(frozen=True)
class TaskAuthorityWakeReference:
    repo: str
    issue_number: int
    comment_id: int
    actor: str
    body: str
    created_at: str
    updated_at: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "repo", _repo(self.repo))
        object.__setattr__(
            self, "issue_number", _positive_int(self.issue_number, "issue_number")
        )
        object.__setattr__(
            self, "comment_id", _positive_int(self.comment_id, "comment_id")
        )
        object.__setattr__(self, "actor", _required_text(self.actor, "actor"))
        object.__setattr__(self, "body", _required_body(self.body, "body"))
        object.__setattr__(
            self, "created_at", _required_text(self.created_at, "created_at")
        )
        object.__setattr__(
            self, "updated_at", _required_text(self.updated_at, "updated_at")
        )

    @property
    def body_digest(self) -> str:
        return canonical_digest({"body": self.body})

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "repo": self.repo,
                "issue_number": self.issue_number,
                "comment_id": self.comment_id,
                "actor": self.actor.lower(),
                "body_digest": self.body_digest,
                "created_at": self.created_at,
                "updated_at": self.updated_at,
            }
        )


@dataclass(frozen=True)
class TaskAuthorityIdentity:
    repo: str
    issue_number: int
    comment_id: int
    actor: str
    body_digest: str
    created_at: str
    updated_at: str

    @classmethod
    def from_live_comment(
        cls,
        reference: TaskAuthorityWakeReference,
        comment: Mapping[str, Any],
    ) -> "TaskAuthorityIdentity":
        body = _required_body(comment.get("body"), "live comment body")
        user = comment.get("user") or {}
        if not isinstance(user, Mapping):
            raise ValueError("live comment user must be an object")
        return cls(
            repo=reference.repo,
            issue_number=reference.issue_number,
            comment_id=_positive_int(comment.get("id"), "live comment id"),
            actor=_required_text(user.get("login"), "live comment actor"),
            body_digest=canonical_digest({"body": body}),
            created_at=_required_text(
                comment.get("created_at"), "live comment created_at"
            ),
            updated_at=_required_text(
                comment.get("updated_at"), "live comment updated_at"
            ),
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "repo": self.repo,
            "issue_number": self.issue_number,
            "comment_id": self.comment_id,
            "actor": self.actor.lower(),
            "body_digest": self.body_digest,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class TaskAuthorityHydrationResult:
    disposition: TaskAuthorityDisposition
    reason: str
    identity: TaskAuthorityIdentity | None = None
    authority: RepositoryTaskAuthority | None = None

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "identity_digest": self.identity.digest if self.identity else "",
                "authority_digest": self.authority.digest if self.authority else "",
            }
        )


def _result(
    disposition: TaskAuthorityDisposition,
    reason: str,
    *,
    identity: TaskAuthorityIdentity | None = None,
    authority: RepositoryTaskAuthority | None = None,
) -> TaskAuthorityHydrationResult:
    return TaskAuthorityHydrationResult(
        disposition=disposition,
        reason=reason,
        identity=identity,
        authority=authority,
    )


def hydrate_task_authority(
    *,
    reference: TaskAuthorityWakeReference,
    issue: Mapping[str, Any] | None,
    comment: Mapping[str, Any] | None,
    trusted_actors: Iterable[str],
) -> TaskAuthorityHydrationResult:
    trusted = {
        str(actor).strip().lower()
        for actor in trusted_actors
        if str(actor).strip()
    }
    if not trusted:
        return _result(
            TaskAuthorityDisposition.REJECTED,
            "no trusted GitHub actors are configured",
        )
    if reference.actor.lower() not in trusted:
        return _result(
            TaskAuthorityDisposition.REJECTED,
            "event actor is not trusted for task authority",
        )
    if issue is None:
        return _result(
            TaskAuthorityDisposition.REJECTED,
            "governing issue no longer exists",
        )
    if comment is None:
        return _result(
            TaskAuthorityDisposition.REJECTED,
            "authority comment no longer exists",
        )
    if not isinstance(issue, Mapping) or not isinstance(comment, Mapping):
        return _result(
            TaskAuthorityDisposition.REJECTED,
            "GitHub task authority truth has malformed shape",
        )

    try:
        issue_number = _positive_int(issue.get("number"), "live issue number")
        issue_state = _required_text(issue.get("state"), "live issue state").lower()
        live_identity = TaskAuthorityIdentity.from_live_comment(reference, comment)
        issue_url = _required_text(comment.get("issue_url"), "live comment issue_url")
        live_body = _required_body(comment.get("body"), "live comment body")
    except ValueError as exc:
        return _result(TaskAuthorityDisposition.REJECTED, str(exc))

    if issue_number != reference.issue_number:
        return _result(
            TaskAuthorityDisposition.REJECTED,
            "hydrated issue number differs from durable task event",
            identity=live_identity,
        )
    expected_issue_suffix = (
        f"/repos/{reference.repo}/issues/{reference.issue_number}"
    )
    if not issue_url.endswith(expected_issue_suffix):
        return _result(
            TaskAuthorityDisposition.REJECTED,
            "authority comment belongs to a different issue or repository",
            identity=live_identity,
        )
    if issue_state != "open":
        return _result(
            TaskAuthorityDisposition.REJECTED,
            "governing issue is not open",
            identity=live_identity,
        )
    if live_identity.comment_id != reference.comment_id:
        return _result(
            TaskAuthorityDisposition.REJECTED,
            "hydrated comment id differs from durable task event",
            identity=live_identity,
        )
    if live_identity.actor.lower() not in trusted:
        return _result(
            TaskAuthorityDisposition.REJECTED,
            "live comment actor is not trusted for task authority",
            identity=live_identity,
        )
    if live_identity.actor.lower() != reference.actor.lower():
        return _result(
            TaskAuthorityDisposition.REJECTED,
            "live comment actor differs from durable task event",
            identity=live_identity,
        )
    if (
        live_body != reference.body
        or live_identity.created_at != reference.created_at
        or live_identity.updated_at != reference.updated_at
        or live_identity.body_digest != reference.body_digest
    ):
        return _result(
            TaskAuthorityDisposition.REJECTED,
            "authority comment changed after the durable task event was recorded",
            identity=live_identity,
        )

    parsed = parse_typed_task_directive(live_body)
    if parsed.disposition is not TaskAuthorityDisposition.EXECUTABLE_V2:
        return _result(
            parsed.disposition,
            parsed.reason,
            identity=live_identity,
        )
    assert parsed.directive is not None

    title = _bounded(issue.get("title"), _MAX_ISSUE_TITLE)
    body = _bounded(issue.get("body"), _MAX_ISSUE_BODY)
    context_parts = [
        f"Repository task issue #{reference.issue_number}: {title}",
        body,
        (
            "Task authority source: "
            f"GitHub comment #{reference.comment_id} by {live_identity.actor}; "
            f"identity={live_identity.digest}"
        ),
    ]
    context_text = "\n\n".join(part for part in context_parts if part)

    try:
        authority = RepositoryTaskAuthority(
            task_id=(
                f"github-issue-{reference.issue_number}-"
                f"comment-{reference.comment_id}"
            ),
            authority_key=f"github-task-authority:{live_identity.digest}",
            issue_numbers=(reference.issue_number,),
            lane=parsed.directive.lane,
            objective=parsed.directive.objective,
            stop_boundary=parsed.directive.stop_boundary,
            allowed_paths=parsed.directive.allowed_paths,
            protected_paths=parsed.directive.protected_paths,
            context_text=context_text,
            auto_merge_eligible=parsed.directive.auto_merge_eligible,
            spec_version=2,
        )
    except ValueError as exc:
        return _result(
            TaskAuthorityDisposition.REJECTED,
            str(exc),
            identity=live_identity,
        )

    return _result(
        TaskAuthorityDisposition.EXECUTABLE_V2,
        "exact trusted GitHub task directive hydrated into repository authority",
        identity=live_identity,
        authority=authority,
    )


class TaskAuthorityRemoteUnavailable(RuntimeError):
    """Exact GitHub task authority truth could not be read safely."""


class TaskAuthorityReadCommandValidator:
    """Allow only exact read-only GitHub issue/comment lookups."""

    def __init__(self, reference: TaskAuthorityWakeReference) -> None:
        self.reference = reference

    def allowed(self) -> set[tuple[str, ...]]:
        ref = self.reference
        return {
            (
                "gh",
                "api",
                f"repos/{ref.repo}/issues/{ref.issue_number}",
            ),
            (
                "gh",
                "api",
                f"repos/{ref.repo}/issues/comments/{ref.comment_id}",
            ),
        }

    def validate(self, args: Sequence[str]) -> tuple[str, ...]:
        command = tuple(str(part) for part in args)
        if command not in self.allowed():
            raise ValueError(
                "command is outside the exact Platform-v2 task-authority read allowlist"
            )
        return command


class GhTaskAuthorityHydrator:
    """Read-only GitHub adapter bound to one durable task-event reference."""

    def __init__(
        self,
        *,
        reference: TaskAuthorityWakeReference,
        trusted_actors: Iterable[str],
        runner=subprocess.run,
    ) -> None:
        self.reference = reference
        self.trusted_actors = tuple(trusted_actors)
        self.validator = TaskAuthorityReadCommandValidator(reference)
        self.runner = runner

    def _read(
        self,
        args: Sequence[str],
        *,
        allow_not_found: bool,
    ) -> Mapping[str, Any] | None:
        command = self.validator.validate(args)
        try:
            result = self.runner(
                list(command),
                check=True,
                text=True,
                capture_output=True,
                timeout=60,
            )
        except subprocess.CalledProcessError as exc:
            stderr = str(exc.stderr or "")
            if allow_not_found and (
                "HTTP 404" in stderr
                or "Not Found" in stderr
                or "Could not resolve to" in stderr
            ):
                return None
            raise TaskAuthorityRemoteUnavailable(
                f"GitHub task-authority read failed: {stderr[:500]}"
            ) from exc
        except subprocess.SubprocessError as exc:
            raise TaskAuthorityRemoteUnavailable(str(exc)) from exc

        try:
            value = json.loads(result.stdout or "{}")
        except json.JSONDecodeError as exc:
            raise TaskAuthorityRemoteUnavailable(
                "GitHub task-authority read returned malformed JSON"
            ) from exc
        if not isinstance(value, Mapping):
            raise TaskAuthorityRemoteUnavailable(
                "GitHub task-authority read returned malformed shape"
            )
        return value

    def hydrate(self) -> TaskAuthorityHydrationResult:
        ref = self.reference
        issue = self._read(
            ["gh", "api", f"repos/{ref.repo}/issues/{ref.issue_number}"],
            allow_not_found=True,
        )
        comment = self._read(
            ["gh", "api", f"repos/{ref.repo}/issues/comments/{ref.comment_id}"],
            allow_not_found=True,
        )
        return hydrate_task_authority(
            reference=ref,
            issue=issue,
            comment=comment,
            trusted_actors=self.trusted_actors,
        )
