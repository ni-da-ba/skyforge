from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

import platform_v2_hosted_runtime as hosted
from test_platform_v2_hosted_runtime import SECRET, write_legacy
from test_platform_v2_hosted_task_preflight import (
    LiveTruthRunner,
    signed,
    task_payload,
)
from test_platform_v2_ordinary_service import FakeRemoteFactory
from v2.activation_gate import ProductionActivationInput
from v2.classifier_provider import ClassifierProviderConfig, parse_classifier_response
from v2.cutover import (
    CutoverReadinessDecision,
    CutoverReadinessDisposition,
    WriterAuthority,
)
from v2.hosted_execution_runtime import (
    HostedExecutionActivationInput,
    HostedExecutionAdvanceDisposition,
    HostedExecutionDependencies,
    HostedExecutionGateDisposition,
    evaluate_hosted_execution_gate,
    load_hosted_execution_gate,
)
from v2.ordinary_effects import OrdinaryEffectStore
from v2.ordinary_pipeline import OrdinaryPipelineStore
from v2.quota import LocalBudgetObservation
from v2.worker_provider import WorkerProviderConfig
from v2.worker_workspace import WorkerWorkspaceManager


DISPATCH = json.dumps(
    {
        "decision": "DISPATCH",
        "lane": "Implementation",
        "objective": "Implement bounded feature",
        "stop_boundary": "merge boundary",
        "worker_tier": "LUNA",
        "allowed_paths": ["docs/operations/**"],
        "reason": "bounded exact R5C24 proposal",
    },
    separators=(",", ":"),
)


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip()


def make_repo(root: Path) -> str:
    git(root, "init", "-b", "main")
    git(root, "config", "user.email", "test@example.invalid")
    git(root, "config", "user.name", "Skyforge R5C24 Test")
    (root / ".gitignore").write_text(
        ".skyforge-platform-v2/\n.skyforge-orchestrator/\n",
        encoding="utf-8",
    )
    target = root / "docs" / "operations"
    target.mkdir(parents=True)
    (target / "base.txt").write_text("base\n", encoding="utf-8")
    git(root, "add", ".gitignore", "docs/operations/base.txt")
    git(root, "commit", "-m", "r5c24 base")
    return git(root, "rev-parse", "HEAD")


def cutover_ready(main: str) -> CutoverReadinessDecision:
    return CutoverReadinessDecision(
        disposition=CutoverReadinessDisposition.READY_FOR_AUTHORITY_SWITCH,
        blockers=(),
        projection_digest="b" * 64,
        accepted_main_sha=main,
        legacy_runtime_sha=main,
    )


def production_input(main: str, **overrides) -> ProductionActivationInput:
    values = {
        "cutover": cutover_ready(main),
        "primary_workstation_preservation_pass": True,
        "dr70_migration_hold_cleared": True,
        "hosted_shadow_parity_accepted": True,
        "hosted_execution_path_accepted": True,
        "remote_effect_path_accepted": True,
        "cutover_rollback_rehearsal_accepted": True,
    }
    values.update(overrides)
    return ProductionActivationInput(**values)


def ready_gate(main: str):
    return evaluate_hosted_execution_gate(
        HostedExecutionActivationInput(
            operator_activation_requested=True,
            production_activation=production_input(main),
            accepted_main_sha=main,
            checkout_head_sha=main,
            legacy_writer_revoked_observed=True,
            writer_authority=WriterAuthority.NONE,
        )
    )


def budget():
    return LocalBudgetObservation(calls_used=0, daily_limit=10)


class FakeClassifier:
    def __init__(self):
        self.calls = 0

    def classify(self, *, request, root, config):
        self.calls += 1
        return DISPATCH, parse_classifier_response(DISPATCH)


class FakeWorker:
    def __init__(self):
        self.calls = 0

    def run(self, *, spec, worktree, config):
        self.calls += 1
        target = Path(worktree) / "docs" / "operations" / "r5c24-output.txt"
        target.write_text("bounded r5c24 output\n", encoding="utf-8")
        return "bounded R5C24 worker complete"


class CompositeReadRunner:
    """Real local git reads + accepted fake GitHub task/main truth."""

    def __init__(self, main: str):
        self.live = LiveTruthRunner(main=main)

    def __call__(self, args, **kwargs):
        if args and args[0] == "git":
            return subprocess.run(args, **kwargs)
        return self.live(args, **kwargs)


