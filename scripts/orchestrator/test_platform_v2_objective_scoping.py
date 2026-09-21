from __future__ import annotations

import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from v2.classifier_provider import ClassifierProviderConfig
from v2.decision import ClassifierDecision, DecisionKind, WorkerTier
from v2.objective_ingress import (
    DevelopmentApiObjectiveSource,
    ObjectiveProposalStore,
    ScopedObjectiveSource,
)
from v2.objective_intake import load_manifest
from v2.objective_lifecycle import (
    ObjectiveLifecycleOperation,
    ObjectiveLifecycleStore,
    effective_objective_progression,
)
from v2.objective_trace import build_objective_trace
from v2.objective_scoping import (
    ObjectiveScopeStatus,
    ObjectiveScopingAdvanceDisposition,
    ObjectiveScopeStore,
    advance_objective_scoping,
    ensure_scoped_issue,
)
from v2.quota import LocalBudgetObservation

REPO_ROOT = Path(__file__).resolve().parents[2]
CONTEXT_FILES = (
    "docs/agent-state/ORCHESTRATOR_ROADMAP.json",
    "docs/agent-state/CURRENT_PROJECT_STATE.md",
    "docs/agent-state/PROGRAM_CHARTER.md",
    "docs/agent-state/CROSS_LANE_CONTRACTS.md",
    "docs/agent-state/EXECUTION_BOUNDARIES.md",
    "docs/agent-state/VALIDATION_POLICY.md",
    "docs/agent-state/IMPLEMENTATION_STATE.md",
    "docs/agent-state/AUTHORSHIP_STATE.md",
)


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def prepare_repo(root: Path) -> None:
    for rel in CONTEXT_FILES:
        dst = root / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO_ROOT / rel, dst)
    for rel, text in {
        "src/example.py": "def current():\n    return 1\n",
        "tests/test_example.py": "def test_current():\n    assert True\n",
        "scripts/orchestrator/example_control.py": "# protected control plane\n",
    }.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
    git(root, "init", "-q")
    git(root, "config", "user.email", "test@example.com")
    git(root, "config", "user.name", "Test")
    git(root, "add", ".")
    git(root, "commit", "-qm", "fixture")

    manifest = load_manifest(root)
    state = {
        "paused": False,
        "blocked_kind": None,
        "pending_worker": None,
        "pending_decision": None,
        "managed": {},
        "pending_events": [],
        "external_producer_claims": {},
        "human_gate_records": {},
        "roadmap": {
            "roadmap_id": manifest.roadmap_id,
            "manifest_fingerprint": manifest.fingerprint,
            "completed_runs": {},
            "blocked_nodes": {},
            "active": None,
            "claims_day": "2026-09-21",
            "claims_today": 0,
        },
    }
    state_dir = root / ".skyforge-orchestrator"
    state_dir.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(state, sort_keys=True, indent=2) + "\n"
    (state_dir / "state.json").write_text(payload)
    (state_dir / "state.json.bak").write_text(payload)


def parent(root: Path, request_id: str = "scope-parent-1"):
    source = DevelopmentApiObjectiveSource(
        repo="ni-da-ba/skyforge",
        request_id=request_id,
        actor="ni-da-ba",
        client="operations-console",
        submitted_at="2026-09-21T15:45:00Z",
        objective_text="Fix the example implementation without changing product semantics",
    )
    return ObjectiveProposalStore.for_root(root).capture(
        source=source,
        delivery_id="",
        root=root,
    ).record


class FakeClassifier:
    def __init__(self, decision: ClassifierDecision):
        self.decision = decision
        self.calls = 0

    def classify(self, *, request, root: Path, config):
        self.calls += 1
        raw = json.dumps(self.decision.as_dict(), sort_keys=True)
        return raw, self.decision


def safe_decision() -> ClassifierDecision:
    return ClassifierDecision(
        kind=DecisionKind.DISPATCH,
        lane="Implementation",
        objective="Fix the example implementation without changing product semantics",
        stop_boundary="Stop after exact scoped source/test changes are ready for normal validation.",
        worker_tier=WorkerTier.LUNA,
        allowed_paths=("src/example.py", "tests/test_example.py"),
        reason="one bounded implementation scope is clear",
    )


def protected_decision() -> ClassifierDecision:
    return ClassifierDecision(
        kind=DecisionKind.DISPATCH,
        lane="Audit",
        objective="Fix the orchestrator control plane",
        stop_boundary="Stop before control-plane mutation.",
        worker_tier=WorkerTier.LUNA,
        allowed_paths=("scripts/orchestrator/example_control.py",),
        reason="control-plane scope",
    )


