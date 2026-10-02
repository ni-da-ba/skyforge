#!/usr/bin/env python3
"""Stage the bounded evidence-review artifact from the canonical evidence manifest."""

from __future__ import annotations

import argparse
import json
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
    'studio-bound-hydrology-semantic-v1': ('.png', '.html', '.json'),
    'studio-bound-hydrology-semantic-v1/terrain': ('.png', '.json'),
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
    staged_paths: set[str] = set()
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
            relative_key = relative.as_posix()
            if relative_key in staged_paths:
                continue
            staged_paths.add(relative_key)
            target = target_root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, target)
            total_files += 1
            total_bytes += source.stat().st_size

    return total_files, total_bytes


STUDIO_APP_FILES = (
    Path('index.html'),
    Path('app.js'),
    Path('scene.js'),
    Path('sample-data.js'),
    Path('trace-viewer.js'),
    Path('comparison-report.js'),
    Path('comparison-report-reader.js'),
    Path('regional-inventory.js'),
    Path('regional-comparison-library.js'),
    Path('regional-comparison.js'),
    Path('workspace-package.js'),
    Path('workspace-session.js'),
    Path('terrain-comparison.js'),
    Path('terrain-comparison-demo.js'),
    Path('terrain-comparison-report.js'),
    Path('terrain-comparison-report-reader.js'),
    Path('terrain-comparison-library.js'),
    Path('profile-backup.js'),
    Path('studio-capabilities.js'),
    Path('styles.css'),
)
STUDIO_EVIDENCE_ID = 'studio-bound-hydrology-semantic-v1'
STUDIO_README = """Skyforge Studio — local S2 review

1. Open index.html in a browser.
2. Click “Open included S2 specimen”.

Studio opens the exact packaged terrain + hydrology pair in one step. In Inspect semantics,
use “Download inspection workspace” to save the current scene, optional reference overlay,
and view settings in one portable JSON file. Use “Open inspection workspace” to restore it.

In “Compare terrain”, click “Load synthetic example” to explore the map, changed cells, and report workflow without locating source files. The made-up sample is clearly marked and is not generated output or project evidence. For real local diagnostics, load reference and candidate terrain volumes with identical grids; the comparison rejects mismatches and never resamples. Save a named comparison in this browser to revisit it on this device, or download a report to move or back it up. Browser saves are not synced, uploaded, or registered.

On Home, “Move your saved work” creates or opens one backup containing world briefs,
the remembered inspection, and saved terrain and regional geology comparisons. Credentials
are excluded. Reopened inspections remain unbound local diagnostics.

Reopened sources are UNBOUND LOCAL DIAGNOSTICS: no token is needed, and the package does
not preserve registered-artifact verification or grant review authority. Comparison
candidates and reports remain separate. The original JSON files remain in sample/ for
inspection or manual import.
"""


def stage_studio_app(root: Path, destination: Path) -> tuple[int, int]:
    """Add a portable Studio app and its exact generated S2 JSON pair to the review bundle."""
    target_root = destination if destination.is_absolute() else root / destination
    app_root = target_root / 'studio-app'
    sample_root = app_root / 'sample'
    sample_root.mkdir(parents=True, exist_ok=True)

    studio_source = root / 'scripts' / 'orchestrator' / 'studio'
    evidence_source = root / EVIDENCE_ROOT / STUDIO_EVIDENCE_ID
    files: list[tuple[Path, Path]] = [
        (studio_source / source.name, app_root / source.name)
        for source in STUDIO_APP_FILES
    ]
    files.extend([
        (
            evidence_source / 'terrain' / 'terrain-semantic-volume.json',
            sample_root / 'terrain-semantic-volume.json',
        ),
        (
            evidence_source / 'hydrology-semantic-layer.json',
            sample_root / 'hydrology-semantic-layer.json',
        ),
    ])

    regional_evidence_source = (
        root / 'skyforge-reference' / 'build' / 'evidence'
        / 'authorship-regional-base-metal-opportunity-v1'
    )
    regional_sample_root = sample_root / 'regional'
    files.extend([
        (regional_evidence_source / 'inventory.csv', regional_sample_root / 'auth-0094-inventory.csv'),
        (regional_evidence_source / 'ranking.csv', regional_sample_root / 'auth-0094-ranking.csv'),
        (regional_evidence_source / 'manifest.csv', regional_sample_root / 'auth-0094-manifest.csv'),
    ])

    manifest_path = regional_evidence_source / 'manifest.csv'
    if not manifest_path.is_file():
        raise BundleError('missing AUTH-0094 machine-evidence manifest')
    manifest_lines = manifest_path.read_text(encoding='utf-8').splitlines()
    if (
        not manifest_lines
        or manifest_lines[0] != 'scenario,pass'
        or len(manifest_lines) != 7
        or any(not line.endswith(',true') for line in manifest_lines[1:])
    ):
        raise BundleError('AUTH-0094 manifest does not record all six checks as passed')

    total_bytes = 0
    for source, target in files:
        if not source.is_file():
            raise BundleError(f'missing Studio review-package input: {source}')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        total_bytes += source.stat().st_size

    terrain_path = sample_root / 'terrain-semantic-volume.json'
    hydrology_path = sample_root / 'hydrology-semantic-layer.json'
    try:
        terrain_artifact = json.loads(terrain_path.read_text(encoding='utf-8'))
        hydrology_artifact = json.loads(hydrology_path.read_text(encoding='utf-8'))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise BundleError(f'invalid Studio S2 sample JSON: {exc}') from exc

    expected_kinds = (
        (terrain_artifact, 'SKYFORGE_TERRAIN_SEMANTIC_VOLUME'),
        (hydrology_artifact, 'SKYFORGE_BOUND_HYDROLOGY_SEMANTIC_LAYER'),
    )
    for artifact, expected_kind in expected_kinds:
        if not isinstance(artifact, dict) or artifact.get('artifact_kind') != expected_kind:
            raise BundleError(
                f'invalid Studio S2 sample: expected artifact_kind {expected_kind}'
            )

    sample_data = app_root / 'sample-data.js'
    sample_data.write_text(
        'window.SKYFORGE_STUDIO_SAMPLE = ' + json.dumps(
            {'terrain': terrain_artifact, 'hydrology': hydrology_artifact},
            ensure_ascii=True,
            separators=(',', ':'),
        ) + ';\n',
        encoding='utf-8',
    )
    total_bytes -= (studio_source / 'sample-data.js').stat().st_size
    total_bytes += sample_data.stat().st_size

    readme = app_root / 'README.txt'
    readme.write_text(STUDIO_README, encoding='utf-8')
    total_bytes += readme.stat().st_size
    return len(files) + 1, total_bytes

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
        studio_files, studio_size = stage_studio_app(args.root, args.destination)
        print(
            f'staged_directories={len(directories)} staged_files={files + studio_files} '
            f'staged_bytes={size + studio_size} studio_app_files={studio_files}'
        )

    if not requested:
        print(f'evidence_directories={len(directories)} upload_patterns={len(upload_patterns(directories))}')
    return 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (BundleError, OSError) as exc:
        raise SystemExit(f'ERROR: {exc}') from exc
