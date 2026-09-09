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
        trusted["comment"]["body"] = "/skyforge-status"
        self.assertEqual(orch.classify_control_command("issue_comment", trusted), "status")
        trusted["comment"]["body"] = "/skyforge-reset-budget"
        self.assertEqual(
            orch.classify_control_command("issue_comment", trusted),
            "reset_budget",
        )
        trusted["comment"]["body"] = "/skyforge-refresh-runtime"
        self.assertEqual(
            orch.classify_control_command("issue_comment", trusted),
            "refresh_runtime",
        )
        trusted["comment"]["body"] = "/skyforge-discard-worker"
        self.assertEqual(
            orch.classify_control_command("issue_comment", trusted),
            "discard_worker",
        )
        for command in (
            "/skyforge-reset-budget",
            "/skyforge-refresh-runtime",
            "/skyforge-discard-worker",
        ):
            untrusted["comment"]["body"] = command
            self.assertIsNone(orch.classify_control_command("issue_comment", untrusted))

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


class ClassifierIdempotencyTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        return orch.Orchestrator(
            root,
            repo="ni-da-ba/skyforge",
            debounce_seconds=999,
            min_dispatch_seconds=0,
            max_parent_turns=24,
            auto_merge=False,
        )

    def snapshot(self, *, run_status="completed"):
        return {
            "captured_at": "2026-09-09T01:00:00Z",
            "main": "mainhead",
            "open_prs": [
                {
                    "number": 358,
                    "title": "morphology gate",
                    "isDraft": False,
                    "headRefName": "impl/test",
                    "headRefOid": "prhead",
                    "baseRefName": "main",
                    "mergeStateStatus": "CLEAN",
                    "author": {"login": "ni-da-ba"},
                }
            ],
            "recent_runs": [
                {
                    "databaseId": 123,
                    "name": "CI",
                    "status": run_status,
                    "conclusion": "success" if run_status == "completed" else None,
                    "headSha": "mainhead",
                    "headBranch": "main",
                    "event": "push",
                    "updatedAt": "2026-09-09T01:00:00Z",
                }
            ],
            "controller_managed": {},
            "orchestrator_metrics": {"classifier_attempts": 999},
        }

    def test_classifier_input_fingerprint_ignores_wallclock_and_metrics_noise(self):
        event = orch.EventDecision(True, "main advanced", "push", head_sha="mainhead")
        first = self.snapshot()
        second = self.snapshot()
        second["captured_at"] = "2030-01-01T00:00:00Z"
        second["orchestrator_metrics"] = {"classifier_attempts": 1}
        second["recent_runs"][0]["updatedAt"] = "2030-01-01T00:00:00Z"

        self.assertEqual(
            orch._classifier_input_fingerprint([event], first),
            orch._classifier_input_fingerprint([event], second),
        )

    def test_classifier_input_fingerprint_changes_on_repository_state_change(self):
        event = orch.EventDecision(True, "main advanced", "push", head_sha="mainhead")
        self.assertNotEqual(
            orch._classifier_input_fingerprint([event], self.snapshot(run_status="in_progress")),
            orch._classifier_input_fingerprint([event], self.snapshot(run_status="completed")),
        )

    def test_identical_semantic_input_reuses_paid_classifier_decision(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="mainhead")
            snap = self.snapshot()
            o._persist_pending_events([event])

            with mock.patch.object(o, "workflows_quiescent", return_value=True), \
                    mock.patch.object(o, "sync_main"), \
                    mock.patch.object(o, "snapshot", return_value=snap), \
                    mock.patch.object(
                        o,
                        "_codex_classifier",
                        return_value={"decision": "NOOP", "reason": "human gates unchanged"},
                    ) as classifier, \
                    mock.patch.object(o, "_schedule_pending"):
                o.dispatch(o._pending_events())
                self.assertEqual(classifier.call_count, 1)
                self.assertEqual(o._pending_events(), [])

                # Re-observing the exact same semantic input may happen after replay/reconciliation.
                # It must restore the cached decision rather than paying Luna a second time.
                o._persist_pending_events([event])
                o.dispatch(o._pending_events())

            self.assertEqual(classifier.call_count, 1)
            self.assertEqual(o._pending_events(), [])
            self.assertEqual(
                o.state.data["metrics"].get("classifier_decision_cache_hits"),
                1,
            )


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
            o.state.data["blocked_reason"] = "authentication Bearer super-secret-token"
            o.state.save()
            health = o.health_snapshot()
            self.assertEqual(health["status"], "ok")
            self.assertEqual(health["repo"], "ni-da-ba/skyforge")
            self.assertNotIn("webhook_secret", health)
            self.assertNotIn("blocked_reason", health)
            self.assertNotIn("x" * 48, str(health))
            self.assertNotIn("super-secret-token", str(health))

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

    def test_status_command_posts_nonsecret_runtime_and_checkout_heads(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.runtime_head = "runtime-head-123"

            calls = []

            def fake_run(args, **kwargs):
                calls.append(args)
                if args[:3] == ["git", "rev-parse", "HEAD"]:
                    return orch.subprocess.CompletedProcess(
                        args, 0, stdout="checkout-head-456\n", stderr=""
                    )
                return orch.subprocess.CompletedProcess(args, 0, stdout="", stderr="")

            with mock.patch.object(orch, "_run", side_effect=fake_run):
                o.post_status(349)

            gh_calls = [args for args in calls if args[:3] == ["gh", "issue", "comment"]]
            self.assertEqual(len(gh_calls), 1)
            body = gh_calls[0][gh_calls[0].index("--body") + 1]
            self.assertIn("[skyforge-orchestrator] STATUS", body)
            self.assertIn('"runtime_head": "runtime-head-123"', body)
            self.assertIn('"checkout_head": "checkout-head-456"', body)
            self.assertNotIn("x" * 48, body)
            self.assertEqual(o.state.data["metrics"].get("status_commands"), 1)

    def test_hosted_installer_enforces_minimal_ufw_surface(self):
        installer = MODULE_PATH.with_name("install_hosted.sh").read_text()
        self.assertIn("sudo ufw default deny incoming", installer)
        self.assertIn("sudo ufw default allow outgoing", installer)
        self.assertIn("sudo ufw allow 22/tcp", installer)
        self.assertIn("sudo ufw allow 80/tcp", installer)
        self.assertIn("sudo ufw allow 443/tcp", installer)
        self.assertIn("sudo ufw --force enable", installer)
        self.assertLess(
            installer.index("sudo ufw allow 22/tcp"),
            installer.index("sudo ufw --force enable"),
        )

    def test_hosted_installer_reuses_durable_host_configuration(self):
        installer = MODULE_PATH.with_name("install_hosted.sh").read_text()
        self.assertIn(
            "s/^SKYFORGE_PUBLIC_HOSTNAME=//p",
            installer,
        )
        self.assertIn(
            "s/^SKYFORGE_WEBHOOK_SECRET=//p",
            installer,
        )
        self.assertIn(
            "s/^SKYFORGE_DROPLET_HOURLY_USD=//p",
            installer,
        )
        self.assertIn(
            "s/^SKYFORGE_VALUE_REPORT_ISSUE=//p",
            installer,
        )
        self.assertIn(
            "SKYFORGE_PUBLIC_HOSTNAME=$SKYFORGE_PUBLIC_HOSTNAME",
            installer,
        )

    def test_hosted_installer_reconciles_runtime_without_resetting_existing_value_baseline(self):
        installer = MODULE_PATH.with_name("install_hosted.sh").read_text()
        self.assertIn(
            '"$VENV_PYTHON" scripts/orchestrator/sync_runtime_dependencies.py --root "$ROOT"',
            installer,
        )
        self.assertIn('if [[ ! -f "$STATE_DIR/report_state.json" ]]; then', installer)
        self.assertIn('echo "Preserving existing hosted value-report baseline."', installer)

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
            state.data["classifier_decision_cache"] = {
                "stale": {"decision": {"decision": "NOOP", "reason": "old"}}
            }
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
            self.assertEqual(o.state.data["classifier_decision_cache"], {})

    def test_classifier_failure_diagnostics_are_nonsecret_and_reset_on_success(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            streak, opened = o._record_classifier_failure(
                "transient",
                RuntimeError("authorization Bearer secret-token transport failed"),
            )
            self.assertEqual(streak, 1)
            self.assertFalse(opened)
            health = o.health_snapshot()
            self.assertEqual(health["classifier_failure_streak"], 1)
            self.assertEqual(health["last_classifier_error_kind"], "transient")
            self.assertNotIn("secret-token", health["last_classifier_error_summary"])
            self.assertIn("[REDACTED]", health["last_classifier_error_summary"])

            o._record_classifier_success()
            health = o.health_snapshot()
            self.assertEqual(health["classifier_failure_streak"], 0)
            self.assertIsNotNone(health["last_classifier_success_at"])

    def test_classifier_failure_circuit_pauses_after_three_consecutive_failures(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            for expected in (1, 2):
                streak, opened = o._record_classifier_failure(
                    "transient",
                    RuntimeError(f"temporary failure {expected}"),
                )
                self.assertEqual(streak, expected)
                self.assertFalse(opened)
                self.assertFalse(o.is_paused())

            streak, opened = o._record_classifier_failure(
                "transient",
                RuntimeError("temporary failure 3"),
            )
            self.assertEqual(streak, 3)
            self.assertTrue(opened)
            self.assertTrue(o.is_paused())
            self.assertEqual(o.state.data["paused_by"], "classifier-failure-circuit")
            self.assertEqual(
                o.state.data["metrics"].get("classifier_failure_circuit_pauses"),
                1,
            )

    def test_first_week_local_budget_defaults_are_conservative(self):
        self.assertEqual(orch.DEFAULT_MAX_CLASSIFIER_CALLS_PER_DAY, 24)
        self.assertEqual(orch.DEFAULT_MAX_WORKER_CALLS_PER_DAY, 4)
        self.assertEqual(orch.DEFAULT_PERIODIC_RECONCILE_SECONDS, 900)

    def test_runtime_refresh_set_covers_controller_dependency_contract(self):
        self.assertIn(
            "scripts/orchestrator/skyforge_orchestrator.py",
            orch.CONTROLLER_RUNTIME_PATHS,
        )
        self.assertIn(
            "scripts/orchestrator/requirements.txt",
            orch.CONTROLLER_RUNTIME_PATHS,
        )
        self.assertIn(
            "scripts/orchestrator/sync_runtime_dependencies.py",
            orch.CONTROLLER_RUNTIME_PATHS,
        )

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

    def test_reconcile_projection_ignores_timestamp_only_noise(self):
        first = {
            "main": "abc",
            "open_prs": [{
                "number": 7,
                "title": "test",
                "isDraft": False,
                "headRefName": "feature",
                "mergeStateStatus": "CLEAN",
                "updatedAt": "2026-09-09T04:00:00Z",
            }],
            "recent_runs": [{
                "databaseId": 9,
                "name": "CI",
                "status": "completed",
                "conclusion": "success",
                "headSha": "abc",
                "headBranch": "main",
                "event": "push",
                "updatedAt": "2026-09-09T04:01:00Z",
            }],
        }
        second = {
            **first,
            "open_prs": [{**first["open_prs"][0], "updatedAt": "2026-09-09T05:00:00Z"}],
            "recent_runs": [{**first["recent_runs"][0], "updatedAt": "2026-09-09T05:01:00Z"}],
        }
        p1 = orch.Orchestrator._reconcile_projection(first)
        p2 = orch.Orchestrator._reconcile_projection(second)
        self.assertEqual(p1, p2)
        self.assertEqual(
            orch.Orchestrator._reconcile_fingerprint(p1),
            orch.Orchestrator._reconcile_fingerprint(p2),
        )

    def test_periodic_reconcile_defers_until_actions_are_quiescent(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o._checkpoint_classifier_reconcile_observation(
                {"main": "abc", "open_prs": [], "recent_runs": []}
            )
            active = {
                "main": "def",
                "open_prs": [],
                "recent_runs": [{
                    "databaseId": 11,
                    "name": "CI",
                    "status": "in_progress",
                    "conclusion": None,
                    "headSha": "def",
                    "headBranch": "main",
                    "event": "push",
                }],
            }
            completed = {
                "main": "def",
                "open_prs": [],
                "recent_runs": [{
                    **active["recent_runs"][0],
                    "status": "completed",
                    "conclusion": "success",
                }],
            }

            with mock.patch.object(o, "_remote_reconcile_snapshot", return_value=active), \
                    mock.patch.object(o, "enqueue") as enqueue:
                o.startup_reconcile_repository(source="periodic")
                enqueue.assert_not_called()
                self.assertEqual(
                    o.state.data["metrics"].get("periodic_reconcile_deferred_active_runs"),
                    1,
                )

                o._remote_reconcile_snapshot.return_value = completed
                o.startup_reconcile_repository(source="periodic")
                enqueue.assert_called_once()
                self.assertEqual(enqueue.call_args.args[0].head_sha, "def")

    def test_json_pages_cmd_parses_gh_paginate_without_slurp(self):
        with tempfile.TemporaryDirectory() as tmp:
            completed = mock.Mock(stdout='[{"id": 1}]\n[{"id": 2}]\n')
            with mock.patch.object(orch, "_run", return_value=completed) as run:
                pages = orch._json_pages_cmd(
                    ["gh", "api", "--paginate", "repos/ni-da-ba/skyforge/issues/comments"],
                    cwd=pathlib.Path(tmp),
                )

            self.assertEqual(pages, [[{"id": 1}], [{"id": 2}]])
            self.assertNotIn("--slurp", run.call_args.args[0])

    def test_issue_comment_reconcile_baselines_history_without_replay(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            comments = [{
                "id": 100,
                "body": "AUDIT: RESTART RECOMMENDED for Content",
                "created_at": "2026-09-09T04:00:00Z",
                "issue_url": "https://api.github.com/repos/ni-da-ba/skyforge/issues/349",
                "user": {"login": "ni-da-ba"},
            }]

            with mock.patch.object(orch, "_json_pages_cmd", return_value=comments), \
                    mock.patch.object(o, "enqueue") as enqueue:
                o.reconcile_issue_comments(source="startup")

            enqueue.assert_not_called()
            self.assertTrue(o.state.data["issue_comment_reconcile_initialized"])
            self.assertTrue(o.issue_comment_seen("100"))
            self.assertEqual(
                o.state.data["metrics"].get("issue_comment_reconcile_baselines"),
                1,
            )

    def test_issue_comment_reconcile_recovers_new_trusted_audit_signal_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.state.data["issue_comment_reconcile_initialized"] = True
            o.state.save()
            comments = [{
                "id": 101,
                "body": "AUDIT: RESTART RECOMMENDED\nLane: Content\nFresh worker required.",
                "created_at": "2026-09-09T04:05:00Z",
                "issue_url": "https://api.github.com/repos/ni-da-ba/skyforge/issues/349",
                "user": {"login": "ni-da-ba"},
            }]

            with mock.patch.object(orch, "_json_pages_cmd", return_value=comments), \
                    mock.patch.object(o, "enqueue") as enqueue:
                o.reconcile_issue_comments(source="periodic")
                o.reconcile_issue_comments(source="periodic")

            enqueue.assert_called_once()
            event = enqueue.call_args.args[0]
            self.assertTrue(event.actionable)
            self.assertEqual(event.event, "issue_comment")
            self.assertEqual(event.signal_kind, "restart_recommended")
            self.assertEqual(event.pr_number, 349)
            self.assertEqual(event.source_id, "101")
            self.assertTrue(o.issue_comment_seen("101"))
            self.assertEqual(
                o.state.data["metrics"].get("issue_comment_recovered_wakes"),
                1,
            )

    def test_issue_comment_reconcile_recovers_trusted_control_without_model(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.state.data["issue_comment_reconcile_initialized"] = True
            o.state.save()
            comments = [{
                "id": 102,
                "body": "/skyforge-pause",
                "created_at": "2026-09-09T04:06:00Z",
                "issue_url": "https://api.github.com/repos/ni-da-ba/skyforge/issues/349",
                "user": {"login": "ni-da-ba"},
            }]

            with mock.patch.object(orch, "_json_pages_cmd", return_value=comments):
                o.reconcile_issue_comments(source="periodic")

            self.assertTrue(o.is_paused())
            self.assertEqual(o.state.data["paused_by"], "ni-da-ba")
            self.assertTrue(o.issue_comment_seen("102"))
            self.assertEqual(
                o.state.data["metrics"].get("issue_comment_recovered_controls"),
                1,
            )

    def test_classifier_checkpoint_suppresses_periodic_reconcile_for_same_state(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            classifier_snapshot = {
                "main": "abc",
                "open_prs": [
                    {
                        "number": 7,
                        "title": "test",
                        "isDraft": False,
                        "headRefName": "feature",
                        "headRefOid": "ignored-extra",
                        "baseRefName": "main",
                        "url": "https://example.invalid/7",
                        "author": {"login": "ni-da-ba"},
                        "updatedAt": "2026-09-09T04:00:00Z",
                        "mergeStateStatus": "CLEAN",
                    }
                ],
                "recent_runs": [
                    {
                        "databaseId": 9,
                        "name": "CI",
                        "status": "completed",
                        "conclusion": "success",
                        "headSha": "abc",
                        "headBranch": "main",
                        "event": "push",
                        "updatedAt": "2026-09-09T04:01:00Z",
                    }
                ],
                "recent_commits": ["ignored"],
                "controller_managed": {},
                "orchestrator_metrics": {},
            }
            o._checkpoint_classifier_reconcile_observation(classifier_snapshot)
            remote = o._reconcile_projection(classifier_snapshot)

            with mock.patch.object(o, "_remote_reconcile_snapshot", return_value=remote), \
                    mock.patch.object(o, "enqueue") as enqueue:
                o.startup_reconcile_repository(source="periodic")

            enqueue.assert_not_called()
            self.assertEqual(
                o.state.data["metrics"].get("periodic_reconcile_noops"),
                1,
            )
            self.assertEqual(
                o.state.data["last_reconcile_source"],
                "periodic",
            )

    def test_reconcile_projection_tracks_pr_head_identity(self):
        first = {
            "main": "abc",
            "open_prs": [{
                "number": 7,
                "title": "test",
                "isDraft": False,
                "headRefName": "feature",
                "headRefOid": "head-a",
                "baseRefName": "main",
                "mergeStateStatus": "CLEAN",
            }],
            "recent_runs": [],
        }
        second = {
            **first,
            "open_prs": [{
                **first["open_prs"][0],
                "headRefOid": "head-b",
            }],
        }
        p1 = orch.Orchestrator._reconcile_projection(first)
        p2 = orch.Orchestrator._reconcile_projection(second)
        self.assertEqual(p1["open_prs"][0]["headRefOid"], "head-a")
        self.assertEqual(p1["open_prs"][0]["baseRefName"], "main")
        self.assertNotEqual(
            orch.Orchestrator._reconcile_fingerprint(p1),
            orch.Orchestrator._reconcile_fingerprint(p2),
        )

    def test_periodic_reconcile_defers_while_controller_owns_pending_work(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.startup_reconcile = True
            o._persist_pending_events([
                orch.EventDecision(True, "main advanced", "push", head_sha="abc")
            ])

            with mock.patch.object(
                o,
                "reconcile_issue_comments",
            ) as comments, mock.patch.object(
                o,
                "startup_reconcile_repository",
            ) as reconcile, mock.patch.object(
                o,
                "_schedule_periodic_reconcile",
            ) as schedule:
                o._run_periodic_reconcile()

            comments.assert_called_once_with(source="periodic")
            reconcile.assert_not_called()
            schedule.assert_called_once_with()
            self.assertEqual(
                o.state.data["metrics"].get("periodic_reconcile_deferred_busy"),
                1,
            )

    def test_periodic_reconcile_wakes_only_for_uncheckpointed_change(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            initial = {"main": "abc", "open_prs": [], "recent_runs": []}
            changed = {"main": "def", "open_prs": [], "recent_runs": []}

            o._checkpoint_classifier_reconcile_observation(initial)
            with mock.patch.object(o, "_remote_reconcile_snapshot", return_value=changed), \
                    mock.patch.object(o, "enqueue") as enqueue:
                o.startup_reconcile_repository(source="periodic")

            enqueue.assert_called_once()
            event = enqueue.call_args.args[0]
            self.assertEqual(event.event, "reconcile")
            self.assertEqual(event.action, "periodic")
            self.assertEqual(event.head_sha, "def")
            self.assertEqual(
                o.state.data["metrics"].get("periodic_reconciliations"),
                1,
            )

    def test_periodic_reconcile_failure_retries_model_free(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.startup_reconcile = True
            with mock.patch.object(
                o,
                "reconcile_issue_comments",
            ), mock.patch.object(
                o,
                "startup_reconcile_repository",
                side_effect=RuntimeError("github unavailable"),
            ), mock.patch.object(
                o,
                "_schedule_periodic_reconcile",
            ) as schedule, mock.patch.object(
                orch,
                "_env_int",
                return_value=30,
            ):
                o._run_periodic_reconcile()

            schedule.assert_called_once_with(30)
            error = o.state.data["last_periodic_reconcile_error"]
            self.assertEqual(error["kind"], "RuntimeError")
            self.assertIn("github unavailable", error["summary"])
            self.assertEqual(
                o.state.data["metrics"].get("periodic_reconcile_failures"),
                1,
            )

    def test_startup_reconcile_failure_is_durable_and_retried_model_free(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.startup_reconcile = True
            with mock.patch.object(
                o,
                "startup_reconcile_repository",
                side_effect=RuntimeError("github temporarily unavailable"),
            ), mock.patch.object(
                o,
                "_schedule_startup_reconcile_retry",
            ) as schedule, mock.patch.object(
                orch,
                "_env_int",
                return_value=30,
            ):
                self.assertFalse(o.attempt_startup_reconcile())

            schedule.assert_called_once_with(30)
            error = o.state.data["last_startup_reconcile_error"]
            self.assertEqual(error["kind"], "RuntimeError")
            self.assertIn("temporarily unavailable", error["summary"])
            self.assertIsNotNone(o.state.data["startup_reconcile_retry_at"])
            self.assertEqual(
                o.state.data["metrics"].get("startup_reconcile_failures"),
                1,
            )

    def test_successful_startup_reconcile_clears_degraded_state(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.startup_reconcile = True
            o.state.data["last_startup_reconcile_error"] = {"kind": "RuntimeError"}
            o.state.data["startup_reconcile_retry_at"] = "later"
            o.state.save()

            with mock.patch.object(o, "startup_reconcile_repository"), \
                    mock.patch.object(o, "reconcile_issue_comments"), \
                    mock.patch.object(o, "_schedule_periodic_reconcile") as schedule:
                self.assertTrue(o.attempt_startup_reconcile())

            schedule.assert_called_once_with()

            self.assertIsNone(o.state.data["last_startup_reconcile_error"])
            self.assertIsNone(o.state.data["startup_reconcile_retry_at"])
            self.assertIsNotNone(o.state.data["last_startup_reconcile_success_at"])


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

    def test_local_state_save_creates_valid_primary_and_backup(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            state = orch.LocalState(root)
            state.data["marker"] = "durable"
            state.save()

            self.assertEqual(
                orch.LocalState._read_mapping(state.path)["marker"],
                "durable",
            )
            self.assertEqual(
                orch.LocalState._read_mapping(state.backup_path)["marker"],
                "durable",
            )

    def test_local_state_recovers_from_backup_when_primary_is_corrupt(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            state = orch.LocalState(root)
            state.data["marker"] = "preserved"
            state.save()
            state.path.write_text("{not-json")

            recovered = orch.LocalState(root)

            self.assertEqual(recovered.data["marker"], "preserved")
            self.assertEqual(
                recovered.data["metrics"].get("state_backup_recoveries"),
                1,
            )
            self.assertEqual(
                recovered.data["last_state_recovery"]["source"],
                "state.json.bak",
            )
            self.assertEqual(
                orch.LocalState._read_mapping(recovered.path)["marker"],
                "preserved",
            )

    def test_local_state_fails_closed_when_primary_and_backup_are_corrupt(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            state = orch.LocalState(root)
            state.save()
            state.path.write_text("{bad-primary")
            state.backup_path.write_text("{bad-backup")

            with self.assertRaisesRegex(RuntimeError, "primary and backup"):
                orch.LocalState(root)

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

    def test_new_task_audit_comment_is_structured_task_authority(self):
        payload = {
            "action": "created",
            "comment": {
                "id": 5603944223,
                "created_at": "2026-09-09T14:56:00+00:00",
                "body": (
                    "AUDIT — NEW CONTENT TASK\n"
                    "Route issue #431 to Content as standalone work."
                ),
                "user": {"login": "ni-da-ba"},
            },
            "issue": {"number": 431},
        }

        decision = orch.classify_event("issue_comment", payload)

        self.assertTrue(decision.actionable)
        self.assertEqual(decision.action, "audit_signal")
        self.assertEqual(decision.signal_kind, "task")
        self.assertEqual(decision.pr_number, 431)

    def test_task_issue_context_hydrates_authoritative_issue_body(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            task = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=431,
                source_id="task-context",
                signal_kind="task",
                signal_text="AUDIT — NEW CONTENT TASK",
            )
            issue = {
                "number": 431,
                "title": "CONTENT: investigate external integration",
                "state": "open",
                "html_url": "https://github.com/ni-da-ba/skyforge/issues/431",
                "body": "Verify the current release and research current upstream issue/changelog evidence.",
            }

            with mock.patch.object(orch, "_json_cmd", return_value=issue) as read_issue:
                context = o._task_issue_context([task])

            self.assertEqual(context[0]["number"], 431)
            self.assertEqual(context[0]["title"], issue["title"])
            self.assertEqual(context[0]["body"], issue["body"])
            read_issue.assert_called_once()

    def test_current_external_evidence_requirement_is_detected(self):
        self.assertTrue(
            orch._requires_current_external_evidence(
                [
                    {
                        "title": "Integration audit",
                        "body": (
                            "Verify the current release and exact compatibility. "
                            "Research current upstream issue/changelog evidence."
                        ),
                    }
                ]
            )
        )
        self.assertFalse(
            orch._requires_current_external_evidence(
                [{"title": "Content doctrine", "body": "Reconcile repository-local accepted state."}]
            )
        )

    def test_external_research_task_dispatch_is_forced_to_human_gate(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            task = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=431,
                source_id="task-capability",
                signal_kind="task",
                signal_text="AUDIT — NEW CONTENT TASK",
            )
            decision = {
                "decision": "DISPATCH",
                "lane": "Content",
                "pr_number": None,
                "objective": "Investigate issue #431.",
                "stop_boundary": "Content handoff.",
                "worker_tier": "LUNA",
                "allowed_paths": ["docs/agent-state/CONTENT_STATE.md"],
                "reason": "task",
            }
            context = [
                {
                    "number": 431,
                    "title": "Create:Aero audit",
                    "body": (
                        "Verify the current release and exact compatibility. "
                        "Research current upstream issue/changelog evidence and license."
                    ),
                }
            ]

            guarded = o._guard_worker_capability(decision, [task], context)

            self.assertEqual(guarded["decision"], "HUMAN_GATE")
            self.assertEqual(guarded["lane"], "Content")
            self.assertIsNone(guarded["worker_tier"])
            self.assertIn("fresh/current", guarded["reason"])
            self.assertEqual(
                o.state.data["metrics"].get("external_research_capability_gates"),
                1,
            )

    def test_duplicate_task_authority_for_same_issue_is_suppressed_while_pending(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            first = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=431,
                source_id="task-first",
                signal_kind="task",
                signal_text="AUDIT — NEW CONTENT TASK",
            )
            duplicate = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=431,
                source_id="task-retry",
                signal_kind="task",
                signal_text="AUDIT — NEW CONTENT TASK (retry)",
            )

            o._persist_pending_events([first])
            o._persist_pending_events([duplicate])

            self.assertEqual(o._pending_events(), [first])
            self.assertIn(
                orch._event_key(duplicate),
                o.state.data.get("completed_authority_event_keys") or [],
            )
            self.assertEqual(
                o.state.data["metrics"].get("duplicate_task_authorities_suppressed"),
                1,
            )

    def test_completed_task_coalesces_same_issue_authority_queued_behind_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            first = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=431,
                source_id="task-owned",
                signal_kind="task",
                signal_text="AUDIT — NEW CONTENT TASK",
            )
            duplicate = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=431,
                source_id="task-later",
                signal_kind="task",
                signal_text="AUDIT — NEW CONTENT TASK retry",
            )
            other = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=500,
                source_id="task-other",
                signal_kind="task",
                signal_text="AUDIT — NEW IMPLEMENTATION TASK",
            )
            # Seed the pre-AUDIT-0031 race shape directly: duplicate authority arrived behind
            # the already-owned first task before same-issue suppression existed.
            o.state.data["pending_events"] = [
                first.to_state(),
                duplicate.to_state(),
                other.to_state(),
            ]
            o.state.save()
            o._cache_decision(
                {"decision": "DISPATCH", "lane": "Content", "pr_number": None},
                [first],
                {"main": "main-head", "open_prs": []},
            )

            o._clear_completed_decision()

            self.assertEqual(o._pending_events(), [other])
            self.assertIn(
                orch._event_key(duplicate),
                o.state.data.get("completed_authority_event_keys") or [],
            )
            if o._timer is not None:
                o._timer.cancel()

    def test_task_authority_outranks_historical_manual_wake(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            historical_manual = orch.EventDecision(
                True,
                "manual orchestration command",
                "issue_comment",
                action="manual_command",
                pr_number=349,
                source_id="old-manual",
            )
            task = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=431,
                source_id="new-task",
                signal_kind="task",
                signal_text="AUDIT — NEW CONTENT TASK",
            )

            self.assertEqual(
                o._select_dispatch_batch([historical_manual, task]),
                [task],
            )

    def test_multiple_task_authority_signals_remain_fifo(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            first = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=431,
                source_id="task-1",
                signal_kind="task",
                signal_text="AUDIT — NEW CONTENT TASK",
            )
            second = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=500,
                source_id="task-2",
                signal_kind="task",
                signal_text="AUDIT — NEW IMPLEMENTATION TASK",
            )
            manual = orch.EventDecision(
                True,
                "manual orchestration command",
                "issue_comment",
                action="manual_command",
                pr_number=349,
                source_id="old-manual",
            )

            self.assertEqual(
                o._select_dispatch_batch([manual, first, second]),
                [first],
            )

    def test_dispatch_batch_isolates_trusted_task_from_repository_noise(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            audit_pr_event = orch.EventDecision(
                True,
                "workflow completed; controller will require head quiescence",
                "workflow_run",
                action="completed",
                head_sha="audit-pr-432-head",
                pr_number=432,
            )
            content_task = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=431,
                source_id="5603650534",
                signal_kind="audit",
                signal_text=(
                    "AUDIT — NEW CONTENT TASK\n"
                    "Route issue #431 to Content. DISPATCH with pr_number=null."
                ),
            )

            selected = o._select_dispatch_batch([audit_pr_event, content_task])

            self.assertEqual(selected, [content_task])

    def test_dispatch_batch_processes_one_authority_directive_at_a_time(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            first = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=431,
                source_id="first-task",
                signal_kind="audit",
                signal_text="AUDIT — NEW CONTENT TASK",
            )
            second = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=500,
                source_id="second-task",
                signal_kind="audit",
                signal_text="AUDIT — NEW IMPLEMENTATION TASK",
            )

            self.assertEqual(o._select_dispatch_batch([first, second]), [first])

    def test_completed_isolated_authority_batch_preserves_unrelated_task_and_noise(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            audit_pr_event = orch.EventDecision(
                True,
                "workflow completed; controller will require head quiescence",
                "workflow_run",
                action="completed",
                head_sha="audit-pr-432-head",
                pr_number=432,
            )
            content_task = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=431,
                source_id="content-task",
                signal_kind="audit",
                signal_text="AUDIT — NEW CONTENT TASK",
            )
            later_task = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=500,
                source_id="later-task",
                signal_kind="audit",
                signal_text="AUDIT — NEW IMPLEMENTATION TASK",
            )
            o._persist_pending_events([audit_pr_event, content_task, later_task])
            selected = o._select_dispatch_batch(o._pending_events())
            self.assertEqual(selected, [content_task])

            o._cache_decision(
                {"decision": "NOOP", "lane": "Content", "reason": "test completion"},
                selected,
                {"main": "main-head", "open_prs": []},
            )
            o._clear_completed_decision()

            remaining = o._pending_events()
            self.assertIn(audit_pr_event, remaining)
            self.assertIn(later_task, remaining)
            self.assertNotIn(content_task, remaining)
            if o._timer is not None:
                o._timer.cancel()

    def test_superseded_history_retirement_preserves_trusted_authority(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            signal = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=349,
                source_id="loop-risk",
                signal_kind="loop_risk",
                signal_text="AUDIT — LOOP RISK",
            )
            captured = [
                orch.EventDecision(
                    True,
                    "PR lifecycle changed",
                    "pull_request",
                    action="closed",
                    head_sha="merged-421-head",
                    pr_number=421,
                ),
                orch.EventDecision(
                    True,
                    "workflow completed",
                    "workflow_run",
                    action="completed",
                    head_sha="merged-421-head",
                    pr_number=421,
                ),
                orch.EventDecision(True, "main advanced", "push", head_sha="old-main"),
                signal,
            ]
            later = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=349,
                source_id="later-signal",
                signal_kind="audit",
                signal_text="AUDIT later authority",
            )
            o._persist_pending_events(captured)
            o._persist_pending_events([later])

            normalized = o._normalize_captured_events_for_snapshot(
                captured,
                {
                    "main": "current-main",
                    "open_prs": [{
                        "number": 358,
                        "headRefOid": "open-358-head",
                    }],
                },
            )

            self.assertIn(signal, normalized)
            self.assertFalse(any(event.pr_number == 421 for event in normalized))
            self.assertTrue(
                any(
                    event.event == "reconcile"
                    and event.action == "superseded_history"
                    and event.head_sha == "current-main"
                    for event in normalized
                )
            )
            pending = o._pending_events()
            self.assertIn(later, pending)
            self.assertIn(signal, pending)
            self.assertFalse(any(event.pr_number == 421 for event in pending))
            self.assertEqual(
                o.state.data["metrics"].get("superseded_pending_events_retired"),
                3,
            )

    def test_dispatch_target_guard_blocks_closed_or_merged_pr(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            decision = {
                "decision": "DISPATCH",
                "lane": "Audit",
                "pr_number": 421,
                "objective": "reconcile stale Audit history",
                "stop_boundary": "record current state",
                "worker_tier": "LUNA",
                "allowed_paths": ["docs/agent-state/AUDIT_STATE.md"],
                "reason": "retained history",
            }
            guarded = o._guard_dispatch_target(
                decision,
                {
                    "main": "current-main",
                    "open_prs": [{"number": 358, "headRefOid": "open-358-head"}],
                },
            )
            self.assertEqual(guarded["decision"], "HUMAN_GATE")
            self.assertEqual(guarded["pr_number"], 421)
            self.assertIn("not open", guarded["reason"])
            self.assertEqual(
                o.state.data["metrics"].get("stale_dispatch_target_gates"),
                1,
            )

            current = {**decision, "pr_number": 358}
            self.assertEqual(
                o._guard_dispatch_target(
                    current,
                    {
                        "main": "current-main",
                        "open_prs": [{"number": 358, "headRefOid": "open-358-head"}],
                    },
                ),
                current,
            )

    def test_cached_dispatch_missing_open_pr_identity_is_stale(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            record = {
                "decision": {
                    "decision": "DISPATCH",
                    "lane": "Audit",
                    "pr_number": 421,
                },
                "snapshot_main": "current-main",
                "source_pr_head": None,
            }
            with mock.patch.object(
                orch,
                "_run",
                return_value=mock.Mock(stdout="current-main\n"),
            ):
                self.assertFalse(o._cached_decision_still_current(record))

    def test_retired_superseded_event_cannot_reenter_queue(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            stale = orch.EventDecision(
                True,
                "PR lifecycle changed",
                "pull_request",
                action="closed",
                head_sha="merged-421-head",
                pr_number=421,
            )
            o._persist_pending_events([stale])
            o._normalize_captured_events_for_snapshot(
                [stale],
                {"main": "current-main", "open_prs": []},
            )
            o._persist_pending_events([stale])

            self.assertFalse(any(event.pr_number == 421 for event in o._pending_events()))
            self.assertGreaterEqual(
                o.state.data["metrics"].get("retired_event_replays_suppressed", 0),
                1,
            )
            self.assertTrue(o.state.data.get("retired_event_keys"))

    def test_completed_trusted_authority_cannot_reenter_queue(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            signal = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=349,
                source_id="authority-1",
                signal_kind="restart_recommended",
                signal_text="AUDIT — RESTART RECOMMENDED",
            )
            o._persist_pending_events([signal])
            o._cache_decision(
                {"decision": "NOOP", "reason": "resolved against current truth"},
                [signal],
                {"main": "current-main", "open_prs": []},
            )
            o._clear_completed_decision()
            self.assertEqual(o._pending_events(), [])

            o._persist_pending_events([signal])
            self.assertEqual(o._pending_events(), [])
            self.assertGreaterEqual(
                o.state.data["metrics"].get("completed_authority_replays_suppressed", 0),
                1,
            )
            self.assertTrue(o.state.data.get("completed_authority_event_keys"))

    def test_pending_events_purges_already_retired_physical_copy(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            stale = orch.EventDecision(
                True,
                "PR lifecycle changed",
                "pull_request",
                action="closed",
                head_sha="merged-421-head",
                pr_number=421,
            )
            payload = stale.to_state()
            key = orch._event_key(payload)
            o.state.data["pending_events"] = [payload]
            o.state.data["retired_event_keys"] = [key]
            o.state.save()

            self.assertEqual(o._pending_events(), [])
            self.assertEqual(o.state.data["pending_events"], [])
            self.assertEqual(
                o.state.data["metrics"].get("suppressed_pending_events_purged"),
                1,
            )
            self.assertEqual(
                o.state.data["last_suppressed_event_purge"]["after"],
                0,
            )

    def test_pending_events_purges_completed_authority_physical_copy(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            signal = orch.EventDecision(
                True,
                "Audit/watchdog orchestration signal",
                "issue_comment",
                action="audit_signal",
                pr_number=349,
                source_id="authority-physical-copy",
                signal_kind="restart_recommended",
                signal_text="AUDIT — RESTART RECOMMENDED",
            )
            payload = signal.to_state()
            key = orch._event_key(payload)
            o.state.data["pending_events"] = [payload]
            o.state.data["completed_authority_event_keys"] = [key]
            o.state.save()

            self.assertEqual(o._pending_events(), [])
            self.assertEqual(o.state.data["pending_events"], [])
            self.assertEqual(
                o.state.data["metrics"].get("suppressed_pending_events_purged"),
                1,
            )

    def test_queue_pressure_compacts_ordinary_history_to_reconcile(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            events = [
                orch.EventDecision(
                    True,
                    "PR lifecycle changed",
                    "pull_request",
                    action="closed",
                    head_sha=f"head-{index}",
                    pr_number=index,
                )
                for index in range(12)
            ]

            with mock.patch.object(orch, "_env_int", return_value=10):
                o._persist_pending_events(events)

            pending = o._pending_events()
            self.assertEqual(len(pending), 10)
            self.assertTrue(
                any(
                    event.event == "reconcile" and event.action == "queue_compaction"
                    for event in pending
                )
            )
            self.assertGreater(
                o.state.data["metrics"].get("pending_events_compacted", 0),
                0,
            )
            report = o.state.data["last_pending_event_compaction"]
            self.assertEqual(report["before"], 12)
            self.assertEqual(report["after"], 10)
            self.assertTrue(report["reconcile_inserted"])

    def test_queue_pressure_never_drops_trusted_signals(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            ordinary = [
                orch.EventDecision(
                    True,
                    "PR lifecycle changed",
                    "pull_request",
                    action="closed",
                    head_sha=f"head-{index}",
                    pr_number=index,
                )
                for index in range(12)
            ]
            signals = [
                orch.EventDecision(
                    True,
                    "Audit/watchdog orchestration signal",
                    "issue_comment",
                    action="audit_signal",
                    pr_number=349,
                    source_id=f"signal-{index}",
                    signal_kind="restart_recommended",
                    signal_text=f"AUDIT restart {index}",
                )
                for index in range(2)
            ]

            with mock.patch.object(orch, "_env_int", return_value=10):
                o._persist_pending_events(ordinary + signals)

            pending = o._pending_events()
            self.assertEqual(len(pending), 10)
            source_ids = {event.source_id for event in pending}
            self.assertIn("signal-0", source_ids)
            self.assertIn("signal-1", source_ids)
            self.assertTrue(
                any(
                    event.event == "reconcile" and event.action == "queue_compaction"
                    for event in pending
                )
            )

    def test_queue_pressure_never_drops_owned_event_keys(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            owned = [
                orch.EventDecision(
                    True,
                    "PR lifecycle changed",
                    "pull_request",
                    action="closed",
                    head_sha=f"owned-{index}",
                    pr_number=index,
                )
                for index in range(2)
            ]
            o._persist_pending_events(owned)
            o._cache_decision(
                {"decision": "NOOP", "reason": "owned batch"},
                owned,
                {"main": "mainhead", "open_prs": []},
            )
            later = [
                orch.EventDecision(
                    True,
                    "PR lifecycle changed",
                    "pull_request",
                    action="closed",
                    head_sha=f"later-{index}",
                    pr_number=100 + index,
                )
                for index in range(12)
            ]

            with mock.patch.object(orch, "_env_int", return_value=10):
                o._persist_pending_events(later)

            pending_keys = {orch._event_key(event) for event in o._pending_events()}
            owned_keys = set(o._decision_record()["event_keys"])
            self.assertTrue(owned_keys.issubset(pending_keys))
            self.assertEqual(len(o._pending_events()), 10)
            self.assertTrue(
                any(
                    event.event == "reconcile" and event.action == "queue_compaction"
                    for event in o._pending_events()
                )
            )

    def test_priority_authority_may_exceed_soft_queue_cap_without_loss(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            signals = [
                orch.EventDecision(
                    True,
                    "Audit/watchdog orchestration signal",
                    "issue_comment",
                    action="audit_signal",
                    pr_number=349,
                    source_id=f"signal-{index}",
                    signal_kind="restart_recommended",
                    signal_text=f"AUDIT restart {index}",
                )
                for index in range(11)
            ]

            with mock.patch.object(orch, "_env_int", return_value=10):
                o._persist_pending_events(signals)

            self.assertEqual(len(o._pending_events()), 11)
            self.assertEqual(
                {event.source_id for event in o._pending_events()},
                {f"signal-{index}" for index in range(11)},
            )
            self.assertEqual(
                o.state.data["metrics"].get("pending_event_protected_overflow"),
                1,
            )

    def test_new_event_queues_behind_cached_terminal_decision(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            first = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([first])
            o._cache_decision(
                {"decision": "NOOP", "reason": "idle"},
                [first],
                {"main": "abc123", "open_prs": []},
            )
            self.assertIsNotNone(o._decision_record())

            second = orch.EventDecision(True, "main advanced", "push", head_sha="def456")
            o._persist_pending_events([second])

            record = o._decision_record()
            self.assertIsNotNone(record)
            self.assertEqual(record["event_keys"], [orch._event_key(first)])
            self.assertEqual(
                o.state.data["metrics"].get("events_queued_behind_cached_decision"),
                1,
            )

            with mock.patch.object(o, "_schedule_pending"):
                o._clear_completed_decision()

            self.assertEqual(o._pending_events(), [second])

    def test_cached_dispatch_revalidates_main_and_source_pr_identity(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(
                True,
                "workflow completed",
                "workflow_run",
                action="completed",
                head_sha="prhead",
                pr_number=77,
            )
            o._persist_pending_events([event])
            o._cache_decision(
                {
                    "decision": "DISPATCH",
                    "lane": "Audit",
                    "pr_number": 77,
                    "reason": "bounded work",
                },
                [event],
                {
                    "main": "mainhead",
                    "open_prs": [{"number": 77, "headRefOid": "prhead"}],
                },
            )
            record = o._decision_record()

            def same_run(args, **kwargs):
                if args[:3] == ["git", "rev-parse", "HEAD"]:
                    return orch.subprocess.CompletedProcess(args, 0, stdout="mainhead\n", stderr="")
                return orch.subprocess.CompletedProcess(args, 0, stdout="", stderr="")

            with mock.patch.object(orch, "_run", side_effect=same_run), \
                    mock.patch.object(
                        orch,
                        "_json_cmd",
                        return_value={"state": "OPEN", "headRefOid": "prhead"},
                    ):
                self.assertTrue(o._cached_decision_still_current(record))

            def moved_main(args, **kwargs):
                if args[:3] == ["git", "rev-parse", "HEAD"]:
                    return orch.subprocess.CompletedProcess(args, 0, stdout="newmain\n", stderr="")
                return orch.subprocess.CompletedProcess(args, 0, stdout="", stderr="")

            with mock.patch.object(orch, "_run", side_effect=moved_main):
                self.assertFalse(o._cached_decision_still_current(record))

            with mock.patch.object(orch, "_run", side_effect=same_run), \
                    mock.patch.object(
                        orch,
                        "_json_cmd",
                        return_value={"state": "OPEN", "headRefOid": "new-pr-head"},
                    ):
                self.assertFalse(o._cached_decision_still_current(record))

    def test_cached_terminal_decision_never_reclassifies_only_because_new_events_arrived(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            first = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            second = orch.EventDecision(
                True,
                "workflow completed",
                "workflow_run",
                action="completed",
                head_sha="abc123",
            )
            o._persist_pending_events([first])
            o._cache_decision(
                {"decision": "NOOP", "reason": "already at human gate"},
                [first],
                {"main": "abc123", "open_prs": []},
            )
            o._persist_pending_events([second])

            with mock.patch.object(o, "workflows_quiescent", return_value=True), \
                    mock.patch.object(o, "sync_main"), \
                    mock.patch.object(o, "_codex_classifier") as classifier, \
                    mock.patch.object(o, "_schedule_pending"):
                o.dispatch(o._pending_events())

            classifier.assert_not_called()
            self.assertEqual(o._pending_events(), [second])

    def test_all_represented_heads_must_be_quiescent_before_classification(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event])

            with mock.patch.object(o, "workflows_quiescent", return_value=False) as quiescent, \
                    mock.patch.object(o, "_schedule_pending") as schedule, \
                    mock.patch.object(o, "sync_main") as sync_main, \
                    mock.patch.object(o, "_codex_classifier") as classifier:
                o.dispatch(o._pending_events())

            quiescent.assert_called_once_with("abc123")
            schedule.assert_called_once()
            sync_main.assert_not_called()
            classifier.assert_not_called()

    def test_audit_worker_may_update_only_explicitly_scoped_audit_state(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            audit_state = "docs/agent-state/AUDIT_STATE.md"
            protocol = "docs/agent-state/ORCHESTRATION_PROTOCOL.md"

            self.assertFalse(
                o._worker_path_forbidden(
                    audit_state,
                    lane="Audit",
                    allowed_paths=[audit_state],
                )
            )
            self.assertTrue(
                o._worker_path_forbidden(
                    audit_state,
                    lane="Implementation",
                    allowed_paths=[audit_state],
                )
            )
            self.assertTrue(
                o._worker_path_forbidden(
                    audit_state,
                    lane="Audit",
                    allowed_paths=None,
                )
            )
            self.assertTrue(
                o._worker_path_forbidden(
                    audit_state,
                    lane="Audit",
                    allowed_paths=["docs/agent-state/**"],
                )
            )
            self.assertTrue(
                o._worker_path_forbidden(
                    protocol,
                    lane="Audit",
                    allowed_paths=[protocol],
                )
            )

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

    def test_multi_event_no_change_schedules_one_bounded_followup(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.runtime_head = "abc123"
            first = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            second = orch.EventDecision(
                True,
                "workflow completed",
                "workflow_run",
                action="completed",
                head_sha="abc123",
            )

            with mock.patch.object(o, "_schedule_pending"):
                self.assertTrue(o._schedule_no_change_followup([first, second]))
                pending = o._pending_events()
                self.assertEqual(len(pending), 1)
                self.assertEqual(pending[0].event, "reconcile")
                self.assertEqual(pending[0].action, "no_change_followup")
                self.assertEqual(pending[0].head_sha, "abc123")
                self.assertEqual(
                    o.state.data["metrics"].get("no_change_followup_reconciliations"),
                    1,
                )

                # A retained follow-up suppresses another synthetic wake.
                self.assertFalse(o._schedule_no_change_followup([first, second]))
                # A single-event batch can never recursively create a follow-up.
                o.state.data["pending_events"] = []
                o.state.save()
                self.assertFalse(o._schedule_no_change_followup([first]))

    def test_no_change_handoff_reports_no_repository_handoff(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))

            def fake_run(args, **kwargs):
                if args[:3] == ["git", "rev-list", "--count"]:
                    return orch.subprocess.CompletedProcess(args, 0, stdout="0\n", stderr="")
                return orch.subprocess.CompletedProcess(args, 0, stdout="", stderr="")

            with mock.patch.object(o, "_changed_paths", return_value=[]), \
                    mock.patch.object(orch, "_run", side_effect=fake_run):
                created = o._handoff_changes(
                    "Audit",
                    "already satisfied",
                    "codex/audit-test",
                    None,
                    "done",
                )

            self.assertFalse(created)
            self.assertEqual(o.state.data["metrics"].get("worker_no_change"), 1)

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

    def test_waiting_dispatch_does_not_snapshot_pending_events_before_lock(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event])

            pending_read = threading.Event()
            original_pending = o._pending_events

            def observed_pending():
                value = original_pending()
                pending_read.set()
                return value

            o._dispatch_lock.acquire()
            try:
                with mock.patch.object(o, "_pending_events", side_effect=observed_pending), \
                        mock.patch.object(o, "dispatch") as dispatch:
                    worker = threading.Thread(target=o._drain_and_dispatch)
                    worker.start()

                    # A callback waiting on the dispatch lock must not capture a stale queue snapshot.
                    self.assertFalse(pending_read.wait(0.05))

                    with o._state_lock:
                        o.state.data["pending_events"] = []
                        o.state.save()
                    o._dispatch_lock.release()

                    worker.join(timeout=1)
                    self.assertFalse(worker.is_alive())
                    self.assertTrue(pending_read.is_set())
                    dispatch.assert_not_called()
            finally:
                if o._dispatch_lock.locked():
                    o._dispatch_lock.release()

    def test_authentication_block_surfaces_durable_operator_gate(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event])

            with mock.patch.object(
                o,
                "dispatch",
                side_effect=orch.RetryBlocked("authentication", 3600, "auth unavailable"),
            ), mock.patch.object(
                o,
                "_set_retry_block",
            ) as set_block, mock.patch.object(
                o,
                "_post_gate",
                return_value=True,
            ) as post_gate:
                o._drain_and_dispatch()

            set_block.assert_called_once_with("authentication", 3600, "auth unavailable")
            post_gate.assert_called_once()
            message = post_gate.call_args.args[0]["human_message"]
            self.assertIn("AUTHENTICATION BLOCK", message)
            self.assertEqual(o._pending_events(), [event])

    def test_classifier_failure_circuit_surfaces_operator_gate(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event])

            def fail_dispatch(_events):
                o.set_paused(True, actor="classifier-failure-circuit")
                raise orch.SafetyPause("classifier circuit open")

            with mock.patch.object(o, "dispatch", side_effect=fail_dispatch), \
                    mock.patch.object(o, "_post_gate", return_value=True) as post_gate:
                o._drain_and_dispatch()

            self.assertTrue(o.is_paused())
            post_gate.assert_called_once()
            self.assertIn(
                "consecutive-failure circuit breaker",
                post_gate.call_args.args[0]["human_message"],
            )
            self.assertEqual(o._pending_events(), [event])

    def test_non_classifier_safety_pause_does_not_duplicate_existing_gate(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event])

            def fail_dispatch(_events):
                o.set_paused(True, actor="controller-safety")
                raise orch.SafetyPause("worker scope pause already surfaced")

            with mock.patch.object(o, "dispatch", side_effect=fail_dispatch), \
                    mock.patch.object(o, "_post_gate") as post_gate:
                o._drain_and_dispatch()

            post_gate.assert_not_called()
            self.assertTrue(o.is_paused())

    def test_restart_preserves_cached_nonworker_decision(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event])
            o._cache_decision({"decision": "NOOP", "reason": "owned batch"}, [event])
            before = o._decision_record()
            self.assertIsNotNone(before)

            o.resume_pending()
            try:
                self.assertEqual(o._decision_record(), before)
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

    def test_existing_controller_gate_after_current_pr_head_is_seeded_and_suppressed(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            decision = {
                "decision": "HUMAN_GATE",
                "lane": "Implementation",
                "pr_number": 358,
                "human_message": "Review the morphology gate.",
            }
            pr_info = {
                "headRefOid": "abc123",
                "state": "OPEN",
                "commits": [
                    {"committedDate": "2026-09-08T15:00:00Z"},
                ],
                "comments": [
                    {
                        "body": "[skyforge-orchestrator] HUMAN_GATE\n\nAlready surfaced.",
                        "createdAt": "2026-09-08T16:00:00Z",
                    }
                ],
            }

            with mock.patch.object(orch, "_json_cmd", return_value=pr_info), \
                    mock.patch.object(orch, "_run") as run:
                o._post_gate(decision)

            run.assert_not_called()
            record = o.state.data["human_gate_records"]["pr:358:implementation"]
            self.assertEqual(record["token"], "abc123:OPEN")
            self.assertTrue(record["seeded_from_github"])
            self.assertEqual(
                o.state.data["metrics"].get("human_gate_duplicates_suppressed"),
                1,
            )

    def test_same_pr_head_human_gate_is_suppressed_after_first_post(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            decision = {
                "decision": "HUMAN_GATE",
                "lane": "Implementation",
                "pr_number": 358,
                "human_message": "Review the morphology gate.",
            }
            pr_info = {
                "headRefOid": "abc123",
                "state": "OPEN",
                "commits": [
                    {"committedDate": "2026-09-08T15:00:00Z"},
                ],
                "comments": [],
            }

            with mock.patch.object(orch, "_json_cmd", return_value=pr_info), \
                    mock.patch.object(
                        orch,
                        "_run",
                        return_value=orch.subprocess.CompletedProcess([], 0, stdout="", stderr=""),
                    ) as run:
                o._post_gate(decision)
                o._post_gate({**decision, "human_message": "Please review that same gate."})

            self.assertEqual(run.call_count, 1)
            self.assertEqual(o.state.data["metrics"].get("human_gates"), 1)
            self.assertEqual(
                o.state.data["metrics"].get("human_gate_duplicates_suppressed"),
                1,
            )

    def test_new_pr_head_resurfaces_human_gate(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            decision = {
                "decision": "HUMAN_GATE",
                "lane": "Implementation",
                "pr_number": 358,
                "human_message": "Review the morphology gate.",
            }
            first = {
                "headRefOid": "abc123",
                "state": "OPEN",
                "commits": [{"committedDate": "2026-09-08T15:00:00Z"}],
                "comments": [],
            }
            second = {
                "headRefOid": "def456",
                "state": "OPEN",
                "commits": [{"committedDate": "2026-09-08T17:00:00Z"}],
                "comments": [],
            }

            with mock.patch.object(orch, "_json_cmd", side_effect=[first, second]), \
                    mock.patch.object(
                        orch,
                        "_run",
                        return_value=orch.subprocess.CompletedProcess([], 0, stdout="", stderr=""),
                    ) as run:
                o._post_gate(decision)
                o._post_gate(decision)

            self.assertEqual(run.call_count, 2)
            self.assertEqual(
                o.state.data["human_gate_records"]["pr:358:implementation"]["token"],
                "def456:OPEN",
            )
            self.assertEqual(o.state.data["metrics"].get("human_gates"), 2)

    def test_human_gate_post_failure_preserves_owned_decision(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event])
            o._cache_decision(
                {
                    "decision": "HUMAN_GATE",
                    "lane": "Audit",
                    "human_message": "Review required.",
                },
                [event],
                {"main": "abc123", "open_prs": []},
            )

            with mock.patch.object(o, "sync_main"), \
                    mock.patch.object(o, "_post_gate", return_value=False):
                with self.assertRaises(orch.RetryBlocked):
                    o.dispatch(o._pending_events())

            self.assertIsNotNone(o._decision_record())
            self.assertEqual(o._pending_events(), [event])

    def test_merge_with_auto_merge_off_surfaces_manual_gate(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "workflow completed", "workflow_run", head_sha="abc123")
            o._persist_pending_events([event])
            with o._state_lock:
                o.state.data["managed"] = {
                    "Audit": {"branch": "codex/audit-test", "pr_number": 77}
                }
                o.state.save()
            o._cache_decision(
                {
                    "decision": "MERGE",
                    "lane": "Audit",
                    "pr_number": 77,
                    "reason": "machine green",
                },
                [event],
                {"main": "abc123", "open_prs": []},
            )

            with mock.patch.object(o, "sync_main"), \
                    mock.patch.object(o, "_post_gate", return_value=True) as post_gate, \
                    mock.patch.object(o, "_merge_managed") as merge_managed:
                o.dispatch(o._pending_events())

            merge_managed.assert_not_called()
            post_gate.assert_called_once()
            message = post_gate.call_args.args[0]["human_message"]
            self.assertIn("auto-merge remains disabled", message)
            self.assertIsNone(o._decision_record())
            self.assertEqual(o._pending_events(), [])
            self.assertEqual(o.state.data["metrics"].get("manual_merge_gates"), 1)

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

    def test_operator_budget_reset_requires_pause_and_preserves_queue(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event])
            o.state.data["classifier_calls_today"] = 22
            o.state.data["luna_worker_calls_today"] = 1
            o.state.data["worker_calls_today"] = 2
            o.state.data["blocked_kind"] = "local_budget"
            o.state.data["blocked_until_epoch"] = 9999999999.0
            o.state.data["blocked_reason"] = "local budget exhausted"
            o.state.save()

            with self.assertRaisesRegex(RuntimeError, "requires the controller to be paused"):
                o.reset_local_budget(actor="ni-da-ba")

            o.set_paused(True, actor="ni-da-ba")
            o.reset_local_budget(actor="ni-da-ba")

            self.assertEqual(o.state.data["classifier_calls_today"], 0)
            self.assertEqual(o.state.data["luna_worker_calls_today"], 0)
            self.assertEqual(o.state.data["worker_calls_today"], 0)
            self.assertIsNone(o.state.data["blocked_kind"])
            self.assertEqual(o.state.data["blocked_until_epoch"], 0.0)
            self.assertEqual(o._pending_events(), [event])
            self.assertTrue(o.is_paused())
            self.assertEqual(o.state.data["last_budget_reset_by"], "ni-da-ba")
            self.assertIsNotNone(o.state.data["last_budget_reset_at"])
            self.assertEqual(o.state.data["metrics"].get("operator_budget_resets"), 1)

    def test_operator_runtime_refresh_is_paused_only_and_schedules_sync(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            with self.assertRaisesRegex(RuntimeError, "requires the controller to be paused"):
                o.refresh_runtime(actor="ni-da-ba")

            o.set_paused(True, actor="ni-da-ba")
            fake_timer = mock.Mock()
            with mock.patch.object(o, "_worktree_clean", return_value=True), \
                    mock.patch.object(orch.threading, "Timer", return_value=fake_timer):
                o.refresh_runtime(actor="ni-da-ba")

            self.assertTrue(fake_timer.daemon)
            fake_timer.start.assert_called_once()
            self.assertEqual(
                o.state.data["last_runtime_refresh_request"]["requested_by"],
                "ni-da-ba",
            )

    def test_operator_worker_discard_preserves_decision_and_events(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            o = self.make_orchestrator(root)
            o.set_paused(True, actor="ni-da-ba")
            event = orch.EventDecision(True, "main advanced", "push", head_sha="abc123")
            o._persist_pending_events([event])
            o._cache_decision(
                {
                    "decision": "DISPATCH",
                    "lane": "Audit",
                    "objective": "reconcile state",
                    "stop_boundary": "state only",
                },
                [event],
                {"main": "abc123", "open_prs": []},
            )
            worktree = root / ".skyforge-orchestrator" / "worktrees" / "codex-audit"
            worktree.mkdir(parents=True)
            o.state.data["pending_worker"] = {
                "lane": "Audit",
                "branch": "codex/audit-test",
                "worktree": str(worktree),
                "managed_pr": None,
                "stage": "handoff",
            }
            o.state.save()

            def fake_run(args, **kwargs):
                if args[:3] == ["git", "rev-list", "--count"]:
                    return orch.subprocess.CompletedProcess(args, 0, stdout="0\n", stderr="")
                return orch.subprocess.CompletedProcess(args, 0, stdout="", stderr="")

            with mock.patch.object(
                o,
                "_changed_paths",
                return_value=["docs/agent-state/AUDIT_STATE.md"],
            ), mock.patch.object(orch, "_run", side_effect=fake_run):
                o.discard_pending_worker(actor="ni-da-ba")

            self.assertIsNone(o.state.data["pending_worker"])
            self.assertIsNotNone(o._decision_record())
            self.assertEqual(o._pending_events(), [event])
            self.assertEqual(o.state.data["last_worker_discard"]["discarded_by"], "ni-da-ba")

    def test_operator_budget_reset_rejects_pending_worker(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.set_paused(True, actor="ni-da-ba")
            o.state.data["pending_worker"] = {"branch": "codex/audit-test"}
            o.state.save()
            with self.assertRaisesRegex(RuntimeError, "forbidden while a worker is pending"):
                o.reset_local_budget(actor="ni-da-ba")

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


class ControllerSelfRefreshTests(unittest.TestCase):
    def make_orchestrator(self, root: pathlib.Path):
        return orch.Orchestrator(
            root,
            repo="ni-da-ba/skyforge",
            debounce_seconds=999,
            min_dispatch_seconds=0,
            max_parent_turns=24,
            auto_merge=False,
        )

    @staticmethod
    def completed(args, stdout=""):
        return orch.subprocess.CompletedProcess(args, 0, stdout=stdout, stderr="")

    def test_sync_main_requests_restart_only_when_controller_python_changed(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.runtime_head = "oldhead"

            def fake_run(args, **kwargs):
                if args[:3] == ["git", "rev-parse", "HEAD"]:
                    return self.completed(args, "newhead\n")
                if args[:3] == ["git", "diff", "--name-only"]:
                    return self.completed(
                        args,
                        "scripts/orchestrator/skyforge_orchestrator.py\n"
                        "docs/agent-state/AUDIT_STATE.md\n",
                    )
                return self.completed(args)

            with mock.patch.object(o, "_worktree_clean", return_value=True), \
                    mock.patch.object(orch, "_run", side_effect=fake_run), \
                    mock.patch.object(o, "_request_runtime_restart") as restart:
                o.sync_main()

            restart.assert_called_once_with(
                "oldhead",
                "newhead",
                ["scripts/orchestrator/skyforge_orchestrator.py"],
            )
            self.assertEqual(o.runtime_head, "newhead")

    def test_sync_main_does_not_restart_for_orchestrator_docs_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            o.runtime_head = "oldhead"

            def fake_run(args, **kwargs):
                if args[:3] == ["git", "rev-parse", "HEAD"]:
                    return self.completed(args, "newhead\n")
                if args[:3] == ["git", "diff", "--name-only"]:
                    return self.completed(
                        args,
                        "scripts/orchestrator/test_skyforge_orchestrator.py\n"
                        "scripts/orchestrator/README.md\n",
                    )
                return self.completed(args)

            with mock.patch.object(o, "_worktree_clean", return_value=True), \
                    mock.patch.object(orch, "_run", side_effect=fake_run), \
                    mock.patch.object(o, "_request_runtime_restart") as restart:
                o.sync_main()

            restart.assert_not_called()
            self.assertEqual(o.runtime_head, "newhead")

    def test_runtime_restart_request_is_durable_before_exit(self):
        with tempfile.TemporaryDirectory() as tmp:
            o = self.make_orchestrator(pathlib.Path(tmp))
            with mock.patch.object(orch.os, "_exit") as exit_process:
                o._request_runtime_restart(
                    "oldhead",
                    "newhead",
                    ["scripts/orchestrator/skyforge_orchestrator.py"],
                )

            exit_process.assert_called_once_with(75)
            request = o.state.data["runtime_restart_requested"]
            self.assertEqual(request["from_head"], "oldhead")
            self.assertEqual(request["to_head"], "newhead")
            self.assertEqual(
                request["changed_paths"],
                ["scripts/orchestrator/skyforge_orchestrator.py"],
            )
            self.assertEqual(
                o.state.data["metrics"].get("runtime_restarts_requested"),
                1,
            )


class WorkerWorktreeIsolationTests(unittest.TestCase):
    def make_repository(self, base: pathlib.Path) -> pathlib.Path:
        origin = base / "origin.git"
        root = base / "repo"
        root.mkdir()
        orch._run(["git", "init", "--bare", str(origin)], cwd=base)
        orch._run(["git", "init", "-b", "main"], cwd=root)
        orch._run(["git", "config", "user.name", "Skyforge Test"], cwd=root)
        orch._run(["git", "config", "user.email", "skyforge-test@example.invalid"], cwd=root)
        (root / ".gitignore").write_text(".skyforge-orchestrator/\n")
        (root / "README.md").write_text("test\n")
        orch._run(["git", "add", ".gitignore", "README.md"], cwd=root)
        orch._run(["git", "commit", "-m", "initial"], cwd=root)
        orch._run(["git", "remote", "add", "origin", str(origin)], cwd=root)
        orch._run(["git", "push", "-u", "origin", "main"], cwd=root)
        return root

    def make_orchestrator(self, root: pathlib.Path):
        return orch.Orchestrator(
            root,
            repo="ni-da-ba/skyforge",
            debounce_seconds=999,
            min_dispatch_seconds=0,
            max_parent_turns=24,
            auto_merge=False,
        )

    def test_closed_managed_pr_record_is_retired_before_worker_reuse(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_repository(pathlib.Path(tmp))
            o = self.make_orchestrator(root)
            o.state.data["managed"] = {
                "Content": {
                    "branch": "codex/content-old",
                    "pr_number": 77,
                }
            }
            o.state.save()

            with mock.patch.object(
                orch,
                "_json_cmd",
                return_value={"state": "MERGED", "headRefName": "codex/content-old"},
            ):
                managed = o._validated_managed_branch("Content")

            self.assertIsNone(managed)
            self.assertNotIn("Content", o.state.data["managed"])
            self.assertEqual(
                o.state.data["metrics"].get("stale_managed_records_retired"),
                1,
            )

    def test_open_managed_pr_record_remains_reusable(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_repository(pathlib.Path(tmp))
            o = self.make_orchestrator(root)
            record = {
                "branch": "codex/content-live",
                "pr_number": 77,
            }
            o.state.data["managed"] = {"Content": dict(record)}
            o.state.save()

            with mock.patch.object(
                orch,
                "_json_cmd",
                return_value={"state": "OPEN", "headRefName": "codex/content-live"},
            ):
                managed = o._validated_managed_branch("Content")

            self.assertEqual(managed, record)
            self.assertEqual(o.state.data["managed"]["Content"], record)
            self.assertEqual(
                o.state.data["metrics"].get("stale_managed_records_retired", 0),
                0,
            )

    def test_prepare_worker_keeps_controller_checkout_on_main(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_repository(pathlib.Path(tmp))
            o = self.make_orchestrator(root)

            branch, managed_pr, worktree = o._prepare_worker_branch("Content", None)
            try:
                self.assertIsNone(managed_pr)
                self.assertNotEqual(worktree.resolve(), root.resolve())
                self.assertEqual(o._current_branch(), "main")
                self.assertEqual(o._current_branch(worktree), branch)
                self.assertTrue(o._worktree_clean())
                self.assertTrue(o._worktree_clean(worktree))
            finally:
                o._retire_worker_worktree(worktree)

    def test_pending_worker_records_isolated_worktree_for_restart(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_repository(pathlib.Path(tmp))
            o = self.make_orchestrator(root)

            branch, managed_pr, worktree = o._resume_or_prepare_worker(
                "Content",
                None,
                "bounded test objective",
                "TERRA",
                None,
            )
            try:
                pending = o.state.data["pending_worker"]
                self.assertEqual(pending["branch"], branch)
                self.assertEqual(pending["managed_pr"], managed_pr)
                self.assertEqual(pathlib.Path(pending["worktree"]).resolve(), worktree.resolve())

                reloaded = self.make_orchestrator(root)
                resumed_branch, resumed_pr, resumed_worktree = reloaded._resume_or_prepare_worker(
                    "Content",
                    None,
                    "bounded test objective",
                    "TERRA",
                    None,
                )
                self.assertEqual(resumed_branch, branch)
                self.assertEqual(resumed_pr, managed_pr)
                self.assertEqual(resumed_worktree.resolve(), worktree.resolve())
                self.assertEqual(reloaded._current_branch(), "main")
            finally:
                o.state.data["pending_worker"] = None
                o.state.save()
                o._retire_worker_worktree(worktree)

    def test_orphaned_dirty_worktree_is_not_reused_without_pending_ownership(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_repository(pathlib.Path(tmp))
            o = self.make_orchestrator(root)
            branch, _, worktree = o._prepare_worker_branch("Content", None)
            orphan = worktree / "orphan.txt"
            orphan.write_text("partial work\n")

            try:
                with self.assertRaisesRegex(RuntimeError, "Orphaned worker worktree is dirty"):
                    o._ensure_worker_worktree(branch, "origin/main")
                self.assertEqual(o._current_branch(), "main")
                self.assertTrue(o._worktree_clean())
            finally:
                orch._run(["git", "reset", "--hard"], cwd=worktree)
                orch._run(["git", "clean", "-fd"], cwd=worktree)
                o._retire_worker_worktree(worktree)

    def test_protected_worker_change_safety_pauses_without_dirtying_controller_root(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.make_repository(pathlib.Path(tmp))
            o = self.make_orchestrator(root)
            branch, _, worktree = o._prepare_worker_branch("Content", None)
            bad = worktree / ".github" / "workflows" / "unsafe.yml"
            bad.parent.mkdir(parents=True)
            bad.write_text("name: unsafe\n")

            try:
                with mock.patch.object(o, "_post_gate") as post_gate:
                    with self.assertRaises(orch.SafetyPause):
                        o._handoff_changes(
                            "Content",
                            "bounded test objective",
                            branch,
                            None,
                            "worker summary",
                            None,
                            worktree,
                        )

                self.assertTrue(o.is_paused())
                self.assertEqual(o._current_branch(), "main")
                self.assertTrue(o._worktree_clean())
                self.assertFalse(o._worktree_clean(worktree))
                post_gate.assert_called_once()
            finally:
                orch._run(["git", "reset", "--hard"], cwd=worktree)
                orch._run(["git", "clean", "-fd"], cwd=worktree)
                o._retire_worker_worktree(worktree)


if __name__ == "__main__":
    unittest.main()
