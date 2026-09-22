#!/usr/bin/env python3
"""Summarize canonical CI wall time, Gradle task timing, and slow JUnit suites."""
from __future__ import annotations

import argparse
from html.parser import HTMLParser
from pathlib import Path
import re
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


class _TableRows(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.rows: list[list[str]] = []
        self._row: list[str] | None = None
        self._cell: list[str] | None = None

    def handle_starttag(self, tag: str, attrs) -> None:
        if tag == "tr":
            self._row = []
        elif tag in {"td", "th"} and self._row is not None:
            self._cell = []

    def handle_data(self, data: str) -> None:
        if self._cell is not None:
            self._cell.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag in {"td", "th"} and self._row is not None and self._cell is not None:
            self._row.append(" ".join("".join(self._cell).split()))
            self._cell = None
        elif tag == "tr" and self._row is not None:
            self.rows.append(self._row)
            self._row = None
            self._cell = None


_DURATION = re.compile(r"^(?:(?P<minutes>\d+(?:\.\d+)?)m\s*)?(?P<seconds>\d+(?:\.\d+)?)s$")
_MILLIS = re.compile(r"^(?P<millis>\d+(?:\.\d+)?)ms$")


def _parse_duration(value: str) -> float | None:
    text = value.strip()
    match = _DURATION.match(text)
    if match:
        return 60.0 * float(match.group("minutes") or 0.0) + float(match.group("seconds"))
    match = _MILLIS.match(text)
    if match:
        return float(match.group("millis")) / 1000.0
    return None


def _gradle_tasks(root: Path) -> list[tuple[float, str]]:
    profiles = sorted(
        root.glob("build/reports/profile/profile-*.html"),
        key=lambda path: path.stat().st_mtime,
        reverse=True,
    )
    for path in profiles:
        try:
            parser = _TableRows()
            parser.feed(path.read_text(encoding="utf-8"))
        except OSError:
            continue
        tasks: list[tuple[float, str]] = []
        for row in parser.rows:
            if not row or not row[0].startswith(":"):
                continue
            duration = next((_parse_duration(cell) for cell in row[1:] if _parse_duration(cell) is not None), None)
            if duration is not None:
                tasks.append((duration, row[0]))
        if tasks:
            tasks.sort(reverse=True)
            return tasks
    return []


def _task_category_totals(tasks: list[tuple[float, str]]) -> dict[str, float]:
    totals = {"compilation": 0.0, "tests": 0.0, "evidence": 0.0}
    for seconds, task in tasks:
        lower = task.lower()
        if "compile" in lower:
            totals["compilation"] += seconds
        if "test" in lower:
            totals["tests"] += seconds
        if any(token in lower for token in ("evidence", "corpus", "acceptance")):
            totals["evidence"] += seconds
    return totals


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--timing-dir", default="build/ci-timing")
    parser.add_argument("--summary")
    args = parser.parse_args()
    timing = Path(args.timing_dir)
    lines = ["## CI timing", "", "| Lane | Duration |", "| --- | ---: |"]
    ordinary = _seconds(timing / "ordinary-check.seconds")
    reference = _seconds(timing / "reference-corpora.seconds")
    ordinary_display = f"{ordinary}s" if ordinary is not None else "n/a"
    lines.append(f"| ordinary `./gradlew check` | {ordinary_display} |")
    lines.append(f"| reference qualification corpora | {reference if reference is not None else 'not run'} |")
    if ordinary is not None and ordinary > 600:
        lines += ["", f"> CI budget flag: ordinary check took {ordinary}s (>10 minutes). Investigate before treating this as the new baseline."]

    tasks = _gradle_tasks(Path("."))
    if tasks:
        totals = _task_category_totals(tasks)
        lines += [
            "",
            "### Gradle task timing",
            "",
            "_Category totals are summed task execution time from the Gradle profile; parallel tasks may overlap in wall-clock time._",
            "",
            "| Category | Task time |",
            "| --- | ---: |",
            f"| compilation | {totals['compilation']:.2f}s |",
            f"| tests | {totals['tests']:.2f}s |",
            f"| evidence / corpus / acceptance | {totals['evidence']:.2f}s |",
            "",
            "### Slowest Gradle tasks",
            "",
            "| Task | Seconds |",
            "| --- | ---: |",
        ]
        lines += [f"| `{task}` | {seconds:.2f} |" for seconds, task in tasks[:10]]

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