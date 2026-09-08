#!/usr/bin/env python3
"""Verify that GitHub enforces the minimum unattended-operation contract on main."""

from __future__ import annotations

import json
import subprocess
import sys
from typing import Any

REPO = "ni-da-ba/skyforge"
BRANCH = "main"
REQUIRED_RULESET_TYPES = {
    "pull_request",
    "required_status_checks",
    "non_fast_forward",
    "deletion",
}


def _run_json(args: list[str]) -> Any:
    result = subprocess.run(
        args,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
        timeout=60,
    )
    return json.loads(result.stdout or "null")


def ruleset_protection_ok(rules: Any) -> tuple[bool, set[str]]:
    if not isinstance(rules, list):
        return False, set()
    kinds = {
        str(item.get("type") or "")
        for item in rules
        if isinstance(item, dict)
    }
    return REQUIRED_RULESET_TYPES.issubset(kinds), kinds


def classic_protection_ok(value: Any) -> bool:
    if not isinstance(value, dict):
        return False
    pr_required = value.get("required_pull_request_reviews") is not None
    status_required = value.get("required_status_checks") is not None
    force_push = bool(((value.get("allow_force_pushes") or {}).get("enabled")))
    deletion = bool(((value.get("allow_deletions") or {}).get("enabled")))
    return pr_required and status_required and not force_push and not deletion


def verify(repo: str = REPO, branch: str = BRANCH) -> tuple[bool, str]:
    rules_error = None
    try:
        rules = _run_json(["gh", "api", f"repos/{repo}/rules/branches/{branch}"])
        ok, kinds = ruleset_protection_ok(rules)
        if ok:
            return True, "active ruleset protection: " + ", ".join(sorted(kinds))
    except Exception as exc:
        rules_error = str(exc)

    classic_error = None
    try:
        classic = _run_json(["gh", "api", f"repos/{repo}/branches/{branch}/protection"])
        if classic_protection_ok(classic):
            return True, "classic branch protection requires PR + status checks and blocks force-push/deletion"
    except Exception as exc:
        classic_error = str(exc)

    details = []
    if rules_error:
        details.append(f"ruleset query failed: {rules_error}")
    if classic_error:
        details.append(f"classic protection query failed: {classic_error}")
    suffix = (" " + " | ".join(details)) if details else ""
    return (
        False,
        "main is not verifiably protected for unattended operation. Require pull requests and status "
        "checks, and block force pushes and branch deletion before hosted activation." + suffix,
    )


def main() -> int:
    repo = sys.argv[1] if len(sys.argv) > 1 else REPO
    branch = sys.argv[2] if len(sys.argv) > 2 else BRANCH
    ok, message = verify(repo, branch)
    print(message)
    return 0 if ok else 2


if __name__ == "__main__":
    raise SystemExit(main())
