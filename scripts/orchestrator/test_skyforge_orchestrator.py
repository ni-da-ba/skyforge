import importlib.util
import pathlib
import sys
import tempfile
import threading
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).with_name("skyforge_orchestrator.py")
SPEC = importlib.util.spec_from_file_location("skyforge_orchestrator", MODULE_PATH)
assert SPEC and SPEC.loader
orch = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = orch
SPEC.loader.exec_module(orch)


class EventFilterTests(unittest.TestCase):
    def test_main_push_is_actionable(self):
        d = orch.classify_event("push", {"ref": "refs/heads/main", "after": "abc123"})
        self.assertTrue(d.actionable)
        self.assertEqual(d.head_sha, "abc123")

    def test_non_main_push_is_ignored(self):
        d = orch.classify_event("push", {"ref": "refs/heads/feature", "after": "abc123"})
        self.assertFalse(d.actionable)

    def test_pr_synchronize_is_ignored_to_wait_for_ci(self):
        d = orch.classify_event(
            "pull_request",
            {
                "action": "synchronize",
                "number": 42,
                "pull_request": {"head": {"sha": "deadbeef"}},
            },
        )
        self.assertFalse(d.actionable)
        self.assertIn("wait for workflow", d.reason)

    def test_pr_closed_is_actionable(self):
        d = orch.classify_event(
            "pull_request",
            {"action": "closed", "number": 42, "pull_request": {"head": {"sha": "deadbeef"}}},
        )
        self.assertTrue(d.actionable)
        self.assertEqual(d.pr_number, 42)

    def test_pr_ready_for_review_is_actionable(self):
        d = orch.classify_event(
            "pull_request",
            {"action": "ready_for_review", "number": 43, "pull_request": {"head": {"sha": "feedface"}}},
        )
        self.assertTrue(d.actionable)

    def test_pr_opened_waits_for_ci(self):
        d = orch.classify_event(
            "pull_request",
            {"action": "opened", "number": 44, "pull_request": {"head": {"sha": "feedface"}}},
        )
        self.assertFalse(d.actionable)

    def test_workflow_completed_is_actionable(self):
        d = orch.classify_event(
            "workflow_run",
            {
                "action": "completed",
                "workflow_run": {
                    "head_sha": "cafebabe",
                    "pull_requests": [{"number": 45}],
                },
            },
        )
        self.assertTrue(d.actionable)
        self.assertEqual(d.head_sha, "cafebabe")
        self.assertEqual(d.pr_number, 45)

    def test_workflow_noncompleted_is_ignored(self):
        d = orch.classify_event(
            "workflow_run",
            {"action": "in_progress", "workflow_run": {"head_sha": "cafebabe"}},
        )
        self.assertFalse(d.actionable)

    def test_audit_comment_wakes_with_structured_restart_directive(self):
        body = (
            "AUDIT — RESTART RECOMMENDED (Content / Experience, hosted wake). "
            "Preserve PR #401 at head `091a2decaf81bebc9e9161e8b04e291c2831d9b0`. "
            "Fresh-worker objective: reuse portable evidence, record C12 B0-A1/B0-A2 acceptance, "
            "and close/merge #401 cleanly."
        )
        d = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 401},
                "comment": {
                    "id": 5588330240,
                    "created_at": "2026-09-08T16:18:09Z",
                    "body": body,
                    "user": {"login": "ni-da-ba"},
                },
            },
        )
        self.assertTrue(d.actionable)
        self.assertEqual(d.pr_number, 401)
        self.assertEqual(d.action, "audit_signal")
        self.assertEqual(d.signal_kind, "restart_recommended")
        self.assertEqual(d.signal_text, body)
        self.assertEqual(d.source_id, "5588330240")
        self.assertEqual(d.observed_at, "2026-09-08T16:18:09Z")

    def test_human_gate_signal_is_structured(self):
        d = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 358},
                "comment": {
                    "id": 99,
                    "body": "AUDIT HUMAN_GATE: morphology review required",
                    "user": {"login": "ni-da-ba"},
                },
            },
        )
        self.assertTrue(d.actionable)
        self.assertEqual(d.signal_kind, "human_gate")

    def test_classifier_prompt_preserves_restart_text_and_does_not_offer_pr_updated_at(self):
        event = orch.EventDecision(
            True,
            "Audit/watchdog orchestration signal",
            "issue_comment",
            action="audit_signal",
            pr_number=401,
            observed_at="2026-09-08T16:18:09+00:00",
            source_id="5588330240",
            signal_kind="restart_recommended",
            signal_text="AUDIT — RESTART RECOMMENDED. Fresh-worker objective: finish #401.",
        )
        prompt = orch._classifier_prompt(
            [event],
            {
                "main": "abc",
                "open_prs": [
                    {
                        "number": 401,
                        "isDraft": True,
                        "headRefOid": "091a2decaf81bebc9e9161e8b04e291c2831d9b0",
                    }
                ],
            },
        )
        self.assertIn('"signal_kind": "restart_recommended"', prompt)
        self.assertIn("RESTART RECOMMENDED", prompt)
        self.assertIn("Fresh-worker objective: finish #401", prompt)
        self.assertIn("2026-09-08T16:18:09+00:00", prompt)
        self.assertIn("updatedAt is not producer-liveness evidence", prompt)

    def test_manual_command_wakes(self):
        d = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 349},
                "comment": {"body": "/skyforge-orchestrate", "user": {"login": "ni-da-ba"}},
            },
        )
        self.assertTrue(d.actionable)
        self.assertEqual(d.action, "manual_command")

    def test_ordinary_comment_is_ignored(self):
        d = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 349},
                "comment": {"body": "Looks good to me."},
            },
        )
        self.assertFalse(d.actionable)

    def test_controller_comment_is_ignored_even_with_gate_words(self):
        d = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 349},
                "comment": {"body": "[skyforge-orchestrator] HUMAN_GATE", "user": {"login": "ni-da-ba"}},
            },
        )
        self.assertFalse(d.actionable)

    def test_untrusted_manual_command_cannot_wake(self):
        d = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 349},
                "comment": {
                    "body": "/skyforge-orchestrate",
                    "user": {"login": "random-contributor"},
                },
            },
        )
        self.assertFalse(d.actionable)
        self.assertIn("untrusted commenter", d.reason)

    def test_untrusted_audit_words_cannot_wake(self):
        d = orch.classify_event(
            "issue_comment",
            {
                "action": "created",
                "issue": {"number": 349},
                "comment": {
                    "body": "AUDIT: LOOP RISK",
                    "user": {"login": "random-contributor"},
                },
            },
        )
        self.assertFalse(d.actionable)

    def test_pause_resume_commands_require_trusted_actor(self):
        trusted = {
            "action": "created",
            "comment": {"body": "/skyforge-pause", "user": {"login": "ni-da-ba"}},
        }
        untrusted = {
            "action": "created",
            "comment": {"body": "/skyforge-pause", "user": {"login": "random-contributor"}},
        }
        self.assertEqual(orch.classify_control_command("issue_comment", trusted), "pause")
        self.assertIsNone(orch.classify_control_command("issue_comment", untrusted))
        trusted["comment"]["body"] = "/skyforge-resume"
        self.assertEqual(orch.classify_control_command("issue_comment", trusted), "resume")

    def test_external_pr_event_is_ignored(self):
        d = orch.classify_event(
            "pull_request",
            {
                "action": "closed",
                "number": 99,
                "pull_request": {
                    "head": {
                        "sha": "abc",
                        "repo": {"full_name": "someone/fork"},
                    }
                },
            },
        )
        self.assertFalse(d.actionable)
        self.assertIn("external/fork", d.reason)

    def test_external_workflow_event_is_ignored(self):
        d = orch.classify_event(
            "workflow_run",
            {
                "action": "completed",
                "workflow_run": {
                    "head_sha": "abc",
                    "head_repository": {"full_name": "someone/fork"},
                    "pull_requests": [],
                },
            },
        )
        self.assertFalse(d.actionable)


