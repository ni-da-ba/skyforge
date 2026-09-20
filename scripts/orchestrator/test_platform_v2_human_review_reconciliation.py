import json
from pathlib import Path
import tempfile
import unittest

from v2.external import ExternalProducerClaim
from v2.external_service import ExternalClaimLedger, ExternalClaimStore
from v2.human_review import (
    HumanReviewLedger,
    HumanReviewSource,
    HumanReviewStore,
    HumanReviewSubmission,
    HumanReviewVerdict,
    RepeatReviewDisposition,
    evaluate_repeat_review_readiness,
)
from v2.objective_intake import compile_objective, load_manifest


REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCE_SHA = "a318f1b1ffb22805087eac52b67035d041a03fb6"
NEXT_SHA = "517c712d91980d5f90defafb3653c15d419bc5da"


def prepare_root(root: Path) -> None:
    manifest_dir = root / "docs/agent-state"
    manifest_dir.mkdir(parents=True)
    source = REPO_ROOT / "docs/agent-state/ORCHESTRATOR_ROADMAP.json"
    (manifest_dir / "ORCHESTRATOR_ROADMAP.json").write_text(
        source.read_text(encoding="utf-8"), encoding="utf-8"
    )
    manifest = load_manifest(root)
    completed = {
        node.node_id: node.max_runs
        for node in manifest.nodes
        if node.kind.value == "task"
    }
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
            "completed_runs": completed,
            "blocked_nodes": {
                "dr-human-exploration-review": {"reason": "historical gate"},
                "dr-human-exploration-rereview": {"reason": "current rereview"},
            },
            "active": None,
            "claims_day": "2026-09-20",
            "claims_today": 0,
        },
    }
    state_dir = root / ".skyforge-orchestrator"
    state_dir.mkdir(parents=True)
    payload = json.dumps(state, indent=2, sort_keys=True) + "\n"
    (state_dir / "state.json").write_text(payload, encoding="utf-8")
    (state_dir / "state.json.bak").write_text(payload, encoding="utf-8")


def review(
    *,
    verdict: HumanReviewVerdict = HumanReviewVerdict.CHANGES_REQUIRED,
    deferred: bool = True,
    comment_id: int = 5752994015,
    artifact_id: str = "dr70:key-2885",
    source_sha: str = SOURCE_SHA,
    material_delta: str = "real fluvial channel replaces token hillside water",
    prior_review_id: str | None = "dr70-2026-09-19",
) -> HumanReviewSubmission:
    return HumanReviewSubmission(
        source=HumanReviewSource(
            repo="ni-da-ba/skyforge",
            issue_number=754,
            comment_id=comment_id,
            actor="ni-da-ba",
            created_at="2026-09-20T22:02:00Z",
            updated_at="2026-09-20T22:02:00Z",
        ),
        gate_id="dr-human-exploration-rereview",
        artifact_id=artifact_id,
        source_sha=source_sha,
        verdict=verdict,
        findings=(
            "review area presents as an ocean biome",
            "water and surrounding placement are visually poor",
            "showcase island is very small",
        ),
        positive_findings=("a real visibly legible water channel is present",),
        material_delta=material_delta,
        next_boundary="defer DR-70 and execute platform optimization first",
        deferred_product_work=deferred,
        prior_review_id=prior_review_id,
    )


