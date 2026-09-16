from __future__ import annotations

import threading
import unittest
from unittest import mock

import skyforge_external_pr_event_filter_runtime as runtime


class _FakeState:
    def __init__(self) -> None:
        self.data = {
            "retired_event_keys": [],
            "metrics": {},
            "pending_events": [],
        }
        self.saves = 0

    def save(self) -> None:
        self.saves += 1


class _FakeOrchestrator:
    def __init__(self) -> None:
        self._state_lock = threading.RLock()
        self.state = _FakeState()
        self.replacements: list[tuple[list, list]] = []
        self.purges = 0

    def _pending_event_priority(self, value: dict) -> bool:
        return False

    def _replace_captured_pending_events(self, captured: list, replacement: list) -> None:
        self.replacements.append((list(captured), list(replacement)))

    def _purge_suppressed_pending_events_locked(self) -> None:
        self.purges += 1


def _workflow(pr: int) -> runtime.core.EventDecision:
    return runtime.core.EventDecision(
        True,
        "CI completed",
        "workflow_run",
        action="completed",
        head_sha=f"{pr:040x}"[-40:],
        pr_number=pr,
    )


class ExternalPrEventFilterRuntimeTests(unittest.TestCase):
    def test_unowned_pr_workflow_completion_is_retired_before_classifier(self) -> None:
        fake = _FakeOrchestrator()
        event = _workflow(101)
        snapshot = {"controller_managed": {}}
        with mock.patch.object(runtime, "_ORIGINAL_NORMALIZE_CAPTURED_EVENTS", return_value=[event]):
            kept = runtime._normalize_captured_events_for_snapshot(fake, [event], snapshot)

        self.assertEqual(kept, [])
        self.assertEqual(len(fake.replacements), 1)
        self.assertEqual(fake.replacements[0][1], [])
        self.assertIn(runtime.core._event_key(event), fake.state.data["retired_event_keys"])
        self.assertEqual(fake.state.data["metrics"]["external_pr_workflow_events_filtered"], 1)
        self.assertEqual(fake.purges, 1)

    def test_controller_owned_pr_workflow_completion_is_preserved(self) -> None:
        fake = _FakeOrchestrator()
        event = _workflow(202)
        snapshot = {
            "controller_managed": {
                "Implementation": {"pr_number": 202, "branch": "codex/implementation-task"}
            }
        }
        with mock.patch.object(runtime, "_ORIGINAL_NORMALIZE_CAPTURED_EVENTS", return_value=[event]):
            kept = runtime._normalize_captured_events_for_snapshot(fake, [event], snapshot)

        self.assertEqual(kept, [event])
        self.assertEqual(fake.replacements, [])
        self.assertEqual(fake.state.data["retired_event_keys"], [])

    def test_non_workflow_repository_event_is_preserved(self) -> None:
        fake = _FakeOrchestrator()
        event = runtime.core.EventDecision(
            True,
            "main advanced",
            "push",
            head_sha="a" * 40,
        )
        with mock.patch.object(runtime, "_ORIGINAL_NORMALIZE_CAPTURED_EVENTS", return_value=[event]):
            kept = runtime._normalize_captured_events_for_snapshot(
                fake,
                [event],
                {"controller_managed": {}},
            )

        self.assertEqual(kept, [event])
        self.assertEqual(fake.replacements, [])

    def test_workflow_without_pr_identity_is_preserved(self) -> None:
        fake = _FakeOrchestrator()
        event = runtime.core.EventDecision(
            True,
            "main workflow completed",
            "workflow_run",
            action="completed",
            head_sha="b" * 40,
            pr_number=None,
        )
        with mock.patch.object(runtime, "_ORIGINAL_NORMALIZE_CAPTURED_EVENTS", return_value=[event]):
            kept = runtime._normalize_captured_events_for_snapshot(
                fake,
                [event],
                {"controller_managed": {}},
            )

        self.assertEqual(kept, [event])
        self.assertEqual(fake.replacements, [])


if __name__ == "__main__":
    unittest.main()