class RestartNoopGuardTests(unittest.TestCase):
    def restart_event(self):
        return orch.EventDecision(
            True,
            "Audit/watchdog orchestration signal",
            "issue_comment",
            action="audit_signal",
            pr_number=401,
            observed_at="2026-09-08T16:18:09+00:00",
            source_id="5588330240",
            signal_kind="restart_recommended",
            signal_text=(
                "AUDIT — RESTART RECOMMENDED. Preserve PR #401 at head "
                "`091a2decaf81bebc9e9161e8b04e291c2831d9b0`."
            ),
        )

    def test_unchanged_open_head_does_not_support_restart_noop(self):
        snapshot = {
            "open_prs": [
                {
                    "number": 401,
                    "headRefOid": "091a2decaf81bebc9e9161e8b04e291c2831d9b0",
                }
            ]
        }
        self.assertFalse(
            orch._restart_noop_has_post_signal_evidence([self.restart_event()], snapshot)
        )

    def test_changed_head_supports_restart_noop_reconsideration(self):
        snapshot = {
            "open_prs": [
                {
                    "number": 401,
                    "headRefOid": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                }
            ]
        }
        self.assertTrue(
            orch._restart_noop_has_post_signal_evidence([self.restart_event()], snapshot)
        )

    def test_closed_target_supports_restart_noop_reconsideration(self):
        self.assertTrue(
            orch._restart_noop_has_post_signal_evidence(
                [self.restart_event()], {"open_prs": []}
            )
        )

    def test_restart_without_signal_time_head_cannot_be_silently_nooped(self):
        event = self.restart_event()
        event = orch.replace(event, signal_text="AUDIT — RESTART RECOMMENDED.")
        snapshot = {
            "open_prs": [
                {
                    "number": 401,
                    "headRefOid": "091a2decaf81bebc9e9161e8b04e291c2831d9b0",
                }
            ]
        }
        self.assertFalse(
            orch._restart_noop_has_post_signal_evidence([event], snapshot)
        )


