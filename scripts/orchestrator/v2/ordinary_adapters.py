"""Bounded ordinary-task Git/workspace and GitHub effect adapters for Platform v2.

These adapters are deliberately narrow.  Each instance is frozen to one
OrdinaryMutationScope and one explicit effect.  They expose no general command
execution surface.
"""

from __future__ import annotations

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
from .ordinary_effect_executor import OrdinaryEffectAdapter, OrdinaryRemoteUnavailable
from .ordinary_effects import OrdinaryMutationScope


PR_LIST_FIELDS = "number,state,mergedAt,headRefName,headRefOid,baseRefName,title,body"
PR_VIEW_FIELDS = "number,state,mergedAt,headRefName,headRefOid,baseRefName,title,body"
ISSUE_VIEW_FIELDS = "comments"


def _validate_repo(repo: str) -> str:
    value = str(repo or "").strip()
    if value.count("/") != 1:
        raise ValueError("repo must be exact owner/name")
    owner, name = value.split("/", 1)
    allowed = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_.-")
    if not owner or not name or any(ch not in allowed for ch in owner + name):
        raise ValueError("repo contains unsupported characters")
    return value


def _validate_branch(branch: str) -> str:
    value = str(branch or "").strip()
    if not value or value.startswith("-") or any(ch.isspace() for ch in value):
        raise ValueError("branch is malformed")
    if ".." in value or value.endswith("/") or "//" in value:
        raise ValueError("branch is malformed")
    return value


def _validate_sha(value: str, label: str) -> str:
    text = str(value or "").strip()
    if len(text) != 40 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be a lowercase 40-character SHA")
    return text


def validate_workspace_command(
    args: Sequence[str],
    *,
    scope: OrdinaryMutationScope,
) -> tuple[str, ...]:
    cmd = tuple(str(part) for part in args)
    branch = _validate_branch(scope.branch)
    expected = _validate_sha(scope.expected_head_sha, "expected_head_sha")
    allowed = {
        ("git", "rev-parse", "HEAD"),
        ("git", "branch", "--show-current"),
        ("git", "status", "--porcelain=v1", "--untracked-files=all"),
        (
            "git",
            "push",
            "origin",
            f"{expected}:refs/heads/{branch}",
        ),
    }
    if cmd not in allowed:
        raise ValueError("command is outside the exact ordinary workspace allowlist")
    return cmd


def validate_github_command(
    args: Sequence[str],
    *,
    scope: OrdinaryMutationScope,
    pr_number: int | None = None,
    comment_body: str | None = None,
) -> tuple[str, ...]:
    repo = _validate_repo(scope.repo)
    branch = _validate_branch(scope.branch)
    encoded_branch = quote(branch, safe="")
    cmd = tuple(str(part) for part in args)

    allowed = {
        ("gh", "api", f"repos/{repo}/commits/main", "--jq", ".sha"),
        (
            "gh",
            "api",
            f"repos/{repo}/commits/{encoded_branch}",
            "--jq",
            ".sha",
        ),
        (
            "gh",
            "pr",
            "list",
            "--repo",
            repo,
            "--head",
            branch,
            "--base",
            "main",
            "--state",
            "all",
            "--json",
            PR_LIST_FIELDS,
            "--jq=.",
        ),
        (
            "gh",
            "pr",
            "create",
            "--repo",
            repo,
            "--base",
            "main",
            "--head",
            branch,
            "--title",
            scope.pr_title,
            "--body",
            scope.pr_body,
        ),
    }
    if pr_number is not None:
        if isinstance(pr_number, bool) or not isinstance(pr_number, int) or pr_number <= 0:
            raise ValueError("pr_number must be positive")
        allowed.add(
            (
                "gh",
                "pr",
                "view",
                str(pr_number),
                "--repo",
                repo,
                "--json",
                PR_VIEW_FIELDS,
                "--jq=.",
            )
        )
        allowed.add(
            (
                "gh",
                "pr",
                "merge",
                str(pr_number),
                "--repo",
                repo,
                "--merge",
                "--match-head-commit",
                scope.expected_head_sha,
            )
        )
    if scope.issue_number is not None and comment_body is not None:
        allowed.add(
            (
                "gh",
                "issue",
                "view",
                str(scope.issue_number),
                "--repo",
                repo,
                "--json",
                ISSUE_VIEW_FIELDS,
                "--jq=.",
            )
        )
        allowed.add(
            (
                "gh",
                "issue",
                "comment",
                str(scope.issue_number),
                "--repo",
                repo,
                "--body",
                comment_body,
            )
        )

    if cmd not in allowed:
        raise ValueError("command is outside the exact ordinary GitHub allowlist")
    return cmd


