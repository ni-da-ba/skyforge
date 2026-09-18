"""Release-4 pre-staged documentation canary executor CLI.

The command is mutation-capable only when the repository mutation gate explicitly
authorizes the exact issue and the legacy controller holds the exact exclusion claim.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys
from typing import Any, Mapping, Sequence
from urllib.parse import quote

import platform_v2_shadow_collector as legacy_reader
from v2.canary import CanaryTaskContract, MutationGateRecord
from v2.canary_executor import (
    CanaryExecutionResult,
    CanaryPRSnapshot,
    CanaryRemoteAdapter,
    CanaryRemoteUnavailable,
    advance_canary,
)
from v2.domain import CIState
from v2.ownership import OwnershipToken


PR_VIEW_FIELDS = "number,state,mergedAt,headRefName,headRefOid,baseRefName,title,body,statusCheckRollup,files"
PR_LIST_FIELDS = "number,state,mergedAt,headRefName,headRefOid,baseRefName,title,body"


def _repo(value: str) -> str:
    return legacy_reader.validate_repo(value)


def validate_gh_command(
    args: Sequence[str],
    *,
    repo: str,
    task: CanaryTaskContract,
    pr_number: int | None = None,
) -> tuple[str, ...]:
    repo = _repo(repo)
    cmd = tuple(str(x) for x in args)

    encoded_branch = quote(task.head_branch, safe="")
    allowed = {
        (
            "gh", "api", f"repos/{repo}/commits/main", "--jq", ".sha"
        ),
        (
            "gh", "api", f"repos/{repo}/commits/{encoded_branch}", "--jq", ".sha"
        ),
        (
            "gh", "pr", "list", "--repo", repo, "--head", task.head_branch,
            "--base", "main", "--state", "all", "--json", PR_LIST_FIELDS, "--jq=."
        ),
        (
            "gh", "pr", "create", "--repo", repo, "--base", "main",
            "--head", task.head_branch, "--title", task.pr_title, "--body", task.pr_body
        ),
    }
    if pr_number is not None:
        allowed.add(
            (
                "gh", "pr", "view", str(pr_number), "--repo", repo,
                "--json", PR_VIEW_FIELDS, "--jq=."
            )
        )
        allowed.add(
            (
                "gh", "pr", "merge", str(pr_number), "--repo", repo,
                "--merge", "--match-head-commit", task.expected_head_sha
            )
        )
    if cmd not in allowed:
        raise ValueError("command is outside the exact Platform-v2 canary GitHub allowlist")
    return cmd


def _ci_state(checks: Any) -> CIState:
    if not isinstance(checks, list) or not checks:
        return CIState.UNKNOWN
    active = False
    bad = False
    success = False
    for raw in checks:
        if not isinstance(raw, Mapping):
            raise ValueError("statusCheckRollup entry must be an object")
        status = str(raw.get("status") or "").upper()
        conclusion = str(raw.get("conclusion") or "").upper()
        if status != "COMPLETED":
            active = True
        elif conclusion in {"FAILURE", "CANCELLED", "TIMED_OUT", "ACTION_REQUIRED", "STARTUP_FAILURE"}:
            bad = True
        elif conclusion == "SUCCESS":
            success = True
    if active:
        return CIState.PENDING
    if bad:
        return CIState.FAIL
    if success:
        return CIState.PASS
    return CIState.UNKNOWN


class GhCanaryRemote(CanaryRemoteAdapter):
    def __init__(
        self,
        *,
        root: Path,
        repo: str,
        task: CanaryTaskContract,
        runner=subprocess.run,
    ) -> None:
        self.root = root
        self.repo = _repo(repo)
        self.task = task
        self.runner = runner

    def _run_json(self, command: Sequence[str], *, pr_number: int | None = None) -> Any:
        validated = validate_gh_command(
            command,
            repo=self.repo,
            task=self.task,
            pr_number=pr_number,
        )
        try:
            result = self.runner(
                list(validated),
                cwd=self.root,
                check=True,
                text=True,
                capture_output=True,
                timeout=60,
            )
        except subprocess.SubprocessError as exc:
            raise CanaryRemoteUnavailable(str(exc)) from exc
        try:
            return json.loads(result.stdout)
        except json.JSONDecodeError as exc:
            raise CanaryRemoteUnavailable("GitHub returned malformed JSON") from exc

    def _run_mutation(self, command: Sequence[str], *, pr_number: int | None = None) -> str:
        validated = validate_gh_command(
            command,
            repo=self.repo,
            task=self.task,
            pr_number=pr_number,
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
        except subprocess.SubprocessError as exc:
            raise CanaryRemoteUnavailable(str(exc)) from exc
        return result.stdout.strip()

    def current_main_sha(self) -> str:
        raw = self._run_mutation(
            ["gh", "api", f"repos/{self.repo}/commits/main", "--jq", ".sha"]
        )
        if len(raw) != 40:
            raise CanaryRemoteUnavailable("current main SHA is malformed")
        return raw

    def branch_head_sha(self, branch: str) -> str:
        if branch != self.task.head_branch:
            raise ValueError("branch read is outside frozen canary task")
        encoded_branch = quote(branch, safe="")
        raw = self._run_mutation(
            ["gh", "api", f"repos/{self.repo}/commits/{encoded_branch}", "--jq", ".sha"]
        )
        if len(raw) != 40:
            raise CanaryRemoteUnavailable("candidate branch SHA is malformed")
        return raw

    def _from_view(self, value: Mapping[str, Any]) -> CanaryPRSnapshot:
        files = value.get("files") or []
        if not isinstance(files, list):
            raise CanaryRemoteUnavailable("PR files observation is malformed")
        paths: list[str] = []
        for raw in files:
            if not isinstance(raw, Mapping) or not isinstance(raw.get("path"), str):
                raise CanaryRemoteUnavailable("PR file entry is malformed")
            paths.append(raw["path"])
        state = str(value.get("state") or "").upper()
        if value.get("mergedAt"):
            state = "MERGED"
        return CanaryPRSnapshot(
            pr_number=int(value["number"]),
            state=state,
            head_branch=str(value.get("headRefName") or ""),
            head_sha=str(value.get("headRefOid") or ""),
            base_branch=str(value.get("baseRefName") or ""),
            title=str(value.get("title") or ""),
            body=str(value.get("body") or ""),
            changed_paths=tuple(sorted(set(paths))),
            ci_state=_ci_state(value.get("statusCheckRollup")),
        )

    def observe_pr(self, task: CanaryTaskContract) -> CanaryPRSnapshot | None:
        if task != self.task:
            raise ValueError("remote adapter is bound to one frozen canary task")
        listing = self._run_json(
            [
                "gh", "pr", "list", "--repo", self.repo,
                "--head", task.head_branch, "--base", "main", "--state", "all",
                "--json", PR_LIST_FIELDS, "--jq=."
            ]
        )
        if not isinstance(listing, list):
            raise CanaryRemoteUnavailable("PR list observation is malformed")
        if not listing:
            return None
        if len(listing) != 1:
            raise CanaryRemoteUnavailable("multiple PRs exist for the frozen canary branch")
        number = listing[0].get("number")
        if isinstance(number, bool) or not isinstance(number, int) or number <= 0:
            raise CanaryRemoteUnavailable("PR list returned invalid number")
        detail = self._run_json(
            [
                "gh", "pr", "view", str(number), "--repo", self.repo,
                "--json", PR_VIEW_FIELDS, "--jq=."
            ],
            pr_number=number,
        )
        if not isinstance(detail, Mapping):
            raise CanaryRemoteUnavailable("PR view observation is malformed")
        return self._from_view(detail)

    def create_pr(self, task: CanaryTaskContract) -> CanaryPRSnapshot:
        if task != self.task:
            raise ValueError("remote adapter is bound to one frozen canary task")
        self._run_mutation(
            [
                "gh", "pr", "create", "--repo", self.repo, "--base", "main",
                "--head", task.head_branch, "--title", task.pr_title, "--body", task.pr_body
            ]
        )
        snapshot = self.observe_pr(task)
        if snapshot is None:
            raise CanaryRemoteUnavailable("PR creation returned but exact PR cannot be observed")
        return snapshot

    def merge_pr(self, pr_number: int, expected_head_sha: str) -> None:
        if expected_head_sha != self.task.expected_head_sha:
            raise ValueError("merge expected-head SHA is outside frozen canary task")
        self._run_mutation(
            [
                "gh", "pr", "merge", str(pr_number), "--repo", self.repo,
                "--merge", "--match-head-commit", expected_head_sha
            ],
            pr_number=pr_number,
        )


def _read_json(path: Path, label: str) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"{label} is unreadable: {path}") from exc


def render_result(result: CanaryExecutionResult) -> str:
    return json.dumps(
        {
            "schema_version": 1,
            "disposition": result.disposition.value,
            "reason": result.reason,
            "state": result.state.as_dict(),
            "state_digest": result.state.digest,
            "pr_snapshot_digest": result.pr_snapshot_digest,
            "result_digest": result.digest,
        },
        sort_keys=True,
        separators=(",", ":"),
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Advance one exact Platform-v2 pre-staged documentation canary."
    )
    parser.add_argument("--root", required=True)
    parser.add_argument("--legacy-root", required=True)
    parser.add_argument("--gate", required=True)
    parser.add_argument("--spec", required=True)
    parser.add_argument("--repo", default=legacy_reader.DEFAULT_REPO)
    parser.add_argument("--controller-id", default="platform-v2-canary")
    parser.add_argument("--generation", required=True, type=int)
    parser.add_argument("--attempt", required=True, type=int)
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    try:
        gate = MutationGateRecord.from_mapping(_read_json(Path(args.gate), "mutation gate"))
        task = CanaryTaskContract.from_mapping(_read_json(Path(args.spec), "canary task"))
        legacy_state = legacy_reader.read_legacy_state(Path(args.legacy_root).resolve())
        remote = GhCanaryRemote(root=root, repo=args.repo, task=task)
        result = advance_canary(
            root=root,
            legacy_state=legacy_state,
            gate=gate,
            task=task,
            ownership_token=OwnershipToken(args.controller_id, args.generation),
            attempt_number=args.attempt,
            remote=remote,
        )
    except (ValueError, CanaryRemoteUnavailable) as exc:
        print(f"canary executor rejected: {exc}", file=sys.stderr)
        return 2

    print(render_result(result))
    return 0 if result.disposition.value != "BLOCKED" else 3


if __name__ == "__main__":
    raise SystemExit(main())
