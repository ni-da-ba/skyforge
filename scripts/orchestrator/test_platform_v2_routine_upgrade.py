from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from test_platform_v2_operator_cutover import (
    FakeServices,
    git,
    make_repo,
    protected_gate_event,
    write_template,
)
from v2.cutover import WriterAuthority
from v2.events import DurableEvent
from v2.operator_cutover import (
    LEGACY_SERVICE,
    V2_SERVICE,
    OperatorCutoverController,
    OperatorDisposition,
)
from v2.routine_upgrade import (
    RoutineUpgradeController,
    RoutineUpgradeDisposition,
    build_routine_upgrade_template,
    load_portable_activation_baseline,
)


def write_quiescent_legacy_state(root: Path, *, events=()) -> Path:
    state_dir = root / ".skyforge-orchestrator"
    state_dir.mkdir(parents=True, exist_ok=True)
    state = state_dir / "state.json"
    backup = state_dir / "state.json.bak"
    raw = {
        "paused": True,
        "blocked_kind": None,
        "pending_worker": None,
        "pending_decision": None,
        "managed": {},
        "pending_events": [event.as_dict() for event in events],
        "external_producer_claims": {},
        "human_gate_records": {},
        "roadmap": {
            "roadmap_id": "test-roadmap",
            "active": None,
            "completed_runs": {},
            "blocked_nodes": {},
            "manifest_fingerprint": "f" * 64,
            "claims_day": "2026-09-20",
            "claims_today": 0,
        },
        "retired_event_keys": [],
        "completed_authority_event_keys": [],
    }
    payload = json.dumps(raw, sort_keys=True, indent=2) + "\n"
    state.write_text(payload, encoding="utf-8")
    backup.write_text(payload, encoding="utf-8")
    return state


def ordinary_reconcile_event(*, source: str = "periodic") -> DurableEvent:
    return DurableEvent(
        actionable=True,
        reason="repository state changed since previous controller observation",
        event="reconcile",
        action=source,
        head_sha="e" * 40,
    )


