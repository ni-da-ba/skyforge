from __future__ import annotations

import hashlib
import hmac
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import platform_v2_hosted_runtime as hosted
from test_platform_v2_hosted_runtime import SECRET, write_legacy
from test_platform_v2_hosted_task_preflight import (
    BODY,
    LiveTruthRunner,
    signed,
    task_payload,
)
from v2.external import ExternalProducerClaim
from v2.hosted_task_plan import (
    HostedTaskPlanDisposition,
    HostedTaskPlanLedger,
    HostedTaskPlanStatus,
    HostedTaskPlanStore,
    advance_claimed_task_preflight,
    claim_next_protected_task,
)
from v2.task_event_composition import TaskAuthorityEventLedger, TaskAuthorityEventStore


def make_runtime(root: Path):
    write_legacy(root)
    return hosted.HostedV2Substrate(
        root,
        repo="ni-da-ba/skyforge",
        require_webhook_secret=True,
        startup_reconcile=True,
        webhook_secret=SECRET,
        trusted_actors=("ni-da-ba",),
    )


def capture_task(root: Path):
    app = make_runtime(root)
    raw, headers = signed(task_payload())
    status, response = app.handle_webhook(headers=headers, raw=raw)
    assert status == 202 and response["task_authority_recorded"]
    event = app.state.inbox.pending_events[0]
    authority = app.task_authority_store.load()
    return app, event, authority


