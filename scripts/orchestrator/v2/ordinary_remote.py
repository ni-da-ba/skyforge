"""Strict Git/GitHub adapters for bounded ordinary Platform-v2 effects."""

from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import subprocess
from typing import Any, Mapping, Sequence
from urllib.parse import quote

from .effects import (
    EffectKind,
    RemoteEffectIdentity,
    RemoteEffectObservation,
    RemoteEffectPresence,
)
from .ordinary_effect_executor import OrdinaryRemoteUnavailable
from .ordinary_effects import OrdinaryMutationScope


PR_LIST_FIELDS = "number,state,mergedAt,headRefName,headRefOid,baseRefName,title,body"
PR_VIEW_FIELDS = PR_LIST_FIELDS
COMMENT_MARKER_PREFIX = "<!-- skyforge:v2-effect:"
COMMENT_MARKER_SUFFIX = " -->"


def _command(value: Sequence[str]) -> tuple[str, ...]:
    return tuple(str(part) for part in value)


def _effect_marker(identity: RemoteEffectIdentity) -> str:
    return f"{COMMENT_MARKER_PREFIX}{identity.effect_id}{COMMENT_MARKER_SUFFIX}"


def comment_payload(identity: RemoteEffectIdentity, body: str) -> str:
    body = str(body or "").strip()
    if not body:
        raise ValueError("comment body is required")
    return f"{_effect_marker(identity)}\n{body}"


@dataclass(frozen=True)
class OrdinaryEffectBinding:
    scope: OrdinaryMutationScope
    identity: RemoteEffectIdentity
    pr_number: int | None = None
    comment_body: str = ""

    def __post_init__(self) -> None:
        expected: RemoteEffectIdentity
        if self.identity.kind is EffectKind.PUSH_BRANCH:
            expected = self.scope.push_identity()
        elif self.identity.kind is EffectKind.CREATE_PR:
            expected = self.scope.create_pr_identity()
        elif self.identity.kind is EffectKind.MERGE_PR:
            if self.pr_number is None:
                raise ValueError("MERGE_PR binding requires pr_number")
            expected = self.scope.merge_identity(self.pr_number)
        elif self.identity.kind is EffectKind.POST_COMMENT:
            expected = self.scope.comment_identity(self.comment_body)
        else:
            raise ValueError(
                f"ordinary adapter does not support effect kind {self.identity.kind.value}"
            )
        if expected != self.identity:
            raise ValueError("effect binding identity does not match frozen mutation scope")


class OrdinaryCommandValidator:
    """Exact command validator; callers cannot synthesize arbitrary gh/git writes."""

    def __init__(self, binding: OrdinaryEffectBinding) -> None:
        self.binding = binding

    @property
    def scope(self) -> OrdinaryMutationScope:
        return self.binding.scope

    def allowed(self) -> set[tuple[str, ...]]:
        scope = self.scope
        encoded_branch = quote(scope.branch, safe="")
        encoded_base = quote(scope.base_ref, safe="")
        commands: set[tuple[str, ...]] = {
            (
                "gh", "api", f"repos/{scope.repo}/commits/{encoded_base}",
                "--jq", ".sha",
            ),
            (
                "gh", "api", f"repos/{scope.repo}/commits/{encoded_branch}",
                "--jq", ".sha",
            ),
            (
                "gh", "pr", "list", "--repo", scope.repo,
                "--head", scope.branch, "--base", scope.base_ref, "--state", "all",
                "--json", PR_LIST_FIELDS, "--jq=.",
            ),
            (
                "git", "push", "origin",
                f"{scope.expected_head_sha}:refs/heads/{scope.branch}",
            ),
            (
                "gh", "pr", "create", "--repo", scope.repo,
                "--draft", "--base", scope.base_ref, "--head", scope.branch,
                "--title", scope.pr_title, "--body", scope.pr_body,
            ),
        }
        if self.binding.pr_number is not None:
            number = str(self.binding.pr_number)
            commands.add(
                (
                    "gh", "pr", "view", number, "--repo", scope.repo,
                    "--json", PR_VIEW_FIELDS, "--jq=.",
                )
            )
            commands.add(
                (
                    "gh", "pr", "merge", number, "--repo", scope.repo,
                    "--merge", "--match-head-commit", scope.expected_head_sha,
                )
            )
        if scope.issue_number is not None and self.binding.comment_body:
            exact_body = comment_payload(
                self.binding.identity,
                self.binding.comment_body,
            )
            commands.add(
                (
                    "gh", "api",
                    f"repos/{scope.repo}/issues/{scope.issue_number}/comments?per_page=100",
                    "--paginate", "--slurp",
                )
            )
            commands.add(
                (
                    "gh", "issue", "comment", str(scope.issue_number),
                    "--repo", scope.repo, "--body", exact_body,
                )
            )
        return commands

    def validate(self, args: Sequence[str]) -> tuple[str, ...]:
        command = _command(args)
        if command not in self.allowed():
            raise ValueError(
                "command is outside the exact Platform-v2 ordinary-effect allowlist"
            )
        return command


