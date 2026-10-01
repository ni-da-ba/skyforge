from __future__ import annotations

import json
import pathlib
import sys
import tempfile
import unittest
from unittest import mock

MODULE_DIR = pathlib.Path(__file__).resolve().parent
if str(MODULE_DIR) not in sys.path:
    sys.path.insert(0, str(MODULE_DIR))

import skyforge_orchestrator as legacy
from v2.decision import (
    CachedDecisionDisposition,
    ClassifierDecision,
    DecisionFreshnessObservation,
    DecisionKind,
    PendingDecisionRecord,
    SourcePRState,
    WorkerTier,
    cached_decision_disposition,
    cached_decision_is_current,
)


def record_mapping(**decision_overrides):
    decision = {
        "decision": "DISPATCH",
        "lane": "Audit",
        "pr_number": 77,
        "objective": "repair bounded state",
        "stop_boundary": "tests green",
        "reusable_evidence": "existing evidence",
        "worker_tier": "TERRA",
        "allowed_paths": None,
        "reason": "bounded work",
        "human_message": None,
    }
    decision.update(decision_overrides)
    return {
        "decision": decision,
        "event_keys": [],
        "authority_event_keys": [],
        "ordinary_event_keys": [],
        "task_issue_numbers": [],
        "captured_at": "2026-09-17T21:00:00+00:00",
        "snapshot_main": "mainhead",
        "source_pr_head": "prhead",
    }


class DecisionProjectionTest(unittest.TestCase):
    def test_current_record_projects_deterministically(self) -> None:
        first = record_mapping()
        second = {
            "source_pr_head": first["source_pr_head"],
            "snapshot_main": first["snapshot_main"],
            "captured_at": first["captured_at"],
            "task_issue_numbers": first["task_issue_numbers"],
            "ordinary_event_keys": first["ordinary_event_keys"],
            "authority_event_keys": first["authority_event_keys"],
            "event_keys": first["event_keys"],
            "decision": dict(reversed(list(first["decision"].items()))),
        }
        a = PendingDecisionRecord.from_legacy_mapping(first)
        b = PendingDecisionRecord.from_legacy_mapping(second)
        self.assertEqual(a, b)
        self.assertEqual(a.digest, b.digest)

    def test_historical_json_event_keys_use_r2b_compatibility_boundary(self) -> None:
        event = legacy.EventDecision(
            True,
            "Audit/watchdog orchestration signal",
            "issue_comment",
            action="audit_signal",
            pr_number=349,
            source_id="100",
            signal_kind="human_gate",
            signal_text="AUDIT — HUMAN GATE",
        )
        payload = event.to_state()
        payload.pop("observed_at", None)
        old_key = json.dumps(payload, sort_keys=True, separators=(",", ":"))

        raw = record_mapping(decision="NOOP", pr_number=None)
        raw["event_keys"] = [old_key]

        record = PendingDecisionRecord.from_legacy_mapping(raw)

        self.assertEqual(record.event_keys, (legacy._event_key(event),))

    def test_malformed_records_fail_closed(self) -> None:
        cases = [
            [],
            {"decision": []},
            {**record_mapping(), "event_keys": "bad"},
            {**record_mapping(), "task_issue_numbers": ["not-a-number"]},
            record_mapping(decision="SURPRISE"),
            record_mapping(worker_tier="MOON"),
            record_mapping(pr_number=0),
            record_mapping(allowed_paths="src/**"),
        ]
        for raw in cases:
            with self.subTest(raw=raw):
                with self.assertRaises(ValueError):
                    PendingDecisionRecord.from_legacy_mapping(raw)


class ExecutionShapeTest(unittest.TestCase):
    def test_dispatch_defaults_to_terra(self) -> None:
        raw = record_mapping(worker_tier=None)
        decision = PendingDecisionRecord.from_legacy_mapping(raw).decision
        self.assertEqual(decision.effective_worker_tier, WorkerTier.TERRA)
        self.assertIsNone(decision.execution_validation_error())

    def test_dispatch_requires_lane_objective_and_stop_boundary(self) -> None:
        cases = [
            record_mapping(lane=None),
            record_mapping(objective=None),
            record_mapping(stop_boundary=None),
        ]
        for raw in cases:
            with self.subTest(raw=raw):
                decision = PendingDecisionRecord.from_legacy_mapping(raw).decision
                self.assertIsNotNone(decision.execution_validation_error())

    def test_luna_requires_nonempty_allowed_paths(self) -> None:
        for allowed in (None, []):
            raw = record_mapping(worker_tier="LUNA", allowed_paths=allowed)
            decision = PendingDecisionRecord.from_legacy_mapping(raw).decision
            self.assertIn("allowed_paths", decision.execution_validation_error())

        raw = record_mapping(
            worker_tier="LUNA",
            allowed_paths=["docs/agent-state/AUDIT_STATE.md"],
        )
        decision = PendingDecisionRecord.from_legacy_mapping(raw).decision
        self.assertIsNone(decision.execution_validation_error())

    def test_merge_requires_lane(self) -> None:
        valid = PendingDecisionRecord.from_legacy_mapping(
            record_mapping(
                decision="MERGE",
                lane="Implementation",
                pr_number=481,
                objective=None,
                stop_boundary=None,
                worker_tier=None,
            )
        ).decision
        invalid = PendingDecisionRecord.from_legacy_mapping(
            record_mapping(
                decision="MERGE",
                lane=None,
                pr_number=481,
                objective=None,
                stop_boundary=None,
                worker_tier=None,
            )
        ).decision
        self.assertIsNone(valid.execution_validation_error())
        self.assertEqual(invalid.execution_validation_error(), "MERGE decision missing lane")


