#!/usr/bin/env python3
"""Stage the bounded evidence-review artifact from the canonical evidence manifest."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path

from verify_evidence_entry_points import DEFAULT_MANIFEST, EVIDENCE_ROOT, load_manifest

DEFAULT_WORKFLOW = Path('.github/workflows/ci.yml')
DEFAULT_DESTINATION = Path('build/ci-evidence-review')
DEFAULT_EXTENSIONS = ('.png', '.html', '.csv')
SPECIAL_EXTENSIONS: dict[str, tuple[str, ...]] = {
    'fixed-seed-island-v1': ('.png', '.html', '.json', '.csv', '.sha256'),
    'signal-free-suspended-volume-v1': ('.png', '.html', '.json', '.csv', '.sha256'),
    'authorship-semantic-fields-v1': ('.png', '.html', '.json', '.csv'),
}
PUBLISH_STEP = '      - name: Publish compact evidence review bundle\n'


class BundleError(RuntimeError):
    """Raised when artifact staging would broaden or lose the bounded review set."""


def manifest_directories(manifest_path: Path) -> list[str]:
    manifest = load_manifest(manifest_path)
    directories: list[str] = []
    prefix = EVIDENCE_ROOT.rstrip('/') + '/'
    for required_path in manifest['required_paths']:
        parent = str(Path(required_path).parent).replace('\\', '/')
        if not parent.startswith(prefix):
            raise BundleError(f'evidence path escaped canonical root: {required_path}')
        directory = parent[len(prefix):]
        if directory not in directories:
            directories.append(directory)
    if not directories:
        raise BundleError('canonical evidence manifest contains no directories')
    return directories


def extensions_for(directory: str) -> tuple[str, ...]:
    return SPECIAL_EXTENSIONS.get(directory, DEFAULT_EXTENSIONS)


def upload_patterns(directories: list[str]) -> list[str]:
    return [
        f'{EVIDENCE_ROOT}{directory}/**/*{extension}'
        for directory in directories
        for extension in extensions_for(directory)
    ]


def extract_inline_upload_patterns(workflow_text: str) -> list[str]:
    if workflow_text.count(PUBLISH_STEP) != 1:
        raise BundleError('canonical workflow must contain exactly one evidence publish step')
    block = workflow_text.split(PUBLISH_STEP, 1)[1]
    patterns = [
        line.strip()
        for line in block.splitlines()
        if line.strip().startswith(EVIDENCE_ROOT) and '/**/*.' in line.strip()
    ]
    if not patterns:
        raise BundleError('canonical workflow contains no inline evidence upload patterns')
    return patterns


def verify_inline_equivalence(workflow_text: str, directories: list[str]) -> None:
    expected = upload_patterns(directories)
    actual = extract_inline_upload_patterns(workflow_text)
    if expected == actual:
        return
    expected_set = set(expected)
    actual_set = set(actual)
    missing = [path for path in expected if path not in actual_set]
    extra = [path for path in actual if path not in expected_set]
    if not missing and not extra:
        raise BundleError('inline upload pattern set matches but ordering differs')
    raise BundleError(f'inline upload patterns differ; missing={missing}; extra={extra}')


def stage_bundle(manifest_path: Path, root: Path, destination: Path) -> tuple[int, int]:
    directories = manifest_directories(manifest_path)
    source_root = root / EVIDENCE_ROOT
    target_root = destination if destination.is_absolute() else root / destination

    if target_root.exists():
        shutil.rmtree(target_root)
    target_root.mkdir(parents=True, exist_ok=True)

    total_files = 0
    total_bytes = 0
    for directory in directories:
        source_directory = source_root / directory
        if not source_directory.is_dir():
            raise BundleError(f'missing canonical evidence directory: {source_directory}')
        allowed = set(extensions_for(directory))
        selected = [
            path
            for path in sorted(source_directory.rglob('*'))
            if path.is_file() and path.suffix in allowed
        ]
        if not selected:
            raise BundleError(f'no review files selected from canonical evidence directory: {directory}')
        for source in selected:
            relative = source.relative_to(source_root)
            target = target_root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            total_files += 1
            total_bytes += source.stat().st_size

    return total_files, total_bytes


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--manifest', type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument('--workflow', type=Path, default=DEFAULT_WORKFLOW)
    parser.add_argument('--root', type=Path, default=Path('.'))
    parser.add_argument('--destination', type=Path, default=DEFAULT_DESTINATION)
    parser.add_argument('--check-inline-equivalence', action='store_true')
    parser.add_argument('--emit-patterns', action='store_true')
    parser.add_argument('--stage', action='store_true')
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    directories = manifest_directories(args.manifest)
    requested = args.check_inline_equivalence or args.emit_patterns or args.stage

    if args.check_inline_equivalence:
        verify_inline_equivalence(args.workflow.read_text(encoding='utf-8'), directories)
        print(f'upload_patterns={len(upload_patterns(directories))} inline_equivalence=PASS')

    if args.emit_patterns:
        print('\n'.join(upload_patterns(directories)))

    if args.stage:
        files, size = stage_bundle(args.manifest, args.root, args.destination)
        print(f'staged_directories={len(directories)} staged_files={files} staged_bytes={size}')

    if not requested:
        print(f'evidence_directories={len(directories)} upload_patterns={len(upload_patterns(directories))}')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (BundleError, OSError) as exc:
        raise SystemExit(f'ERROR: {exc}') from exc