class GhGitOrdinaryEffectAdapter:
    """Adapter bound to one immutable ordinary effect."""

    def __init__(
        self,
        *,
        root: Path,
        binding: OrdinaryEffectBinding,
        runner=subprocess.run,
    ) -> None:
        self.root = Path(root)
        self.binding = binding
        self.validator = OrdinaryCommandValidator(binding)
        self.runner = runner

    def _run(
        self,
        args: Sequence[str],
        *,
        mutation: bool = False,
        allow_not_found: bool = False,
    ) -> str | None:
        command = self.validator.validate(args)
        try:
            result = self.runner(
                list(command),
                cwd=self.root,
                check=True,
                text=True,
                capture_output=True,
                timeout=120 if mutation else 60,
            )
        except subprocess.CalledProcessError as exc:
            stderr = str(exc.stderr or "")
            if allow_not_found and (
                "HTTP 404" in stderr
                or "Not Found" in stderr
                or "No commit found for SHA" in stderr
            ):
                return None
            raise OrdinaryRemoteUnavailable(
                f"command failed without safe exact result: {stderr[:500]}"
            ) from exc
        except subprocess.SubprocessError as exc:
            raise OrdinaryRemoteUnavailable(str(exc)) from exc
        return result.stdout.strip()

    def _current_base(self) -> str:
        scope = self.binding.scope
        encoded = quote(scope.base_ref, safe="")
        output = self._run(
            [
                "gh", "api", f"repos/{scope.repo}/commits/{encoded}",
                "--jq", ".sha",
            ]
        )
        if output is None or len(output) != 40:
            raise OrdinaryRemoteUnavailable("current base SHA is malformed")
        return output

    def _branch_head(self) -> str | None:
        scope = self.binding.scope
        encoded = quote(scope.branch, safe="")
        output = self._run(
            [
                "gh", "api", f"repos/{scope.repo}/commits/{encoded}",
                "--jq", ".sha",
            ],
            allow_not_found=True,
        )
        if output is None:
            return None
        if len(output) != 40:
            raise OrdinaryRemoteUnavailable("remote branch SHA is malformed")
        return output

    def _pr_listing(self) -> list[Mapping[str, Any]]:
        scope = self.binding.scope
        output = self._run(
            [
                "gh", "pr", "list", "--repo", scope.repo,
                "--head", scope.branch, "--base", scope.base_ref, "--state", "all",
                "--json", PR_LIST_FIELDS, "--jq=.",
            ]
        )
        try:
            value = json.loads(output or "[]")
        except json.JSONDecodeError as exc:
            raise OrdinaryRemoteUnavailable("PR list returned malformed JSON") from exc
        if not isinstance(value, list) or any(
            not isinstance(item, Mapping) for item in value
        ):
            raise OrdinaryRemoteUnavailable("PR list returned malformed shape")
        return value

    def _pr_view(self, pr_number: int) -> Mapping[str, Any]:
        scope = self.binding.scope
        output = self._run(
            [
                "gh", "pr", "view", str(pr_number), "--repo", scope.repo,
                "--json", PR_VIEW_FIELDS, "--jq=.",
            ]
        )
        try:
            value = json.loads(output or "{}")
        except json.JSONDecodeError as exc:
            raise OrdinaryRemoteUnavailable("PR view returned malformed JSON") from exc
        if not isinstance(value, Mapping):
            raise OrdinaryRemoteUnavailable("PR view returned malformed shape")
        return value

    def _exact_pr_identity(self, value: Mapping[str, Any]) -> tuple[bool, str]:
        scope = self.binding.scope
        try:
            number = int(value.get("number"))
        except (TypeError, ValueError):
            return False, ""
        state = str(value.get("state") or "").upper()
        if value.get("mergedAt"):
            state = "MERGED"
        exact = (
            str(value.get("headRefName") or "") == scope.branch
            and str(value.get("headRefOid") or "") == scope.expected_head_sha
            and str(value.get("baseRefName") or "") == scope.base_ref
            and str(value.get("title") or "") == scope.pr_title
            and str(value.get("body") or "") == scope.pr_body
        )
        if not exact:
            return False, ""
        return True, f"pr:{number}:{state}@{scope.expected_head_sha}"

    def _comments(self) -> list[Mapping[str, Any]]:
        scope = self.binding.scope
        assert scope.issue_number is not None
        output = self._run(
            [
                "gh", "api",
                f"repos/{scope.repo}/issues/{scope.issue_number}/comments?per_page=100",
                "--paginate", "--slurp",
            ]
        )
        try:
            pages = json.loads(output or "[]")
        except json.JSONDecodeError as exc:
            raise OrdinaryRemoteUnavailable("comment observation returned malformed JSON") from exc
        if not isinstance(pages, list):
            raise OrdinaryRemoteUnavailable("comment observation returned malformed shape")
        result: list[Mapping[str, Any]] = []
        for page in pages:
            if not isinstance(page, list):
                raise OrdinaryRemoteUnavailable("comment page is malformed")
            for item in page:
                if not isinstance(item, Mapping):
                    raise OrdinaryRemoteUnavailable("comment item is malformed")
                result.append(item)
        return result

    def observe(
        self,
        identity: RemoteEffectIdentity,
    ) -> RemoteEffectObservation:
        if identity != self.binding.identity:
            raise ValueError("adapter is bound to a different effect identity")
        scope = self.binding.scope

        if identity.kind is EffectKind.PUSH_BRANCH:
            head = self._branch_head()
            if head is None:
                return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
            if head == scope.expected_head_sha:
                return RemoteEffectObservation(
                    RemoteEffectPresence.PRESENT_EXACT,
                    remote_identity=f"branch:{scope.branch}@{head}",
                )
            return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)

        if identity.kind is EffectKind.CREATE_PR:
            listing = self._pr_listing()
            if not listing:
                return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
            exact: list[str] = []
            conflicts = 0
            for item in listing:
                ok, remote = self._exact_pr_identity(item)
                if ok:
                    exact.append(remote)
                else:
                    conflicts += 1
            if len(exact) == 1 and conflicts == 0 and len(listing) == 1:
                return RemoteEffectObservation(
                    RemoteEffectPresence.PRESENT_EXACT,
                    remote_identity=exact[0],
                )
            return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)

        if identity.kind is EffectKind.POST_COMMENT:
            marker = _effect_marker(identity)
            matches = [
                item for item in self._comments()
                if marker in str(item.get("body") or "")
            ]
            if not matches:
                return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
            if len(matches) != 1:
                return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
            item = matches[0]
            expected_body = comment_payload(identity, self.binding.comment_body)
            if str(item.get("body") or "") != expected_body:
                return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
            comment_id = item.get("id")
            if isinstance(comment_id, bool) or not isinstance(comment_id, int):
                return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
            return RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT,
                remote_identity=f"comment:{comment_id}",
            )

        if identity.kind is EffectKind.MERGE_PR:
            assert self.binding.pr_number is not None
            view = self._pr_view(self.binding.pr_number)
            ok, _ = self._exact_pr_identity(view)
            if not ok:
                return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
            state = (
                "MERGED"
                if view.get("mergedAt")
                else str(view.get("state") or "").upper()
            )
            if state == "OPEN":
                return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
            if state == "MERGED":
                return RemoteEffectObservation(
                    RemoteEffectPresence.PRESENT_EXACT,
                    remote_identity=(
                        f"merge:pr:{self.binding.pr_number}@"
                        f"{scope.expected_head_sha}"
                    ),
                )
            return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)

        raise ValueError("unsupported ordinary effect kind")

    def execute(self, identity: RemoteEffectIdentity) -> str:
        if identity != self.binding.identity:
            raise ValueError("adapter is bound to a different effect identity")
        scope = self.binding.scope

        if identity.kind is EffectKind.PUSH_BRANCH:
            self._run(
                [
                    "git", "push", "origin",
                    f"{scope.expected_head_sha}:refs/heads/{scope.branch}",
                ],
                mutation=True,
            )
            return f"branch:{scope.branch}@{scope.expected_head_sha}"

        if identity.kind is EffectKind.CREATE_PR:
            # Close the stale-read window immediately before PR creation.  The PR is
            # meaningful only against the frozen base/head pair.
            if self._current_base() != scope.base_sha:
                raise OrdinaryRemoteUnavailable(
                    (
                        "current main moved from frozen ordinary-task base"
                        if scope.base_ref == "main"
                        else "current base ref moved from frozen ordinary-task base"
                    )
                )
            if self._branch_head() != scope.expected_head_sha:
                raise OrdinaryRemoteUnavailable(
                    "remote branch moved from frozen ordinary-task head"
                )
            self._run(
                [
                    "gh", "pr", "create", "--repo", scope.repo,
                    "--draft", "--base", scope.base_ref, "--head", scope.branch,
                    "--title", scope.pr_title, "--body", scope.pr_body,
                ],
                mutation=True,
            )
            # Exact PR number is learned from post-execution observation.
            return ""

        if identity.kind is EffectKind.POST_COMMENT:
            assert scope.issue_number is not None
            body = comment_payload(identity, self.binding.comment_body)
            output = self._run(
                [
                    "gh", "issue", "comment", str(scope.issue_number),
                    "--repo", scope.repo, "--body", body,
                ],
                mutation=True,
            )
            # gh output identity is not trusted; observation owns exact comment ID.
            return ""

        if identity.kind is EffectKind.MERGE_PR:
            assert self.binding.pr_number is not None
            # Re-observe exact PR identity immediately before mutation rather than
            # relying only on the executor's preceding observation.
            view = self._pr_view(self.binding.pr_number)
            ok, _ = self._exact_pr_identity(view)
            state = (
                "MERGED"
                if view.get("mergedAt")
                else str(view.get("state") or "").upper()
            )
            if not ok or state != "OPEN":
                raise OrdinaryRemoteUnavailable(
                    "PR identity/head moved before expected-head merge"
                )
            self._run(
                [
                    "gh", "pr", "merge", str(self.binding.pr_number),
                    "--repo", scope.repo, "--merge",
                    "--match-head-commit", scope.expected_head_sha,
                ],
                mutation=True,
            )
            return (
                f"merge:pr:{self.binding.pr_number}@{scope.expected_head_sha}"
            )

        raise ValueError("unsupported ordinary effect kind")