class IssueRunner:
    def __init__(self):
        self.issue = None
        self.create_calls = 0
        self.list_calls = 0

    def assert_supported_issue_list_command(self, command):
        self.list_calls += 1
        if "--slurp" in command:
            raise AssertionError("deployed gh CLI does not support --slurp")
        if "--paginate" not in command or "--jq" not in command:
            raise AssertionError("issue discovery must remain paginated and bounded to exact fields")

    def __call__(self, args, **kwargs):
        command = tuple(args)
        if command[:3] == ("gh", "api", "repos/ni-da-ba/skyforge/issues?state=all&per_page=100"):
            self.assert_supported_issue_list_command(command)
            output = "" if self.issue is None else json.dumps(self.issue) + "\n"
            return subprocess.CompletedProcess(args, 0, stdout=output, stderr="")
        if command[:4] == ("gh", "api", "--method", "POST"):
            self.create_calls += 1
            title = next(value.removeprefix("title=") for value in args if value.startswith("title="))
            body = next(value.removeprefix("body=") for value in args if value.startswith("body="))
            self.issue = {"number": 1200, "title": title, "body": body}
            return subprocess.CompletedProcess(args, 0, stdout="1200\n", stderr="")
        if command[:4] == ("gh", "issue", "view", "1200"):
            if self.issue is None:
                raise AssertionError("scoped issue must exist before retrieval")
            payload = {
                **self.issue,
                "comments": [],
                "updatedAt": "2026-09-21T15:50:00Z",
                "state": "OPEN",
            }
            return subprocess.CompletedProcess(args, 0, stdout=json.dumps(payload), stderr="")
        raise AssertionError(args)


