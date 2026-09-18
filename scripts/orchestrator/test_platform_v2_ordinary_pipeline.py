from __future__ import annotations

from dataclasses import replace
from pathlib import Path
import tempfile
import unittest

from v2.classifier_provider import (
    ClassifierProviderError,
    ClassifierRequest,
    ClassifierRunRecord,
    ClassifierRunStatus,
    ClassifierRunStore,
    classifier_provider_config,
)
from v2.decision import ClassifierDecision, DecisionFreshnessObservation, SourcePRState
from v2.dispatch_admission import RepositoryTaskAuthority
from v2.external import ExternalProducerClaim
from v2.fence import WriterFence
from v2.ordinary_effects import OrdinaryMutationScope
from v2.ordinary_pipeline import (
    OrdinaryPipelineDisposition,
    OrdinaryPipelineRequest,
    OrdinaryPipelineStage,
    OrdinaryPipelineStore,
    advance_ordinary_pipeline,
)
from v2.ordinary_service import ManagedOrdinaryHandoff
from v2.ownership import OwnershipToken
from v2.quota import LocalBudgetObservation, ProviderQuotaDecision
from v2.worker_provider import WorkerAdvanceDisposition


MAIN = "a" * 40
HEAD = "b" * 40


def authority() -> RepositoryTaskAuthority:
    return RepositoryTaskAuthority(
        task_id="r5c6-fixture",
        authority_key="issue:862",
        issue_numbers=(862,),
        lane="Implementation",
        objective="Implement the bounded fixture.",
        stop_boundary="Stop after fixture acceptance.",
        allowed_paths=("docs/operations/**",),
        protected_paths=("docs/agent-state/ORCHESTRATOR_ROADMAP.json",),
        context_text="Repository-owned fixture authority.",
        auto_merge_eligible=False,
    )


def dispatch_decision(*, pr_number=None):
    return ClassifierDecision.from_legacy_mapping(
        {
            "decision": "DISPATCH",
            "lane": "Implementation",
            "pr_number": pr_number,
            "objective": "Implement the bounded fixture.",
            "stop_boundary": "Stop after fixture acceptance.",
            "worker_tier": "TERRA",
            "allowed_paths": ["docs/operations/**"],
            "reason": "bounded task is actionable",
        }
    )


class FakeClassifier:
    def __init__(self, decision=None, error=None):
        self.calls = 0
        self.decision = decision or dispatch_decision()
        self.error = error

    def classify(self, *, request, root, config):
        self.calls += 1
        if self.error is not None:
            raise self.error
        raw = __import__("json").dumps(self.decision.as_dict(), sort_keys=True)
        return raw, self.decision


class CrashClassifier:
    def __init__(self):
        self.calls = 0

    def classify(self, *, request, root, config):
        self.calls += 1
        raise KeyboardInterrupt("fixture crash")


class FakeHandoff:
    def __init__(self, *, complete=True):
        self.calls = 0
        self.complete = complete
        self.last_worker_spec = None

    def advance(self, *, worker_spec, authority, request):
        self.calls += 1
        self.last_worker_spec = worker_spec
        if not self.complete:
            return (
                WorkerAdvanceDisposition.FAILED,
                "fixture worker failed",
                "c" * 64,
                None,
            )
        scope = OrdinaryMutationScope(
            attempt_id=worker_spec.attempt_id,
            repo=request.repo,
            base_sha=worker_spec.base_sha,
            branch=worker_spec.branch,
            expected_head_sha=HEAD,
            pr_title=request.pr_title,
            pr_body=request.pr_body,
        )
        handoff = ManagedOrdinaryHandoff(
            task_id=worker_spec.task_id,
            authority_key=worker_spec.authority_key,
            task_spec_hash=worker_spec.task_spec_hash,
            lane=worker_spec.lane,
            scope=scope,
            pr_number=900,
            changed_paths=("docs/operations/fixture.md",),
            auto_merge_eligible=False,
        )
        return (
            WorkerAdvanceDisposition.HANDOFF_READY,
            "managed draft PR ready",
            "d" * 64,
            handoff,
        )


