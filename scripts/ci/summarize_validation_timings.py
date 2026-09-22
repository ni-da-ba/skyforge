#!/usr/bin/env python3
"""Summarize Skyforge CI wall time, Gradle task profile, and slow JUnit classes."""
from __future__ import annotations

import argparse
from html.parser import HTMLParser
import os
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET


_DURATION_RE = re.compile(r"^\s*(?:(\d+)m)?\s*(?:(\d+(?:\.\d+)?)s|([\d.]+)ms)?\s*$")


def parse_duration(value: str) -> float | None:
    value = value.strip()
    if not value:
        return None
    match = _DURATION_RE.match(value)
    if not match:
        return None
    minutes = float(match.group(1) or 0)
    seconds = float(match.group(2) or 0)
    millis = float(match.group(3) or 0)
    return minutes * 60.0 + seconds + millis / 1000.0


class _GradleProfileParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.heading = ""
        self._in_h2 = False
        self._in_row = False
        self._in_cell = False
        self._text: list[str] = []
        self._row: list[str] = []
        self.tasks: list[tuple[str, float]] = []

    def handle_starttag(self, tag: str, attrs) -> None:
        if tag == "h2":
            self._in_h2 = True
            self._text = []
        elif tag == "tr":
            self._in_row = True
            self._row = []
        elif tag in {"td", "th"} and self._in_row:
            self._in_cell = True
            self._text = []

    def handle_data(self, data: str) -> None:
        if self._in_h2 or self._in_cell:
            self._text.append(data)

    def handle_endtag(self, tag: str) -> None:
        if tag == "h2" and self._in_h2:
            self.heading = "".join(self._text).strip()
            self._in_h2 = False
        elif tag in {"td", "th"} and self._in_cell:
            self._row.append("".join(self._text).strip())
            self._in_cell = False
        elif tag == "tr" and self._in_row:
            if self.heading == "Task Execution" and len(self._row) >= 2:
                duration = parse_duration(self._row[1])
                if self._row[0].startswith(":") and duration is not None:
                    self.tasks.append((self._row[0], duration))
            self._in_row = False


def latest_profile(root: Path) -> Path | None:
    profiles = sorted(root.glob("profile-*.html"), key=lambda path: path.stat().st_mtime)
    return profiles[-1] if profiles else None


def read_tasks(profile: Path | None) -> list[tuple[str, float]]:
    if profile is None:
        return []
    parser = _GradleProfileParser()
    parser.feed(profile.read_text(encoding="utf-8", errors="replace"))
    return sorted(parser.tasks, key=lambda item: item[1], reverse=True)


def read_test_suites(root: Path) -> list[tuple[str, float]]:
    suites: list[tuple[str, float]] = []
    for path in root.glob("**/build/test-results/*/TEST-*.xml"):
        try:
            suite = ET.parse(path).getroot()
            duration = float(suite.attrib.get("time", "0") or 0)
            name = suite.attrib.get("name") or path.stem.removeprefix("TEST-")
            suites.append((name, duration))
        except (ET.ParseError, ValueError, OSError):
            continue
    return sorted(suites, key=lambda item: item[1], reverse=True)


def render(label: str, wall_seconds: float, tasks, suites, profile: Path | None) -> str:
    lines = [
        f"### Validation timing — {label}",
        "",
        f"- Wall time: **{wall_seconds:.1f}s**",
        f"- Gradle profile: `{profile}`" if profile else "- Gradle profile: unavailable",
        "",
        "| Slow Gradle task | Seconds |",
        "| --- | ---: |",
    ]
    for name, seconds in tasks[:10]:
        lines.append(f"| `{name}` | {seconds:.2f} |")
    if not tasks:
        lines.append("| _profile unavailable_ | — |")
    lines += ["", "| Slow JUnit class | Seconds |", "| --- | ---: |"]
    for name, seconds in suites[:10]:
        lines.append(f"| `{name}` | {seconds:.2f} |")
    if not suites:
        lines.append("| _no JUnit XML found_ | — |")
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--label", required=True)
    parser.add_argument("--wall-seconds", required=True, type=float)
    parser.add_argument("--warn-over-seconds", type=float, default=600.0)
    parser.add_argument("--profile-root", default="build/reports/profile")
    parser.add_argument("--repository-root", default=".")
    parser.add_argument("--github-summary", default=os.environ.get("GITHUB_STEP_SUMMARY", ""))
    args = parser.parse_args()

    profile = latest_profile(Path(args.profile_root))
    tasks = read_tasks(profile)
    suites = read_test_suites(Path(args.repository_root))
    summary = render(args.label, args.wall_seconds, tasks, suites, profile)
    print(summary, end="")
    if args.github_summary:
        with open(args.github_summary, "a", encoding="utf-8") as handle:
            handle.write(summary)
    if args.wall_seconds > args.warn_over_seconds:
        print(
            f"::warning::Validation lane {args.label!r} took {args.wall_seconds:.1f}s, "
            f"above the {args.warn_over_seconds:.0f}s investigation budget."
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