class ObjectiveScopingTest(unittest.TestCase):
    def test_fix_objective_reaches_bounded_scoped_state(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_repo(root)
            proposal = parent(root)
            provider = FakeClassifier(safe_decision())
            budget = LocalBudgetObservation(calls_used=0, daily_limit=10)
            config = ClassifierProviderConfig(model="test", reasoning_effort="low")

            first = advance_objective_scoping(
                root=root,
                repo="ni-da-ba/skyforge",
                classifier_provider=provider,
                classifier_local_budget=budget,
                classifier_config=config,
            )
            self.assertEqual(first.disposition, ObjectiveScopingAdvanceDisposition.PREPARED)
            self.assertEqual(provider.calls, 0)

            second = advance_objective_scoping(
                root=root,
                repo="ni-da-ba/skyforge",
                classifier_provider=provider,
                classifier_local_budget=budget,
                classifier_config=config,
            )
            self.assertEqual(second.disposition, ObjectiveScopingAdvanceDisposition.CLASSIFIER_ADVANCED)
            self.assertEqual(second.record.status, ObjectiveScopeStatus.SCOPED)
            self.assertEqual(second.record.parent_proposal_id, proposal.proposal_id)
            self.assertEqual(second.record.proposed_paths, ("src/example.py", "tests/test_example.py"))
            self.assertEqual(provider.calls, 1)

            trace = build_objective_trace(root=root, correlation_id=proposal.proposal_id)
            scoping = next(value for value in trace["stages"] if value["stage"] == "OBJECTIVE_SCOPING")
            self.assertEqual(scoping["status"], "SCOPED")
            self.assertEqual(scoping["identities"]["parent_proposal_id"], proposal.proposal_id)

    def test_protected_control_plane_scope_stops_at_human_gate(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_repo(root)
            parent(root)
            provider = FakeClassifier(protected_decision())
            budget = LocalBudgetObservation(calls_used=0, daily_limit=10)
            config = ClassifierProviderConfig(model="test", reasoning_effort="low")
            advance_objective_scoping(
                root=root,
                repo="ni-da-ba/skyforge",
                classifier_provider=provider,
                classifier_local_budget=budget,
                classifier_config=config,
            )
            result = advance_objective_scoping(
                root=root,
                repo="ni-da-ba/skyforge",
                classifier_provider=provider,
                classifier_local_budget=budget,
                classifier_config=config,
            )
            self.assertEqual(result.disposition, ObjectiveScopingAdvanceDisposition.HUMAN_GATE)
            self.assertEqual(result.record.status, ObjectiveScopeStatus.HUMAN_GATE)
            self.assertIn("protected control-plane paths", result.reason)

    def test_scope_issue_is_exact_and_idempotent_then_child_inherits_parent_control(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_repo(root)
            proposal = parent(root)
            provider = FakeClassifier(safe_decision())
            budget = LocalBudgetObservation(calls_used=0, daily_limit=10)
            config = ClassifierProviderConfig(model="test", reasoning_effort="low")
            advance_objective_scoping(
                root=root, repo="ni-da-ba/skyforge", classifier_provider=provider,
                classifier_local_budget=budget, classifier_config=config,
            )
            scoped = advance_objective_scoping(
                root=root, repo="ni-da-ba/skyforge", classifier_provider=provider,
                classifier_local_budget=budget, classifier_config=config,
            ).record
            runner = IssueRunner()
            issue = ensure_scoped_issue(
                root=root,
                repo="ni-da-ba/skyforge",
                parent=proposal,
                record=scoped,
                runner=runner,
            )
            replay = ensure_scoped_issue(
                root=root,
                repo="ni-da-ba/skyforge",
                parent=proposal,
                record=scoped,
                runner=runner,
            )
            self.assertEqual(issue, 1200)
            self.assertEqual(replay, 1200)
            self.assertEqual(runner.create_calls, 1)
            self.assertIn("src/example.py", runner.issue["body"])

            issue_step = advance_objective_scoping(
                root=root, repo="ni-da-ba/skyforge", classifier_provider=provider,
                classifier_local_budget=budget, classifier_config=config, runner=runner,
            )
            self.assertEqual(issue_step.disposition, ObjectiveScopingAdvanceDisposition.ISSUE_ADVANCED)
            child_step = advance_objective_scoping(
                root=root, repo="ni-da-ba/skyforge", classifier_provider=provider,
                classifier_local_budget=budget, classifier_config=config, runner=runner,
            )
            self.assertEqual(child_step.disposition, ObjectiveScopingAdvanceDisposition.CHILD_CREATED)
            child = next(
                value for value in ObjectiveProposalStore.for_root(root).load().records
                if value.proposal_id == child_step.record.child_proposal_id
            )
            self.assertIsInstance(child.source, ScopedObjectiveSource)
            self.assertEqual(child.source.parent_proposal_id, proposal.proposal_id)
            self.assertEqual(child.compiled.disposition.value, "CANDIDATE_TASK")

            ObjectiveLifecycleStore.for_root(root).apply(
                root=root,
                request_id="pause-scoped-parent",
                proposal_id=proposal.proposal_id,
                operation=ObjectiveLifecycleOperation.PAUSE,
                reason="operator pause",
                actor="ni-da-ba",
                client="test",
            )
            decision = effective_objective_progression(root, child.proposal_id)
            self.assertFalse(decision.allowed)
            self.assertEqual(decision.controlled_by_proposal_id, proposal.proposal_id)

    def test_scoped_child_reuses_existing_context_retrieval_and_promotion(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_repo(root)
            parent(root)
            provider = FakeClassifier(safe_decision())
            budget = LocalBudgetObservation(calls_used=0, daily_limit=10)
            config = ClassifierProviderConfig(model="test", reasoning_effort="low")
            runner = IssueRunner()

            results = []
            for _ in range(7):
                results.append(
                    advance_objective_scoping(
                        root=root,
                        repo="ni-da-ba/skyforge",
                        classifier_provider=provider,
                        classifier_local_budget=budget,
                        classifier_config=config,
                        runner=runner,
                    )
                )

            self.assertEqual(
                [value.disposition for value in results],
                [
                    ObjectiveScopingAdvanceDisposition.PREPARED,
                    ObjectiveScopingAdvanceDisposition.CLASSIFIER_ADVANCED,
                    ObjectiveScopingAdvanceDisposition.ISSUE_ADVANCED,
                    ObjectiveScopingAdvanceDisposition.CHILD_CREATED,
                    ObjectiveScopingAdvanceDisposition.PACKAGE_CREATED,
                    ObjectiveScopingAdvanceDisposition.RETRIEVAL_CREATED,
                    ObjectiveScopingAdvanceDisposition.PROMOTION_CREATED,
                ],
            )
            final = results[-1].record
            self.assertEqual(final.status, ObjectiveScopeStatus.PROMOTION_READY)
            self.assertTrue(final.child_proposal_id)
            self.assertTrue(final.package_id)
            self.assertTrue(final.retrieval_id)
            self.assertTrue(final.promotion_id)
            self.assertEqual(runner.create_calls, 1)

    def test_scoping_ledger_round_trips(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_repo(root)
            parent(root)
            provider = FakeClassifier(safe_decision())
            budget = LocalBudgetObservation(calls_used=0, daily_limit=10)
            config = ClassifierProviderConfig(model="test", reasoning_effort="low")
            advance_objective_scoping(
                root=root, repo="ni-da-ba/skyforge", classifier_provider=provider,
                classifier_local_budget=budget, classifier_config=config,
            )
            advance_objective_scoping(
                root=root, repo="ni-da-ba/skyforge", classifier_provider=provider,
                classifier_local_budget=budget, classifier_config=config,
            )
            first = ObjectiveScopeStore.for_root(root).load()
            second = ObjectiveScopeStore.for_root(root).load()
            self.assertEqual(first, second)
            self.assertEqual(first.records[0].status, ObjectiveScopeStatus.SCOPED)


if __name__ == "__main__":
    unittest.main()
