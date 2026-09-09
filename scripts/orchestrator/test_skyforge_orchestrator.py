import pathlib
import tempfile
import unittest
from unittest import mock

_CORE_TEST_PATH = pathlib.Path(__file__).with_name("test_skyforge_orchestrator_core.py")
_CORE_TEST_SOURCE = _CORE_TEST_PATH.read_text()
_CORE_TEST_MAIN = '\nif __name__ == "__main__":\n    unittest.main()\n'
if _CORE_TEST_SOURCE.count(_CORE_TEST_MAIN) != 1:
    raise RuntimeError("Skyforge orchestrator core test entrypoint shape changed; refuse overlay load")
exec(
    compile(_CORE_TEST_SOURCE.replace(_CORE_TEST_MAIN, "\n"), str(_CORE_TEST_PATH), "exec"),
    globals(),
    globals(),
)


class MaterialStateCoalescingTests(HostedTransportTests):
    def test_budget_reset_signal_requires_trusted_actor(self):
        trusted = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 349},
                "comment": {
                    "id": 123,
                    "body": "/skyforge-reset-budget",
                    "user": {"login": "ni-da-ba"},
                },
            },
        )
        self.assertTrue(trusted.actionable)
        self.assertEqual(trusted.signal_kind, "budget_reset")

        untrusted = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 349},
                "comment": {
                    "id": 124,
                    "body": "/skyforge-reset-budget",
                    "user": {"login": "intruder"},
                },
            },
        )
        self.assertFalse(untrusted.actionable)

    def test_budget_reset_is_zero_cost_and_preserves_queue(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.state.data["classifier_calls_today"] = 22
            o.state.data["luna_worker_calls_today"] = 1
            o.state.data["worker_calls_today"] = 2
            o.state.data["blocked_kind"] = "local_budget"
            o.state.data["blocked_until_epoch"] = 9999999999.0
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc")
            o._persist_pending_events([event])
            o.set_paused(True, actor="ni-da-ba")

            reset = orch.EventDecision(
                True,
                "trusted reset",
                "issue_comment",
                action="audit_signal",
                signal_kind="budget_reset",
            )
            o.enqueue(reset)

            self.assertEqual(o.state.data["classifier_calls_today"], 0)
            self.assertEqual(o.state.data["luna_worker_calls_today"], 0)
            self.assertEqual(o.state.data["worker_calls_today"], 0)
            self.assertIsNone(o.state.data["blocked_kind"])
            self.assertEqual(len(o._pending_events()), 1)
            self.assertTrue(o.is_paused())
            self.assertEqual(o.state.data["metrics"].get("manual_budget_resets"), 1)

    def test_budget_reset_rejected_while_running(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.state.data["classifier_calls_today"] = 7
            reset = orch.EventDecision(
                True,
                "trusted reset",
                "issue_comment",
                action="audit_signal",
                signal_kind="budget_reset",
            )
            o.enqueue(reset)
            self.assertEqual(o.state.data["classifier_calls_today"], 7)
            self.assertEqual(o.state.data["metrics"].get("budget_reset_rejections"), 1)

    def test_push_head_waits_for_quiescence_before_core_dispatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc")
            with mock.patch.object(o, "_settle_remaining", return_value=0), \
                 mock.patch.object(o, "workflows_quiescent", return_value=False) as quiet, \
                 mock.patch.object(o, "_schedule_pending") as schedule, \
                 mock.patch.object(orch._BaseOrchestrator, "dispatch") as base_dispatch:
                o.dispatch([event])
            quiet.assert_called_once_with("abc")
            schedule.assert_called_once()
            base_dispatch.assert_not_called()

    def test_duplicate_material_state_returns_noop_without_budget_use(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "workflow completed", "workflow_run", head_sha="abc")
            snapshot = {
                "main": "main",
                "open_prs": [],
                "controller_managed": [],
                "classifier_policy": orch.CLASSIFIER_POLICY_FINGERPRINT,
            }
            fingerprint = o._material_fingerprint(snapshot)
            o.state.data["last_material_classifier_fingerprint"] = fingerprint
            o.state.save()
            o._active_dispatch_events = [event]

            with mock.patch.object(o, "_material_state_snapshot", return_value=snapshot), \
                 mock.patch.object(orch._BaseOrchestrator, "_codex_classifier") as base_classifier:
                decision = o._codex_classifier("prompt")

            self.assertEqual(decision["decision"], "NOOP")
            self.assertTrue(decision["material_state_duplicate"])
            base_classifier.assert_not_called()
            self.assertEqual(o.state.data["classifier_calls_today"], 0)
            self.assertEqual(
                o.state.data["metrics"].get("material_duplicate_batches_suppressed"), 1
            )

    def test_material_fingerprint_persists_only_after_decision_cache(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "workflow completed", "workflow_run", head_sha="abc")
            snapshot = {
                "main": "new",
                "open_prs": [],
                "controller_managed": [],
                "classifier_policy": orch.CLASSIFIER_POLICY_FINGERPRINT,
            }
            actual = {
                "decision": "NOOP",
                "lane": None,
                "pr_number": None,
                "objective": None,
                "stop_boundary": None,
                "reusable_evidence": None,
                "worker_tier": None,
                "allowed_paths": None,
                "reason": "done",
                "human_message": None,
            }
            o._active_dispatch_events = [event]
            with mock.patch.object(o, "_material_state_snapshot", return_value=snapshot), \
                 mock.patch.object(
                     orch._BaseOrchestrator, "_codex_classifier", return_value=actual
                 ):
                result = o._codex_classifier("prompt")
            self.assertNotIn("last_material_classifier_fingerprint", o.state.data)
            o._cache_decision(result, [event])
            self.assertEqual(
                o.state.data["last_material_classifier_fingerprint"],
                o._material_fingerprint(snapshot),
            )

    def test_manual_audit_signal_bypasses_material_duplicate(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(
                True,
                "audit",
                "issue_comment",
                action="audit_signal",
                signal_kind="audit",
            )
            snapshot = {
                "main": "same",
                "open_prs": [],
                "controller_managed": [],
                "classifier_policy": orch.CLASSIFIER_POLICY_FINGERPRINT,
            }
            o.state.data["last_material_classifier_fingerprint"] = o._material_fingerprint(snapshot)
            o.state.save()
            o._active_dispatch_events = [event]
            actual = {
                "decision": "NOOP",
                "lane": None,
                "pr_number": None,
                "objective": None,
                "stop_boundary": None,
                "reusable_evidence": None,
                "worker_tier": None,
                "allowed_paths": None,
                "reason": "operator-forced",
                "human_message": None,
            }
            with mock.patch.object(o, "_material_state_snapshot", return_value=snapshot), \
                 mock.patch.object(
                     orch._BaseOrchestrator, "_codex_classifier", return_value=actual
                 ) as base_classifier:
                o._codex_classifier("prompt")
            base_classifier.assert_called_once()

    def test_material_fingerprint_ignores_actions_timing(self):
        first = {
            "main": "abc",
            "open_prs": [{"number": 1, "headRefOid": "def", "isDraft": False}],
            "controller_managed": [],
            "classifier_policy": "policy",
        }
        second = dict(first)
        self.assertEqual(
            orch.Orchestrator._material_fingerprint(first),
            orch.Orchestrator._material_fingerprint(second),
        )


if __name__ == "__main__":
    unittest.main()
