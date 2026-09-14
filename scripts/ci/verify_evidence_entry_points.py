#!/usr/bin/env python3
"""Manage the canonical CI evidence entry-point contract.

This tool is intentionally dependency-free. During migration it can extract the
legacy inline `test -f` contract from `.github/workflows/ci.yml`, emit a compact
ordered manifest, and compare that manifest with the inline source. At runtime,
the persisted manifest is authoritative and can be validated or used to verify
required evidence files without reading workflow YAML.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shlex
from pathlib import Path
from typing import Iterable

STEP_NAME = "Verify evidence review entry points"
DEFAULT_WORKFLOW = Path(".github/workflows/ci.yml")
DEFAULT_MANIFEST = Path("config/ci/evidence-entry-points.json")
EVIDENCE_ROOT = "skyforge-reference/build/evidence/"
DEFAULT_FILES = ["index.html", "atlas.png", "manifest.csv"]
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

    _validate_required_paths(paths, f"workflow step {step_name!r}")
    return paths


def _validate_path(path: str) -> None:
    if not path.startswith(EVIDENCE_ROOT):
        raise ContractError(f"evidence path escapes canonical evidence root: {path}")
    if any(char in path for char in "*?[\n\r"):
        raise ContractError(f"evidence path must be concrete, not a glob/control string: {path}")
    pure = Path(path)
    if pure.is_absolute() or ".." in pure.parts:
        raise ContractError(f"unsafe evidence path: {path}")


def _validate_required_paths(paths: list[str], source: str) -> None:
    if not paths:
        raise ContractError(f"{source} contains no evidence paths")
    for path in paths:
        _validate_path(path)
    if len(paths) != len(set(paths)):
        duplicates = sorted({path for path in paths if paths.count(path) > 1})
        raise ContractError(f"{source} contains duplicate evidence paths: {duplicates}")


def _validate_filename(filename: object, source: str) -> str:
    if not isinstance(filename, str) or not filename:
        raise ContractError(f"{source} contains a non-string or empty filename")
    if "/" in filename or "\\" in filename or filename in {".", ".."}:
        raise ContractError(f"{source} contains unsafe filename: {filename!r}")
    if any(char in filename for char in "*?[\n\r"):
        raise ContractError(f"{source} contains glob/control filename: {filename!r}")
    return filename


def contract_digest(paths: Iterable[str]) -> str:
    """Return the stable SHA-256 used to identify an ordered path contract."""
    ordered = list(paths)
    _validate_required_paths(ordered, "contract digest input")
    return hashlib.sha256(("\n".join(ordered) + "\n").encode("utf-8")).hexdigest()


def make_manifest(paths: Iterable[str]) -> dict[str, object]:
    """Compact an ordered path contract without changing its expansion order."""
    ordered = list(paths)
    _validate_required_paths(ordered, "manifest input")

    directories: list[str] = []
    files_by_directory: dict[str, list[str]] = {}
    for path in ordered:
        directory, filename = path.rsplit("/", 1)
        short_directory = directory.removeprefix(EVIDENCE_ROOT)
        if not short_directory or directory != EVIDENCE_ROOT.rstrip("/") + "/" + short_directory:
            raise ContractError(f"unable to compact evidence directory safely: {directory}")
        if short_directory not in files_by_directory:
            directories.append(short_directory)
            files_by_directory[short_directory] = []
        files_by_directory[short_directory].append(filename)

    groups: list[dict[str, object]] = []
    for directory in directories:
        files = files_by_directory[directory]
        group: dict[str, object] = {"d": directory}
        if files[: len(DEFAULT_FILES)] == DEFAULT_FILES:
            extras = files[len(DEFAULT_FILES) :]
            if extras:
                group["x"] = extras
        else:
            group["f"] = files
        groups.append(group)

    return {
        "schema_version": 2,
        "source_step": STEP_NAME,
        "root": EVIDENCE_ROOT,
        "default_files": DEFAULT_FILES,
        "groups": groups,
    }


def _expand_schema_v2(payload: dict[str, object], path: Path) -> list[str]:
    if payload.get("root") != EVIDENCE_ROOT:
        raise ContractError(f"unexpected evidence root in {path}")
    defaults = payload.get("default_files")
    if not isinstance(defaults, list) or not defaults:
        raise ContractError(f"default_files in {path} must be a non-empty list")
    default_files = [_validate_filename(item, f"default_files in {path}") for item in defaults]

    groups = payload.get("groups")
    if not isinstance(groups, list) or not groups:
        raise ContractError(f"groups in {path} must be a non-empty list")

    required: list[str] = []
    seen_directories: set[str] = set()
    for index, raw_group in enumerate(groups):
        source = f"group {index} in {path}"
        if not isinstance(raw_group, dict):
            raise ContractError(f"{source} must be an object")
        directory = raw_group.get("d")
        if not isinstance(directory, str) or not directory:
            raise ContractError(f"{source} must contain non-empty string `d`")
        if directory in seen_directories:
            raise ContractError(f"duplicate evidence directory in {path}: {directory}")
        seen_directories.add(directory)
        if directory.startswith("/") or ".." in Path(directory).parts or any(char in directory for char in "*?[\n\r"):
            raise ContractError(f"unsafe evidence directory in {source}: {directory}")

        explicit = raw_group.get("f")
        extras = raw_group.get("x")
        if explicit is not None and extras is not None:
            raise ContractError(f"{source} cannot contain both `f` and `x`")
        if explicit is None:
            files = list(default_files)
            if extras is not None:
                if not isinstance(extras, list):
                    raise ContractError(f"`x` in {source} must be a list")
                files.extend(_validate_filename(item, f"`x` in {source}") for item in extras)
        else:
            if not isinstance(explicit, list) or not explicit:
                raise ContractError(f"`f` in {source} must be a non-empty list")
            files = [_validate_filename(item, f"`f` in {source}") for item in explicit]

        for filename in files:
            required.append(f"{EVIDENCE_ROOT}{directory}/{filename}")

    _validate_required_paths(required, f"manifest {path}")
    return required


def load_manifest(path: Path) -> dict[str, object]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ContractError(f"unable to load manifest {path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise ContractError(f"manifest {path} must contain a JSON object")
    if payload.get("source_step") != STEP_NAME:
        raise ContractError(f"unexpected source_step in {path}")

    version = payload.get("schema_version")
    if version == 1:
        required = payload.get("required_paths")
        if not isinstance(required, list) or not all(isinstance(item, str) for item in required):
            raise ContractError(f"required_paths in {path} must be a string list")
        normalized = list(required)
        _validate_required_paths(normalized, f"manifest {path}")
    elif version == 2:
        normalized = _expand_schema_v2(payload, path)
    else:
        raise ContractError(f"unsupported manifest schema in {path}: {version!r}")

    result = dict(payload)
    result["required_paths"] = normalized
    return result


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
    parser.add_argument("--emit-manifest", action="store_true", help="emit compact JSON derived from the legacy inline CI contract")
    parser.add_argument("--check-equivalence", action="store_true", help="migration check: require manifest and legacy inline CI contracts to match exactly")
    parser.add_argument("--validate-manifest", action="store_true", help="validate the persisted manifest and print its ordered contract identity")
    parser.add_argument("--verify-files", action="store_true", help="verify every required manifest path exists without reading workflow YAML")
    parser.add_argument("--root", type=Path, default=Path("."), help="repository root used by --verify-files")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    requested = args.emit_manifest or args.check_equivalence or args.validate_manifest or args.verify_files

    workflow_paths: list[str] | None = None
    if args.emit_manifest or args.check_equivalence or not requested:
        try:
            workflow_text = args.workflow.read_text(encoding="utf-8")
        except OSError as exc:
            raise ContractError(f"unable to load workflow {args.workflow}: {exc}") from exc
        workflow_paths = extract_required_paths(workflow_text)

    if args.emit_manifest:
        assert workflow_paths is not None
        print(json.dumps(make_manifest(workflow_paths), indent=2) + "\n", end="")

    manifest_paths: list[str] | None = None
    if args.check_equivalence or args.validate_manifest or args.verify_files:
        manifest = load_manifest(args.manifest)
        manifest_paths = list(manifest["required_paths"])

    if args.check_equivalence:
        assert workflow_paths is not None and manifest_paths is not None
        verify_equivalence(workflow_paths, manifest_paths)

    if args.validate_manifest:
        assert manifest_paths is not None
        print(
            f"manifest_paths={len(manifest_paths)} "
            f"contract_sha256={contract_digest(manifest_paths)}"
        )

    if args.verify_files:
        assert manifest_paths is not None
        verify_files(manifest_paths, args.root)
        print(f"verified_files={len(manifest_paths)}")

    if not requested:
        assert workflow_paths is not None
        print(f"{len(workflow_paths)} canonical evidence paths extracted from {args.workflow}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ContractError as exc:
        raise SystemExit(f"ERROR: {exc}") from exc
