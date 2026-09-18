from __future__ import annotations

from argparse import Namespace
from unittest.mock import patch
import unittest

import skyforge_orchestrator as core


class FakeOrchestrator:
    def __init__(self, *, reconcile: bool):
        self.repo = "ni-da-ba/skyforge"
        self.auto_merge = False
        self.require_webhook_secret = True
        self.startup_reconcile = True
        self.reconcile = reconcile
        self.resume_calls = 0

    def validate_environment(self):
        return None

    def _metric(self, _name):
        return None

    def attempt_startup_reconcile(self):
        return self.reconcile

    def resume_pending(self):
        self.resume_calls += 1


class FakeServer:
    def __init__(self):
        self.closed = 0
        self.served = 0

    def serve_forever(self):
        self.served += 1
        raise KeyboardInterrupt

    def server_close(self):
        self.closed += 1


def args(*, require=True, startup=True):
    return Namespace(
        root=core.Path.cwd(),
        repo="ni-da-ba/skyforge",
        debounce_seconds=1,
        min_dispatch_seconds=1,
        max_parent_turns=1,
        auto_merge=False,
        require_webhook_secret=True,
        startup_reconcile=startup,
        require_startup_reconcile_success=require,
        bind="127.0.0.1",
        port=0,
    )


class StartupReconcileGuardTest(unittest.TestCase):
    def test_required_reconcile_failure_exits_before_resume(self):
        orchestrator = FakeOrchestrator(reconcile=False)
        server = FakeServer()
        with (
            patch.object(core, "parse_args", return_value=args()),
            patch.object(core, "Orchestrator", return_value=orchestrator),
            patch.object(core, "ThreadingHTTPServer", return_value=server),
        ):
            result = core.main()
        self.assertEqual(result, 75)
        self.assertEqual(orchestrator.resume_calls, 0)
        self.assertEqual(server.served, 0)
        self.assertGreaterEqual(server.closed, 1)

    def test_required_reconcile_success_resumes_after_reconcile(self):
        orchestrator = FakeOrchestrator(reconcile=True)
        server = FakeServer()
        with (
            patch.object(core, "parse_args", return_value=args()),
            patch.object(core, "Orchestrator", return_value=orchestrator),
            patch.object(core, "ThreadingHTTPServer", return_value=server),
        ):
            result = core.main()
        self.assertEqual(result, 0)
        self.assertEqual(orchestrator.resume_calls, 1)
        self.assertEqual(server.served, 1)

    def test_required_guard_without_startup_reconcile_exits_before_resume(self):
        orchestrator = FakeOrchestrator(reconcile=True)
        orchestrator.startup_reconcile = False
        server = FakeServer()
        with (
            patch.object(core, "parse_args", return_value=args(startup=False)),
            patch.object(core, "Orchestrator", return_value=orchestrator),
            patch.object(core, "ThreadingHTTPServer", return_value=server),
        ):
            result = core.main()
        self.assertEqual(result, 64)
        self.assertEqual(orchestrator.resume_calls, 0)
        self.assertEqual(server.served, 0)


if __name__ == "__main__":
    unittest.main()
