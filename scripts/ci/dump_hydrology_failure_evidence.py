#!/usr/bin/env python3
"""Print compact hydrology evidence rows after a failed Actions validation."""

from pathlib import Path
import re

REPORTS = (
    Path("skyforge-world/build/evidence/hydrology-d2-search-test/key-287.txt"),
    Path("skyforge-reference/build/evidence/hydrology-voxel-quantization-test-a/summary.csv"),
    Path("skyforge-reference/build/evidence/hydrology-world-water-test-a/summary.csv"),
    Path("skyforge-reference/build/evidence/hydrology-world-water-test-a/reaches.csv"),
    Path("skyforge-reference/build/evidence/hydrology-world-head-refinement-test-a/summary.csv"),
    Path("skyforge-reference/build/evidence/hydrology-world-head-refinement-test-a/components.csv"),
    Path("skyforge-reference/build/evidence/hydrology-lateral-refinement-test-a/summary.csv"),
    Path("skyforge-reference/build/evidence/hydrology-lateral-refinement-test-a/reaches.csv"),
)
ROW = re.compile(r"^(ordinary-77(?:-weak)?|primary-287|confluence-632|lake-609)(?:,|-)")


def main() -> None:
    for report in REPORTS:
        if not report.is_file():
            continue
        print(f"--- {report.as_posix()} ---")
        lines = report.read_text(encoding="utf-8-sig").splitlines()
        if report.suffix == ".txt":
            print("\n".join(lines))
        else:
            print("\n".join(line for line in lines if ROW.match(line)))


if __name__ == "__main__":
    main()