def pipeline_request(
    *,
    decision_pr=None,
    freshness=None,
    claims=(),
    local_budget=None,
) -> OrdinaryPipelineRequest:
    req = ClassifierRequest(
        current_main=MAIN,
        semantic_input={
            "event_keys": ["task:862"],
            "task_issue_numbers": [862],
            "repository_snapshot": {"main": MAIN},
        },
    )
    return OrdinaryPipelineRequest(
        classifier_request=req,
        authority=authority(),
        freshness=freshness
        or DecisionFreshnessObservation(
            current_main=MAIN,
            source_pr_state=SourcePRState.UNKNOWN,
        ),
        current_main=MAIN,
        external_claims=tuple(claims),
        provider_quota=None,
        local_budget=local_budget or LocalBudgetObservation(0, 2),
        attempt_number=1,
        repo="ni-da-ba/skyforge",
        pr_title="R5C6 fixture",
        pr_body="Bounded R5C6 fixture.",
        event_keys=("task:862",),
        authority_event_keys=("task:862",),
        ordinary_event_keys=(),
        source_pr_head_at_classification=(
            "c" * 40 if decision_pr is not None else ""
        ),
    )


class OrdinaryPipelineCompositionTest(unittest.TestCase):
    def run_pipeline(self, root, classifier, handoff, req=None):
        return advance_ordinary_pipeline(
            request=req or pipeline_request(),
            root=root,
            pipeline_store=OrdinaryPipelineStore.for_root(root),
            classifier_store=ClassifierRunStore.for_root(root),
            classifier_provider=classifier,
            classifier_config=classifier_provider_config({}),
            worker_handoff=handoff,
            ownership_token=OwnershipToken("r5c6-test", 1),
        )

    def test_classifier_admission_worker_handoff_happy_path(self):
        classifier = FakeClassifier()
        handoff = FakeHandoff()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            result = self.run_pipeline(root, classifier, handoff)
            self.assertEqual(result.disposition, OrdinaryPipelineDisposition.COMPLETE)
            self.assertEqual(result.record.stage, OrdinaryPipelineStage.COMPLETE)
            self.assertEqual(result.record.pr_number, 900)
            self.assertTrue(result.record.task_spec_hash)
            self.assertTrue(result.record.attempt_id)
            self.assertTrue(result.record.worker_spec_digest)
            self.assertTrue(result.record.handoff_digest)
            self.assertIsNotNone(result.record.managed_handoff)
            self.assertEqual(
                result.record.managed_handoff.digest,
                result.record.handoff_digest,
            )
            self.assertEqual(
                result.record.managed_handoff.pr_number,
                result.record.pr_number,
            )
            self.assertEqual(classifier.calls, 1)
            self.assertEqual(handoff.calls, 1)
            self.assertEqual(
                handoff.last_worker_spec.authority_key,
                authority().authority_key,
            )

            again = self.run_pipeline(root, classifier, handoff)
            self.assertEqual(again.disposition, OrdinaryPipelineDisposition.COMPLETE)
            self.assertEqual(classifier.calls, 1)
            self.assertEqual(handoff.calls, 2)
            self.assertEqual(
                again.record.task_spec_hash,
                result.record.task_spec_hash,
            )
            self.assertEqual(again.record.attempt_id, result.record.attempt_id)

            store = OrdinaryPipelineStore.for_root(root)
            reloaded = store.load()
            durable = reloaded.get(result.record.pipeline_id)
            self.assertIsNotNone(durable)
            self.assertIsNotNone(durable.managed_handoff)
            self.assertEqual(
                durable.managed_handoff.digest,
                result.record.handoff_digest,
            )
            self.assertEqual(
                reloaded.reconstructible_managed_handoffs(),
                (durable.managed_handoff,),
            )
            self.assertEqual(reloaded.incomplete_completed_records(), ())
            self.assertEqual(
                store.adapter.path.read_bytes(),
                store.adapter.backup_path.read_bytes(),
            )

    def test_classifier_noop_never_crosses_worker_boundary(self):
        classifier = FakeClassifier(
            ClassifierDecision.from_legacy_mapping(
                {"decision": "NOOP", "reason": "nothing actionable"}
            )
        )
        handoff = FakeHandoff()
        with tempfile.TemporaryDirectory() as td:
            result = self.run_pipeline(Path(td), classifier, handoff)
        self.assertEqual(
            result.disposition,
            OrdinaryPipelineDisposition.NOT_DISPATCH,
        )
        self.assertEqual(handoff.calls, 0)

    def test_stale_source_pr_reclassifies_without_worker(self):
        classifier = FakeClassifier(dispatch_decision(pr_number=77))
        handoff = FakeHandoff()
        req = pipeline_request(
            decision_pr=77,
            freshness=DecisionFreshnessObservation(
                current_main=MAIN,
                source_pr_state=SourcePRState.OPEN,
                source_pr_head="e" * 40,
            ),
        )
        with tempfile.TemporaryDirectory() as td:
            result = self.run_pipeline(Path(td), classifier, handoff, req)
        self.assertEqual(result.disposition, OrdinaryPipelineDisposition.RECLASSIFY)
        self.assertEqual(handoff.calls, 0)

    def test_external_manual_owner_blocks_before_worker(self):
        classifier = FakeClassifier()
        handoff = FakeHandoff()
        req = pipeline_request(
            claims=(
                ExternalProducerClaim(
                    issue_number=862,
                    claimed_by="ni-da-ba",
                    lane="Implementation",
                    branch="external/862",
                ),
            )
        )
        with tempfile.TemporaryDirectory() as td:
            result = self.run_pipeline(Path(td), classifier, handoff, req)
        self.assertEqual(result.disposition, OrdinaryPipelineDisposition.BLOCKED)
        self.assertIn("external/manual", result.reason)
        self.assertEqual(handoff.calls, 0)

    def test_quota_block_does_not_cross_worker_boundary(self):
        classifier = FakeClassifier()
        handoff = FakeHandoff()
        req = pipeline_request(local_budget=LocalBudgetObservation(2, 2))
        with tempfile.TemporaryDirectory() as td:
            result = self.run_pipeline(Path(td), classifier, handoff, req)
        self.assertEqual(result.disposition, OrdinaryPipelineDisposition.BLOCKED)
        self.assertFalse(result.admission.consume_attempt)
        self.assertEqual(handoff.calls, 0)

    def test_classifier_failure_is_durable_and_not_recalled(self):
        classifier = FakeClassifier(
            error=ClassifierProviderError(
                "provider_capacity",
                900,
                "fixture capacity block",
            )
        )
        handoff = FakeHandoff()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            first = self.run_pipeline(root, classifier, handoff)
            second = self.run_pipeline(root, classifier, handoff)
        self.assertEqual(first.disposition, OrdinaryPipelineDisposition.BLOCKED)
        self.assertEqual(second.disposition, OrdinaryPipelineDisposition.BLOCKED)
        self.assertEqual(classifier.calls, 1)
        self.assertEqual(handoff.calls, 0)

    def test_classifier_crash_restarts_as_recovery_without_second_call(self):
        classifier = CrashClassifier()
        handoff = FakeHandoff()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            with self.assertRaises(KeyboardInterrupt):
                self.run_pipeline(root, classifier, handoff)
            restarted = self.run_pipeline(root, classifier, handoff)
        self.assertEqual(
            restarted.disposition,
            OrdinaryPipelineDisposition.BLOCKED,
        )
        self.assertIn("do not spend another call", restarted.reason)
        self.assertEqual(classifier.calls, 1)
        self.assertEqual(handoff.calls, 0)

    def test_writer_fence_contention_blocks_worker_handoff(self):
        classifier = FakeClassifier()
        handoff = FakeHandoff()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            fence = root / ".skyforge-platform-v2/writer.lock"
            with WriterFence(fence, "other-controller", 1):
                result = self.run_pipeline(root, classifier, handoff)
        self.assertEqual(result.disposition, OrdinaryPipelineDisposition.BLOCKED)
        self.assertIn("writer fence", result.reason)
        self.assertEqual(handoff.calls, 0)


class R5C6IsolationTest(unittest.TestCase):
    def test_concrete_port_composes_accepted_lower_layers(self):
        module = __import__("v2.ordinary_pipeline", fromlist=["x"])
        source = Path(module.__file__).read_text(encoding="utf-8")
        for required in (
            "WorkerWorkspaceManager",
            "advance_worker_run",
            "WorkspaceCommitAdapter",
            "advance_prepared_handoff",
            "GhGitOrdinaryEffectAdapter",
            "admit_dispatch",
            "advance_classifier",
            "WriterFence.for_token",
        ):
            with self.subTest(required=required):
                self.assertIn(required, source)

    def test_hosted_runtime_remains_unactivated(self):
        root = Path(__file__).resolve().parent
        source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        self.assertNotIn("ordinary_pipeline", source)
        self.assertNotIn("advance_ordinary_pipeline", source)
        self.assertIn('"mutation_authority": False', source)
        self.assertIn('"ordinary_v2_mutation_authority": False', source)


if __name__ == "__main__":
    unittest.main()
