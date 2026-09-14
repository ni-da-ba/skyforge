#!/usr/bin/env python3
"""Manage the canonical CI evidence entry-point contract.

This tool is intentionally dependency-free. During migration it can extract the
existing inline `test -f` contract from `.github/workflows/ci.yml`, emit that
contract as JSON, compare a persisted manifest with the inline source, and
verify the required files against a repository root.
"""

from __future__ import annotations

import argparse
import json
import re
import shlex
from pathlib import Path
from typing import Iterable

STEP_NAME = "Verify evidence review entry points"
DEFAULT_WORKFLOW = Path(".github/workflows/ci.yml")
DEFAULT_MANIFEST = Path("config/ci/evidence-entry-points.json")
STEP_RE = re.compile(r"^(?P<indent>\s*)-\s+name:\s*(?P<name>.+?)\s*$")


class ContractError(RuntimeError):
    """Raised when the evidence contract cannot be interpreted safely."""


def extract_required_paths(workflow_text: str, step_name: str = STEP_NAME) -> list[str]:
    lines = workflow_text.splitlines()
    start = None
    step_indent = None
    for index, line in enumerate(lines):
        match = STEP_RE.match(line)
        if match and match.group("name") == step_name:
            if start is not None:
                raise ContractError(f"workflow contains duplicate step named {step_name!r}")
            start = index + 1
            step_indent = len(match.group("indent"))

    if start is None or step_indent is None:
        raise ContractError(f"workflow step {step_name!r} was not found")

    paths: list[str] = []
    for line in lines[start:]:
        match = STEP_RE.match(line)
        if match and len(match.group("indent")) == step_indent:
            break

        stripped = line.strip()
        if not stripped.startswith("test -f "):
            continue
        tokens = shlex.split(stripped)
        if len(tokens) != 3 or tokens[:2] != ["test", "-f"]:
            raise ContractError(f"unsupported evidence assertion: {stripped}")
        path = tokens[2]
        _validate_path(path)
        paths.append(path)

    if not paths:
        raise ContractError(f"workflow step {step_name!r} contains no `test -f` assertions")
    if len(paths) != len(set(paths)):
        duplicates = sorted({path for path in paths if paths.count(path) > 1})
        raise ContractError(f"duplicate evidence paths: {duplicates}")
    return paths


def _validate_path(path: str) -> None:
    if not path.startswith("skyforge-reference/build/evidence/"):
        raise ContractError(f"evidence path escapes canonical evidence root: {path}")
    if any(char in path for char in "*?[\n\r"):
        raise ContractError(f"evidence path must be concrete, not a glob/control string: {path}")
    pure = Path(path)
    if pure.is_absolute() or ".." in pure.parts:
        raise ContractError(f"unsafe evidence path: {path}")


def make_manifest(paths: Iterable[str]) -> dict[str, object]:
    return {
        "schema_version": 1,
        "source_step": STEP_NAME,
        "required_paths": list(paths),
    }


def load_manifest(path: Path) -> dict[str, object]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ContractError(f"unable to load manifest {path}: {exc}") from exc
    if payload.get("schema_version") != 1:
        raise ContractError(f"unsupported manifest schema in {path}")
    if payload.get("source_step") != STEP_NAME:
        raise ContractError(f"unexpected source_step in {path}")
    required = payload.get("required_paths")
    if not isinstance(required, list) or not required or not all(isinstance(item, str) for item in required):
        raise ContractError(f"required_paths in {path} must be a non-empty string list")
    for item in required:
        _validate_path(item)
    if len(required) != len(set(required)):
        raise ContractError(f"manifest {path} contains duplicate paths")
    return payload


def verify_equivalence(workflow_paths: list[str], manifest_paths: list[str]) -> None:
    if workflow_paths == manifest_paths:
        return
    workflow_set = set(workflow_paths)
    manifest_set = set(manifest_paths)
    missing = [path for path in workflow_paths if path not in manifest_set]
    extra = [path for path in manifest_paths if path not in workflow_set]
    order_only = not missing and not extra
    details = []
    if missing:
        details.append(f"missing from manifest: {missing}")
    if extra:
        details.append(f"extra in manifest: {extra}")
    if order_only:
        details.append("path sets match but ordering differs")
    raise ContractError("manifest is not equivalent to canonical CI contract; " + "; ".join(details))


def verify_files(paths: Iterable[str], root: Path) -> None:
    missing = [path for path in paths if not (root / path).is_file()]
    if missing:
        raise ContractError("required evidence files are missing:\n" + "\n".join(missing))


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--workflow", type=Path, default=DEFAULT_WORKFLOW)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--emit-manifest", action="store_true", help="emit JSON derived from the current inline CI contract")
    parser.add_argument("--check-equivalence", action="store_true", help="require manifest and inline CI contracts to match exactly")
    parser.add_argument("--verify-files", action="store_true", help="verify every required manifest path exists")
    parser.add_argument("--root", type=Path, default=Path("."), help="repository root used by --verify-files")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    workflow_paths = extract_required_paths(args.workflow.read_text(encoding="utf-8"))

    if args.emit_manifest:
        print(json.dumps(make_manifest(workflow_paths), indent=2) + "\n", end="")

    if args.check_equivalence or args.verify_files:
        manifest = load_manifest(args.manifest)
        manifest_paths = list(manifest["required_paths"])
        if args.check_equivalence:
            verify_equivalence(workflow_paths, manifest_paths)
        if args.verify_files:
            verify_files(manifest_paths, args.root)

    if not (args.emit_manifest or args.check_equivalence or args.verify_files):
        print(f"{len(workflow_paths)} canonical evidence paths extracted from {args.workflow}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ContractError as exc:
        raise SystemExit(f"ERROR: {exc}") from exc
