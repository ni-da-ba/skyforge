from __future__ import annotations

import json
from pathlib import Path
import subprocess
import tempfile
import unittest

from v2.domain import CIState, PRClass, TransitionKind
from v2.managed_pr_observation import (
    GhManagedPRObserver,
    ManagedPRReadCommandValidator,
    ManagedPRRemoteUnavailable,
    ManagedPRTruthDisposition,
    PR_VIEW_FIELDS,
    decide_managed_pr_truth,
    observe_managed_pr_truth,
)
from v2.ordinary_effects import OrdinaryMutationScope
from v2.ordinary_service import ManagedOrdinaryHandoff


HEAD = "a" * 40
BASE = "b" * 40
ATTEMPT = "c" * 64
SPEC = "d" * 64


def handoff(*, auto=True):
    return ManagedOrdinaryHandoff(
        task_id="github-issue-872-comment-1",
        authority_key="github-task-authority:" + "e" * 64,
        task_spec_hash=SPEC,
        lane="Implementation",
        scope=OrdinaryMutationScope(
            attempt_id=ATTEMPT,
            repo="ni-da-ba/skyforge",
            base_sha=BASE,
            branch="codex/implementation-r5c11",
            expected_head_sha=HEAD,
            pr_title="R5C11 managed observation",
            pr_body="Exact frozen PR body.",
            issue_number=872,
        ),
        pr_number=902,
        changed_paths=(
            "docs/operations/a.md",
            "scripts/orchestrator/v2/example.py",
        ),
        auto_merge_eligible=auto,
    )


def pr(**overrides):
    values = {
        "number": 902,
        "state": "OPEN",
        "mergedAt": None,
        "isDraft": True,
        "mergeStateStatus": "CLEAN",
        "statusCheckRollup": [
            {"name": "build", "status": "COMPLETED", "conclusion": "SUCCESS"}
        ],
        "headRefName": "codex/implementation-r5c11",
        "headRefOid": HEAD,
        "baseRefName": "main",
        "title": "R5C11 managed observation",
        "body": "Exact frozen PR body.",
        "reviewDecision": "",
    }
    values.update(overrides)
    return values


class ManagedPRProjectionTest(unittest.TestCase):
    def test_exact_machine_only_truth_projects_exact_acceptance_identity(self):
        result = observe_managed_pr_truth(
            handoff=handoff(),
            pr=pr(),
            changed_paths=("docs/operations/a.md",),
        )
        self.assertEqual(result.disposition, ManagedPRTruthDisposition.OBSERVED)
        obs = result.observation
        self.assertEqual(obs.ci_state, CIState.PASS)
        self.assertEqual(obs.evidence_sha, HEAD)
        self.assertEqual(obs.reviewed_sha, HEAD)
        self.assertEqual(obs.accepted_task_spec_hash, SPEC)
        self.assertEqual(obs.pr_class, PRClass.DELIVERY)
        self.assertFalse(obs.human_gate_pending)
        self.assertFalse(obs.review_required)
        self.assertEqual(result.merge_state, "CLEAN")

    def test_pending_and_failed_ci_never_create_evidence_sha(self):
        pending = pr(
            statusCheckRollup=[
                {"name": "build", "status": "IN_PROGRESS", "conclusion": ""}
            ]
        )
        failed = pr(
            statusCheckRollup=[
                {"name": "build", "status": "COMPLETED", "conclusion": "FAILURE"}
            ]
        )
        for value, expected in ((pending, CIState.PENDING), (failed, CIState.FAIL)):
            result = observe_managed_pr_truth(
                handoff=handoff(),
                pr=value,
                changed_paths=("docs/operations/a.md",),
            )
            with self.subTest(expected=expected):
                self.assertEqual(result.observation.ci_state, expected)
                self.assertEqual(result.observation.evidence_sha, "")

    def test_no_checks_or_only_neutral_is_unknown(self):
        for checks in (
            [],
            [{"name": "lint", "status": "COMPLETED", "conclusion": "NEUTRAL"}],
        ):
            result = observe_managed_pr_truth(
                handoff=handoff(),
                pr=pr(statusCheckRollup=checks),
                changed_paths=("docs/operations/a.md",),
            )
            with self.subTest(checks=checks):
                self.assertEqual(result.observation.ci_state, CIState.UNKNOWN)
                self.assertEqual(result.observation.evidence_sha, "")

    def test_review_requirement_is_human_gate_and_cannot_mechanically_accept(self):
        for review in ("REVIEW_REQUIRED", "CHANGES_REQUESTED"):
            result = observe_managed_pr_truth(
                handoff=handoff(),
                pr=pr(reviewDecision=review),
                changed_paths=("docs/operations/a.md",),
            )
            with self.subTest(review=review):
                obs = result.observation
                self.assertTrue(obs.review_required)
                self.assertTrue(obs.human_gate_pending)
                self.assertEqual(obs.pr_class, PRClass.HUMAN_GATE)
                self.assertEqual(obs.reviewed_sha, "")
                self.assertEqual(obs.accepted_task_spec_hash, "")
                self.assertEqual(obs.evidence_sha, "")

    def test_non_auto_merge_handoff_is_always_human_gate_class(self):
        result = observe_managed_pr_truth(
            handoff=handoff(auto=False),
            pr=pr(),
            changed_paths=("docs/operations/a.md",),
        )
        obs = result.observation
        self.assertEqual(obs.ci_state, CIState.PASS)
        self.assertTrue(obs.human_gate_pending)
        self.assertEqual(obs.pr_class, PRClass.HUMAN_GATE)
        self.assertEqual(obs.reviewed_sha, "")
        self.assertEqual(obs.accepted_task_spec_hash, "")
        self.assertEqual(obs.evidence_sha, "")

    def test_identity_head_title_body_and_path_drift_fail_closed(self):
        cases = [
            (pr(headRefOid="f" * 40), ("docs/operations/a.md",)),
            (pr(headRefName="other"), ("docs/operations/a.md",)),
            (pr(baseRefName="release"), ("docs/operations/a.md",)),
            (pr(title="edited"), ("docs/operations/a.md",)),
            (pr(body="edited"), ("docs/operations/a.md",)),
            (pr(), ("outside/scope.txt",)),
        ]
        for value, paths in cases:
            result = observe_managed_pr_truth(
                handoff=handoff(),
                pr=value,
                changed_paths=paths,
            )
            with self.subTest(value=value, paths=paths):
                self.assertEqual(
                    result.disposition,
                    ManagedPRTruthDisposition.REJECTED,
                )
                self.assertIsNone(result.observation)

    def test_observed_truth_reduces_without_mutation(self):
        ready = observe_managed_pr_truth(
            handoff=handoff(),
            pr=pr(),
            changed_paths=("docs/operations/a.md",),
        )
        self.assertEqual(
            decide_managed_pr_truth(handoff=handoff(), truth=ready).transition.kind,
            TransitionKind.MERGE_ELIGIBLE,
        )

        pending = observe_managed_pr_truth(
            handoff=handoff(),
            pr=pr(
                statusCheckRollup=[
                    {"name": "build", "status": "IN_PROGRESS", "conclusion": ""}
                ]
            ),
            changed_paths=("docs/operations/a.md",),
        )
        self.assertEqual(
            decide_managed_pr_truth(handoff=handoff(), truth=pending).transition.kind,
            TransitionKind.WAIT,
        )

        gated_handoff = handoff(auto=False)
        gated = observe_managed_pr_truth(
            handoff=gated_handoff,
            pr=pr(),
            changed_paths=("docs/operations/a.md",),
        )
        self.assertEqual(
            decide_managed_pr_truth(
                handoff=gated_handoff,
                truth=gated,
            ).transition.kind,
            TransitionKind.HUMAN_GATE,
        )

    def test_rejected_truth_cannot_enter_reducer(self):
        value = handoff()
        rejected = observe_managed_pr_truth(
            handoff=value,
            pr=pr(headRefOid="f" * 40),
            changed_paths=("docs/operations/a.md",),
        )
        with self.assertRaises(ValueError):
            decide_managed_pr_truth(handoff=value, truth=rejected)

    def test_closed_exact_pr_is_observed_inactive_without_acceptance(self):
        result = observe_managed_pr_truth(
            handoff=handoff(),
            pr=pr(state="CLOSED"),
            changed_paths=("docs/operations/a.md",),
        )
        self.assertEqual(result.disposition, ManagedPRTruthDisposition.OBSERVED)
        obs = result.observation
        self.assertFalse(obs.active_pr)
        self.assertEqual(obs.reviewed_sha, "")
        self.assertEqual(obs.accepted_task_spec_hash, "")
        self.assertEqual(obs.evidence_sha, "")


