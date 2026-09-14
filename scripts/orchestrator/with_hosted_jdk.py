#!/usr/bin/env python3
"""Retired compatibility entrypoint for the former hosted project JDK.

Project builds/tests/runtime work belong on GitHub Actions. The DigitalOcean host is the
Skyforge orchestration control plane only; see docs/agent-state/EXECUTION_BOUNDARIES.md.
"""

from __future__ import annotations

import sys


MESSAGE = """ERROR: hosted project execution is disabled by Skyforge execution policy.

The DigitalOcean skyforge-orchestrator droplet is the orchestration control plane only.
Do not run Gradle, NeoForge/Minecraft, automated project tests, benchmarks, or generated
machine-evidence workloads here. Commit/hand off the bounded repository change and use
GitHub Actions for automated verification. Perform visual/play/listening/manual gates on
the project owner's local machine.

See docs/agent-state/EXECUTION_BOUNDARIES.md.
"""


def main() -> int:
    sys.stderr.write(MESSAGE)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