class HumanReviewReconciliationTest(unittest.TestCase):
    def test_unresolved_exact_prerequisite_external_claim_blocks_human_gate(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            ExternalClaimStore.for_root(root).save(
                ExternalClaimLedger(
                    (
                        ExternalProducerClaim(
                            issue_number=754,
                            claimed_by="ni-da-ba",
                            lane="Implementation",
                            branch="implementation/754-dr70-hydrology-geomorphology",
                            pr_number=949,
                        ),
                    )
                )
            )
            result = compile_objective("Continue DR-70", root=root)
            self.assertEqual(result.disposition.value, "BLOCKED")
            self.assertIn("prerequisite issue #754", result.reason)
            self.assertIn("PR #949", result.reason)
            self.assertIsNone(result.human_gate)

    def test_unrelated_external_claim_does_not_block_dr70_human_gate(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            ExternalClaimStore.for_root(root).save(
                ExternalClaimLedger(
                    (
                        ExternalProducerClaim(
                            issue_number=613,
                            claimed_by="ni-da-ba",
                            lane="Implementation",
                            branch="platform/613-aero-moment-contract",
                            pr_number=762,
                        ),
                    )
                )
            )
            result = compile_objective("Continue DR-70", root=root)
            self.assertEqual(result.disposition.value, "HUMAN_GATE")
            self.assertEqual(
                result.human_gate.node_id,
                "dr-human-exploration-rereview",
            )

    def test_continue_dr70_does_not_resurface_deferred_changes_required_gate(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            before = compile_objective("Continue DR-70", root=root)
            self.assertEqual(before.disposition.value, "HUMAN_GATE")

            store = HumanReviewStore.for_root(root)
            store.capture(review())
            after = compile_objective("Continue DR-70", root=root)
            self.assertEqual(after.disposition.value, "BLOCKED")
            self.assertIn("CHANGES_REQUIRED", after.reason)
            self.assertIn("deferred", after.reason)
            self.assertIsNone(after.human_gate)

    def test_accepted_review_blocks_stale_gate_until_roadmap_reconciliation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            HumanReviewStore.for_root(root).capture(
                review(verdict=HumanReviewVerdict.ACCEPTED, deferred=False)
            )
            result = compile_objective("Continue DR-70", root=root)
            self.assertEqual(result.disposition.value, "BLOCKED")
            self.assertIn("accepted", result.reason)
            self.assertIn("reconciliation", result.reason)
            self.assertIsNone(result.human_gate)

    def test_changes_required_without_deferral_requires_new_artifact_delta(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            HumanReviewStore.for_root(root).capture(review(deferred=False))
            result = compile_objective("Continue DR-70", root=root)
            self.assertEqual(result.disposition.value, "BLOCKED")
            self.assertIn("new qualified artifact", result.reason)
            self.assertIn("material delta", result.reason)

    def test_x4_repeat_review_readiness_rejects_unchanged_or_unlinked_artifact(self):
        first = review()
        ledger = HumanReviewLedger((first,))

        unchanged = evaluate_repeat_review_readiness(
            ledger,
            gate_id=first.gate_id,
            artifact_id=first.artifact_id,
            source_sha=first.source_sha,
            material_delta="new biome and material realization",
            prior_review_id=first.review_id,
        )
        self.assertEqual(
            unchanged.disposition,
            RepeatReviewDisposition.BLOCKED_UNCHANGED_ARTIFACT,
        )

        unlinked = evaluate_repeat_review_readiness(
            ledger,
            gate_id=first.gate_id,
            artifact_id="dr70:key-5001",
            source_sha=NEXT_SHA,
            material_delta="new biome and material realization",
            prior_review_id="wrong-review",
        )
        self.assertEqual(
            unlinked.disposition,
            RepeatReviewDisposition.BLOCKED_MISSING_PRIOR_LINK,
        )

        same_delta = evaluate_repeat_review_readiness(
            ledger,
            gate_id=first.gate_id,
            artifact_id="dr70:key-5001",
            source_sha=NEXT_SHA,
            material_delta=first.material_delta,
            prior_review_id=first.review_id,
        )
        self.assertEqual(
            same_delta.disposition,
            RepeatReviewDisposition.BLOCKED_MISSING_MATERIAL_DELTA,
        )

        ready = evaluate_repeat_review_readiness(
            ledger,
            gate_id=first.gate_id,
            artifact_id="dr70:key-5001",
            source_sha=NEXT_SHA,
            material_delta="biome authority and water/material realization materially changed",
            prior_review_id=first.review_id,
        )
        self.assertTrue(ready.ready)
        self.assertEqual(
            ready.disposition,
            RepeatReviewDisposition.READY_NEW_MATERIAL_DELTA,
        )

    def test_accepted_gate_cannot_be_resurfaced(self):
        accepted = review(
            verdict=HumanReviewVerdict.ACCEPTED,
            deferred=False,
        )
        result = evaluate_repeat_review_readiness(
            HumanReviewLedger((accepted,)),
            gate_id=accepted.gate_id,
            artifact_id="dr70:key-5001",
            source_sha=NEXT_SHA,
            material_delta="irrelevant after acceptance",
            prior_review_id=accepted.review_id,
        )
        self.assertFalse(result.ready)
        self.assertEqual(
            result.disposition,
            RepeatReviewDisposition.BLOCKED_ALREADY_ACCEPTED,
        )


if __name__ == "__main__":
    unittest.main()