class ClassifierJsonTests(unittest.TestCase):
    def test_plain_json(self):
        value = orch._clean_json_object('{"decision":"NOOP","reason":"idle"}')
        self.assertEqual(value["decision"], "NOOP")

    def test_fenced_json(self):
        value = orch._clean_json_object('''```json
{"decision":"DISPATCH","reason":"work"}
```''')
        self.assertEqual(value["decision"], "DISPATCH")


class FailurePolicyTests(unittest.TestCase):
    def test_quota_failure_is_long_backoff(self):
        kind, delay = orch._codex_failure_policy(RuntimeError("Usage limit reached for Codex"))
        self.assertEqual(kind, "quota")
        self.assertGreaterEqual(delay, 60)

    def test_rate_limit_failure_is_shorter_backoff(self):
        kind, delay = orch._codex_failure_policy(RuntimeError("429 rate limit exceeded"))
        self.assertEqual(kind, "rate_limit")
        self.assertGreaterEqual(delay, 30)

    def test_unknown_failure_is_transient(self):
        kind, delay = orch._codex_failure_policy(RuntimeError("socket disappeared"))
        self.assertEqual(kind, "transient")
        self.assertGreaterEqual(delay, 30)


class HostedTransportTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        return orch.Orchestrator(
            root,
            repo="ni-da-ba/skyforge",
            debounce_seconds=999,
            min_dispatch_seconds=0,
            max_parent_turns=24,
            auto_merge=False,
            webhook_secret="x" * 48,
            require_webhook_secret=True,
            startup_reconcile=True,
        )

    def test_webhook_signature_matches_github_reference_vector(self):
        self.assertTrue(
            orch.verify_webhook_signature(
                "It's a Secret to Everybody",
                b"Hello, World!",
                "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17",
            )
        )

    def test_webhook_signature_rejects_missing_or_tampered_values(self):
        self.assertFalse(orch.verify_webhook_signature(None, b"payload", "sha256=abc"))
        self.assertFalse(orch.verify_webhook_signature("secret", b"payload", None))
        self.assertFalse(
            orch.verify_webhook_signature(
                "secret",
                b"payload",
                "sha256=0000000000000000000000000000000000000000000000000000000000000000",
            )
        )

    def test_delivery_ids_persist_and_are_bounded(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            o = self.make_orchestrator(root)
            for index in range(orch.DEFAULT_MAX_SEEN_DELIVERIES + 3):
                o.record_delivery(f"delivery-{index}")

            self.assertEqual(
                len(o.state.data["seen_deliveries"]),
                orch.DEFAULT_MAX_SEEN_DELIVERIES,
            )
            self.assertFalse(o.delivery_seen("delivery-0"))
            self.assertTrue(
                o.delivery_seen(
                    f"delivery-{orch.DEFAULT_MAX_SEEN_DELIVERIES + 2}"
                )
            )

            reloaded = self.make_orchestrator(root)
            self.assertTrue(
                reloaded.delivery_seen(
                    f"delivery-{orch.DEFAULT_MAX_SEEN_DELIVERIES + 2}"
                )
            )

    def test_health_snapshot_exposes_state_not_secrets(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            health = o.health_snapshot()
            self.assertEqual(health["status"], "ok")
            self.assertEqual(health["repo"], "ni-da-ba/skyforge")
            self.assertNotIn("webhook_secret", health)
            self.assertNotIn("x" * 48, str(health))

    def test_pause_state_persists_and_health_reports_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            o = self.make_orchestrator(root)
            o.set_paused(True, actor="ni-da-ba")
            self.assertTrue(o.is_paused())
            self.assertTrue(o.health_snapshot()["paused"])

            reloaded = self.make_orchestrator(root)
            self.assertTrue(reloaded.is_paused())
            reloaded.set_paused(False, actor="ni-da-ba")
            self.assertFalse(reloaded.is_paused())

    def test_worker_control_plane_paths_are_forbidden(self):
        forbidden = [
            "scripts/orchestrator/skyforge_orchestrator.py",
            "deploy/orchestrator/Caddyfile.in",
            ".github/workflows/ci.yml",
            ".github/dependabot.yml",
            "AGENTS.md",
            "docs/agent-state/VALIDATION_POLICY.md",
            "docs/agent-state/AUDIT_STATE.md",
            ".skyforge-orchestrator/state.json",
        ]
        for path in forbidden:
            with self.subTest(path=path):
                self.assertTrue(orch.Orchestrator._worker_path_forbidden(path))
        self.assertFalse(
            orch.Orchestrator._worker_path_forbidden(
                "src/main/java/com/skyforge/example/Feature.java"
            )
        )
        self.assertFalse(
            orch.Orchestrator._worker_path_forbidden(
                "docs/agent-state/IMPLEMENTATION_STATE.md"
            )
        )

    def test_classifier_policy_change_rotates_persistent_parent_thread(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            state = orch.LocalState(root)
            state.data["parent_thread_id"] = "stale-thread"
            state.data["parent_turns"] = 7
            state.data["classifier_policy_fingerprint"] = "old-policy"
            state.save()

            o = self.make_orchestrator(root)
            self.assertIsNone(o.state.data["parent_thread_id"])
            self.assertEqual(o.state.data["parent_turns"], 0)
            self.assertEqual(
                o.state.data["classifier_policy_fingerprint"],
                orch.CLASSIFIER_POLICY_FINGERPRINT,
            )
            self.assertEqual(
                o.state.data["metrics"].get("classifier_policy_rotations"),
                1,
            )

    def test_first_week_local_budget_defaults_are_conservative(self):
        self.assertEqual(orch.DEFAULT_MAX_CLASSIFIER_CALLS_PER_DAY, 24)
        self.assertEqual(orch.DEFAULT_MAX_WORKER_CALLS_PER_DAY, 4)

    def test_reconcile_fingerprint_is_order_stable_for_mapping_keys(self):
        first = {
            "main": "abc",
            "open_prs": [{"number": 2, "title": "x"}],
            "recent_runs": [{"databaseId": 5, "status": "completed"}],
        }
        second = {
            "recent_runs": [{"status": "completed", "databaseId": 5}],
            "open_prs": [{"title": "x", "number": 2}],
            "main": "abc",
        }
        self.assertEqual(
            orch.Orchestrator._reconcile_fingerprint(first),
            orch.Orchestrator._reconcile_fingerprint(second),
        )

    def test_startup_reconcile_wakes_only_after_baseline_changes(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            initial = {"main": "abc", "open_prs": [], "recent_runs": []}
            changed = {
                "main": "def",
                "open_prs": [{"number": 7}],
                "recent_runs": [],
            }

            with mock.patch.object(o, "_remote_reconcile_snapshot", return_value=initial), \
                    mock.patch.object(o, "enqueue") as enqueue:
                o.startup_reconcile_repository()
                enqueue.assert_not_called()

                o.startup_reconcile_repository()
                enqueue.assert_not_called()

                o._remote_reconcile_snapshot.return_value = changed
                o.startup_reconcile_repository()
                enqueue.assert_called_once()
                event = enqueue.call_args.args[0]
                self.assertEqual(event.event, "reconcile")
                self.assertTrue(event.actionable)
                self.assertEqual(event.head_sha, "def")


class DurableStateTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        return orch.Orchestrator(
            root,
            repo="ni-da-ba/skyforge",
            debounce_seconds=999,
            min_dispatch_seconds=0,
            max_parent_turns=24,
            auto_merge=False,
        )

    def test_event_state_round_trip(self):
        event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
        restored = orch.EventDecision.from_state(event.to_state())
        self.assertEqual(restored, event)
        self.assertEqual(orch._event_key(restored), orch._event_key(event))

    def test_event_identity_ignores_observation_timestamp(self):
        first = orch.EventDecision(
            True,
            "main advanced",
            "push",
            head_sha="abc123",
            observed_at="2026-09-08T01:00:00+00:00",
        )
        second = orch.EventDecision(
            True,
            "main advanced",
            "push",
            head_sha="abc123",
            observed_at="2026-09-08T01:05:00+00:00",
        )
        self.assertEqual(orch._event_key(first), orch._event_key(second))

    def test_distinct_audit_comments_with_same_text_do_not_collapse(self):
        first = orch.EventDecision(
            True,
            "Audit/watchdog orchestration signal",
            "issue_comment",
            action="audit_signal",
            pr_number=401,
            source_id="100",
            signal_kind="restart_recommended",
            signal_text="AUDIT: RESTART RECOMMENDED",
        )
        second = orch.EventDecision(
            True,
            "Audit/watchdog orchestration signal",
            "issue_comment",
            action="audit_signal",
            pr_number=401,
            source_id="101",
            signal_kind="restart_recommended",
            signal_text="AUDIT: RESTART RECOMMENDED",
        )
        self.assertNotEqual(orch._event_key(first), orch._event_key(second))

    def test_pending_events_are_durable_and_deduplicated(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            o = self.make_orchestrator(root)
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event, event])
            self.assertEqual(o._pending_events(), [event])

            reloaded = self.make_orchestrator(root)
            self.assertEqual(reloaded._pending_events(), [event])

    def test_new_event_invalidates_cached_decision_when_no_worker_active(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            first = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([first])
            o._cache_decision({"decision": "NOOP", "reason": "idle"}, [first])
            self.assertIsNotNone(o._decision_record())

            second = orch.EventDecision(True, "main advanced", "push", head_sha="def456")
            o._persist_pending_events([second])
            self.assertIsNone(o._decision_record())

    def test_new_event_preserves_inflight_worker_decision(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            first = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([first])
            o._cache_decision({"decision": "DISPATCH", "lane": "Audit", "reason": "work"}, [first])
            o.state.data["pending_worker"] = {"branch": "codex/audit-test"}
            o.state.save()

            second = orch.EventDecision(True, "main advanced", "push", head_sha="def456")
            o._persist_pending_events([second])
            self.assertIsNotNone(o._decision_record())

    def test_completed_decision_removes_only_its_event_batch(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            first = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            second = orch.EventDecision(True, "main advanced", "push", head_sha="def456")
            o._persist_pending_events([first])
            o._cache_decision({"decision": "DISPATCH", "lane": "Audit", "reason": "work"}, [first])
            o.state.data["pending_worker"] = {"branch": "codex/audit-test"}
            o.state.save()
            o._persist_pending_events([second])

            o._clear_completed_decision()
            self.assertEqual(o._pending_events(), [second])
            if o._timer is not None:
                o._timer.cancel()

    def test_enqueue_journals_actionable_event_before_dispatch(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o.enqueue(event)
            try:
                pending = o._pending_events()
                self.assertEqual(len(pending), 1)
                self.assertEqual(orch._event_key(pending[0]), orch._event_key(event))
                self.assertIsNotNone(pending[0].observed_at)

                reloaded = self.make_orchestrator(pathlib.Path(tmp))
                restored = reloaded._pending_events()
                self.assertEqual(len(restored), 1)
                self.assertEqual(orch._event_key(restored[0]), orch._event_key(event))
                self.assertEqual(restored[0].observed_at, pending[0].observed_at)
            finally:
                if o._timer is not None:
                    o._timer.cancel()

    def test_restart_invalidates_cached_nonworker_decision(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event])
            o._cache_decision({"decision": "NOOP", "reason": "old snapshot"}, [event])
            self.assertIsNotNone(o._decision_record())

            o.resume_pending()
            try:
                self.assertIsNone(o._decision_record())
                self.assertEqual(o._pending_events(), [event])
            finally:
                if o._timer is not None:
                    o._timer.cancel()

    def test_worker_handoff_stage_is_durable(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            o = self.make_orchestrator(root)
            o.state.data["pending_worker"] = {
                "lane": "Audit",
                "branch": "codex/audit-test",
                "stage": "editing",
            }
            o.state.save()

            o._mark_worker_handoff("tests passed")
            reloaded = self.make_orchestrator(root)
            pending = reloaded.state.data["pending_worker"]
            self.assertEqual(pending["stage"], "handoff")
            self.assertEqual(pending["worker_summary"], "tests passed")

    def test_protected_handoff_safety_pauses_without_commit(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            with mock.patch.object(
                o,
                "_changed_paths",
                return_value=[".github/workflows/ci.yml"],
            ), mock.patch.object(o, "_post_gate") as post_gate:
                with self.assertRaisesRegex(RuntimeError, "safety-paused"):
                    o._handoff_changes(
                        "Implementation",
                        "unsafe test",
                        "codex/implementation-test",
                        77,
                        "done",
                    )

            self.assertTrue(o.is_paused())
            self.assertEqual(o.state.data["paused_by"], "controller-safety")
            post_gate.assert_called_once()
            message = post_gate.call_args.args[0]["human_message"]
            self.assertIn("No autonomous commit/push occurred", message)

    def test_handoff_reuses_existing_open_pr_after_interruption(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.state.data["pending_worker"] = {
                "lane": "Audit",
                "branch": "codex/audit-test",
                "stage": "handoff",
                "worker_summary": "done",
            }
            o.state.save()

            def fake_run(args, **kwargs):
                if args[:3] == ["git", "rev-list", "--count"]:
                    return orch.subprocess.CompletedProcess(args, 0, stdout="1\n", stderr="")
                return orch.subprocess.CompletedProcess(args, 0, stdout="", stderr="")

            with mock.patch.object(o, "_changed_paths", return_value=[]), \
                    mock.patch.object(orch, "_run", side_effect=fake_run), \
                    mock.patch.object(orch, "_json_cmd", return_value=[{"number": 77}]):
                o._handoff_changes("Audit", "durable handoff", "codex/audit-test", None, "done")

            self.assertEqual(o.state.data["managed"]["Audit"]["pr_number"], 77)
            self.assertEqual(o.state.data["managed"]["Audit"]["branch"], "codex/audit-test")

    def test_concurrent_state_saves_remain_valid_json(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            o = self.make_orchestrator(root)
            errors = []

            def writer(index):
                try:
                    for value in range(20):
                        with o._state_lock:
                            o.state.data[f"thread_{index}"] = value
                            o.state.save()
                except Exception as exc:
                    errors.append(exc)

            threads = [threading.Thread(target=writer, args=(index,)) for index in range(4)]
            for thread in threads:
                thread.start()
            for thread in threads:
                thread.join()

            self.assertEqual(errors, [])
            reloaded = self.make_orchestrator(root)
            for index in range(4):
                self.assertEqual(reloaded.state.data[f"thread_{index}"], 19)

    def test_local_budget_blocks_without_spending_beyond_limit(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            old = orch.os.environ.get("SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY")
            orch.os.environ["SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY"] = "1"
            try:
                o._consume_budget("worker")
                with self.assertRaises(orch.RetryBlocked) as ctx:
                    o._consume_budget("worker")
                self.assertEqual(ctx.exception.kind, "local_budget")
                self.assertEqual(o.state.data["worker_calls_today"], 1)
            finally:
                if old is None:
                    orch.os.environ.pop("SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY", None)
                else:
                    orch.os.environ["SKYFORGE_ORCHESTRATOR_MAX_WORKER_CALLS_PER_DAY"] = old


class FrugalRoutingTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        return orch.Orchestrator(
            root,
            repo="ni-da-ba/skyforge",
            debounce_seconds=999,
            min_dispatch_seconds=0,
            max_parent_turns=24,
            auto_merge=False,
            webhook_secret="x" * 48,
            require_webhook_secret=True,
            startup_reconcile=True,
        )

    def test_luna_worker_shares_classifier_daily_ceiling(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            old = orch.os.environ.get("SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY")
            orch.os.environ["SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY"] = "2"
            try:
                o._consume_budget("classifier")
                o._consume_budget("luna_worker")
                with self.assertRaises(orch.RetryBlocked) as ctx:
                    o._consume_budget("classifier")
                self.assertEqual(ctx.exception.kind, "local_budget")
                self.assertEqual(o.state.data["classifier_calls_today"], 1)
                self.assertEqual(o.state.data["luna_worker_calls_today"], 1)
            finally:
                if old is None:
                    orch.os.environ.pop("SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY", None)
                else:
                    orch.os.environ["SKYFORGE_ORCHESTRATOR_MAX_CLASSIFIER_CALLS_PER_DAY"] = old

    def test_worker_scope_rejection_safety_pauses_without_commit(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            with mock.patch.object(
                o,
                "_changed_paths",
                return_value=[
                    "docs/agent-state/CONTENT_STATE.md",
                    "skyforge-neoforge-1211/build.gradle.kts",
                ],
            ), mock.patch.object(o, "_post_gate") as post_gate:
                with self.assertRaises(orch.SafetyPause):
                    o._handoff_changes(
                        "Content",
                        "record accepted C12 boundary",
                        "codex/content-test",
                        None,
                        "done",
                        ["docs/agent-state/CONTENT_STATE.md"],
                    )

            self.assertTrue(o.is_paused())
            self.assertEqual(o.state.data["paused_by"], "controller-safety")
            post_gate.assert_called_once()
            message = post_gate.call_args.args[0]["human_message"]
            self.assertIn("outside the bounded edit scope", message)
            self.assertEqual(
                o.state.data["metrics"].get("worker_scope_rejections"),
                1,
            )

    def test_allowed_path_prefix(self):
        self.assertTrue(
            orch.Orchestrator._worker_path_allowed(
                "docs/design-audit/example.md",
                ["docs/design-audit/**"],
            )
        )
        self.assertFalse(
            orch.Orchestrator._worker_path_allowed(
                "skyforge-neoforge-1211/build.gradle.kts",
                ["docs/design-audit/**"],
            )
        )

    def test_health_exposes_pending_worker_tier_and_age(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.state.data["pending_worker"] = {
                "lane": "Content",
                "branch": "codex/content-test",
                "stage": "editing",
                "worker_tier": "LUNA",
                "started_at": orch._utc_now(),
            }
            o.state.data["classifier_calls_today"] = 3
            o.state.data["luna_worker_calls_today"] = 2
            o.state.data["worker_calls_today"] = 1
            o.state.save()

            health = o.health_snapshot()
            self.assertEqual(health["worker_lane"], "Content")
            self.assertEqual(health["worker_stage"], "editing")
            self.assertEqual(health["worker_tier"], "LUNA")
            self.assertIsNotNone(health["worker_age_seconds"])
            self.assertEqual(health["luna_calls_today_total"], 5)
            self.assertEqual(health["terra_worker_calls_today"], 1)


if __name__ == "__main__":
    unittest.main()
