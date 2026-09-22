from __future__ import annotations

from dataclasses import replace
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
from test_platform_v2_scope_promotion import write_state
from v2.activation_gate import ProductionActivationInput
from v2.classifier_provider import ClassifierProviderConfig, ClassifierProviderError, ClassifierRunStatus, ClassifierRunStore, parse_classifier_response
from v2.cutover import (
    CutoverReadinessDecision,
    CutoverReadinessDisposition,
    WriterAuthority,
)
from v2.events import DurableEvent
from v2.dormant_handoff_commit import (
    DormantCommitOutcome,
    DormantHandoffCommitLedger,
    DormantHandoffCommitRecord,
    DormantHandoffCommitStore,
)
from v2.hosted_execution_runtime import (
    HostedExecutionActivationInput,
    HostedExecutionAdvanceDisposition,
    HostedExecutionDependencies,
    HostedExecutionGateDisposition,
    evaluate_hosted_execution_gate,
    load_hosted_execution_gate,
)
from v2.hosted_state import HostedStateStore
from v2.human_review import (
    HumanReviewLedger,
    HumanReviewSource,
    HumanReviewSubmission,
    HumanReviewVerdict,
)
from v2.inbox import InboxState
from v2.ordinary_effects import OrdinaryEffectStore
from v2.objective_ingress import DevelopmentApiObjectiveSource, ObjectiveProposalStore
from v2.ordinary_pipeline import OrdinaryPipelineStore
from v2.quota import LocalBudgetObservation
from v2.roadmap_shadow import ShadowRoadmapManifest
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


def install_terminal_roadmap(root: Path) -> str:
    payload = {
        "schema_version": 1,
        "roadmap_id": "terminal-runtime-test",
        "enabled": True,
        "max_auto_claims_per_utc_day": 2,
        "nodes": [
            {
                "id": "task",
                "kind": "task",
                "lane": "Implementation",
                "issue_number": 1,
                "priority": 100,
                "max_runs": 1,
                "prerequisites": [],
                "objective_hint": "do it",
                "stop_boundary": "stop",
            },
            {
                "id": "gate",
                "kind": "gate",
                "lane": "Implementation",
                "priority": 90,
                "max_runs": 1,
                "prerequisites": ["task"],
                "human_message": "review it",
            },
        ],
    }
    manifest = ShadowRoadmapManifest.from_mapping(payload)
    manifest_path = root / "docs/agent-state/ORCHESTRATOR_ROADMAP.json"
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(payload), encoding="utf-8")
    git(root, "add", str(manifest_path.relative_to(root)))
    git(root, "commit", "-m", "terminal roadmap fixture")

    state_path = root / ".skyforge-orchestrator/state.json"
    state = json.loads(state_path.read_text(encoding="utf-8"))
    state["roadmap"] = {
        "roadmap_id": manifest.roadmap_id,
        "manifest_fingerprint": manifest.fingerprint,
        "completed_runs": {"task": 1},
        "blocked_nodes": {"gate": {"reason": "human review"}},
        "active": None,
        "claims_day": "2026-09-19",
        "claims_today": 0,
    }
    payload_bytes = (json.dumps(state, indent=2, sort_keys=True) + "\n").encode()
    state_path.write_bytes(payload_bytes)
    (state_path.parent / "state.json.bak").write_bytes(payload_bytes)
    return git(root, "rev-parse", "HEAD")


class FakeClassifier:
    def __init__(self):
        self.calls = 0

    def classify(self, *, request, root, config):
        self.calls += 1
        return DISPATCH, parse_classifier_response(DISPATCH)


class SourcePRClassifier:
    def __init__(self):
        self.calls = 0

    def classify(self, *, request, root, config):
        self.calls += 1
        raw = json.dumps({
            "decision": "DISPATCH",
            "lane": "Implementation",
            "pr_number": 77,
            "objective": "Implement bounded feature",
            "stop_boundary": "merge boundary",
            "worker_tier": "LUNA",
            "allowed_paths": ["docs/operations/**"],
            "reason": "mistook task issue for source PR",
        }, separators=(",", ":"))
        return raw, parse_classifier_response(raw)


