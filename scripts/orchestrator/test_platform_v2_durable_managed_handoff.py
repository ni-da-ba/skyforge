from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from v2.ordinary_effects import OrdinaryMutationScope
from v2.ordinary_pipeline import (
    OrdinaryPipelineLedger,
    OrdinaryPipelineRecord,
    OrdinaryPipelineStage,
    OrdinaryPipelineStore,
)
from v2.ordinary_service import ManagedOrdinaryHandoff


ATTEMPT = "a" * 64
TASK = "b" * 64
PIPELINE = "c" * 64
AUTHORITY = "d" * 64
HEAD = "e" * 40
BASE = "f" * 40


def handoff() -> ManagedOrdinaryHandoff:
    return ManagedOrdinaryHandoff(
        task_id="github-issue-870-comment-1",
        authority_key="github-task-authority:" + "1" * 64,
        task_spec_hash=TASK,
        lane="Implementation",
        scope=OrdinaryMutationScope(
            attempt_id=ATTEMPT,
            repo="ni-da-ba/skyforge",
            base_sha=BASE,
            branch="codex/implementation-r5c10",
            expected_head_sha=HEAD,
            pr_title="R5C10 managed handoff",
            pr_body="Fixture body.",
            issue_number=870,
        ),
        pr_number=901,
        changed_paths=("docs/operations/a.md", "docs/operations/b.md"),
        auto_merge_eligible=False,
    )


def record(*, embedded=True) -> OrdinaryPipelineRecord:
    value = handoff()
    return OrdinaryPipelineRecord(
        pipeline_id=PIPELINE,
        classifier_request_id="2" * 64,
        authority_digest=AUTHORITY,
        stage=OrdinaryPipelineStage.COMPLETE,
        reason="managed draft PR ready",
        classifier_run_id="3" * 64,
        classifier_decision_digest="4" * 64,
        admission_digest="5" * 64,
        task_spec_hash=TASK,
        attempt_id=ATTEMPT,
        worker_spec_digest="6" * 64,
        worker_run_id="7" * 64,
        handoff_digest=value.digest,
        pr_number=value.pr_number,
        managed_handoff=value if embedded else None,
    )


class DurableManagedHandoffTest(unittest.TestCase):
    def test_scope_and_handoff_round_trip_canonically(self):
        value = handoff()
        rebuilt = ManagedOrdinaryHandoff.from_mapping(value.as_dict())
        self.assertEqual(rebuilt, value)
        self.assertEqual(rebuilt.digest, value.digest)
        self.assertEqual(
            OrdinaryMutationScope.from_mapping(value.scope.as_dict()),
            value.scope,
        )

    def test_pipeline_ledger_round_trip_preserves_complete_handoff(self):
        ledger = OrdinaryPipelineLedger((record(),))
        rebuilt = OrdinaryPipelineLedger.from_mapping(ledger.as_dict())
        self.assertEqual(rebuilt, ledger)
        self.assertEqual(
            rebuilt.reconstructible_managed_handoffs(),
            (handoff(),),
        )
        self.assertEqual(rebuilt.incomplete_completed_records(), ())

    def test_store_restart_preserves_exact_handoff(self):
        with tempfile.TemporaryDirectory() as td:
            store = OrdinaryPipelineStore.for_root(Path(td))
            original = OrdinaryPipelineLedger((record(),))
            store.save(original)
            restarted = OrdinaryPipelineStore.for_root(Path(td)).load()
            self.assertEqual(restarted, original)
            self.assertEqual(
                restarted.reconstructible_managed_handoffs()[0].digest,
                handoff().digest,
            )

    def test_pre_r5c10_complete_record_remains_visible_but_non_reconstructible(self):
        old = record(embedded=False)
        raw = old.as_dict()
        raw.pop("managed_handoff")
        rebuilt = OrdinaryPipelineLedger.from_mapping(
            {"schema_version": 1, "records": [raw]}
        )
        self.assertEqual(rebuilt.reconstructible_managed_handoffs(), ())
        self.assertEqual(rebuilt.incomplete_completed_records(), (old,))

    def test_tampered_embedded_handoff_fails_closed(self):
        base = record().as_dict()
        mutations = []

        bad_digest = dict(base)
        bad_digest["handoff_digest"] = "0" * 64
        mutations.append(bad_digest)

        bad_pr = dict(base)
        bad_pr["pr_number"] = 999
        mutations.append(bad_pr)

        bad_task = dict(base)
        bad_task["task_spec_hash"] = "9" * 64
        mutations.append(bad_task)

        bad_attempt = dict(base)
        bad_attempt["attempt_id"] = "8" * 64
        mutations.append(bad_attempt)

        for raw in mutations:
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                OrdinaryPipelineRecord.from_mapping(raw)

    def test_corrupt_handoff_or_scope_shape_fails_closed(self):
        raw = handoff().as_dict()
        bad_paths = dict(raw)
        bad_paths["changed_paths"] = "../escape"
        with self.assertRaises(ValueError):
            ManagedOrdinaryHandoff.from_mapping(bad_paths)

        bad_scope = dict(raw)
        bad_scope["scope"] = {"repo": "ni-da-ba/skyforge"}
        with self.assertRaises(ValueError):
            ManagedOrdinaryHandoff.from_mapping(bad_scope)

        bad_absolute = dict(raw)
        bad_absolute["changed_paths"] = ["/tmp/escape"]
        with self.assertRaises(ValueError):
            ManagedOrdinaryHandoff.from_mapping(bad_absolute)


if __name__ == "__main__":
    unittest.main()
