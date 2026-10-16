#!/usr/bin/env python3
"""Fetch and expose the official HEC-RAS 6.6 Stream Junction Example 10 inputs.

This is benchmark-fixture acquisition only; it does not run HEC-RAS or assert
hydraulic results. The Skyforge regression should be authored from the exact
geometry/flow files and separately checked against HEC's published output.
"""
from __future__ import annotations

import hashlib
import json
import re
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ARCHIVE_URL = (
    "https://github.com/HydrologicEngineeringCenter/hec-downloads/releases/"
    "download/1.0.33/Example_Projects_6_6.zip"
)
EXPECTED_ARCHIVE_BYTES = 432_389_121
TEXT_INPUT_SUFFIX = re.compile(r"\.(?:prj|p\d{2}|g\d{2}|f\d{2})$", re.IGNORECASE)
MAX_MEMBER_BYTES = 160_000
MAX_TOTAL_BYTES = 500_000


def normalized(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", value.casefold()).strip()


def selected_members(names: list[str]) -> list[str]:
    selected = []
    for name in names:
        normalized_name = normalized(name)
        if (
            "applications guide" in normalized_name
            and "example 10" in normalized_name
            and "stream junction" in normalized_name
            and TEXT_INPUT_SUFFIX.search(name)
        ):
            selected.append(name)
    return sorted(selected, key=str.casefold)


def main() -> int:
    output = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("hec-example10-inputs.json")
    with tempfile.TemporaryDirectory(prefix="skyforge-hec10-") as directory:
        archive = Path(directory) / "Example_Projects_6_6.zip"
        digest = hashlib.sha256()
        received = 0
        request = urllib.request.Request(
            ARCHIVE_URL, headers={"User-Agent": "Skyforge-Hydrology-Benchmark/1.0"}
        )
        with urllib.request.urlopen(request, timeout=90) as response, archive.open("wb") as target:
            declared_length = response.headers.get("Content-Length")
            if declared_length and int(declared_length) != EXPECTED_ARCHIVE_BYTES:
                raise RuntimeError(
                    f"Unexpected HEC archive size: {declared_length}; "
                    f"expected {EXPECTED_ARCHIVE_BYTES}"
                )
            while chunk := response.read(1024 * 1024):
                received += len(chunk)
                digest.update(chunk)
                target.write(chunk)
        if received != EXPECTED_ARCHIVE_BYTES:
            raise RuntimeError(
                f"Downloaded {received} bytes; expected {EXPECTED_ARCHIVE_BYTES}"
            )

        with zipfile.ZipFile(archive) as bundle:
            names = bundle.namelist()
            members = selected_members(names)
            if not members:
                candidates = [
                    name for name in names
                    if "example 10" in normalized(name) and "stream junction" in normalized(name)
                ]
                raise RuntimeError(
                    "Official archive did not contain the expected Applications Guide "
                    "Example 10 text inputs. Candidate members: " + repr(candidates[:40])
                )
            total = sum(bundle.getinfo(name).file_size for name in members)
            if total > MAX_TOTAL_BYTES:
                raise RuntimeError(
                    f"Selected HEC text fixture is unexpectedly large: {total} bytes"
                )
            fixture = []
            for name in members:
                info = bundle.getinfo(name)
                if info.file_size > MAX_MEMBER_BYTES:
                    raise RuntimeError(
                        f"Selected text input is unexpectedly large: {name} "
                        f"({info.file_size} bytes)"
                    )
                raw = bundle.read(name)
                try:
                    text = raw.decode("utf-8")
                except UnicodeDecodeError:
                    text = raw.decode("cp1252")
                fixture.append({
                    "archive_path": name,
                    "size_bytes": len(raw),
                    "sha256": hashlib.sha256(raw).hexdigest(),
                    "text": text,
                })

    result = {
        "source_url": ARCHIVE_URL,
        "archive_size_bytes": received,
        "archive_sha256": digest.hexdigest(),
        "project": "Applications Guide / Example 10 - Stream Junction",
        "fixture_scope": "HEC-RAS project, geometry, and steady-flow text inputs only",
        "hydraulic_results_included": False,
        "files": fixture,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
