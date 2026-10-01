#!/usr/bin/env python3
"""Print compact hydrology evidence rows after a failed Actions validation."""

from pathlib import Path
import re
import xml.etree.ElementTree as ET

REPORTS = (
    Path("skyforge-world/build/evidence/hydrology-d2-search-test/key-287.txt"),
    Path("skyforge-world/build/evidence/hydrology-d2-search-test/key-287-corridor-summary.txt"),
    Path("skyforge-reference/build/evidence/hydrology-voxel-quantization-test-a/summary.csv"),
    Path("skyforge-reference/build/evidence/hydrology-world-water-test-a/summary.csv"),
    Path("skyforge-reference/build/evidence/hydrology-world-water-test-a/reaches.csv"),
    Path("skyforge-reference/build/evidence/hydrology-world-head-refinement-test-a/summary.csv"),
    Path("skyforge-reference/build/evidence/hydrology-world-head-refinement-test-a/components.csv"),
    Path("skyforge-reference/build/evidence/hydrology-lateral-refinement-test-a/summary.csv"),
    Path("skyforge-reference/build/evidence/hydrology-lateral-refinement-test-a/reaches.csv"),
)
F3H_REPORTS = Path("skyforge-world/build/test-results/test")
ROW = re.compile(r"^(ordinary-77(?:-weak)?|primary-287|confluence-632|lake-609)(?:,|-)|^(?:709,559|1742,1842)(?:,|$)")


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

    for report in sorted(F3H_REPORTS.glob("TEST-*SkyIslandConfluenceCascadeHeadCompatibilityPlannerTest*.xml")):
        root = ET.parse(report).getroot()
        failures = root.findall(".//failure") + root.findall(".//error")
        if not failures:
            continue
        print(f"--- {report.as_posix()} (F3H failure detail) ---")
        for failure in failures:
            print(failure.attrib.get("message", ""))
            if failure.text:
                print(failure.text.strip())
        for tag in ("system-out", "system-err"):
            output = root.find(f".//{tag}")
            if output is not None and output.text:
                print(f"--- {tag} ---")
                print(output.text.strip())


if __name__ == "__main__":
    main()
