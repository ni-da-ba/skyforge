#!/usr/bin/env python3
"""Summarize canonical CI wall time and slow JUnit suites."""
from __future__ import annotations
import argparse
from pathlib import Path
import xml.etree.ElementTree as ET


def _seconds(path: Path) -> int | None:
    try:
        return int(path.read_text(encoding="utf-8").strip())
    except (OSError, ValueError):
        return None


def _slow_suites(root: Path, limit: int = 10) -> list[tuple[float, str]]:
    rows: list[tuple[float, str]] = []
    for path in root.glob("**/build/test-results/test/TEST-*.xml"):
        try:
            node = ET.parse(path).getroot()
            rows.append((float(node.attrib.get("time", "0") or 0), node.attrib.get("name", path.stem)))
        except (OSError, ValueError, ET.ParseError):
            continue
    rows.sort(reverse=True)
    return rows[:limit]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--timing-dir", default="build/ci-timing")
    parser.add_argument("--summary")
    args = parser.parse_args()
    timing = Path(args.timing_dir)
    lines = ["## CI timing", "", "| Lane | Duration |", "| --- | ---: |"]
    ordinary = _seconds(timing / "ordinary-check.seconds")
    reference = _seconds(timing / "reference-corpora.seconds")
    lines.append(f"| ordinary `./gradlew check` | {ordinary if ordinary is not None else 'n/a'}s |")
    lines.append(f"| reference qualification corpora | {reference if reference is not None else 'not run'} |")
    if ordinary is not None and ordinary > 600:
        lines += ["", f"> CI budget flag: ordinary check took {ordinary}s (>10 minutes). Investigate before treating this as the new baseline."]
    suites = _slow_suites(Path("."))
    if suites:
        lines += ["", "### Slowest JUnit suites", "", "| Suite | Seconds |", "| --- | ---: |"]
        lines += [f"| `{name}` | {seconds:.2f} |" for seconds, name in suites]
    text = "\n".join(lines) + "\n"
    print(text)
    if args.summary:
        with open(args.summary, "a", encoding="utf-8") as handle:
            handle.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