class HostedExecutionGateTest(unittest.TestCase):
    def test_all_evidence_plus_none_writer_reaches_ready(self):
        main = "a" * 40
        gate = ready_gate(main)
        self.assertEqual(gate.disposition, HostedExecutionGateDisposition.READY)
        self.assertEqual(gate.blockers, ())
        self.assertTrue(gate.enabled)
        self.assertEqual(gate.accepted_main_sha, main)

    def test_each_activation_boundary_fails_closed(self):
        main = "a" * 40
        cases = [
            HostedExecutionActivationInput(
                False, production_input(main), main, main, True, WriterAuthority.NONE
            ),
            HostedExecutionActivationInput(
                True,
                production_input(main, remote_effect_path_accepted=False),
                main,
                main,
                True,
                WriterAuthority.NONE,
            ),
            HostedExecutionActivationInput(
                True, production_input(main), "c" * 40, main, True, WriterAuthority.NONE
            ),
            HostedExecutionActivationInput(
                True, production_input(main), main, "c" * 40, True, WriterAuthority.NONE
            ),
            HostedExecutionActivationInput(
                True, production_input(main), main, main, False, WriterAuthority.NONE
            ),
            HostedExecutionActivationInput(
                True, production_input(main), main, main, True, WriterAuthority.LEGACY
            ),
            HostedExecutionActivationInput(
                True, production_input(main), main, main, True, WriterAuthority.V2
            ),
        ]
        for value in cases:
            with self.subTest(value=value):
                gate = evaluate_hosted_execution_gate(value)
                self.assertEqual(
                    gate.disposition,
                    HostedExecutionGateDisposition.BLOCKED,
                )
                self.assertTrue(gate.blockers)

    def test_activation_evidence_file_is_recomputed_and_bound_to_checkout(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            main = make_repo(root)
            activation = production_input(main)
            raw = {
                "schema_version": 1,
                "operator_activation_requested": True,
                "accepted_main_sha": main,
                "legacy_writer_revoked_observed": True,
                "writer_authority": "NONE",
                "production_activation_input_digest": activation.digest,
                "production_activation_input": {
                    "cutover": {
                        "disposition": activation.cutover.disposition.value,
                        "blockers": list(activation.cutover.blockers),
                        "projection_digest": activation.cutover.projection_digest,
                        "accepted_main_sha": activation.cutover.accepted_main_sha,
                        "legacy_runtime_sha": activation.cutover.legacy_runtime_sha,
                    },
                    "primary_workstation_preservation_pass": True,
                    "dr70_migration_hold_cleared": True,
                    "hosted_shadow_parity_accepted": True,
                    "hosted_execution_path_accepted": True,
                    "remote_effect_path_accepted": True,
                    "cutover_rollback_rehearsal_accepted": True,
                },
            }
            path = root / "activation.json"
            path.write_text(json.dumps(raw), encoding="utf-8")
            gate = load_hosted_execution_gate(path, root=root)
            self.assertTrue(gate.enabled)

            raw["production_activation_input"]["dr70_migration_hold_cleared"] = False
            path.write_text(json.dumps(raw), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "digest mismatch"):
                load_hosted_execution_gate(path, root=root)


class HostedRuntimeGateIntegrationTest(unittest.TestCase):
    def test_default_runtime_stays_read_only_and_webhook_cannot_enable_gate(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            make_repo(root)
            write_legacy(root)
            app = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                trusted_actors=("ni-da-ba",),
            )
            raw, headers = signed(task_payload(), delivery="r5c24-default-off")
            status, response = app.handle_webhook(headers=headers, raw=raw)
            self.assertEqual(status, 202)
            self.assertFalse(response["mutation_authority"])
            health = app.health_snapshot()
            self.assertFalse(health["production_execution_requested"])
            self.assertFalse(health["production_execution_enabled"])
            self.assertFalse(health["mutation_authority"])
            self.assertFalse(health["worker_dispatch_enabled"])
            self.assertFalse(health["remote_effect_execution_enabled"])
            with self.assertRaisesRegex(RuntimeError, "disabled"):
                app.advance_one_execution_step(None)

    def test_startup_refuses_blocked_execution_gate(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            main = make_repo(root)
            write_legacy(root)
            blocked = evaluate_hosted_execution_gate(
                HostedExecutionActivationInput(
                    operator_activation_requested=True,
                    production_activation=production_input(main),
                    accepted_main_sha=main,
                    checkout_head_sha=main,
                    legacy_writer_revoked_observed=False,
                    writer_authority=WriterAuthority.NONE,
                )
            )
            with self.assertRaisesRegex(RuntimeError, "without an accepted"):
                hosted.HostedV2Substrate(
                    root,
                    repo="ni-da-ba/skyforge",
                    require_webhook_secret=True,
                    startup_reconcile=True,
                    webhook_secret=SECRET,
                    trusted_actors=("ni-da-ba",),
                    production_execution_requested=True,
                    execution_gate=blocked,
                )


class HostedExecutionCoordinatorTest(unittest.TestCase):
    def restart(self, root: Path, gate):
        return hosted.HostedV2Substrate(
            root,
            repo="ni-da-ba/skyforge",
            require_webhook_secret=True,
            startup_reconcile=True,
            webhook_secret=SECRET,
            trusted_actors=("ni-da-ba",),
            production_execution_requested=True,
            execution_gate=gate,
        )

    def test_restart_at_each_boundary_reaches_exact_draft_pr_handoff(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = make_repo(root)
            write_legacy(root)
            gate = ready_gate(base)
            self.assertTrue(gate.enabled)

            app = self.restart(root, gate)
            raw, headers = signed(task_payload(), delivery="r5c24-e2e")
            status, response = app.handle_webhook(headers=headers, raw=raw)
            self.assertEqual(status, 202)
            self.assertTrue(response["task_authority_recorded"])
            self.assertTrue(response["mutation_authority"])

            classifier = FakeClassifier()
            worker = FakeWorker()
            remote = FakeRemoteFactory()
            runner = CompositeReadRunner(base)
            deps = HostedExecutionDependencies(
                classifier_provider=classifier,
                worker_provider=worker,
                classifier_local_budget=budget(),
                worker_local_budget=budget(),
                classifier_config=ClassifierProviderConfig(
                    "fixture-classifier",
                    "low",
                ),
                worker_config=WorkerProviderConfig(
                    tier=__import__(
                        "v2.worker_provider",
                        fromlist=["WorkerTier"],
                    ).WorkerTier.LUNA,
                    model="fixture-worker",
                    reasoning_effort="low",
                ),
                attempt_number=1,
                runner=runner,
                remote_factory=lambda worktree, binding: remote(binding),
            )

            expected = [
                HostedExecutionAdvanceDisposition.TASK_CLAIMED,
                HostedExecutionAdvanceDisposition.PREFLIGHT_ADVANCED,
                HostedExecutionAdvanceDisposition.CLASSIFIER_ADVANCED,
                HostedExecutionAdvanceDisposition.ADMISSION_ADVANCED,
                HostedExecutionAdvanceDisposition.WORKER_ADVANCED,
                HostedExecutionAdvanceDisposition.LOCAL_COMMIT_ADVANCED,
                HostedExecutionAdvanceDisposition.REMOTE_HANDOFF_ADVANCED,
            ]
            observed = []
            for disposition in expected:
                app = self.restart(root, gate)
                health = app.health_snapshot()
                self.assertTrue(health["production_execution_enabled"])
                self.assertTrue(health["mutation_authority"])
                result = app.advance_one_execution_step(deps)
                observed.append(result.disposition)
                self.assertEqual(result.disposition, disposition)

            self.assertEqual(observed, expected)
            self.assertEqual(classifier.calls, 1)
            self.assertEqual(worker.calls, 1)
            self.assertEqual(remote.total_executes, 2)

            admission = app.admission_store.load().record
            self.assertIsNotNone(admission)
            handoffs = OrdinaryPipelineStore.for_root(root).load().reconstructible_managed_handoffs()
            self.assertEqual(len(handoffs), 1)
            handoff = handoffs[0]
            self.assertEqual(handoff.scope.attempt_id, admission.attempt.attempt_id)
            self.assertEqual(handoff.pr_number, 42)
            self.assertEqual(
                handoff.changed_paths,
                ("docs/operations/r5c24-output.txt",),
            )
            effects = OrdinaryEffectStore.for_root(root).load()
            self.assertEqual(len(effects.records), 2)
            self.assertTrue(all(record.status.value == "COMPLETE" for record in effects.records))

            self.assertEqual(git(root, "rev-parse", "HEAD"), base)
            worker_tree = WorkerWorkspaceManager(
                root=root
            ).expected_path(admission.worker_spec)
            self.assertNotEqual(git(worker_tree, "rev-parse", "HEAD"), base)
            self.assertEqual(git(worker_tree, "status", "--porcelain"), "")

    def test_checkout_head_drift_blocks_before_any_lifecycle_mutation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = make_repo(root)
            write_legacy(root)
            gate = ready_gate(base)
            app = self.restart(root, gate)

            # Change only the controller checkout after activation evidence was accepted.
            (root / "docs/operations/drift.txt").write_text("drift\n", encoding="utf-8")
            git(root, "add", "docs/operations/drift.txt")
            git(root, "commit", "-m", "unexpected main drift")

            deps = HostedExecutionDependencies(
                classifier_provider=FakeClassifier(),
                worker_provider=FakeWorker(),
                classifier_local_budget=budget(),
                worker_local_budget=budget(),
                runner=CompositeReadRunner(base),
            )
            result = app.advance_one_execution_step(deps)
            self.assertEqual(
                result.disposition,
                HostedExecutionAdvanceDisposition.GATE_BLOCKED,
            )
            self.assertIn("checkout HEAD moved", result.reason)
            self.assertEqual(len(OrdinaryEffectStore.for_root(root).load().records), 0)


class CapabilityBoundaryTest(unittest.TestCase):
    def test_webhook_does_not_call_execution_coordinator(self):
        root = Path(__file__).resolve().parent
        source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        handler_start = source.index("    def handle_webhook(")
        handler_end = source.index("    def claim_next_task_plan(", handler_start)
        handler = source[handler_start:handler_end]
        self.assertNotIn("advance_one_execution_step", handler)
        self.assertNotIn("HostedExecutionCoordinator", handler)

    def test_no_writer_transition_or_service_control_in_execution_module(self):
        root = Path(__file__).resolve().parent
        source = (root / "v2/hosted_execution_runtime.py").read_text(encoding="utf-8")
        for token in (
            "advance_writer_authority(",
            "systemctl",
            "skyforge-orchestrator.service",
            "Caddy",
        ):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
