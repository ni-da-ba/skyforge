#!/usr/bin/env python3
"""Compatibility entrypoint for the hosted Skyforge controller.

The installed systemd unit on existing hosts still launches this path. Keep that deployment stable
while layering the bounded roadmap runtime on top of the accepted replay-safe controller implementation.
"""

from __future__ import annotations

import skyforge_control_replay_base as _base

# Re-export the full replay-safe runtime surface, including private test hooks relied on by the
# deterministic orchestrator suite.
for _name in dir(_base):
    if not _name.startswith("__"):
        globals()[_name] = getattr(_base, _name)

# Importing the roadmap runtime installs its extension onto the shared controller core. Its own main()
# delegates back to this module's replay-safe main, so keep main bound to the accepted base runtime.
import skyforge_roadmap_runtime as _roadmap_runtime  # noqa: E402,F401

main = _base.main


if __name__ == "__main__":
    raise SystemExit(main())
