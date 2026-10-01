from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from v2.core import (
    ControllerState,
    ManagedPRObservation,
    ManagedPRState,
    PendingWorkerState,
    reduce_managed_pr,
)
from v2.domain import CIState, PRClass, TransitionKind
from v2.parity import load_legacy_corpus
from v2.state_store import JsonStateStoreAdapter, StateStoreError


HEAD = "a" * 40
OLD_HEAD = "c" * 40
SPEC = "b" * 64


def managed_mapping(
    *,
    pr_number: int = 481,
    branch: str = "codex/implementation-task-467-test",
    expected_head: str = HEAD,
    eligible: bool = True,
) -> dict:
    return {
        "managed": {
            "Implementation": {
                "branch": branch,
                "pr_number": pr_number,
                "authority_key": "task:467",
                "expected_head": expected_head,
                "changed_paths": ["src/A.java"],
                "auto_merge_eligible": eligible,
            }
        },
        "pending_worker": None,
        "human_gate_records": {},
        "paused": False,
    }


def observation(
    *,
    pr_number: int = 481,
    active_pr: bool = True,
    base_branch: str = "main",
    head_branch: str = "codex/implementation-task-467-test",
    head: str = HEAD,
    evidence: str = HEAD,
    reviewed: str = HEAD,
    ci_state: CIState = CIState.PASS,
    pr_class: PRClass = PRClass.DELIVERY,
    human_gate_pending: bool = False,
    review_required: bool = False,
) -> ManagedPRObservation:
    return ManagedPRObservation(
        lane="Implementation",
        pr_number=pr_number,
        active_pr=active_pr,
        base_branch=base_branch,
        head_branch=head_branch,
        current_head_sha=head,
        evidence_sha=evidence,
        reviewed_sha=reviewed,
        task_spec_hash=SPEC,
        accepted_task_spec_hash=SPEC,
        ci_state=ci_state,
        pr_class=pr_class,
        human_gate_pending=human_gate_pending,
        review_required=review_required,
    )


class ControllerStateProjectionTest(unittest.TestCase):
    def test_current_style_mapping_projects_deterministically(self) -> None:
        first = managed_mapping()
        first["human_gate_records"] = {
            "pr:481:Implementation": {
                "token": HEAD,
                "target": "481",
                "seeded_from_github": False,
            }
        }
        second = {
            "paused": False,
            "human_gate_records": first["human_gate_records"],
            "pending_worker": None,
            "managed": first["managed"],
        }

        a = ControllerState.from_legacy_mapping(first)
        b = ControllerState.from_legacy_mapping(second)

        self.assertEqual(a, b)
        self.assertEqual(a.digest, b.digest)
        self.assertEqual(a.managed_for("Implementation").pr_number, 481)

    def test_legacy_record_without_new_optional_metadata_is_safe_but_incomplete(self) -> None:
        state = ControllerState.from_legacy_mapping(
            {
                "managed": {
                    "Implementation": {
                        "branch": "codex/old",
                        "pr_number": 10,
                    }
                },
                "pending_worker": None,
                "human_gate_records": {},
            }
        )
        record = state.managed_for("Implementation")
        self.assertEqual(record.authority_key, "")
        self.assertEqual(record.expected_head, "")
        self.assertFalse(record.auto_merge_eligible)

    def test_malformed_managed_pending_and_gate_structures_fail_closed(self) -> None:
        bad_states = [
            {"managed": [], "pending_worker": None, "human_gate_records": {}},
            {"managed": {}, "pending_worker": "worker", "human_gate_records": {}},
            {
                "managed": {},
                "pending_worker": None,
                "human_gate_records": {"gate": {"target": "1"}},
            },
            {
                "managed": {
                    "Implementation": {
                        "branch": "",
                        "pr_number": 1,
                    }
                },
                "pending_worker": None,
                "human_gate_records": {},
            },
        ]
        for raw in bad_states:
            with self.subTest(raw=raw):
                with self.assertRaises(ValueError):
                    ControllerState.from_legacy_mapping(raw)


