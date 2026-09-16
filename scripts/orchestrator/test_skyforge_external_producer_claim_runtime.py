from __future__ import annotations

import threading
import unittest
from unittest import mock

import skyforge_external_producer_claim_runtime as runtime


class _FakeState:
    def __init__(self) -> None:
        self.data = {
            "external_producer_claims": {},
            "managed": {},
            "metrics": {},
            "pending_events": [],
            "pending_worker": None,
            "pending_decision": None,
            "roadmap": {},
        }
        self.saves = 0

    def save(self) -> None:
        self.saves += 1


class _FakeOrchestrator:
    def __init__(self) -> None:
        self._state_lock = threading.RLock()
        self.state = _FakeState()
        self.repo = "ni-da-ba/skyforge"
        self.root = mock.MagicMock()
        self.metrics: dict[str, int] = {}
        self.scheduled: list[int] = []
        self.paused = False

    def _metric(self, name: str, amount: int = 1) -> None:
        self.metrics[name] = self.metrics.get(name, 0) + amount

    def _decision_record(self):
        value = self.state.data.get("pending_decision")
        return value if isinstance(value, dict) else None

    def _validated_managed_branch(self, lane: str):
        value = (self.state.data.get("managed") or {}).get(lane)
        return value if isinstance(value, dict) else None

    def is_paused(self) -> bool:
        return self.paused

    def _pending_events(self):
        return self.state.data.get("pending_events") or []

    def _schedule_pending(self, seconds: int) -> None:
        self.scheduled.append(seconds)


def _payload(body: str, *, issue: int = 492, actor: str = "ni-da-ba") -> dict:
    return {
        "action": "created",
        "comment": {
            "id": 12345,
            "body": body,
            "user": {"login": actor},
        },
        "issue": {"number": issue},
    }


def _task(issue: int) -> runtime.core.EventDecision:
    return runtime.core.EventDecision(
        True,
        "explicit bounded task authority",
        "roadmap",
        action="advance",
        pr_number=issue,
        signal_kind="task",
        signal_text=f"task #{issue}",
    )