class RoutineUpgradeTest(unittest.TestCase):
    def build(self, *, waived: bool = True):
        td = tempfile.TemporaryDirectory()
        root = Path(td.name)
        accepted = make_repo(root)
        template, evidence = write_template(
            root,
            accepted,
            dr70=not waived,
            dr70_waived=waived,
        )
        state = write_quiescent_legacy_state(root)
        services = FakeServices(evidence)

        def health():
            if services.state[V2_SERVICE]["active"]:
                return {
                    "controller": "platform-v2",
                    "production_execution_enabled": True,
                    "production_execution_driver": {"running": True},
                }
            return {
                "status": "ok",
                "last_startup_reconcile_error": None,
                "last_startup_reconcile_success_at": "2026-09-20T22:00:00Z",
            }

        operator = OperatorCutoverController(
            root=root,
            activation_template=template,
            activation_evidence=evidence,
            services=services,
            health_probe=health,
            geteuid=lambda: 0,
            sleep=lambda _seconds: None,
        )
        operator._verify_v2_health = lambda _digest: None
        self.assertEqual(
            operator.cutover(execute=True).disposition,
            OperatorDisposition.CUTOVER_COMPLETE,
        )

        (root / "tracked.txt").write_text("accepted\ntarget\n", encoding="utf-8")
        git(root, "add", "tracked.txt")
        git(root, "commit", "-m", "routine upgrade target")
        target = git(root, "rev-parse", "HEAD")
        git(root, "update-ref", "refs/remotes/origin/main", target)

        controller = RoutineUpgradeController(
            root=root,
            activation_template=template,
            activation_evidence=evidence,
            operator=operator,
        )
        return td, root, accepted, target, template, evidence, state, services, operator, controller

    def test_read_only_plan_is_ready_and_nonmutating(self):
        td, root, _accepted, target, template, evidence, state, services, _operator, controller = self.build()
        self.addCleanup(td.cleanup)
        evidence_before = evidence.read_bytes()
        state_before = state.read_bytes()
        service_before = {name: dict(value) for name, value in services.state.items()}

        report = controller.upgrade(target, execute=False)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.READY)
        self.assertEqual(report.authority, WriterAuthority.V2)
        self.assertEqual(report.target_sha, target)
        self.assertEqual(evidence.read_bytes(), evidence_before)
        self.assertEqual(state.read_bytes(), state_before)
        self.assertEqual(services.state, service_before)
        self.assertEqual(git(root, "rev-parse", "HEAD"), target)
        self.assertTrue(template.is_file())

    def test_read_only_plan_allows_only_model_free_reconcile_noise(self):
        td, root, _accepted, target, _template, _evidence, _state, _services, _operator, controller = self.build()
        self.addCleanup(td.cleanup)
        write_quiescent_legacy_state(
            root,
            events=(ordinary_reconcile_event(),),
        )

        report = controller.upgrade(target, execute=False)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.READY)
        self.assertEqual(report.authority, WriterAuthority.V2)

    def test_read_only_plan_rejects_other_ordinary_pending_work(self):
        td, root, _accepted, target, _template, _evidence, _state, _services, _operator, controller = self.build()
        self.addCleanup(td.cleanup)
        other = DurableEvent(
            actionable=True,
            reason="workflow completed",
            event="workflow_run",
            action="completed",
            source_id="ordinary-work",
        )
        write_quiescent_legacy_state(root, events=(other,))

        report = controller.upgrade(target, execute=False)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.BLOCKED)
        self.assertTrue(
            any("not bounded model-free reconcile noise" in item for item in report.blockers)
        )

    def test_read_only_plan_allows_snapshot_covered_lifecycle_noise(self):
        td, root, _accepted, target, _template, _evidence, _state, _services, _operator, controller = self.build()
        self.addCleanup(td.cleanup)
        events = (
            DurableEvent(
                actionable=True,
                reason="repository state changed since previous controller observation",
                event="reconcile",
                action="startup",
                head_sha=target,
            ),
            DurableEvent(
                actionable=True,
                reason="workflow completed",
                event="workflow_run",
                action="completed",
                head_sha=target,
                pr_number=1053,
            ),
            DurableEvent(
                actionable=True,
                reason="PR lifecycle changed",
                event="pull_request",
                action="closed",
                head_sha=target,
                pr_number=1053,
            ),
            DurableEvent(
                actionable=True,
                reason="main advanced",
                event="push",
                head_sha=target,
            ),
        )
        write_quiescent_legacy_state(root, events=events)

        report = controller.upgrade(target, execute=False)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.READY)

    def test_upgrade_retires_snapshot_covered_lifecycle_noise(self):
        td, root, _accepted, target, _template, _evidence, _state, services, operator, controller = self.build()
        self.addCleanup(td.cleanup)
        real_rollback = operator.rollback

        events = (
            DurableEvent(
                actionable=True,
                reason="repository state changed since previous controller observation",
                event="reconcile",
                action="startup",
                head_sha=target,
            ),
            DurableEvent(
                actionable=True,
                reason="workflow completed",
                event="workflow_run",
                action="completed",
                head_sha=target,
                pr_number=1053,
            ),
            DurableEvent(
                actionable=True,
                reason="PR lifecycle changed",
                event="pull_request",
                action="closed",
                head_sha=target,
                pr_number=1053,
            ),
            DurableEvent(
                actionable=True,
                reason="main advanced",
                event="push",
                head_sha=target,
            ),
        )

        def rollback_with_snapshot_noise(*, execute=False):
            report = real_rollback(execute=execute)
            if execute and report.disposition is OperatorDisposition.ROLLBACK_COMPLETE:
                write_quiescent_legacy_state(root, events=events)
            return report

        operator.rollback = rollback_with_snapshot_noise
        operator.sleep = lambda _seconds: None

        report = controller.upgrade(target, execute=True)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.COMPLETE)
        self.assertEqual(report.authority, WriterAuthority.V2)
        state = json.loads(
            (root / ".skyforge-orchestrator/state.json").read_text(encoding="utf-8")
        )
        self.assertEqual(state["pending_events"], [])
        self.assertEqual(
            state["metrics"]["routine_upgrade_snapshot_events_retired_model_free"],
            len(events),
        )
        self.assertEqual(
            state["last_routine_upgrade_snapshot_quiescence"]["target_sha"],
            target,
        )
        self.assertEqual(
            state["last_routine_upgrade_snapshot_quiescence"]["retired_events"],
            len(events),
        )
        self.assertFalse(services.state[LEGACY_SERVICE]["active"])
        self.assertTrue(services.state[V2_SERVICE]["active"])

    def test_upgrade_waits_for_model_free_reconcile_after_rollback(self):
        td, root, _accepted, target, _template, _evidence, _state, _services, operator, controller = self.build()
        self.addCleanup(td.cleanup)
        real_rollback = operator.rollback
        sleeps = []

        def rollback_with_reconcile(*, execute=False):
            report = real_rollback(execute=execute)
            if execute and report.disposition is OperatorDisposition.ROLLBACK_COMPLETE:
                write_quiescent_legacy_state(
                    root,
                    events=(ordinary_reconcile_event(),),
                )
            return report

        def drain_on_sleep(seconds):
            sleeps.append(seconds)
            write_quiescent_legacy_state(root)

        operator.rollback = rollback_with_reconcile
        operator.sleep = drain_on_sleep

        report = controller.upgrade(target, execute=True)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.COMPLETE)
        self.assertEqual(report.authority, WriterAuthority.V2)
        self.assertTrue(sleeps)
        self.assertEqual(git(root, "rev-parse", "HEAD"), target)

    def test_upgrade_retires_stuck_model_free_reconcile_noise_under_stopped_service(self):
        td, root, _accepted, target, _template, _evidence, _state, services, operator, controller = self.build()
        self.addCleanup(td.cleanup)
        real_rollback = operator.rollback

        def rollback_with_reconcile(*, execute=False):
            report = real_rollback(execute=execute)
            if execute and report.disposition is OperatorDisposition.ROLLBACK_COMPLETE:
                write_quiescent_legacy_state(
                    root,
                    events=(ordinary_reconcile_event(source="startup"),),
                )
            return report

        operator.rollback = rollback_with_reconcile
        operator.sleep = lambda _seconds: None

        report = controller.upgrade(target, execute=True)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.COMPLETE)
        self.assertEqual(report.authority, WriterAuthority.V2)
        state = json.loads(
            (root / ".skyforge-orchestrator/state.json").read_text(encoding="utf-8")
        )
        self.assertEqual(state["pending_events"], [])
        self.assertEqual(
            state["metrics"]["routine_upgrade_reconcile_events_retired_model_free"],
            1,
        )
        self.assertEqual(
            state["last_routine_upgrade_reconcile_quiescence"]["retired_events"],
            1,
        )
        retired = state["retired_event_keys"]
        self.assertEqual(len(retired), 1)
        self.assertTrue(retired[0].startswith("sha256:"))
        self.assertFalse(services.state[LEGACY_SERVICE]["active"])
        self.assertTrue(services.state[V2_SERVICE]["active"])

    def test_reconcile_noise_retirement_refuses_non_model_free_work(self):
        td, root, _accepted, _target, _template, _evidence, _state, _services, _operator, controller = self.build()
        self.addCleanup(td.cleanup)
        other = DurableEvent(
            actionable=True,
            reason="workflow completed",
            event="workflow_run",
            action="completed",
            source_id="ordinary-work",
        )
        write_quiescent_legacy_state(root, events=(other,))

        with self.assertRaisesRegex(RuntimeError, "non-model-free pending work"):
            controller._retire_model_free_reconcile_noise()

        state = json.loads(
            (root / ".skyforge-orchestrator/state.json").read_text(encoding="utf-8")
        )
        self.assertEqual(len(state["pending_events"]), 1)
        self.assertEqual(state["retired_event_keys"], [])

    def test_successful_upgrade_rebuilds_template_and_restores_exclusive_v2(self):
        td, root, accepted, target, template, evidence, _state, services, _operator, controller = self.build()
        self.addCleanup(td.cleanup)

        report = controller.upgrade(target, execute=True)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.COMPLETE)
        self.assertEqual(report.authority, WriterAuthority.V2)
        self.assertEqual(report.prior_accepted_main_sha, accepted)
        self.assertEqual(git(root, "rev-parse", "HEAD"), target)
        self.assertFalse(services.state[LEGACY_SERVICE]["active"])
        self.assertFalse(services.state[LEGACY_SERVICE]["enabled"])
        self.assertTrue(services.state[V2_SERVICE]["active"])
        self.assertTrue(services.state[V2_SERVICE]["enabled"])

        refreshed = json.loads(template.read_text(encoding="utf-8"))
        self.assertEqual(refreshed["accepted_main_sha"], target)
        self.assertEqual(
            refreshed["routine_upgrade"]["prior_accepted_main_sha"],
            accepted,
        )
        final = json.loads(evidence.read_text(encoding="utf-8"))
        self.assertEqual(final["accepted_main_sha"], target)
        self.assertEqual(
            final["production_activation_input_digest"],
            refreshed["production_activation_input_digest"],
        )
        kinds = [event.kind for event in report.events]
        self.assertEqual(
            kinds,
            [
                "UPGRADE_CHECKPOINT",
                "ROLLBACK_TO_LEGACY_COMPLETE",
                "CHECKOUT_UPDATED",
                "LEGACY_RESTARTED_AT_TARGET",
                "ACTIVATION_TEMPLATE_REFRESHED",
                "RECUTOVER_COMPLETE",
            ],
        )

    def test_protected_authority_discovered_after_rollback_stops_on_legacy(self):
        td, _root, _accepted, target, _template, _evidence, _state, services, operator, controller = self.build()
        self.addCleanup(td.cleanup)
        real_rollback = operator.rollback

        def rollback_with_gate(*, execute=False):
            report = real_rollback(execute=execute)
            if execute and report.disposition is OperatorDisposition.ROLLBACK_COMPLETE:
                write_quiescent_legacy_state(operator.root, events=(protected_gate_event(issue=754),))
            return report

        operator.rollback = rollback_with_gate
        report = controller.upgrade(target, execute=True)

        self.assertEqual(
            report.disposition,
            RoutineUpgradeDisposition.FAILED_SAFE_LEGACY,
        )
        self.assertEqual(report.authority, WriterAuthority.LEGACY)
        self.assertTrue(any("explicit reconciliation/transfer" in item for item in report.blockers))
        self.assertTrue(services.state[LEGACY_SERVICE]["active"])
        self.assertFalse(services.state[V2_SERVICE]["active"])

    def test_validation_only_divergent_checkout_is_portable(self):
        td, root, accepted, target, _template, _evidence, _state, _services, _operator, controller = self.build()
        self.addCleanup(td.cleanup)
        git(root, "switch", "--detach", accepted)
        workflow = root / ".github/workflows/ci.yml"
        workflow.parent.mkdir(parents=True, exist_ok=True)
        workflow.write_text("name: local validation maintenance\n", encoding="utf-8")
        git(root, "add", str(workflow.relative_to(root)))
        git(root, "commit", "-m", "local validation maintenance")
        divergent = git(root, "rev-parse", "HEAD")
        self.assertNotEqual(divergent, target)

        report = controller.upgrade(target, execute=False)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.READY)
        self.assertEqual(report.previous_head_sha, divergent)
        self.assertEqual(report.target_sha, target)

    def test_runtime_divergent_checkout_is_not_discarded(self):
        td, root, accepted, target, _template, _evidence, _state, _services, _operator, controller = self.build()
        self.addCleanup(td.cleanup)
        git(root, "switch", "--detach", accepted)
        runtime = root / "scripts/orchestrator/v2/local_only.py"
        runtime.parent.mkdir(parents=True, exist_ok=True)
        runtime.write_text("VALUE = 1\n", encoding="utf-8")
        git(root, "add", str(runtime.relative_to(root)))
        git(root, "commit", "-m", "local runtime divergence")

        report = controller.upgrade(target, execute=False)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.BLOCKED)
        self.assertTrue(any("non-portable divergent changes" in item for item in report.blockers))
        self.assertTrue(any("scripts/orchestrator/v2/local_only.py" in item for item in report.blockers))

    def test_deployment_or_dependency_contract_change_requires_explicit_path(self):
        td, root, _accepted, _target, _template, _evidence, _state, _services, _operator, controller = self.build()
        self.addCleanup(td.cleanup)
        requirements = root / "scripts/orchestrator/requirements.txt"
        requirements.parent.mkdir(parents=True, exist_ok=True)
        requirements.write_text("new-package==1.0\n", encoding="utf-8")
        git(root, "add", str(requirements.relative_to(root)))
        git(root, "commit", "-m", "dependency change")
        target = git(root, "rev-parse", "HEAD")
        git(root, "update-ref", "refs/remotes/origin/main", target)

        report = controller.upgrade(target, execute=False)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.BLOCKED)
        self.assertTrue(any("deployment/dependency contract changes" in item for item in report.blockers))

    def test_non_origin_main_target_is_rejected(self):
        td, root, _accepted, target, _template, _evidence, _state, _services, _operator, controller = self.build()
        self.addCleanup(td.cleanup)
        (root / "tracked.txt").write_text("accepted\ntarget\nnewer\n", encoding="utf-8")
        git(root, "add", "tracked.txt")
        git(root, "commit", "-m", "not fetched main")
        not_main = git(root, "rev-parse", "HEAD")

        report = controller.upgrade(not_main, execute=False)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.BLOCKED)
        self.assertTrue(any("must equal the fetched origin/main" in item for item in report.blockers))
        self.assertNotEqual(target, not_main)

    def test_non_root_execute_is_refused_without_service_mutation(self):
        td, _root, _accepted, target, _template, _evidence, _state, services, operator, controller = self.build()
        self.addCleanup(td.cleanup)
        operator.geteuid = lambda: 1000
        before = list(services.log)

        report = controller.upgrade(target, execute=True)

        self.assertEqual(report.disposition, RoutineUpgradeDisposition.BLOCKED)
        self.assertIn("root operator", report.blockers[0])
        self.assertEqual(services.log, before)
        self.assertTrue(services.state[V2_SERVICE]["active"])

    def test_failed_recutover_recovers_to_legacy(self):
        td, _root, _accepted, target, _template, _evidence, _state, services, _operator, controller = self.build()
        self.addCleanup(td.cleanup)
        services.fail_start_v2 = True

        report = controller.upgrade(target, execute=True)

        self.assertEqual(
            report.disposition,
            RoutineUpgradeDisposition.FAILED_SAFE_LEGACY,
        )
        self.assertEqual(report.authority, WriterAuthority.LEGACY)
        self.assertTrue(services.state[LEGACY_SERVICE]["active"])
        self.assertFalse(services.state[V2_SERVICE]["active"])
        self.assertFalse(services.state[V2_SERVICE]["enabled"])

    def test_template_refresh_preserves_deferred_dr70_waiver(self):
        td, _root, accepted, target, _template, evidence, state, _services, _operator, _controller = self.build()
        self.addCleanup(td.cleanup)
        baseline = load_portable_activation_baseline(evidence)
        template = build_routine_upgrade_template(
            baseline=baseline,
            target_sha=target,
            legacy_state=json.loads(state.read_text(encoding="utf-8")),
        )
        self.assertEqual(template["accepted_main_sha"], target)
        self.assertEqual(
            template["routine_upgrade"]["prior_accepted_main_sha"],
            accepted,
        )
        prod = template["production_activation_input"]
        self.assertFalse(prod["dr70_migration_hold_cleared"])
        self.assertTrue(prod["dr70_migration_hold_waived"])


if __name__ == "__main__":
    unittest.main()
