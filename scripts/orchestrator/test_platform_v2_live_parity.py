from __future__ import annotations

import json
from pathlib import Path
import unittest

import platform_v2_shadow_runner as runner
from v2.live_parity import LiveParityClassification, analyze_live_parity


HEAD = "a" * 40
SPEC = "b" * 64
MAIN = "c" * 40


def snapshot(
    *,
    ci_state="PASS",
    active=True,
    human_gate=False,
    review_required=False,
    complete_acceptance=False,
):
    return {
        "schema_version": 1,
        "managed": {
            "state": {
                "managed": {
                    "Implementation": {
                        "pr_number": 900,
                        "branch": "codex/task-900",
                        "authority_key": "task:900",
                        "expected_head": HEAD,
                        "auto_merge_eligible": True,
                        "changed_paths": [],
                    }
                },
                "pending_worker": None,
                "human_gate_records": {},
                "paused": False,
            },
            "observation": {
                "lane": "Implementation",
                "pr_number": 900,
                "active_pr": active,
                "base_branch": "main",
                "head_branch": "codex/task-900",
                "current_head_sha": HEAD,
                "evidence_sha": HEAD if ci_state == "PASS" else "",
                "reviewed_sha": HEAD if complete_acceptance else "",
                "task_spec_hash": SPEC if complete_acceptance else "",
                "accepted_task_spec_hash": SPEC if complete_acceptance else "",
                "ci_state": ci_state,
                "pr_class": "HUMAN_GATE" if human_gate or review_required else "DELIVERY",
                "human_gate_pending": human_gate,
                "review_required": review_required,
            },
        },
    }


def legacy(kind: str, *, pr=900, lane="Implementation", snapshot_main=MAIN, source_head=HEAD):
    decision = {
        "decision": kind,
        "lane": lane,
        "pr_number": pr,
        "objective": "repair bounded managed PR" if kind == "DISPATCH" else None,
        "stop_boundary": "machine checks green" if kind == "DISPATCH" else None,
        "reusable_evidence": None,
        "worker_tier": "TERRA" if kind == "DISPATCH" else None,
        "allowed_paths": None,
        "reason": "test",
        "human_message": None,
    }
    return {
        "decision": decision,
        "event_keys": [],
        "authority_event_keys": [],
        "ordinary_event_keys": [],
        "task_issue_numbers": [],
        "captured_at": "2026-09-17T23:00:00+00:00",
        "snapshot_main": snapshot_main,
        "source_pr_head": source_head,
    }


def analyze(raw_legacy, snap):
    report = json.loads(runner.render_report(snap))
    return analyze_live_parity(
        legacy_pending_decision=raw_legacy,
        current_main=MAIN,
        snapshot=snap,
        report=report,
    )


class LiveParityAnalyzerTest(unittest.TestCase):
    def test_human_gate_agrees(self) -> None:
        sample = analyze(legacy("HUMAN_GATE"), snapshot(human_gate=True))
        self.assertEqual(sample.classification, LiveParityClassification.AGREE)
        self.assertEqual(sample.v2_disposition, "HUMAN_GATE")

    def test_failed_managed_repair_agrees(self) -> None:
        sample = analyze(legacy("DISPATCH"), snapshot(ci_state="FAIL"))
        self.assertEqual(sample.classification, LiveParityClassification.AGREE)
        self.assertEqual(sample.v2_disposition, "REPAIR_ELIGIBLE")

    def test_merge_with_complete_acceptance_agrees(self) -> None:
        sample = analyze(legacy("MERGE"), snapshot(complete_acceptance=True))
        self.assertEqual(sample.classification, LiveParityClassification.AGREE)
        self.assertEqual(sample.v2_disposition, "MERGE_ELIGIBLE")

    def test_live_collector_merge_case_is_explicit_stricter_evidence_gap(self) -> None:
        sample = analyze(legacy("MERGE"), snapshot())
        self.assertEqual(
            sample.classification,
            LiveParityClassification.STRICTER_V2_EVIDENCE_GAP,
        )
        self.assertEqual(sample.v2_disposition, "RECONCILE")

    def test_stale_dispatch_is_non_comparable(self) -> None:
        sample = analyze(
            legacy("DISPATCH", snapshot_main="d" * 40),
            snapshot(ci_state="FAIL"),
        )
        self.assertEqual(sample.classification, LiveParityClassification.NON_COMPARABLE)
        self.assertEqual(sample.freshness, "RECLASSIFY")

    def test_identity_mismatch_is_non_comparable(self) -> None:
        sample = analyze(legacy("HUMAN_GATE", pr=901), snapshot(human_gate=True))
        self.assertEqual(sample.classification, LiveParityClassification.NON_COMPARABLE)

    def test_unscoped_noop_is_not_false_agreement(self) -> None:
        raw = legacy("NOOP", pr=None, lane=None)
        sample = analyze(raw, snapshot(active=False))
        self.assertEqual(sample.classification, LiveParityClassification.NON_COMPARABLE)

    def test_real_mismatch_is_divergence(self) -> None:
        sample = analyze(legacy("DISPATCH"), snapshot(ci_state="PENDING"))
        self.assertEqual(sample.classification, LiveParityClassification.DIVERGENCE)
        self.assertEqual(sample.v2_disposition, "WAIT")

    def test_missing_decision_is_non_comparable(self) -> None:
        sample = analyze(None, snapshot())
        self.assertEqual(sample.classification, LiveParityClassification.NON_COMPARABLE)
        self.assertEqual(sample.legacy_kind, "NONE")

    def test_malformed_decision_is_non_comparable(self) -> None:
        sample = analyze({"decision": []}, snapshot())
        self.assertEqual(sample.classification, LiveParityClassification.NON_COMPARABLE)
        self.assertEqual(sample.legacy_kind, "INVALID")

    def test_sample_digest_is_deterministic(self) -> None:
        first = analyze(legacy("HUMAN_GATE"), snapshot(human_gate=True))
        second = analyze(legacy("HUMAN_GATE"), snapshot(human_gate=True))
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)

    def test_cli_module_has_no_network_subprocess_or_write_surface(self) -> None:
        import platform_v2_shadow_parity as cli

        source = Path(cli.__file__).read_text(encoding="utf-8")
        for token in (
            "subprocess",
            "socket",
            "requests",
            "openai_codex",
            "write_text",
            "write_bytes",
            "skyforge_orchestrator",
        ):
            self.assertNotIn(token, source)


if __name__ == "__main__":
    unittest.main()