class OrdinaryWorkspaceEffectAdapter(OrdinaryEffectAdapter):
    """Exact worktree/branch adapter for PUSH_BRANCH only."""

    def __init__(
        self,
        *,
        worktree: Path,
        scope: OrdinaryMutationScope,
        runner=subprocess.run,
    ) -> None:
        self.worktree = Path(worktree).resolve()
        self.scope = scope
        self.runner = runner

    def _run(self, command: Sequence[str]) -> str:
        validated = validate_workspace_command(command, scope=self.scope)
        try:
            result = self.runner(
                list(validated),
                cwd=self.worktree,
                check=True,
                text=True,
                capture_output=True,
                timeout=90,
            )
        except (OSError, subprocess.SubprocessError) as exc:
            raise OrdinaryRemoteUnavailable(str(exc)) from exc
        return result.stdout.strip()

    def _require_push_identity(self, identity: RemoteEffectIdentity) -> None:
        if identity != self.scope.push_identity():
            raise ValueError("workspace adapter is bound to exact PUSH_BRANCH identity")

    def _local_identity(self) -> tuple[str, str, str]:
        head = self._run(["git", "rev-parse", "HEAD"])
        branch = self._run(["git", "branch", "--show-current"])
        status = self._run(
            ["git", "status", "--porcelain=v1", "--untracked-files=all"]
        )
        if head != self.scope.expected_head_sha:
            raise OrdinaryRemoteUnavailable("worktree HEAD moved from frozen expected SHA")
        if branch != self.scope.branch:
            raise OrdinaryRemoteUnavailable("worktree branch moved from frozen branch")
        if status:
            raise OrdinaryRemoteUnavailable("worktree is dirty; push fails closed")
        return head, branch, status

    def _remote_head(self) -> str | None:
        # ls-remote is intentionally not part of the mutation command allowlist and is
        # executed through git itself with an exact immutable argument shape.
        cmd = (
            "git",
            "ls-remote",
            "--heads",
            "origin",
            f"refs/heads/{self.scope.branch}",
        )
        try:
            result = self.runner(
                list(cmd),
                cwd=self.worktree,
                check=True,
                text=True,
                capture_output=True,
                timeout=60,
            )
        except (OSError, subprocess.SubprocessError) as exc:
            raise OrdinaryRemoteUnavailable(str(exc)) from exc
        text = result.stdout.strip()
        if not text:
            return None
        rows = [row for row in text.splitlines() if row.strip()]
        if len(rows) != 1:
            raise OrdinaryRemoteUnavailable("remote branch observation is ambiguous")
        fields = rows[0].split()
        if len(fields) != 2 or fields[1] != f"refs/heads/{self.scope.branch}":
            raise OrdinaryRemoteUnavailable("remote branch observation is malformed")
        return _validate_sha(fields[0], "remote branch SHA")

    def observe(self, identity: RemoteEffectIdentity) -> RemoteEffectObservation:
        self._require_push_identity(identity)
        self._local_identity()
        remote = self._remote_head()
        if remote is None:
            return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
        if remote == self.scope.expected_head_sha:
            return RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT,
                remote_identity=f"branch:{self.scope.branch}@{remote}",
            )
        return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)

    def execute(self, identity: RemoteEffectIdentity) -> str:
        self._require_push_identity(identity)
        self._local_identity()
        self._run(
            [
                "git",
                "push",
                "origin",
                f"{self.scope.expected_head_sha}:refs/heads/{self.scope.branch}",
            ]
        )
        return f"branch:{self.scope.branch}@{self.scope.expected_head_sha}"


