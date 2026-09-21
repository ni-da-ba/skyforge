from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from test_platform_v2_human_review_ingress import review_payload
from test_platform_v2_roadmap_service import gate, legacy_projection, manifest, task
from v2.human_review import (
    DevelopmentApiHumanReviewSource,
    HumanReviewStore,
    HumanReviewSubmission,
    HumanReviewVerdict,
    parse_human_review_comment,
)
from v2.human_review_command import (
    HumanReviewAuthorityScope,
    HumanReviewCommandPhase,
    HumanReviewCommandStore,
    advance_human_review_command,
    prepare_human_review_command,
    reconcile_pending_human_review_commands,
)
from v2.roadmap_service import (
    RoadmapAuthorityLedger,
    RoadmapAuthorityStore,
    RoadmapBlockRecord,
    apply_human_gate_review,
)


SHA = "a" * 40


def fixture_manifest():
    return manifest(
        [
            task("repair", 754, max_runs=1),
            gate("review", prereq=("repair",), message="Inspect exact artifact."),
            task("later", 900, priority=10, prereq=("review",)),
        ]
    )


def initial_roadmap(m):
    return RoadmapAuthorityLedger.from_legacy_projection(
        legacy_projection(
            m,
            completed={"repair": 1},
            blocked={"review": "awaiting human review"},
        ),
        m,
    )


def api_review(
    *,
    request_id="review-request-0001",
    verdict=HumanReviewVerdict.ACCEPTED,
    findings=("reviewed exact artifact",),
    material_delta="new reviewed artifact",
):
    return HumanReviewSubmission(
        source=DevelopmentApiHumanReviewSource(
            repo="ni-da-ba/skyforge",
            request_id=request_id,
            actor="ni-da-ba",
            client="operations-console",
            submitted_at="2026-09-20T23:50:00Z",
        ),
        gate_id="review",
        artifact_id="artifact:test",
        source_sha=SHA,
        verdict=verdict,
        findings=findings,
        positive_findings=("positive observation",),
        material_delta=material_delta,
        next_boundary="continue test roadmap",
        deferred_product_work=(verdict is HumanReviewVerdict.CHANGES_REQUIRED),
        prior_review_id=None,
    )


