#!/usr/bin/env python3
"""Compatibility entrypoint for the hosted Skyforge controller.

Existing hosts still launch this path. Preserve the accepted replay-safe module object exactly, then
load the bounded roadmap and recovery extensions onto its shared controller core. This keeps old test
hooks and runtime globals authoritative while avoiding a privileged systemd entrypoint migration.
"""

from __future__ import annotations

import sys

import skyforge_control_replay_base as _base

# Make imports of the historical entrypoint resolve to the actual replay-safe implementation module,
# not a copied namespace. Functions therefore retain one set of module globals and existing patches /
# regression hooks continue to operate exactly as before.
sys.modules[__name__] = _base

# Importing these modules installs bounded roadmap continuation plus hosted recovery/liveness guards
# on _base.core. When this compatibility module itself is reached through a direct
# ``skyforge_roadmap_runtime`` import, that roadmap module is still partially initialized; defer the
# closed-active-issue extension in that one import direction. Hosted entrypoint startup imports the
# roadmap here first, so it is fully initialized before the extension is loaded.
import skyforge_roadmap_runtime as _roadmap_runtime  # noqa: E402,F401

_roadmap_closed_issue_recovery_runtime = None
if hasattr(_roadmap_runtime, "core"):
    import skyforge_roadmap_closed_issue_recovery_runtime as _roadmap_closed_issue_recovery_runtime  # noqa: E402,F401

import skyforge_handoff_recovery_runtime as _handoff_recovery_runtime  # noqa: E402,F401
import skyforge_managed_pr_no_change_runtime as _managed_pr_no_change_runtime  # noqa: E402,F401
import skyforge_pending_timer_fairness_runtime as _pending_timer_fairness_runtime  # noqa: E402,F401

_base._roadmap_runtime = _roadmap_runtime
if _roadmap_closed_issue_recovery_runtime is not None:
    _base._roadmap_closed_issue_recovery_runtime = _roadmap_closed_issue_recovery_runtime
_base._handoff_recovery_runtime = _handoff_recovery_runtime
_base._managed_pr_no_change_runtime = _managed_pr_no_change_runtime
_base._pending_timer_fairness_runtime = _pending_timer_fairness_runtime


if __name__ == "__main__":
    raise SystemExit(_base.main())