class ExternalProducerClaimRuntimeTests(unittest.TestCase):
    def test_claim_control_is_trusted_and_supports_metadata(self) -> None:
        payload = _payload(
            "/skyforge-claim-external lane=Implementation branch=manual/foo pr=553"
        )
        with mock.patch.object(runtime, "_ORIGINAL_CLASSIFY_CONTROL_COMMAND", return_value=None):
            control = runtime.classify_control_command(
                "issue_comment",
                payload,
                trusted_actors=("ni-da-ba",),
            )
        self.assertEqual(control, "claim_external")
        self.assertEqual(
            runtime._parse_claim_options(payload),
            {"lane": "Implementation", "branch": "manual/foo", "pr": 553},
        )

    def test_malformed_trusted_claim_is_still_recognized_as_control(self) -> None:
        payload = _payload("/skyforge-claim-external nonsense")
        with mock.patch.object(runtime, "_ORIGINAL_CLASSIFY_CONTROL_COMMAND", return_value=None):
            control = runtime.classify_control_command(
                "issue_comment",
                payload,
                trusted_actors=("ni-da-ba",),
            )
        self.assertEqual(control, "claim_external")

    def test_untrusted_claim_is_not_a_control(self) -> None:
        payload = _payload("/skyforge-claim-external", actor="someone-else")
        with mock.patch.object(runtime, "_ORIGINAL_CLASSIFY_CONTROL_COMMAND", return_value=None):
            control = runtime.classify_control_command(
                "issue_comment",
                payload,
                trusted_actors=("ni-da-ba",),
            )
        self.assertIsNone(control)

    def test_claim_is_recorded_when_issue_is_unowned(self) -> None:
        fake = _FakeOrchestrator()
        accepted = runtime._claim_external(
            fake,
            _payload("/skyforge-claim-external lane=Implementation"),
            actor="ni-da-ba",
        )
        self.assertTrue(accepted)
        claim = fake.state.data["external_producer_claims"]["492"]
        self.assertEqual(claim["issue_number"], 492)
        self.assertEqual(claim["lane"], "Implementation")
        self.assertEqual(claim["claimed_by"], "ni-da-ba")
        self.assertEqual(fake.metrics["external_producer_claims"], 1)

    def test_malformed_claim_is_rejected_without_throwing_or_recording_claim(self) -> None:
        fake = _FakeOrchestrator()
        accepted = runtime._claim_external(
            fake,
            _payload("/skyforge-claim-external nonsense"),
            actor="ni-da-ba",
        )
        self.assertFalse(accepted)
        self.assertNotIn("492", fake.state.data["external_producer_claims"])
        rejection = fake.state.data["last_external_producer_claim_rejection"]
        self.assertEqual(rejection["issue_number"], 492)
        self.assertIn("key=value", rejection["reason"])
        self.assertEqual(fake.metrics["external_producer_claim_rejections"], 1)

    def test_claim_is_rejected_without_throwing_when_controller_worker_owns_issue(self) -> None:
        fake = _FakeOrchestrator()
        fake.state.data["pending_decision"] = {"task_issue_numbers": [492]}
        fake.state.data["pending_worker"] = {
            "lane": "Implementation",
            "branch": "codex/implementation-task-492",
            "stage": "editing",
        }
        accepted = runtime._claim_external(
            fake,
            _payload("/skyforge-claim-external"),
            actor="ni-da-ba",
        )
        self.assertFalse(accepted)
        self.assertNotIn("492", fake.state.data["external_producer_claims"])
        self.assertEqual(fake.metrics["external_producer_claim_rejections"], 1)
        self.assertEqual(
            fake.state.data["last_external_producer_claim_rejection"]["controller_owner"]["kind"],
            "pending_worker",
        )

    def test_active_roadmap_authority_rejects_manual_claim_before_worker_exists(self) -> None:
        fake = _FakeOrchestrator()
        fake.state.data["roadmap"] = {
            "active": {
                "issue_number": 492,
                "node_id": "dr-20-visible-hydrology",
                "lane": "Implementation",
            }
        }
        accepted = runtime._claim_external(
            fake,
            _payload("/skyforge-claim-external"),
            actor="ni-da-ba",
        )
        self.assertFalse(accepted)
        self.assertEqual(
            fake.state.data["last_external_producer_claim_rejection"]["controller_owner"]["kind"],
            "active_roadmap",
        )

    def test_active_claim_holds_task_before_original_dispatch(self) -> None:
        fake = _FakeOrchestrator()
        fake.state.data["external_producer_claims"]["492"] = {
            "state": "active",
            "issue_number": 492,
            "claimed_by": "ni-da-ba",
            "claimed_at": runtime.core._utc_now(),
        }
        with (
            mock.patch.object(runtime, "_prune_external_claims", return_value=0),
            mock.patch.object(runtime, "_ORIGINAL_DISPATCH") as original,
            self.assertRaises(runtime.core.RetryBlocked) as caught,
        ):
            runtime._dispatch(fake, [_task(492)])
        self.assertEqual(caught.exception.kind, "external_producer")
        self.assertFalse(original.called)
        self.assertEqual(fake.metrics["external_producer_dispatch_holds"], 1)
        self.assertIn(492, fake.state.data["last_external_producer_hold"]["issue_numbers"])

    def test_claim_for_other_issue_does_not_hold_dispatch(self) -> None:
        fake = _FakeOrchestrator()
        fake.state.data["external_producer_claims"]["547"] = {
            "state": "active",
            "issue_number": 547,
            "claimed_by": "ni-da-ba",
            "claimed_at": runtime.core._utc_now(),
        }
        with (
            mock.patch.object(runtime, "_prune_external_claims", return_value=0),
            mock.patch.object(runtime, "_ORIGINAL_DISPATCH") as original,
        ):
            runtime._dispatch(fake, [_task(492)])
        original.assert_called_once()

    def test_explicit_release_clears_hold_and_reschedules(self) -> None:
        fake = _FakeOrchestrator()
        fake.state.data["external_producer_claims"]["547"] = {
            "state": "active",
            "issue_number": 547,
            "claimed_by": "ni-da-ba",
        }
        fake.state.data["pending_events"] = [_task(547).to_state()]
        fake.state.data["blocked_kind"] = "external_producer"
        fake.state.data["blocked_until_epoch"] = 9999999999.0
        released = runtime._release_external(
            fake,
            _payload("/skyforge-release-external", issue=547),
            actor="ni-da-ba",
        )
        self.assertTrue(released)
        self.assertNotIn("547", fake.state.data["external_producer_claims"])
        self.assertIsNone(fake.state.data["blocked_kind"])
        self.assertEqual(fake.state.data["blocked_until_epoch"], 0.0)
        self.assertEqual(fake.scheduled, [1])

    def test_pr_bound_claim_auto_retires_on_merge(self) -> None:
        fake = _FakeOrchestrator()
        fake.state.data["external_producer_claims"]["547"] = {
            "state": "active",
            "issue_number": 547,
            "claimed_by": "ni-da-ba",
            "pr_number": 548,
        }
        with mock.patch.object(
            runtime.core,
            "_json_cmd",
            return_value={"state": "MERGED", "mergedAt": "2026-09-13T13:57:26Z"},
        ):
            retired = runtime._prune_external_claims(fake)
        self.assertEqual(retired, 1)
        self.assertNotIn("547", fake.state.data["external_producer_claims"])
        self.assertEqual(fake.metrics["external_producer_auto_retires"], 1)

    def test_remote_failure_keeps_claim_fail_closed(self) -> None:
        fake = _FakeOrchestrator()
        fake.state.data["external_producer_claims"]["547"] = {
            "state": "active",
            "issue_number": 547,
            "claimed_by": "ni-da-ba",
            "pr_number": 548,
        }
        with mock.patch.object(runtime.core, "_json_cmd", side_effect=RuntimeError("offline")):
            retired = runtime._prune_external_claims(fake)
        self.assertEqual(retired, 0)
        self.assertIn("547", fake.state.data["external_producer_claims"])


if __name__ == "__main__":
    unittest.main()