class HumanReviewCommandTest(unittest.TestCase):
    def stores(self, root: Path, m):
        command_store = HumanReviewCommandStore.for_root(root)
        review_store = HumanReviewStore.for_root(root)
        roadmap_store = RoadmapAuthorityStore.for_root(root)
        roadmap_store.save(initial_roadmap(m))
        return command_store, review_store, roadmap_store

    def test_accepted_command_persists_review_then_completes_gate(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            m = fixture_manifest()
            command_store, review_store, roadmap_store = self.stores(root, m)
            review = api_review()
            prepared = prepare_human_review_command(
                store=command_store,
                review=review,
                roadmap=roadmap_store.load(),
                manifest=m,
            )
            self.assertEqual(prepared.phase, HumanReviewCommandPhase.PREPARED)
            self.assertEqual(len(review_store.load().records), 0)

            complete = advance_human_review_command(
                command_store=command_store,
                review_store=review_store,
                roadmap_store=roadmap_store,
                manifest=m,
                request_id=review.source.request_id,
            )
            self.assertEqual(complete.phase, HumanReviewCommandPhase.RECONCILED)
            self.assertEqual(review_store.load().records[0].review_id, review.review_id)
            road = roadmap_store.load()
            self.assertEqual(dict(road.completed_runs)["review"], 1)
            self.assertIsNone(road.block_for("review"))

    def test_changes_required_persists_review_and_keeps_gate_blocked(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            m = fixture_manifest()
            command_store, review_store, roadmap_store = self.stores(root, m)
            review = api_review(verdict=HumanReviewVerdict.CHANGES_REQUIRED)
            prepare_human_review_command(
                store=command_store,
                review=review,
                roadmap=roadmap_store.load(),
                manifest=m,
            )
            complete = advance_human_review_command(
                command_store=command_store,
                review_store=review_store,
                roadmap_store=roadmap_store,
                manifest=m,
                request_id=review.source.request_id,
            )
            self.assertEqual(complete.phase, HumanReviewCommandPhase.RECONCILED)
            road = roadmap_store.load()
            self.assertNotIn("review", dict(road.completed_runs))
            self.assertIn(review.review_id, road.block_for("review").reason)

    def test_same_request_is_idempotent_but_conflicting_payload_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            m = fixture_manifest()
            command_store, _review_store, roadmap_store = self.stores(root, m)
            first = api_review()
            one = prepare_human_review_command(
                store=command_store,
                review=first,
                roadmap=roadmap_store.load(),
                manifest=m,
            )
            replay = prepare_human_review_command(
                store=command_store,
                review=first,
                roadmap=roadmap_store.load(),
                manifest=m,
            )
            self.assertEqual(one, replay)

            conflict = api_review(findings=("different judgment payload",))
            with self.assertRaisesRegex(ValueError, "conflicting payload"):
                prepare_human_review_command(
                    store=command_store,
                    review=conflict,
                    roadmap=roadmap_store.load(),
                    manifest=m,
                )

    def test_restart_after_review_persist_completes_roadmap_reconciliation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            m = fixture_manifest()
            command_store, review_store, roadmap_store = self.stores(root, m)
            review = api_review()
            prepared = prepare_human_review_command(
                store=command_store,
                review=review,
                roadmap=roadmap_store.load(),
                manifest=m,
            )
            review_store.capture(review)
            command_store.put(prepared.with_phase(HumanReviewCommandPhase.REVIEW_PERSISTED))

            recovered = reconcile_pending_human_review_commands(
                command_store=HumanReviewCommandStore.for_root(root),
                review_store=HumanReviewStore.for_root(root),
                roadmap_store=RoadmapAuthorityStore.for_root(root),
                manifest=m,
            )
            self.assertEqual(len(recovered), 1)
            self.assertEqual(recovered[0].phase, HumanReviewCommandPhase.RECONCILED)
            self.assertEqual(dict(RoadmapAuthorityStore.for_root(root).load().completed_runs)["review"], 1)

    def test_restart_after_roadmap_save_only_marks_command_reconciled(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            m = fixture_manifest()
            command_store, review_store, roadmap_store = self.stores(root, m)
            review = api_review()
            prepared = prepare_human_review_command(
                store=command_store,
                review=review,
                roadmap=roadmap_store.load(),
                manifest=m,
            )
            review_store.capture(review)
            persisted = command_store.put(
                prepared.with_phase(HumanReviewCommandPhase.REVIEW_PERSISTED)
            )
            reduction = apply_human_gate_review(
                ledger=roadmap_store.load(),
                manifest=m,
                gate_id=review.gate_id,
                verdict=review.verdict.value,
                review_id=review.review_id,
            )
            self.assertEqual(reduction.ledger.digest, persisted.roadmap_after_digest)
            roadmap_store.save(reduction.ledger)

            recovered = reconcile_pending_human_review_commands(
                command_store=HumanReviewCommandStore.for_root(root),
                review_store=HumanReviewStore.for_root(root),
                roadmap_store=RoadmapAuthorityStore.for_root(root),
                manifest=m,
            )
            self.assertEqual(recovered[0].phase, HumanReviewCommandPhase.RECONCILED)
            self.assertEqual(RoadmapAuthorityStore.for_root(root).load().digest, persisted.roadmap_after_digest)

    def test_roadmap_drift_after_prepare_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            m = fixture_manifest()
            command_store, review_store, roadmap_store = self.stores(root, m)
            review = api_review()
            prepare_human_review_command(
                store=command_store,
                review=review,
                roadmap=roadmap_store.load(),
                manifest=m,
            )
            original = roadmap_store.load()
            drifted = RoadmapAuthorityLedger(
                roadmap_id=original.roadmap_id,
                manifest_fingerprint=original.manifest_fingerprint,
                completed_runs=original.completed_runs,
                blocked_nodes=original.blocked_nodes + (
                    RoadmapBlockRecord("later", "concurrent authority change"),
                ),
                active=original.active,
                claims_day=original.claims_day,
                claims_today=original.claims_today,
                human_gate_records=original.human_gate_records,
            )
            roadmap_store.save(drifted)
            with self.assertRaisesRegex(ValueError, "roadmap authority changed"):
                advance_human_review_command(
                    command_store=command_store,
                    review_store=review_store,
                    roadmap_store=roadmap_store,
                    manifest=m,
                    request_id=review.source.request_id,
                )
            self.assertEqual(len(review_store.load().records), 1)
            self.assertEqual(command_store.load().pending[0].phase, HumanReviewCommandPhase.REVIEW_PERSISTED)

    def test_program_gate_command_persists_without_roadmap_authority(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            command_store = HumanReviewCommandStore.for_root(root)
            review_store = HumanReviewStore.for_root(root)
            review = HumanReviewSubmission(
                source=DevelopmentApiHumanReviewSource(
                    repo="ni-da-ba/skyforge",
                    request_id="program-review-0001",
                    actor="ni-da-ba",
                    client="operations-console",
                    submitted_at="2026-09-21T23:40:00Z",
                ),
                gate_id="pre-bootstrap-development-platform-gate",
                artifact_id="platform:pre-bootstrap-gate",
                source_sha="b" * 40,
                verdict=HumanReviewVerdict.ACCEPTED,
                findings=("platform gate reviewed against exact evidence",),
                positive_findings=("workflow gate criteria demonstrated",),
                material_delta="Platform-v2 gate evidence snapshot",
                next_boundary="resume bounded DR-70 repair",
                deferred_product_work=False,
            )
            digest = "d" * 64
            prepared = prepare_human_review_command(
                store=command_store,
                review=review,
                authority_scope=HumanReviewAuthorityScope.PROGRAM,
                program_projection_digest=digest,
            )
            self.assertEqual(
                prepared.authority_scope,
                HumanReviewAuthorityScope.PROGRAM,
            )
            self.assertEqual(prepared.roadmap_before_digest, digest)
            self.assertEqual(prepared.roadmap_after_digest, digest)

            complete = advance_human_review_command(
                command_store=command_store,
                review_store=review_store,
                request_id=review.source.request_id,
                program_projection_digest=digest,
            )
            self.assertEqual(complete.phase, HumanReviewCommandPhase.RECONCILED)
            self.assertEqual(len(review_store.load().records), 1)
            self.assertEqual(
                review_store.load().records[0].review_id,
                review.review_id,
            )
            self.assertFalse(
                RoadmapAuthorityStore.for_root(root).adapter.path.exists()
            )

    def test_program_gate_restart_rejects_projection_drift(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            command_store = HumanReviewCommandStore.for_root(root)
            review_store = HumanReviewStore.for_root(root)
            review = HumanReviewSubmission(
                source=DevelopmentApiHumanReviewSource(
                    repo="ni-da-ba/skyforge",
                    request_id="program-review-0002",
                    actor="ni-da-ba",
                    client="operations-console",
                    submitted_at="2026-09-21T23:41:00Z",
                ),
                gate_id="pre-bootstrap-development-platform-gate",
                artifact_id="platform:pre-bootstrap-gate",
                source_sha="b" * 40,
                verdict=HumanReviewVerdict.ACCEPTED,
                findings=("reviewed",),
                positive_findings=(),
                material_delta="exact platform gate evidence",
                next_boundary="resume DR-70",
                deferred_product_work=False,
            )
            prepared = prepare_human_review_command(
                store=command_store,
                review=review,
                authority_scope=HumanReviewAuthorityScope.PROGRAM,
                program_projection_digest="d" * 64,
            )
            review_store.capture(review)
            command_store.put(
                prepared.with_phase(HumanReviewCommandPhase.REVIEW_PERSISTED)
            )
            with self.assertRaisesRegex(ValueError, "program projection changed"):
                reconcile_pending_human_review_commands(
                    command_store=command_store,
                    review_store=review_store,
                    program_projection_digest="e" * 64,
                )

    def test_legacy_github_review_identity_remains_exact(self):
        import json

        payload = json.loads(review_payload().decode("utf-8"))
        review = parse_human_review_comment(
            event_name="issue_comment",
            payload=payload,
            repo="ni-da-ba/skyforge",
            trusted_actors=("ni-da-ba",),
        )
        self.assertEqual(
            review.review_id,
            "3d9a7dd0e8046b60bdffd7d3fc730c7e44a50691edca717e08a118015f6753fd",
        )
        self.assertEqual(
            review.source.digest,
            "e8235cbb768e822bc64bd8323a02efc4c42acd8c3aed2e86a685f536ccffba5d",
        )


if __name__ == "__main__":
    unittest.main()
