"""Read-only live external-producer claim parity collector for Platform v2."""

from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import subprocess
import sys
from typing import Any, Callable, Mapping, Sequence

import platform_v2_shadow_collector as base
from v2.decision import SourcePRState
from v2.external import (
    ClaimRetentionDisposition,
    ExternalIssueState,
    ExternalProducerClaim,
    classify_claim_retention,
)
from v2.identity import canonical_digest


PR_JSON_FIELDS = "state,mergedAt"
ISSUE_JSON_FIELDS = "state"


def validate_readonly_pr_command(
    args: Sequence[str],
    *,
    repo: str,
    expected_pr: int,
) -> tuple[str, ...]:
    validated_repo = base.validate_repo(repo)
    command = tuple(str(part) for part in args)
    expected = (
        "gh",
        "pr",
        "view",
        str(expected_pr),
        "--repo",
        validated_repo,
        "--json",
        PR_JSON_FIELDS,
        "--jq=.",
    )
    if command != expected or expected_pr <= 0:
        raise ValueError("external claim shadow permits only exact bound-PR reads")
    return command


def validate_readonly_issue_command(
    args: Sequence[str],
    *,
    repo: str,
    expected_issue: int,
) -> tuple[str, ...]:
    validated_repo = base.validate_repo(repo)
    command = tuple(str(part) for part in args)
    expected = (
        "gh",
        "issue",
        "view",
        str(expected_issue),
        "--repo",
        validated_repo,
        "--json",
        ISSUE_JSON_FIELDS,
        "--jq=.",
    )
    if command != expected or expected_issue <= 0:
        raise ValueError("external claim shadow permits only exact governing-issue reads")
    return command


def _run_json(
    command: Sequence[str],
    *,
    root: Path,
    runner: Callable[..., subprocess.CompletedProcess[str]],
) -> Mapping[str, Any]:
    result = runner(
        list(command),
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
        timeout=60,
    )
    try:
        payload = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise ValueError("external claim remote observation returned malformed JSON") from exc
    if not isinstance(payload, Mapping):
        raise ValueError("external claim remote observation must be an object")
    return payload


def _observe_bound_pr(
    claim: ExternalProducerClaim,
    *,
    root: Path,
    repo: str,
    runner: Callable[..., subprocess.CompletedProcess[str]],
) -> tuple[SourcePRState, dict[str, Any]]:
    if claim.pr_number is None:
        raise ValueError("bound-PR observation requires pr_number")
    command = validate_readonly_pr_command(
        [
            "gh",
            "pr",
            "view",
            str(claim.pr_number),
            "--repo",
            repo,
            "--json",
            PR_JSON_FIELDS,
            "--jq=.",
        ],
        repo=repo,
        expected_pr=claim.pr_number,
    )
    payload = _run_json(command, root=root, runner=runner)
    state = str(payload.get("state") or "").upper()
    merged_at = payload.get("mergedAt")
    if merged_at or state == "MERGED":
        resolved = SourcePRState.MERGED
    elif state == "OPEN":
        resolved = SourcePRState.OPEN
    elif state == "CLOSED":
        resolved = SourcePRState.CLOSED
    else:
        resolved = SourcePRState.UNKNOWN
    return resolved, {"state": state, "merged_at": merged_at}


def _observe_issue(
    claim: ExternalProducerClaim,
    *,
    root: Path,
    repo: str,
    runner: Callable[..., subprocess.CompletedProcess[str]],
) -> tuple[ExternalIssueState, dict[str, Any]]:
    command = validate_readonly_issue_command(
        [
            "gh",
            "issue",
            "view",
            str(claim.issue_number),
            "--repo",
            repo,
            "--json",
            ISSUE_JSON_FIELDS,
            "--jq=.",
        ],
        repo=repo,
        expected_issue=claim.issue_number,
    )
    payload = _run_json(command, root=root, runner=runner)
    state = str(payload.get("state") or "").upper()
    if state == "OPEN":
        resolved = ExternalIssueState.OPEN
    elif state == "CLOSED":
        resolved = ExternalIssueState.CLOSED
    else:
        resolved = ExternalIssueState.UNKNOWN
    return resolved, {"state": state}