class JsonStateStoreAdapterTest(unittest.TestCase):
    def test_dual_file_save_and_primary_load(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            adapter = JsonStateStoreAdapter.for_legacy_root(Path(td))
            saved = adapter.save(managed_mapping())
            loaded = adapter.load()

            self.assertTrue(adapter.path.exists())
            self.assertTrue(adapter.backup_path.exists())
            self.assertFalse(loaded.recovered_from_backup)
            self.assertEqual(loaded.source, "state.json")
            self.assertEqual(saved.digest, loaded.digest)

    def test_corrupt_primary_recovers_valid_backup(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            adapter = JsonStateStoreAdapter.for_legacy_root(Path(td))
            adapter.save(managed_mapping())
            adapter.path.write_text("{not-json", encoding="utf-8")

            loaded = adapter.load()

            self.assertTrue(loaded.recovered_from_backup)
            self.assertEqual(loaded.source, "state.json.bak")
            self.assertEqual(
                ControllerState.from_legacy_mapping(loaded.as_dict()).managed_for(
                    "Implementation"
                ).pr_number,
                481,
            )

    def test_dual_corruption_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            adapter = JsonStateStoreAdapter.for_legacy_root(Path(td))
            adapter.path.parent.mkdir(parents=True, exist_ok=True)
            adapter.path.write_text("{bad", encoding="utf-8")
            adapter.backup_path.write_text("[]", encoding="utf-8")

            with self.assertRaises(StateStoreError):
                adapter.load()

    def test_missing_state_matches_legacy_empty_start(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            adapter = JsonStateStoreAdapter.for_legacy_root(Path(td))
            loaded = adapter.load()
            self.assertEqual(loaded.as_dict(), {})
            self.assertEqual(loaded.source, "defaults")


class PureManagedPRCoreTest(unittest.TestCase):
    def test_exact_owned_head_green_is_merge_eligible(self) -> None:
        state = ControllerState.from_legacy_mapping(managed_mapping())
        decision = reduce_managed_pr(state, observation())
        self.assertEqual(decision.transition.kind, TransitionKind.MERGE_ELIGIBLE)
        self.assertEqual(decision.state_digest, state.digest)

    def test_unowned_or_wrong_target_pr_reconciles(self) -> None:
        state = ControllerState.from_legacy_mapping(managed_mapping())
        self.assertEqual(
            reduce_managed_pr(state, observation(pr_number=999)).transition.kind,
            TransitionKind.RECONCILE,
        )
        self.assertEqual(
            reduce_managed_pr(
                state, observation(base_branch="release")
            ).transition.kind,
            TransitionKind.RECONCILE,
        )

    def test_managed_branch_identity_drift_reconciles(self) -> None:
        state = ControllerState.from_legacy_mapping(managed_mapping())
        decision = reduce_managed_pr(
            state,
            observation(head_branch="codex/unrelated"),
        )
        self.assertEqual(decision.transition.kind, TransitionKind.RECONCILE)

    def test_expected_head_movement_invalidates_evidence(self) -> None:
        state = ControllerState.from_legacy_mapping(
            managed_mapping(expected_head=OLD_HEAD)
        )
        decision = reduce_managed_pr(
            state,
            observation(head=HEAD, evidence=OLD_HEAD, reviewed=OLD_HEAD),
        )
        self.assertEqual(
            decision.transition.kind,
            TransitionKind.INVALIDATE_EVIDENCE,
        )

    def test_pending_worker_blocks_merge(self) -> None:
        raw = managed_mapping()
        raw["pending_worker"] = {
            "branch": "codex/implementation-task-467-test",
            "authority_key": "task:467",
        }
        state = ControllerState.from_legacy_mapping(raw)
        self.assertEqual(
            reduce_managed_pr(state, observation()).transition.kind,
            TransitionKind.WAIT,
        )

    def test_machine_ineligible_and_review_work_are_human_gates(self) -> None:
        ineligible = ControllerState.from_legacy_mapping(
            managed_mapping(eligible=False)
        )
        self.assertEqual(
            reduce_managed_pr(ineligible, observation()).transition.kind,
            TransitionKind.HUMAN_GATE,
        )

        eligible = ControllerState.from_legacy_mapping(managed_mapping())
        self.assertEqual(
            reduce_managed_pr(
                eligible,
                observation(review_required=True),
            ).transition.kind,
            TransitionKind.HUMAN_GATE,
        )

    def test_ci_states_preserve_mechanical_policy(self) -> None:
        state = ControllerState.from_legacy_mapping(managed_mapping())
        self.assertEqual(
            reduce_managed_pr(
                state, observation(ci_state=CIState.PENDING)
            ).transition.kind,
            TransitionKind.WAIT,
        )
        self.assertEqual(
            reduce_managed_pr(
                state, observation(ci_state=CIState.FAIL)
            ).transition.kind,
            TransitionKind.REPAIR_ELIGIBLE,
        )

    def test_closed_pr_does_not_invent_work(self) -> None:
        state = ControllerState.from_legacy_mapping(managed_mapping())
        self.assertEqual(
            reduce_managed_pr(
                state, observation(active_pr=False)
            ).transition.kind,
            TransitionKind.NOOP,
        )

    def test_incomplete_older_managed_record_reconciles(self) -> None:
        state = ControllerState(
            managed=(
                ManagedPRState(
                    lane="Implementation",
                    pr_number=481,
                    branch="codex/implementation-task-467-test",
                ),
            )
        )
        self.assertEqual(
            reduce_managed_pr(state, observation()).transition.kind,
            TransitionKind.RECONCILE,
        )


class LegacyCorpusThroughCoreTest(unittest.TestCase):
    def test_existing_release1_corpus_matches_through_release2_core(self) -> None:
        fixture = (
            Path(__file__).resolve().parent
            / "v2"
            / "fixtures"
            / "legacy_transition_cases.json"
        )
        cases = load_legacy_corpus(fixture)

        for case in cases:
            snap = case.snapshot
            expected_head = (
                snap.evidence_sha
                if snap.evidence_sha and snap.evidence_sha != snap.current_head_sha
                else snap.current_head_sha
            )
            raw = managed_mapping(
                expected_head=expected_head,
                eligible=snap.pr_class is PRClass.DELIVERY,
            )
            if snap.pending_worker:
                raw["pending_worker"] = {
                    "branch": "codex/implementation-task-467-test",
                    "authority_key": "task:467",
                }
            state = ControllerState.from_legacy_mapping(raw)
            observed = ManagedPRObservation(
                lane="Implementation",
                pr_number=481,
                active_pr=snap.active_pr,
                base_branch="main",
                head_branch="codex/implementation-task-467-test",
                current_head_sha=snap.current_head_sha,
                evidence_sha=snap.evidence_sha,
                reviewed_sha=snap.reviewed_sha,
                task_spec_hash=snap.task_spec_hash,
                accepted_task_spec_hash=snap.accepted_task_spec_hash,
                ci_state=snap.ci_state,
                pr_class=snap.pr_class,
                human_gate_pending=snap.human_gate_pending,
                review_required=snap.review_required,
            )

            with self.subTest(case=case.case_id):
                self.assertEqual(
                    reduce_managed_pr(state, observed).transition.kind,
                    case.expected_kind,
                )


if __name__ == "__main__":
    unittest.main()