class CachedFreshnessParityTest(unittest.TestCase):
    def make_legacy(self):
        o = legacy.Orchestrator.__new__(legacy.Orchestrator)
        o.root = pathlib.Path(".")
        o.repo = "ni-da-ba/skyforge"
        return o

    def test_terminal_decisions_remain_current_like_legacy(self) -> None:
        for kind in ("NOOP", "HUMAN_GATE", "MERGE"):
            raw = record_mapping(
                decision=kind,
                lane="Implementation" if kind == "MERGE" else None,
                pr_number=481 if kind == "MERGE" else None,
                objective=None,
                stop_boundary=None,
                worker_tier=None,
            )
            record = PendingDecisionRecord.from_legacy_mapping(raw)
            observation = DecisionFreshnessObservation(
                current_main="moved-main",
                source_pr_state=SourcePRState.CLOSED,
                source_pr_head="moved-head",
            )
            with self.subTest(kind=kind):
                self.assertTrue(
                    self.make_legacy()._cached_decision_still_current(raw)
                )
                self.assertTrue(cached_decision_is_current(record, observation))

    def test_dispatch_main_and_source_pr_identity_match_legacy(self) -> None:
        raw = record_mapping()
        record = PendingDecisionRecord.from_legacy_mapping(raw)
        o = self.make_legacy()

        cases = [
            (
                "mainhead",
                {"state": "OPEN", "headRefOid": "prhead"},
                DecisionFreshnessObservation(
                    current_main="mainhead",
                    source_pr_state=SourcePRState.OPEN,
                    source_pr_head="prhead",
                ),
                True,
            ),
            (
                "newmain",
                {"state": "OPEN", "headRefOid": "prhead"},
                DecisionFreshnessObservation(
                    current_main="newmain",
                    source_pr_state=SourcePRState.OPEN,
                    source_pr_head="prhead",
                ),
                False,
            ),
            (
                "mainhead",
                {"state": "OPEN", "headRefOid": "new-pr-head"},
                DecisionFreshnessObservation(
                    current_main="mainhead",
                    source_pr_state=SourcePRState.OPEN,
                    source_pr_head="new-pr-head",
                ),
                False,
            ),
            (
                "mainhead",
                {"state": "CLOSED", "headRefOid": "prhead"},
                DecisionFreshnessObservation(
                    current_main="mainhead",
                    source_pr_state=SourcePRState.CLOSED,
                    source_pr_head="prhead",
                ),
                False,
            ),
        ]

        for main_head, pr, observation, expected in cases:
            with self.subTest(main=main_head, pr=pr):
                with (
                    mock.patch.object(
                        legacy,
                        "_run",
                        return_value=legacy.subprocess.CompletedProcess(
                            ["git"], 0, stdout=main_head + "\n", stderr=""
                        ),
                    ),
                    mock.patch.object(legacy, "_json_cmd", return_value=pr),
                ):
                    legacy_result = o._cached_decision_still_current(raw)
                self.assertEqual(legacy_result, expected)
                self.assertEqual(
                    cached_decision_is_current(record, observation),
                    expected,
                )

    def test_dispatch_without_source_pr_remains_current_when_main_matches(self) -> None:
        raw = record_mapping(pr_number=None)
        raw["source_pr_head"] = None
        record = PendingDecisionRecord.from_legacy_mapping(raw)
        observation = DecisionFreshnessObservation(current_main="mainhead")
        self.assertTrue(cached_decision_is_current(record, observation))

    def test_dispatch_missing_captured_source_head_is_stale(self) -> None:
        raw = record_mapping()
        raw["source_pr_head"] = None
        record = PendingDecisionRecord.from_legacy_mapping(raw)
        observation = DecisionFreshnessObservation(
            current_main="mainhead",
            source_pr_state=SourcePRState.OPEN,
            source_pr_head="prhead",
        )
        self.assertFalse(cached_decision_is_current(record, observation))


class CachedDecisionDispositionTest(unittest.TestCase):
    def test_stale_dispatch_reclassifies_before_execution_shape_matters(self) -> None:
        raw = record_mapping(objective=None)
        record = PendingDecisionRecord.from_legacy_mapping(raw)
        observation = DecisionFreshnessObservation(
            current_main="newmain",
            source_pr_state=SourcePRState.OPEN,
            source_pr_head="prhead",
        )
        self.assertEqual(
            cached_decision_disposition(record, observation),
            CachedDecisionDisposition.RECLASSIFY,
        )

    def test_current_invalid_decision_fails_closed(self) -> None:
        raw = record_mapping(worker_tier="LUNA", allowed_paths=None)
        record = PendingDecisionRecord.from_legacy_mapping(raw)
        observation = DecisionFreshnessObservation(
            current_main="mainhead",
            source_pr_state=SourcePRState.OPEN,
            source_pr_head="prhead",
        )
        self.assertEqual(
            cached_decision_disposition(record, observation),
            CachedDecisionDisposition.INVALID,
        )

    def test_current_valid_decision_is_reusable(self) -> None:
        record = PendingDecisionRecord.from_legacy_mapping(record_mapping())
        observation = DecisionFreshnessObservation(
            current_main="mainhead",
            source_pr_state=SourcePRState.OPEN,
            source_pr_head="prhead",
        )
        self.assertEqual(
            cached_decision_disposition(record, observation),
            CachedDecisionDisposition.REUSE,
        )


if __name__ == "__main__":
    unittest.main()