def _claim_sample(
    claim: ExternalProducerClaim,
    *,
    sample_kind: str,
    expected_disposition: ClaimRetentionDisposition,
    root: Path,
    repo: str,
    runner: Callable[..., subprocess.CompletedProcess[str]],
    legacy_reason: str | None = None,
) -> dict[str, Any]:
    pr_state = SourcePRState.UNKNOWN
    issue_state = ExternalIssueState.UNKNOWN
    remote: dict[str, Any]

    if claim.pr_number is not None:
        pr_state, remote = _observe_bound_pr(
            claim,
            root=root,
            repo=repo,
            runner=runner,
        )
        decision = classify_claim_retention(
            claim,
            pr_state,
            remote_pr_observation_available=True,
        )
        remote_kind = "pr"
    else:
        issue_state, remote = _observe_issue(
            claim,
            root=root,
            repo=repo,
            runner=runner,
        )
        decision = classify_claim_retention(
            claim,
            remote_issue_state=issue_state,
            remote_issue_observation_available=True,
        )
        remote_kind = "issue"

    agrees = decision.disposition is expected_disposition
    classification = "AGREE" if agrees else "DIVERGENCE"
    body = {
        "sample_kind": sample_kind,
        "issue_number": claim.issue_number,
        "pr_number": claim.pr_number,
        "claim_digest": claim.digest,
        "remote_kind": remote_kind,
        "remote": remote,
        "v2_disposition": decision.disposition.value,
        "v2_reason": decision.reason,
        "v2_decision_digest": decision.digest,
        "expected_legacy_disposition": expected_disposition.value,
        "legacy_retire_reason": legacy_reason,
        "classification": classification,
    }
    return {**body, "digest": canonical_digest(body)}


def _active_claims(state: Mapping[str, Any]) -> list[ExternalProducerClaim]:
    raw = state.get("external_producer_claims") or {}
    if not isinstance(raw, Mapping):
        raise ValueError("external_producer_claims must be an object")
    claims: list[ExternalProducerClaim] = []
    for key, value in sorted(raw.items(), key=lambda item: str(item[0])):
        claim = ExternalProducerClaim.from_legacy_mapping(value)
        if str(claim.issue_number) != str(key):
            raise ValueError("external claim key/issue identity mismatch")
        claims.append(claim)
    return claims


def _recent_retired_claims(state: Mapping[str, Any]) -> list[tuple[ExternalProducerClaim, str]]:
    raw = state.get("last_external_producer_auto_retire")
    if raw is None:
        return []
    if not isinstance(raw, Mapping):
        raise ValueError("last_external_producer_auto_retire must be an object")
    retired = raw.get("retired") or []
    if not isinstance(retired, list):
        raise ValueError("last_external_producer_auto_retire.retired must be a list")

    result: list[tuple[ExternalProducerClaim, str]] = []
    valid_reasons = {"bound_pr_merged", "bound_pr_closed", "issue_closed"}
    for value in retired:
        if not isinstance(value, Mapping):
            raise ValueError("retired external claim record must be an object")
        reason = str(value.get("reason") or "")
        if reason not in valid_reasons:
            raise ValueError(f"unsupported external auto-retire reason: {reason!r}")
        result.append((ExternalProducerClaim.from_legacy_mapping(value), reason))
    return result


def collect_external_claim_shadow(
    *,
    root: Path,
    repo: str,
    gh_runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> dict[str, Any]:
    repo = base.validate_repo(repo)
    state = base.read_legacy_state(root)
    samples: list[dict[str, Any]] = []

    for claim in _active_claims(state):
        samples.append(
            _claim_sample(
                claim,
                sample_kind="ACTIVE",
                expected_disposition=ClaimRetentionDisposition.KEEP,
                root=root,
                repo=repo,
                runner=gh_runner,
            )
        )

    for claim, reason in _recent_retired_claims(state):
        samples.append(
            _claim_sample(
                claim,
                sample_kind="RECENT_AUTO_RETIRED",
                expected_disposition=ClaimRetentionDisposition.RETIRE,
                root=root,
                repo=repo,
                runner=gh_runner,
                legacy_reason=reason,
            )
        )

    counts = Counter(sample["classification"] for sample in samples)
    body = {
        "schema_version": 1,
        "sample_count": len(samples),
        "samples": samples,
        "summary": dict(sorted(counts.items())),
    }
    return {**body, "digest": canonical_digest(body)}


def render(value: Mapping[str, Any]) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Collect live read-only external-producer claim parity evidence."
    )
    parser.add_argument("--root", default=".")
    parser.add_argument("--repo", default=base.DEFAULT_REPO)
    args = parser.parse_args(argv)
    try:
        value = collect_external_claim_shadow(
            root=Path(args.root).resolve(),
            repo=args.repo,
        )
    except (ValueError, subprocess.SubprocessError) as exc:
        print(f"external claim shadow rejected: {exc}", file=sys.stderr)
        return 2
    print(render(value))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
