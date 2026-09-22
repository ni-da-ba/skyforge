#!/usr/bin/env python3
"""Verify retained DR characterization workflow contracts without executing Minecraft."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
SETUP_ACTION = "uses: ./.github/actions/setup-java-gradle"
FORBIDDEN_DUPLICATE_TASKS = (
    ":skyforge-neoforge-1211:compileJava",
    ":skyforge-neoforge-1211:test",
)


@dataclass(frozen=True)
class WorkflowContract:
    path: str
    required_triggers: tuple[str, ...]
    forbidden_trigger: str
    acceptance_tasks: tuple[str, ...]
    setup_count: int


CONTRACTS = (
    WorkflowContract(
        ".github/workflows/dr30-native-structure-acceptance.yml",
        ('- "skyforge-neoforge-1211/src/main/**"', "workflow_dispatch:"),
        '- ".github/workflows/dr30-native-structure-acceptance.yml"',
        (":skyforge-neoforge-1211:dr30NativeStructureAcceptance",),
        1,
    ),
    WorkflowContract(
        ".github/workflows/dr40-production-ecology.yml",
        (
            '- "skyforge-world/**"',
            '- "skyforge-neoforge-1211/src/main/**"',
            '- "docs/agent-state/DR40_ECOLOGY_*.json"',
            "workflow_dispatch:",
        ),
        '- ".github/workflows/dr40-production-ecology.yml"',
        (":skyforge-neoforge-1211:dr40ProductionEcologyAcceptance",),
        1,
    ),
    WorkflowContract(
        ".github/workflows/dr50-integrated-dressed-region.yml",
        (
            '- "skyforge-world/**"',
            '- "skyforge-neoforge-1211/src/main/**"',
            '- "docs/agent-state/DR50_INTEGRATED_REGION_EVIDENCE.json"',
            "workflow_dispatch:",
        ),
        '- ".github/workflows/dr50-integrated-dressed-region.yml"',
        (
            ":skyforge-neoforge-1211:dr50IntegratedRegionAcceptance",
            ":skyforge-neoforge-1211:dr50IntegratedRegionDeterminismAcceptance",
        ),
        2,
    ),
)


def verify_text(contract: WorkflowContract, text: str) -> list[str]:
    errors: list[str] = []
    for required in contract.required_triggers:
        if required not in text:
            errors.append(f"{contract.path}: missing required trigger/dispatch entry {required!r}")
    if contract.forbidden_trigger in text:
        errors.append(f"{contract.path}: workflow must not self-trigger on its YAML path")
    if text.count(SETUP_ACTION) != contract.setup_count:
        errors.append(
            f"{contract.path}: expected {contract.setup_count} shared setup action use(s), "
            f"found {text.count(SETUP_ACTION)}"
        )
    for task in contract.acceptance_tasks:
        if text.count(task) != 1:
            errors.append(
                f"{contract.path}: expected exactly one retained characterization task {task!r}"
            )
    for task in FORBIDDEN_DUPLICATE_TASKS:
        if task in text:
            errors.append(
                f"{contract.path}: duplicate canonical compile/unit task remains: {task}"
            )
    return errors


def verify_root(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    broad_trigger = "skyforge-neoforge-1211/build.gradle.kts"
    for path in sorted((root / ".github/workflows").glob("*.yml")):
        if path.name == "ci.yml":
            continue
        text = path.read_text(encoding="utf-8")
        relative = path.relative_to(root).as_posix()
        if broad_trigger in text:
            errors.append(
                f"{relative}: retained/focused workflow must not fan out on shared NeoForge build.gradle.kts"
            )
        lines = text.splitlines()
        for index, line in enumerate(lines):
            if line.strip() != "paths:":
                continue
            indent = len(line) - len(line.lstrip())
            has_entry = False
            for following in lines[index + 1:]:
                if not following.strip():
                    continue
                following_indent = len(following) - len(following.lstrip())
                if following_indent <= indent:
                    break
                if following.lstrip().startswith("- "):
                    has_entry = True
                    break
            if not has_entry:
                errors.append(f"{relative}: empty paths block broadens or invalidates trigger intent")
    for contract in CONTRACTS:
        path = root / contract.path
        if not path.is_file():
            errors.append(f"{contract.path}: missing workflow")
            continue
        errors.extend(verify_text(contract, path.read_text(encoding="utf-8")))

    qualification_path = root / ".github/workflows/neoforge-focused-qualification.yml"
    if not qualification_path.is_file():
        errors.append(".github/workflows/neoforge-focused-qualification.yml: missing workflow")
    else:
        qualification = qualification_path.read_text(encoding="utf-8")
        expected = ":skyforge-neoforge-1211:test -PskyforgeQualification=true"
        if expected not in qualification:
            errors.append(
                ".github/workflows/neoforge-focused-qualification.yml: "
                "must use the canonical ModDev-configured test task in qualification mode"
            )
        if "neoforgeQualificationTest" in qualification:
            errors.append(
                ".github/workflows/neoforge-focused-qualification.yml: "
                "must not create/use a second unsupported ModDev Test task"
            )
    return errors


def main() -> int:
    errors = verify_root()
    if errors:
        print("\n".join(f"ERROR: {error}" for error in errors), file=sys.stderr)
        return 1
    print("PASS retained DR workflow contracts")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())