class FakeResult:
    def __init__(self, stdout):
        self.stdout = stdout
        self.stderr = ""


class GhManagedPRObserverTest(unittest.TestCase):
    def test_exact_read_only_commands_and_result(self):
        value = handoff()
        calls = []

        def runner(args, **kwargs):
            calls.append(tuple(args))
            if args[1:3] == ["pr", "view"]:
                return FakeResult(json.dumps(pr()))
            if args[1:3] == ["pr", "diff"]:
                return FakeResult("docs/operations/a.md\n")
            raise AssertionError(args)

        with tempfile.TemporaryDirectory() as td:
            result = GhManagedPRObserver(
                root=Path(td),
                handoff=value,
                runner=runner,
            ).observe()

        self.assertEqual(result.disposition, ManagedPRTruthDisposition.OBSERVED)
        self.assertEqual(
            calls,
            [
                (
                    "gh","pr","view","902","--repo","ni-da-ba/skyforge",
                    "--json",PR_VIEW_FIELDS,"--jq=.",
                ),
                (
                    "gh","pr","diff","902","--repo","ni-da-ba/skyforge",
                    "--name-only",
                ),
            ],
        )

    def test_validator_rejects_every_mutation_escape(self):
        validator = ManagedPRReadCommandValidator(handoff())
        forbidden = (
            ["gh","pr","ready","902","--repo","ni-da-ba/skyforge"],
            ["gh","pr","merge","902","--repo","ni-da-ba/skyforge","--merge"],
            ["gh","issue","comment","872","--body","x"],
            ["git","push","origin","main"],
            ["gh","pr","view","903","--repo","ni-da-ba/skyforge","--json",PR_VIEW_FIELDS,"--jq=."],
        )
        for command in forbidden:
            with self.subTest(command=command), self.assertRaises(ValueError):
                validator.validate(command)

    def test_malformed_json_and_remote_failure_fail_closed(self):
        def bad_json(args, **kwargs):
            return FakeResult("{bad")

        with tempfile.TemporaryDirectory() as td:
            with self.assertRaises(ManagedPRRemoteUnavailable):
                GhManagedPRObserver(
                    root=Path(td),
                    handoff=handoff(),
                    runner=bad_json,
                ).observe()

        def unavailable(args, **kwargs):
            raise subprocess.CalledProcessError(1, args, stderr="network down")

        with tempfile.TemporaryDirectory() as td:
            with self.assertRaises(ManagedPRRemoteUnavailable):
                GhManagedPRObserver(
                    root=Path(td),
                    handoff=handoff(),
                    runner=unavailable,
                ).observe()


if __name__ == "__main__":
    unittest.main()
