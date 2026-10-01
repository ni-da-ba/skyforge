from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

import platform_v2_canary_guard as cli
from v2.canary import (
    CANARY_DOC_PREFIX,
    CanaryGuardDisposition,
    CanaryTaskContract,
    LegacyCanaryExclusion,
    MutationGateRecord,
    WRITER_FENCE_RELATIVE_PATH,
    evaluate_canary_guard,
)
from v2.ownership import OwnershipToken


BASE = "a" * 40
HEAD = "b" * 40


def gate(*, preservation=False, enabled=False, issue=None):
    return MutationGateRecord.from_mapping(
        {
            "schema_version": 1,
            "release3_accepted": True,
            "release3_acceptance_main": "5" * 40,
            "workstation_preservation_passed": preservation,
            "workstation_evidence": (
                "primary-workstation audit evidence" if preservation else None
            ),
            "canary_enabled": enabled,
            "canary_issue_number": issue,
        }
    )


def task(issue=900):
    return CanaryTaskContract(
        task_id="platform-v2-r4-canary-900",
        issue_number=issue,
        base_sha=BASE,
        head_branch="platform/v2-canary/900-doc",
        expected_head_sha=HEAD,
        documentation_path=CANARY_DOC_PREFIX + "canary-900.md",
        pr_title="Platform v2 canary",
        pr_body="Bounded Release-4 canary.",
    )


def exclusion(issue=900, *, active=True, branch="platform/v2-canary/900-doc"):
    return LegacyCanaryExclusion(
        issue_number=issue,
        active=active,
        branch=branch,
        claimed_by="ni-da-ba",
    )


class CanaryGuardTest(unittest.TestCase):
    def evaluate(
        self,
        *,
        g=None,
        t=None,
        x=None,
        main=BASE,
        execute=False,
    ):
        return evaluate_canary_guard(
            gate=g or gate(),
            task=t or task(),
            exclusion=x or exclusion(),
            current_main=main,
            ownership_token=OwnershipToken("v2-canary", 1),
            attempt_number=1,
            execute_requested=execute,
        )

    def test_workstation_preservation_gate_blocks_mutation(self) -> None:
        decision = self.evaluate(
            g=gate(preservation=False, enabled=True, issue=900),
            execute=True,
        )
        self.assertEqual(decision.disposition, CanaryGuardDisposition.BLOCKED)
        self.assertIn("workstation", decision.reason)

    def test_ready_and_allow_are_distinct(self) -> None:
        ready = self.evaluate(
            g=gate(preservation=True, enabled=True, issue=900),
            execute=False,
        )
        allowed = self.evaluate(
            g=gate(preservation=True, enabled=True, issue=900),
            execute=True,
        )
        self.assertEqual(ready.disposition, CanaryGuardDisposition.READY)
        self.assertEqual(
            allowed.disposition,
            CanaryGuardDisposition.ALLOW_MUTATION,
        )
        self.assertEqual(ready.task_spec_hash, allowed.task_spec_hash)
        self.assertEqual(ready.attempt_id, allowed.attempt_id)
        self.assertEqual(
            allowed.writer_fence_relative_path,
            WRITER_FENCE_RELATIVE_PATH,
        )

    def test_exact_legacy_exclusion_claim_is_required(self) -> None:
        g = gate(preservation=True, enabled=True, issue=900)
        for x in (
            exclusion(active=False),
            exclusion(branch="platform/v2-canary/wrong"),
            LegacyCanaryExclusion(901, True, "platform/v2-canary/900-doc", "ni-da-ba"),
        ):
            with self.subTest(exclusion=x):
                self.assertEqual(
                    self.evaluate(g=g, x=x, execute=True).disposition,
                    CanaryGuardDisposition.BLOCKED,
                )

    def test_current_main_must_equal_frozen_base(self) -> None:
        decision = self.evaluate(
            g=gate(preservation=True, enabled=True, issue=900),
            main="c" * 40,
            execute=True,
        )
        self.assertEqual(decision.disposition, CanaryGuardDisposition.BLOCKED)
        self.assertIn("base SHA", decision.reason)

    def test_attempt_identity_is_deterministic(self) -> None:
        g = gate(preservation=True, enabled=True, issue=900)
        first = self.evaluate(g=g)
        second = self.evaluate(g=g)
        self.assertEqual(first, second)
        self.assertEqual(first.digest, second.digest)

    def test_canary_contract_is_path_and_branch_constrained(self) -> None:
        with self.assertRaises(ValueError):
            CanaryTaskContract(
                task_id="bad",
                issue_number=900,
                base_sha=BASE,
                head_branch="feature/not-canary",
                expected_head_sha=HEAD,
                documentation_path=CANARY_DOC_PREFIX + "x.md",
                pr_title="x",
                pr_body="x",
            )
        with self.assertRaises(ValueError):
            CanaryTaskContract(
                task_id="bad",
                issue_number=900,
                base_sha=BASE,
                head_branch="platform/v2-canary/x",
                expected_head_sha=HEAD,
                documentation_path="scripts/orchestrator/unsafe.py",
                pr_title="x",
                pr_body="x",
            )

    def test_legacy_claim_projection_fails_closed(self) -> None:
        missing = LegacyCanaryExclusion.from_legacy_state(
            {"external_producer_claims": {}},
            issue_number=900,
        )
        self.assertFalse(missing.active)
        with self.assertRaises(ValueError):
            LegacyCanaryExclusion.from_legacy_state(
                {
                    "external_producer_claims": {
                        "900": {
                            "state": "active",
                            "issue_number": 901,
                            "claimed_by": "ni-da-ba",
                        }
                    }
                },
                issue_number=900,
            )

    def test_cli_source_has_no_remote_or_write_adapter(self) -> None:
        source = Path(cli.__file__).read_text(encoding="utf-8")
        for token in (
            "subprocess",
            "gh ",
            "requests",
            "urllib",
            "openai_codex",
            "write_text",
            "write_bytes",
            "create_pull",
            "merge_pull",
        ):
            self.assertNotIn(token, source)


class MachineGateFileTest(unittest.TestCase):
    def test_repository_gate_is_coherent_after_preservation_pass(self) -> None:
        root = Path(__file__).resolve().parents[2]
        path = root / "docs/agent-state/PLATFORM_V2_MUTATION_GATE.json"
        value = MutationGateRecord.from_mapping(json.loads(path.read_text()))
        self.assertTrue(value.release3_accepted)
        self.assertTrue(value.workstation_preservation_passed)
        self.assertTrue(value.workstation_evidence)
        if value.canary_enabled:
            self.assertIsNotNone(value.canary_issue_number)
        else:
            self.assertIsNone(value.canary_issue_number)


if __name__ == "__main__":
    unittest.main()
