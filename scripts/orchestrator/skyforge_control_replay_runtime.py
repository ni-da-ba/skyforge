#!/usr/bin/env python3
"""Compatibility entrypoint for the hosted Skyforge controller.

Existing hosts still launch this path. Preserve the accepted replay-safe module object exactly, then
load the bounded roadmap and stuck-handoff recovery extensions onto its shared controller core. This
keeps old test hooks and runtime globals authoritative while avoiding a privileged systemd entrypoint
migration.
"""

from __future__ import annotations

import sys

import skyforge_control_replay_base as _base

# Make imports of the historical entrypoint resolve to the actual replay-safe implementation module,
# not a copied namespace. Functions therefore retain one set of module globals and existing patches /
# regression hooks continue to operate exactly as before.
sys.modules[__name__] = _base

# Importing these modules installs bounded roadmap continuation, active-roadmap closed-issue
# reconciliation, and paused-only open-handoff recovery on _base.core. The roadmap runtime imports
# ``skyforge_control_replay_runtime`` during initialization; that lookup now resolves directly to
# _base, so there is no duplicate replay-runtime state.
import skyforge_roadmap_runtime as _roadmap_runtime  # noqa: E402,F401
import skyforge_roadmap_closed_issue_recovery_runtime as _roadmap_closed_issue_recovery_runtime  # noqa: E402,F401
import skyforge_handoff_recovery_runtime as _handoff_recovery_runtime  # noqa: E402,F401

_base._roadmap_runtime = _roadmap_runtime
_base._roadmap_closed_issue_recovery_runtime = _roadmap_closed_issue_recovery_runtime
_base._handoff_recovery_runtime = _handoff_recovery_runtime


if __name__ == "__main__":
    raise SystemExit(_base.main())