class OrdinaryGitHubEffectAdapter(OrdinaryEffectAdapter):
    """Exact GitHub adapter for CREATE_PR, POST_COMMENT, and MERGE_PR."""

    def __init__(
        self,
        *,
        root: Path,
        scope: OrdinaryMutationScope,
        pr_number: int | None = None,
        comment_body: str | None = None,
        runner=subprocess.run,
    ) -> None:
        self.root = Path(root).resolve()
        self.scope = scope
        self.pr_number = pr_number
        self.comment_body = comment_body
        self.runner = runner

    def _run(self, command: Sequence[str]) -> str:
        validated = validate_github_command(
            command,
            scope=self.scope,
            pr_number=self.pr_number,
            comment_body=self.comment_body,
        )
        try:
            result = self.runner(
                list(validated),
                cwd=self.root,
                check=True,
                text=True,
                capture_output=True,
                timeout=90,
            )
        except (OSError, subprocess.SubprocessError) as exc:
            raise OrdinaryRemoteUnavailable(str(exc)) from exc
        return result.stdout.strip()

    def _run_json(self, command: Sequence[str]) -> Any:
        raw = self._run(command)
        try:
            return json.loads(raw)
        except json.JSONDecodeError as exc:
            raise OrdinaryRemoteUnavailable("GitHub returned malformed JSON") from exc

    def _current_main(self) -> str:
        value = self._run(
            ["gh", "api", f"repos/{self.scope.repo}/commits/main", "--jq", ".sha"]
        )
        return _validate_sha(value, "current main SHA")

    def _branch_head(self) -> str:
        encoded = quote(self.scope.branch, safe="")
        value = self._run(
            [
                "gh",
                "api",
                f"repos/{self.scope.repo}/commits/{encoded}",
                "--jq",
                ".sha",
            ]
        )
        return _validate_sha(value, "branch head SHA")

    def _pr_list(self) -> list[Mapping[str, Any]]:
        value = self._run_json(
            [
                "gh",
                "pr",
                "list",
                "--repo",
                self.scope.repo,
                "--head",
                self.scope.branch,
                "--base",
                "main",
                "--state",
                "all",
                "--json",
                PR_LIST_FIELDS,
                "--jq=.",
            ]
        )
        if not isinstance(value, list):
            raise OrdinaryRemoteUnavailable("PR list observation is malformed")
        rows: list[Mapping[str, Any]] = []
        for item in value:
            if not isinstance(item, Mapping):
                raise OrdinaryRemoteUnavailable("PR list row is malformed")
            rows.append(item)
        return rows

    def _exact_pr_row(self) -> Mapping[str, Any] | None:
        rows = self._pr_list()
        if not rows:
            return None
        if len(rows) != 1:
            raise OrdinaryRemoteUnavailable("multiple PRs exist for frozen branch")
        row = rows[0]
        if (
            str(row.get("headRefName") or "") != self.scope.branch
            or str(row.get("headRefOid") or "") != self.scope.expected_head_sha
            or str(row.get("baseRefName") or "") != "main"
            or str(row.get("title") or "") != self.scope.pr_title
            or str(row.get("body") or "") != self.scope.pr_body
        ):
            return {"conflict": True, **dict(row)}
        return row

    def _view_pr(self) -> Mapping[str, Any]:
        if self.pr_number is None:
            raise ValueError("PR-bound effect requires pr_number")
        value = self._run_json(
            [
                "gh",
                "pr",
                "view",
                str(self.pr_number),
                "--repo",
                self.scope.repo,
                "--json",
                PR_VIEW_FIELDS,
                "--jq=.",
            ]
        )
        if not isinstance(value, Mapping):
            raise OrdinaryRemoteUnavailable("PR view is malformed")
        return value

    def _require_identity(self, identity: RemoteEffectIdentity) -> EffectKind:
        if identity == self.scope.create_pr_identity():
            return EffectKind.CREATE_PR
        if self.comment_body is not None and identity == self.scope.comment_identity(
            self.comment_body
        ):
            return EffectKind.POST_COMMENT
        if self.pr_number is not None and identity == self.scope.merge_identity(
            self.pr_number
        ):
            return EffectKind.MERGE_PR
        raise ValueError("GitHub adapter is not bound to this effect identity")

    def observe(self, identity: RemoteEffectIdentity) -> RemoteEffectObservation:
        kind = self._require_identity(identity)

        if kind is EffectKind.CREATE_PR:
            row = self._exact_pr_row()
            if row is None:
                return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
            if row.get("conflict"):
                return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
            number = row.get("number")
            if isinstance(number, bool) or not isinstance(number, int) or number <= 0:
                raise OrdinaryRemoteUnavailable("PR number is malformed")
            return RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT,
                remote_identity=f"pr:{number}",
            )

        if kind is EffectKind.POST_COMMENT:
            if self.scope.issue_number is None or self.comment_body is None:
                raise ValueError("comment effect is incompletely bound")
            value = self._run_json(
                [
                    "gh",
                    "issue",
                    "view",
                    str(self.scope.issue_number),
                    "--repo",
                    self.scope.repo,
                    "--json",
                    ISSUE_VIEW_FIELDS,
                    "--jq=.",
                ]
            )
            if not isinstance(value, Mapping) or not isinstance(value.get("comments"), list):
                raise OrdinaryRemoteUnavailable("issue comments observation is malformed")
            exact_ids: list[int] = []
            for raw in value["comments"]:
                if not isinstance(raw, Mapping):
                    raise OrdinaryRemoteUnavailable("issue comment row is malformed")
                if str(raw.get("body") or "") == self.comment_body:
                    ident = raw.get("id")
                    if isinstance(ident, bool) or not isinstance(ident, int) or ident <= 0:
                        raise OrdinaryRemoteUnavailable("exact comment has malformed identity")
                    exact_ids.append(ident)
            if not exact_ids:
                return RemoteEffectObservation(RemoteEffectPresence.ABSENT)
            if len(exact_ids) != 1:
                return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
            return RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT,
                remote_identity=f"comment:{exact_ids[0]}",
            )

        view = self._view_pr()
        head = str(view.get("headRefOid") or "")
        if head != self.scope.expected_head_sha:
            return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
        state = str(view.get("state") or "").upper()
        merged = bool(view.get("mergedAt")) or state == "MERGED"
        if merged:
            return RemoteEffectObservation(
                RemoteEffectPresence.PRESENT_EXACT,
                remote_identity=(
                    f"merge:pr:{self.pr_number}@{self.scope.expected_head_sha}"
                ),
            )
        if state != "OPEN":
            return RemoteEffectObservation(RemoteEffectPresence.PRESENT_CONFLICT)
        return RemoteEffectObservation(RemoteEffectPresence.ABSENT)

    def execute(self, identity: RemoteEffectIdentity) -> str:
        kind = self._require_identity(identity)

        if kind is EffectKind.CREATE_PR:
            if self._current_main() != self.scope.base_sha:
                raise OrdinaryRemoteUnavailable("current main moved from frozen base")
            if self._branch_head() != self.scope.expected_head_sha:
                raise OrdinaryRemoteUnavailable("candidate branch head moved")
            self._run(
                [
                    "gh",
                    "pr",
                    "create",
                    "--repo",
                    self.scope.repo,
                    "--base",
                    "main",
                    "--head",
                    self.scope.branch,
                    "--title",
                    self.scope.pr_title,
                    "--body",
                    self.scope.pr_body,
                ]
            )
            observation = self.observe(identity)
            if observation.presence is not RemoteEffectPresence.PRESENT_EXACT:
                raise OrdinaryRemoteUnavailable(
                    "PR create returned without exact observable PR"
                )
            return observation.remote_identity

        if kind is EffectKind.POST_COMMENT:
            if self.scope.issue_number is None or self.comment_body is None:
                raise ValueError("comment effect is incompletely bound")
            self._run(
                [
                    "gh",
                    "issue",
                    "comment",
                    str(self.scope.issue_number),
                    "--repo",
                    self.scope.repo,
                    "--body",
                    self.comment_body,
                ]
            )
            observation = self.observe(identity)
            if observation.presence is not RemoteEffectPresence.PRESENT_EXACT:
                raise OrdinaryRemoteUnavailable(
                    "comment returned without exact observable identity"
                )
            return observation.remote_identity

        if self.pr_number is None:
            raise ValueError("merge effect requires pr_number")
        view = self._view_pr()
        if str(view.get("headRefOid") or "") != self.scope.expected_head_sha:
            raise OrdinaryRemoteUnavailable("PR head moved before merge")
        if str(view.get("state") or "").upper() != "OPEN" or view.get("mergedAt"):
            raise OrdinaryRemoteUnavailable("merge requires exact open PR")
        self._run(
            [
                "gh",
                "pr",
                "merge",
                str(self.pr_number),
                "--repo",
                self.scope.repo,
                "--merge",
                "--match-head-commit",
                self.scope.expected_head_sha,
            ]
        )
        return f"merge:pr:{self.pr_number}@{self.scope.expected_head_sha}"