class HostedTaskPlanClaimTest(unittest.TestCase):
    def test_exact_protected_task_claim_is_restart_safe(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, event, authority = capture_task(root)
            result = claim_next_protected_task(
                ledger=HostedTaskPlanLedger(),
                inbox=app.state.inbox,
                authority_events=authority,
            )
            self.assertEqual(result.disposition, HostedTaskPlanDisposition.CLAIMED)
            self.assertEqual(result.ledger.active.event_id, event.event_id)
            self.assertEqual(result.ledger.active.issue_number, 900)
            self.assertEqual(result.ledger.active.status, HostedTaskPlanStatus.CLAIMED)

            store = HostedTaskPlanStore.for_root(root)
            store.save(result.ledger)
            restarted = HostedTaskPlanStore.for_root(root).load()
            self.assertEqual(restarted, result.ledger)
            self.assertEqual(
                store.adapter.path.read_bytes(),
                store.adapter.backup_path.read_bytes(),
            )

    def test_existing_active_plan_prevents_second_claim(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, _, authority = capture_task(root)
            first = claim_next_protected_task(
                ledger=HostedTaskPlanLedger(),
                inbox=app.state.inbox,
                authority_events=authority,
            )
            second = claim_next_protected_task(
                ledger=first.ledger,
                inbox=app.state.inbox,
                authority_events=authority,
            )
            self.assertEqual(
                second.disposition,
                HostedTaskPlanDisposition.ALREADY_ACTIVE,
            )
            self.assertEqual(second.ledger, first.ledger)

    def test_external_claim_blocks_before_preflight(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, _, authority = capture_task(root)
            hold = ExternalProducerClaim(
                issue_number=900,
                claimed_by="ni-da-ba",
                lane="Implementation",
                branch="manual/900",
            )
            result = claim_next_protected_task(
                ledger=HostedTaskPlanLedger(),
                inbox=app.state.inbox,
                authority_events=authority,
                external_claims=(hold,),
            )
            self.assertEqual(
                result.disposition,
                HostedTaskPlanDisposition.EXTERNAL_HOLD,
            )
            self.assertIsNone(result.ledger.active)

    def test_missing_capture_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, _, _ = capture_task(root)
            result = claim_next_protected_task(
                ledger=HostedTaskPlanLedger(),
                inbox=app.state.inbox,
                authority_events=TaskAuthorityEventLedger(),
            )
            self.assertEqual(
                result.disposition,
                HostedTaskPlanDisposition.MISSING_CAPTURE,
            )
            self.assertIsNone(result.ledger.active)

    def test_ordinary_event_is_not_claimed_by_task_planner(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app = make_runtime(root)
            payload = json.dumps(
                {
                    "ref": "refs/heads/main",
                    "after": "a" * 40,
                    "repository": {"full_name": "ni-da-ba/skyforge"},
                },
                separators=(",", ":"),
            ).encode()
            digest = hmac.new(SECRET.encode(), payload, hashlib.sha256).hexdigest()
            status, _ = app.handle_webhook(
                headers={
                    "X-GitHub-Event": "push",
                    "X-GitHub-Delivery": "push-r5c15",
                    "X-Hub-Signature-256": "sha256=" + digest,
                },
                raw=payload,
            )
            self.assertEqual(status, 202)
            result = claim_next_protected_task(
                ledger=HostedTaskPlanLedger(),
                inbox=app.state.inbox,
                authority_events=TaskAuthorityEventLedger(),
            )
            self.assertEqual(
                result.disposition,
                HostedTaskPlanDisposition.NO_PROTECTED_TASK,
            )


class HostedTaskPlanPreflightTest(unittest.TestCase):
    def claimed(self, root: Path):
        app, event, authority = capture_task(root)
        result = claim_next_protected_task(
            ledger=HostedTaskPlanLedger(),
            inbox=app.state.inbox,
            authority_events=authority,
        )
        self.assertEqual(result.disposition, HostedTaskPlanDisposition.CLAIMED)
        return app, event, authority, result.ledger

    def test_ready_seed_is_exactly_persistable_and_reconstructible(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, event, authority, ledger = self.claimed(root)
            runner = LiveTruthRunner()
            ready = advance_claimed_task_preflight(
                ledger=ledger,
                authority_events=authority,
                trusted_actors=("ni-da-ba",),
                repo="ni-da-ba/skyforge",
                runner=runner,
            )
            self.assertEqual(
                ready.disposition,
                HostedTaskPlanDisposition.READY_FOR_CLASSIFIER,
            )
            plan = ready.ledger.active
            self.assertEqual(plan.status, HostedTaskPlanStatus.READY_FOR_CLASSIFIER)
            self.assertEqual(plan.seed.event_id, event.event_id)
            self.assertEqual(plan.seed.issue_number, 900)
            self.assertEqual(plan.seed.classifier_request.current_main, "a" * 40)
            self.assertEqual(
                runner.calls,
                [
                    ("gh","api","repos/ni-da-ba/skyforge/issues/900"),
                    ("gh","api","repos/ni-da-ba/skyforge/issues/comments/12345"),
                    ("gh","api","repos/ni-da-ba/skyforge/commits/main","--jq",".sha"),
                ],
            )

            store = HostedTaskPlanStore.for_root(root)
            store.save(ready.ledger)
            restarted = store.load()
            self.assertEqual(restarted, ready.ledger)
            self.assertEqual(
                restarted.active.seed.digest,
                ready.ledger.active.seed.digest,
            )

    def test_remote_outage_waits_and_retry_uses_same_plan(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, _, authority, ledger = self.claimed(root)

            def unavailable(args, **kwargs):
                raise subprocess.CalledProcessError(1, args, stderr="offline")

            waiting = advance_claimed_task_preflight(
                ledger=ledger,
                authority_events=authority,
                trusted_actors=("ni-da-ba",),
                repo="ni-da-ba/skyforge",
                runner=unavailable,
            )
            self.assertEqual(
                waiting.disposition,
                HostedTaskPlanDisposition.WAIT_REMOTE,
            )
            self.assertEqual(
                waiting.ledger.active.plan_id,
                ledger.active.plan_id,
            )

            ready = advance_claimed_task_preflight(
                ledger=waiting.ledger,
                authority_events=authority,
                trusted_actors=("ni-da-ba",),
                repo="ni-da-ba/skyforge",
                runner=LiveTruthRunner(),
            )
            self.assertEqual(
                ready.disposition,
                HostedTaskPlanDisposition.READY_FOR_CLASSIFIER,
            )
            self.assertEqual(
                ready.ledger.active.plan_id,
                ledger.active.plan_id,
            )

    def test_changed_live_authority_becomes_blocked_not_replanned(self):
        cases = [
            LiveTruthRunner(issue_state="closed"),
            LiveTruthRunner(actor="mallory"),
            LiveTruthRunner(comment_body=BODY + " "),
        ]
        for runner in cases:
            with self.subTest(case=runner.__dict__), tempfile.TemporaryDirectory() as td:
                root = Path(td)
                _, _, authority, ledger = self.claimed(root)
                result = advance_claimed_task_preflight(
                    ledger=ledger,
                    authority_events=authority,
                    trusted_actors=("ni-da-ba",),
                    repo="ni-da-ba/skyforge",
                    runner=runner,
                )
                self.assertEqual(
                    result.disposition,
                    HostedTaskPlanDisposition.BLOCKED,
                )
                self.assertEqual(result.ledger.active.status, HostedTaskPlanStatus.BLOCKED)
                again = advance_claimed_task_preflight(
                    ledger=result.ledger,
                    authority_events=authority,
                    trusted_actors=("ni-da-ba",),
                    repo="ni-da-ba/skyforge",
                    runner=LiveTruthRunner(),
                )
                self.assertEqual(
                    again.disposition,
                    HostedTaskPlanDisposition.ALREADY_TERMINAL,
                )

    def test_capture_digest_change_blocks_before_remote_read(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _, _, authority, ledger = self.claimed(root)
            plan = ledger.active
            tampered = HostedTaskPlanLedger(
                active=type(plan)(
                    event_id=plan.event_id,
                    issue_number=plan.issue_number,
                    authority_record_digest="f" * 64,
                    status=plan.status,
                    reason=plan.reason,
                )
            )
            runner = LiveTruthRunner()
            result = advance_claimed_task_preflight(
                ledger=tampered,
                authority_events=authority,
                trusted_actors=("ni-da-ba",),
                repo="ni-da-ba/skyforge",
                runner=runner,
            )
            self.assertEqual(result.disposition, HostedTaskPlanDisposition.BLOCKED)
            self.assertEqual(runner.calls, [])


class InboxProtectionTest(unittest.TestCase):
    def test_new_ordinary_ingress_cannot_erase_claimed_protected_task(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, event, authority = capture_task(root)
            claimed = claim_next_protected_task(
                ledger=HostedTaskPlanLedger(),
                inbox=app.state.inbox,
                authority_events=authority,
            )
            self.assertEqual(claimed.disposition, HostedTaskPlanDisposition.CLAIMED)

            for index in range(5):
                payload = json.dumps(
                    {
                        "ref": "refs/heads/main",
                        "after": f"{index + 1:040x}"[-40:],
                        "repository": {"full_name": "ni-da-ba/skyforge"},
                    },
                    separators=(",", ":"),
                ).encode()
                digest = hmac.new(SECRET.encode(), payload, hashlib.sha256).hexdigest()
                status, _ = app.handle_webhook(
                    headers={
                        "X-GitHub-Event": "push",
                        "X-GitHub-Delivery": f"r5c15-push-{index}",
                        "X-Hub-Signature-256": "sha256=" + digest,
                    },
                    raw=payload,
                )
                self.assertEqual(status, 202)

            self.assertIn(
                event.event_id,
                {value.event_id for value in app.state.inbox.pending_events},
            )


class HostedRuntimePlanningIntegrationTest(unittest.TestCase):
    def test_webhook_capture_does_not_auto_claim_or_preflight(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, _, _ = capture_task(root)
            self.assertIsNone(app.task_plan_store.load().active)
            health = app.health_snapshot()
            self.assertTrue(health["hosted_task_planning_enabled"])
            self.assertEqual(health["active_task_plan_id"], "")
            self.assertEqual(health["active_task_plan_status"], "")
            self.assertFalse(health["mutation_authority"])
            self.assertFalse(health["worker_dispatch_enabled"])
            self.assertFalse(health["remote_effect_execution_enabled"])

    def test_explicit_runtime_claim_and_preflight_persist_across_restart(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, event, _ = capture_task(root)

            claimed = app.claim_next_task_plan()
            self.assertEqual(
                claimed.disposition,
                HostedTaskPlanDisposition.CLAIMED,
            )
            self.assertEqual(claimed.ledger.active.event_id, event.event_id)
            health = app.health_snapshot()
            self.assertEqual(
                health["active_task_plan_status"],
                HostedTaskPlanStatus.CLAIMED.value,
            )
            self.assertFalse(health["mutation_authority"])

            ready = app.advance_task_plan_preflight(runner=LiveTruthRunner())
            self.assertEqual(
                ready.disposition,
                HostedTaskPlanDisposition.READY_FOR_CLASSIFIER,
            )
            ready_id = ready.ledger.active.plan_id
            self.assertEqual(
                app.health_snapshot()["active_task_plan_status"],
                HostedTaskPlanStatus.READY_FOR_CLASSIFIER.value,
            )

            restarted = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                trusted_actors=("ni-da-ba",),
            )
            after = restarted.health_snapshot()
            self.assertEqual(after["active_task_plan_id"], ready_id)
            self.assertEqual(
                after["active_task_plan_status"],
                HostedTaskPlanStatus.READY_FOR_CLASSIFIER.value,
            )
            self.assertFalse(after["mutation_authority"])
            self.assertFalse(after["worker_dispatch_enabled"])
            self.assertFalse(after["remote_effect_execution_enabled"])

    def test_runtime_external_hold_creates_no_plan(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            app, _, _ = capture_task(root)
            hold = ExternalProducerClaim(
                issue_number=900,
                claimed_by="ni-da-ba",
                lane="Implementation",
                branch="manual/900",
            )
            result = app.claim_next_task_plan(external_claims=(hold,))
            self.assertEqual(
                result.disposition,
                HostedTaskPlanDisposition.EXTERNAL_HOLD,
            )
            self.assertIsNone(app.task_plan_store.load().active)


class CapabilityBoundaryTest(unittest.TestCase):
    def test_planner_source_has_no_provider_worker_or_mutation_surface(self):
        source = (
            Path(__file__).resolve().parent / "v2/hosted_task_plan.py"
        ).read_text(encoding="utf-8")
        for token in (
            "CodexClassifierProvider",
            "ClassifierProvider",
            "WorkerProvider",
            "advance_ordinary_pipeline",
            "WriterFence",
            "git push",
            "gh pr",
            "gh issue",
            "subprocess",
        ):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