class OneBadScopeThenGoodClassifier:
    def __init__(self):
        self.calls = 0

    def classify(self, *, request, root, config):
        self.calls += 1
        authority = request.semantic_input["task_authority"]
        allowed = (
            ["docs/not-authorized/**"]
            if self.calls == 1
            else list(authority["allowed_paths"])
        )
        raw = json.dumps(
            {
                "decision": "DISPATCH",
                "lane": authority["lane"],
                "pr_number": None,
                "objective": authority["objective"],
                "stop_boundary": authority["stop_boundary"],
                "worker_tier": "LUNA",
                "allowed_paths": allowed,
                "reusable_evidence": None,
                "reason": "fixture classifier proposal",
                "human_message": None,
            },
            separators=(",", ":"),
        )
        return raw, parse_classifier_response(raw)


class AlwaysBadScopeClassifier(OneBadScopeThenGoodClassifier):
    def classify(self, *, request, root, config):
        self.calls += 1
        authority = request.semantic_input["task_authority"]
        raw = json.dumps(
            {
                "decision": "DISPATCH",
                "lane": authority["lane"],
                "pr_number": None,
                "objective": authority["objective"],
                "stop_boundary": authority["stop_boundary"],
                "worker_tier": "LUNA",
                "allowed_paths": ["docs/not-authorized/**"],
                "reusable_evidence": None,
                "reason": "fixture classifier proposal remains invalid",
                "human_message": None,
            },
            separators=(",", ":"),
        )
        return raw, parse_classifier_response(raw)


class FailingClassifier:
    def __init__(self):
        self.calls = 0

    def classify(self, *, request, root, config):
        self.calls += 1
        raise ClassifierProviderError(
            "classifier_invalid_response",
            0,
            "unknown classifier decision kind",
        )


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


class HumanGateProjectionTest(unittest.TestCase):
    def test_reviewed_legacy_gate_is_hidden_until_program_resurfaces_it(self):
        review = HumanReviewSubmission(
            source=HumanReviewSource(
                repo="ni-da-ba/skyforge",
                issue_number=754,
                comment_id=5752994015,
                actor="ni-da-ba",
                created_at="2026-09-20T22:02:53Z",
                updated_at="2026-09-20T22:02:53Z",
            ),
            gate_id="dr-human-exploration-rereview",
            artifact_id="dr70:key-2885",
            source_sha="a" * 40,
            verdict=HumanReviewVerdict.CHANGES_REQUIRED,
            findings=("reviewed and changes required",),
            positive_findings=(),
            material_delta="historical reviewed specimen",
            next_boundary="repair before another review",
            deferred_product_work=True,
        )
        reviews = HumanReviewLedger((review,))

        self.assertFalse(
            hosted.legacy_human_gate_is_actionable(
                reviews=reviews,
                gate_id="dr-human-exploration-rereview",
            )
        )
        self.assertTrue(
            hosted.legacy_human_gate_is_actionable(
                reviews=reviews,
                gate_id="dr-human-exploration-rereview",
                active_program_gate_id="dr-human-exploration-rereview",
            )
        )
        self.assertTrue(
            hosted.legacy_human_gate_is_actionable(
                reviews=reviews,
                gate_id="unreviewed-gate",
            )
        )


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

    def test_terminal_gate_quiesces_ordinary_v2_inbox_without_model_calls(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            make_repo(root)
            write_legacy(root)
            base = install_terminal_roadmap(root)
            gate = ready_gate(base)
            app = self.restart(root, gate)

            ordinary = DurableEvent(
                actionable=True,
                reason="CI completed",
                event="workflow_run",
                action="completed",
                head_sha=base,
            )
            current = app.store.load()
            app.store.save(
                replace(
                    current,
                    inbox=InboxState(pending_events=(ordinary,)),
                )
            )

            classifier = FakeClassifier()
            worker = FakeWorker()
            deps = HostedExecutionDependencies(
                classifier_provider=classifier,
                worker_provider=worker,
                classifier_local_budget=budget(),
                worker_local_budget=budget(),
                runner=CompositeReadRunner(base),
            )
            result = app.advance_one_execution_step(deps)
            self.assertEqual(
                result.disposition,
                HostedExecutionAdvanceDisposition.ORDINARY_QUIESCED,
            )
            self.assertIn("retired 1 ordinary inbox event", result.reason)
            self.assertEqual(classifier.calls, 0)
            self.assertEqual(worker.calls, 0)

            state = HostedStateStore.for_root(root).load()
            self.assertEqual(state.inbox.pending_events, ())
            self.assertIn(ordinary.event_id, state.inbox.retired_event_keys)

            idle = app.advance_one_execution_step(deps)
            self.assertEqual(idle.disposition, HostedExecutionAdvanceDisposition.IDLE)

    def test_terminal_gate_quiesces_with_retained_stale_handoff_evidence(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            make_repo(root)
            write_legacy(root)
            base = install_terminal_roadmap(root)
            gate = ready_gate(base)
            app = self.restart(root, gate)

            historical = DormantHandoffCommitRecord(
                admission_record_id="a" * 64,
                worker_run_id="b" * 64,
                attempt_id="c" * 64,
                branch="codex/stale-history",
                base_sha=base,
                outcome=DormantCommitOutcome.COMMITTED,
                reason="retained stale completion evidence",
                head_sha="d" * 40,
                changed_paths=("docs/operations/stale-history.md",),
            )
            DormantHandoffCommitStore.for_root(root).save(
                DormantHandoffCommitLedger((historical,))
            )

            ordinary = DurableEvent(
                actionable=True,
                reason="closed PR lifecycle noise",
                event="pull_request",
                action="closed",
                head_sha=base,
                pr_number=1032,
            )
            current = app.store.load()
            app.store.save(
                replace(
                    current,
                    inbox=InboxState(pending_events=(ordinary,)),
                )
            )

            classifier = FakeClassifier()
            worker = FakeWorker()
            deps = HostedExecutionDependencies(
                classifier_provider=classifier,
                worker_provider=worker,
                classifier_local_budget=budget(),
                worker_local_budget=budget(),
                runner=CompositeReadRunner(base),
            )
            result = app.advance_one_execution_step(deps)

            self.assertEqual(
                result.disposition,
                HostedExecutionAdvanceDisposition.ORDINARY_QUIESCED,
            )
            self.assertEqual(classifier.calls, 0)
            self.assertEqual(worker.calls, 0)
            state = HostedStateStore.for_root(root).load()
            self.assertEqual(state.inbox.pending_events, ())
            self.assertIn(ordinary.event_id, state.inbox.retired_event_keys)

            retained = DormantHandoffCommitStore.for_root(root).load()
            self.assertEqual(retained.for_attempt(historical.attempt_id), historical)

    def test_reviewed_legacy_gate_is_not_projected_as_fresh_operator_work(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            make_repo(root)
            write_legacy(root)
            base = install_terminal_roadmap(root)
            gate = ready_gate(base)
            app = self.restart(root, gate)

            app.human_review_store.capture(
                HumanReviewSubmission(
                    source=HumanReviewSource(
                        repo="ni-da-ba/skyforge",
                        issue_number=1,
                        comment_id=9001,
                        actor="ni-da-ba",
                        created_at="2026-09-21T20:00:00Z",
                        updated_at="2026-09-21T20:00:00Z",
                    ),
                    gate_id="gate",
                    artifact_id="fixture:gate",
                    source_sha=base,
                    verdict=HumanReviewVerdict.CHANGES_REQUIRED,
                    findings=("review already happened",),
                    positive_findings=(),
                    material_delta="fixture review evidence",
                    next_boundary="perform repair before another review",
                    deferred_product_work=True,
                )
            )

            snapshot = app.development_snapshot()
            self.assertFalse(
                any(value["gate_id"] == "gate" for value in snapshot["human_gates"])
            )
            self.assertEqual(
                snapshot["human_reviews"][-1]["gate_id"],
                "gate",
                "durable review history must remain visible when the gate is no longer actionable",
            )

    def test_waiting_continue_skyforge_gate_does_not_block_unrelated_task_claim(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            make_repo(root)
            write_legacy(root)

            repo_root = Path(__file__).resolve().parents[2]
            for rel in (
                "docs/agent-state/PROGRAM_ROADMAP.md",
                "docs/architecture/PRE_BOOTSTRAP_DEVELOPMENT_PLATFORM_GATE.md",
                "docs/agent-state/ORCHESTRATOR_ROADMAP.json",
                "docs/agent-state/PROGRAM_PROGRESSION.json",
            ):
                source = repo_root / rel
                target = root / rel
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(source.read_bytes())
            artifact_manifest = root / "docs/agent-state/REVIEW_ARTIFACTS.json"
            artifact_manifest.write_text(
                json.dumps({"schema_version": 1, "artifacts": []}),
                encoding="utf-8",
            )
            git(root, "add", "docs")
            git(root, "commit", "-m", "install canonical program projection")
            write_state(root, gate=False)
            base = git(root, "rev-parse", "HEAD")
            gate = ready_gate(base)
            app = self.restart(root, gate)

            source = DevelopmentApiObjectiveSource(
                repo="ni-da-ba/skyforge",
                request_id="continue-runtime-0001",
                actor="ni-da-ba",
                client="runtime-test",
                submitted_at="2026-09-21T03:45:00Z",
                objective_text="Continue Skyforge",
            )
            captured = ObjectiveProposalStore.for_root(root).capture(
                source=source,
                delivery_id="runtime-program",
                root=root,
            )
            self.assertEqual(
                captured.record.compiled.disposition.value,
                "PROGRAM_CONTINUE",
            )

            raw, headers = signed(task_payload(), delivery="r5c24-program-wait")
            status, response = app.handle_webhook(headers=headers, raw=raw)
            self.assertEqual(status, 202)
            self.assertTrue(response["task_authority_recorded"])

            deps = HostedExecutionDependencies(
                classifier_provider=FakeClassifier(),
                worker_provider=FakeWorker(),
                classifier_local_budget=budget(),
                worker_local_budget=budget(),
                runner=CompositeReadRunner(base),
            )
            first = app.advance_one_execution_step(deps)
            self.assertEqual(
                first.disposition,
                HostedExecutionAdvanceDisposition.PROGRAM_ADVANCED,
            )
            second = app.advance_one_execution_step(deps)
            self.assertEqual(
                second.disposition,
                HostedExecutionAdvanceDisposition.TASK_CLAIMED,
            )

            snapshot = app.development_snapshot()
            program = snapshot["program_progression"]
            self.assertEqual(
                program["active_session"]["disposition"],
                "WAIT_HUMAN",
            )
            self.assertEqual(
                program["active_session"]["gate_id"],
                "pre-bootstrap-development-platform-gate",
            )
            self.assertTrue(
                any(
                    value["gate_id"]
                    == "pre-bootstrap-development-platform-gate"
                    for value in snapshot["human_gates"]
                )
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

    def test_one_bounded_classifier_validation_retry_recovers_without_worker_attempt(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = make_repo(root)
            write_legacy(root)
            gate = ready_gate(base)
            app = self.restart(root, gate)
            raw, headers = signed(task_payload(), delivery="r5c24-classifier-scope-retry")
            status, response = app.handle_webhook(headers=headers, raw=raw)
            self.assertEqual(status, 202)
            self.assertTrue(response["task_authority_recorded"])

            classifier = OneBadScopeThenGoodClassifier()
            worker = FakeWorker()
            deps = HostedExecutionDependencies(
                classifier_provider=classifier,
                worker_provider=worker,
                classifier_local_budget=budget(),
                worker_local_budget=budget(),
                classifier_config=ClassifierProviderConfig("fixture-classifier", "low"),
                runner=CompositeReadRunner(base),
            )
            self.assertEqual(
                app.advance_one_execution_step(deps).disposition,
                HostedExecutionAdvanceDisposition.TASK_CLAIMED,
            )
            self.assertEqual(
                app.advance_one_execution_step(deps).disposition,
                HostedExecutionAdvanceDisposition.PREFLIGHT_ADVANCED,
            )
            self.assertEqual(
                app.advance_one_execution_step(deps).disposition,
                HostedExecutionAdvanceDisposition.CLASSIFIER_ADVANCED,
            )
            blocked = app.advance_one_execution_step(deps)
            self.assertEqual(
                blocked.disposition,
                HostedExecutionAdvanceDisposition.ADMISSION_ADVANCED,
            )
            first_admission = app.admission_store.load().record
            self.assertEqual(first_admission.outcome.value, "BLOCKED")
            self.assertFalse(first_admission.consume_attempt)
            self.assertIsNone(first_admission.attempt)
            self.assertIsNone(first_admission.worker_spec)
            plan = app.task_plan_store.load().active
            old_request_id = plan.seed.classifier_request.request_id

            retry = app.advance_one_execution_step(deps)
            self.assertEqual(
                retry.disposition,
                HostedExecutionAdvanceDisposition.CLASSIFIER_RETRY_ACCEPTED,
            )
            self.assertEqual(classifier.calls, 1)
            self.assertIsNone(app.admission_store.load().record)
            retry_plan = app.task_plan_store.load().active
            self.assertEqual(retry_plan.plan_id, plan.plan_id)
            new_request_id = retry_plan.seed.classifier_request.request_id
            self.assertNotEqual(new_request_id, old_request_id)
            self.assertEqual(
                retry_plan.seed.classifier_request.semantic_input[
                    "classifier_validation_retry"
                ]["prior_request_id"],
                old_request_id,
            )
            self.assertEqual(
                ClassifierRunStore.for_root(root).load().get(old_request_id).status,
                ClassifierRunStatus.COMPLETE,
            )

            self.assertEqual(
                app.advance_one_execution_step(deps).disposition,
                HostedExecutionAdvanceDisposition.CLASSIFIER_ADVANCED,
            )
            self.assertEqual(classifier.calls, 2)
            admitted = app.advance_one_execution_step(deps)
            self.assertEqual(
                admitted.disposition,
                HostedExecutionAdvanceDisposition.ADMISSION_ADVANCED,
            )
            second_admission = app.admission_store.load().record
            self.assertEqual(second_admission.outcome.value, "ADMITTED")
            self.assertIsNotNone(second_admission.attempt)
            self.assertIsNotNone(second_admission.worker_spec)
            self.assertEqual(worker.calls, 0)
            self.assertEqual(
                ClassifierRunStore.for_root(root).load().get(new_request_id).status,
                ClassifierRunStatus.COMPLETE,
            )

    def test_second_classifier_authority_violation_hard_stops_without_execution(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = make_repo(root)
            write_legacy(root)
            gate = ready_gate(base)
            app = self.restart(root, gate)
            raw, headers = signed(task_payload(), delivery="r5c24-classifier-scope-exhausted")
            status, _ = app.handle_webhook(headers=headers, raw=raw)
            self.assertEqual(status, 202)

            classifier = AlwaysBadScopeClassifier()
            worker = FakeWorker()
            deps = HostedExecutionDependencies(
                classifier_provider=classifier,
                worker_provider=worker,
                classifier_local_budget=budget(),
                worker_local_budget=budget(),
                classifier_config=ClassifierProviderConfig("fixture-classifier", "low"),
                runner=CompositeReadRunner(base),
            )
            for expected in (
                HostedExecutionAdvanceDisposition.TASK_CLAIMED,
                HostedExecutionAdvanceDisposition.PREFLIGHT_ADVANCED,
                HostedExecutionAdvanceDisposition.CLASSIFIER_ADVANCED,
                HostedExecutionAdvanceDisposition.ADMISSION_ADVANCED,
                HostedExecutionAdvanceDisposition.CLASSIFIER_RETRY_ACCEPTED,
                HostedExecutionAdvanceDisposition.CLASSIFIER_ADVANCED,
                HostedExecutionAdvanceDisposition.ADMISSION_ADVANCED,
            ):
                self.assertEqual(app.advance_one_execution_step(deps).disposition, expected)

            stopped = app.advance_one_execution_step(deps)
            self.assertEqual(stopped.disposition, HostedExecutionAdvanceDisposition.BLOCKED)
            self.assertIn("retry already exhausted", stopped.reason)
            self.assertEqual(classifier.calls, 2)
            self.assertEqual(worker.calls, 0)
            admission = app.admission_store.load().record
            self.assertEqual(admission.outcome.value, "BLOCKED")
            self.assertFalse(admission.consume_attempt)
            self.assertIsNone(admission.attempt)
            self.assertIsNone(admission.worker_spec)

    def test_new_signed_revision_supersedes_nonexecuted_reclassify_admission(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = make_repo(root)
            write_legacy(root)
            gate = ready_gate(base)
            app = self.restart(root, gate)
            raw, headers = signed(task_payload(), delivery="r5c24-reclassify-original")
            status, response = app.handle_webhook(headers=headers, raw=raw)
            self.assertEqual(status, 202)
            self.assertTrue(response["task_authority_recorded"])

            classifier = SourcePRClassifier()
            deps = HostedExecutionDependencies(
                classifier_provider=classifier,
                worker_provider=FakeWorker(),
                classifier_local_budget=budget(),
                worker_local_budget=budget(),
                classifier_config=ClassifierProviderConfig("fixture-classifier", "low"),
                runner=CompositeReadRunner(base),
            )
            self.assertEqual(app.advance_one_execution_step(deps).disposition, HostedExecutionAdvanceDisposition.TASK_CLAIMED)
            self.assertEqual(app.advance_one_execution_step(deps).disposition, HostedExecutionAdvanceDisposition.PREFLIGHT_ADVANCED)
            self.assertEqual(app.advance_one_execution_step(deps).disposition, HostedExecutionAdvanceDisposition.CLASSIFIER_ADVANCED)
            admission_result = app.advance_one_execution_step(deps)
            self.assertEqual(admission_result.disposition, HostedExecutionAdvanceDisposition.ADMISSION_ADVANCED)
            admission = app.admission_store.load().record
            self.assertIsNotNone(admission)
            self.assertEqual(admission.outcome.value, "RECLASSIFY")
            self.assertIsNone(admission.attempt)
            self.assertIsNone(admission.worker_spec)
            plan = app.task_plan_store.load().active
            old_event_id = plan.event_id
            old_request_id = plan.seed.classifier_request.request_id

            revised = task_payload()
            revised["comment"]["id"] = 12347
            revised["comment"]["created_at"] = "2026-09-18T03:02:00Z"
            revised["comment"]["updated_at"] = "2026-09-18T03:02:00Z"
            revised["comment"]["body"] = revised["comment"]["body"].replace(
                "Implement bounded feature", "Implement bounded feature revision two"
            )
            raw2, headers2 = signed(revised, delivery="r5c24-reclassify-revision")
            status2, response2 = app.handle_webhook(headers=headers2, raw=raw2)
            self.assertEqual(status2, 202)
            self.assertTrue(response2["task_authority_recorded"])

            superseded = app.advance_one_execution_step(deps)
            self.assertEqual(superseded.disposition, HostedExecutionAdvanceDisposition.TASK_REVISION_ACCEPTED)
            self.assertEqual(classifier.calls, 1)
            state = app.store.load()
            self.assertIn(old_event_id, state.inbox.retired_event_keys)
            self.assertNotIn(old_event_id, state.inbox.completed_authority_event_keys)
            self.assertIsNone(app.task_plan_store.load().active)
            self.assertIsNone(app.admission_store.load().record)
            self.assertEqual(
                ClassifierRunStore.for_root(root).load().get(old_request_id).status,
                ClassifierRunStatus.COMPLETE,
            )
            reclaimed = app.advance_one_execution_step(deps)
            self.assertEqual(reclaimed.disposition, HostedExecutionAdvanceDisposition.TASK_CLAIMED)
            self.assertNotEqual(app.task_plan_store.load().active.event_id, old_event_id)

    def test_new_signed_revision_supersedes_failed_classifier_without_retrying_old_request(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = make_repo(root)
            write_legacy(root)
            gate = ready_gate(base)
            app = self.restart(root, gate)
            raw, headers = signed(task_payload(), delivery="r5c24-failed-original")
            status, response = app.handle_webhook(headers=headers, raw=raw)
            self.assertEqual(status, 202)
            self.assertTrue(response["task_authority_recorded"])

            classifier = FailingClassifier()
            deps = HostedExecutionDependencies(
                classifier_provider=classifier,
                worker_provider=FakeWorker(),
                classifier_local_budget=budget(),
                worker_local_budget=budget(),
                classifier_config=ClassifierProviderConfig("fixture-classifier", "low"),
                runner=CompositeReadRunner(base),
            )
            self.assertEqual(
                app.advance_one_execution_step(deps).disposition,
                HostedExecutionAdvanceDisposition.TASK_CLAIMED,
            )
            self.assertEqual(
                app.advance_one_execution_step(deps).disposition,
                HostedExecutionAdvanceDisposition.PREFLIGHT_ADVANCED,
            )
            failed = app.advance_one_execution_step(deps)
            self.assertEqual(failed.disposition, HostedExecutionAdvanceDisposition.CLASSIFIER_ADVANCED)
            self.assertEqual(classifier.calls, 1)
            plan = app.task_plan_store.load().active
            self.assertIsNotNone(plan)
            old_event_id = plan.event_id
            old_request_id = plan.seed.classifier_request.request_id
            self.assertEqual(
                ClassifierRunStore.for_root(root).load().get(old_request_id).status,
                ClassifierRunStatus.FAILED,
            )

            revised = task_payload()
            revised["comment"]["id"] = 12346
            revised["comment"]["created_at"] = "2026-09-18T03:01:00Z"
            revised["comment"]["updated_at"] = "2026-09-18T03:01:00Z"
            revised["comment"]["body"] = revised["comment"]["body"].replace(
                "Implement bounded feature", "Implement bounded feature revision"
            )
            raw2, headers2 = signed(revised, delivery="r5c24-failed-revision")
            status2, response2 = app.handle_webhook(headers=headers2, raw=raw2)
            self.assertEqual(status2, 202)
            self.assertTrue(response2["task_authority_recorded"])

            superseded = app.advance_one_execution_step(deps)
            self.assertEqual(
                superseded.disposition,
                HostedExecutionAdvanceDisposition.TASK_REVISION_ACCEPTED,
            )
            self.assertEqual(classifier.calls, 1)
            state = app.store.load()
            self.assertIn(old_event_id, state.inbox.retired_event_keys)
            self.assertNotIn(old_event_id, state.inbox.completed_authority_event_keys)
            self.assertIsNone(app.task_plan_store.load().active)
            self.assertEqual(
                ClassifierRunStore.for_root(root).load().get(old_request_id).status,
                ClassifierRunStatus.FAILED,
            )

            reclaimed = app.advance_one_execution_step(deps)
            self.assertEqual(
                reclaimed.disposition,
                HostedExecutionAdvanceDisposition.TASK_CLAIMED,
            )
            self.assertNotEqual(app.task_plan_store.load().active.event_id, old_event_id)

    def test_docs_only_checkout_advance_preserves_reviewed_runtime_gate(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = make_repo(root)
            write_legacy(root)
            gate = ready_gate(base)
            app = self.restart(root, gate)

            (root / "docs/operations/drift.txt").write_text("drift\n", encoding="utf-8")
            git(root, "add", "docs/operations/drift.txt")
            git(root, "commit", "-m", "ordinary docs advance")

            deps = HostedExecutionDependencies(
                classifier_provider=FakeClassifier(),
                worker_provider=FakeWorker(),
                classifier_local_budget=budget(),
                worker_local_budget=budget(),
                runner=CompositeReadRunner(base),
            )
            result = app.advance_one_execution_step(deps)
            self.assertNotEqual(
                result.disposition,
                HostedExecutionAdvanceDisposition.GATE_BLOCKED,
            )
            self.assertEqual(len(OrdinaryEffectStore.for_root(root).load().records), 0)

    def test_runtime_checkout_advance_blocks_before_any_lifecycle_mutation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            base = make_repo(root)
            write_legacy(root)
            gate = ready_gate(base)
            app = self.restart(root, gate)

            runtime = root / "scripts" / "orchestrator" / "v2" / "drift.py"
            runtime.parent.mkdir(parents=True, exist_ok=True)
            runtime.write_text("# changed runtime\n", encoding="utf-8")
            git(root, "add", "scripts/orchestrator/v2/drift.py")
            git(root, "commit", "-m", "runtime drift")

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
            self.assertIn("runtime/deployment", result.reason)
            self.assertIn("scripts/orchestrator/v2/drift.py", result.reason)
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
