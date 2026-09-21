import copy
import unittest

from v2.development_read_model import build_development_snapshot


SHA = "a" * 40


def sample_kwargs():
    return {
        "repo": "ni-da-ba/skyforge",
        "checkout_head_sha": SHA,
        "legacy_projection": {
            "roadmap": {
                "roadmap_id": "skyforge-test-roadmap",
                "manifest_fingerprint": "f" * 64,
                "active": None,
                "completed_runs": {"a": 1},
                "blocked_nodes": {"gate": {"reason": "human review required"}},
                "claims_day": "2026-09-20",
                "claims_today": 1,
            }
        },
        "objective_records": (
            {
                "proposal_id": "proposal-1",
                "delivery_id": "must-not-leak",
                "source": {
                    "issue_number": 961,
                    "comment_id": 1001,
                    "actor": "ni-da-ba",
                    "created_at": "2026-09-20T22:00:00Z",
                    "objective_text": "Continue Skyforge",
                },
                "compiled": {
                    "disposition": "HUMAN_GATE",
                    "reason": "review required",
                    "candidate_task": None,
                    "human_gate": {
                        "roadmap_id": "skyforge-test-roadmap",
                        "node_id": "gate",
                        "lane": "Implementation",
                        "message": "Review artifact",
                    },
                },
            },
        ),
        "active_plan": {
            "plan_id": "plan-1",
            "event_id": "event-1",
            "issue_number": 961,
            "status": "READY_FOR_CLASSIFIER",
            "reason": "ready",
            "seed": {"large": "raw detail must not leak"},
        },
        "admission": {
            "record_id": "admission-1",
            "plan_id": "plan-1",
            "event_id": "event-1",
            "issue_number": 961,
            "current_main": SHA,
            "attempt_number": 1,
            "outcome": "ADMITTED",
            "reason": "admitted",
            "attempt": {"attempt_id": "attempt-1"},
            "worker_spec": {
                "task_id": "task-1",
                "lane": "Audit",
                "branch": "codex/audit-task",
                "base_sha": SHA,
                "tier": "TERRA",
                "context_text": "must not leak",
            },
        },
        "plan_records": (
            {
                "plan_id": "plan-1",
                "event_id": "event-1",
                "issue_number": 961,
                "status": "READY_FOR_CLASSIFIER",
                "reason": "ready",
            },
            {
                "plan_id": "plan-2",
                "event_id": "event-2",
                "issue_number": 962,
                "status": "CLAIMED",
                "reason": "claimed",
            },
        ),
        "admission_records": (
            {
                "record_id": "admission-1",
                "plan_id": "plan-1",
                "event_id": "event-1",
                "issue_number": 961,
                "current_main": SHA,
                "attempt_number": 1,
                "outcome": "ADMITTED",
                "reason": "admitted",
                "attempt": {"attempt_id": "attempt-1"},
                "worker_spec": {
                    "task_id": "task-1",
                    "lane": "Audit",
                    "branch": "codex/audit-task",
                    "base_sha": SHA,
                    "tier": "TERRA",
                    "context_text": "multi admission private context",
                },
            },
            {
                "record_id": "admission-2",
                "plan_id": "plan-2",
                "event_id": "event-2",
                "issue_number": 962,
                "current_main": SHA,
                "attempt_number": 1,
                "outcome": "BLOCKED",
                "reason": "serialized",
                "attempt": None,
                "worker_spec": None,
            },
        ),
        "local_commit_records": (
            {
                "record_id": "commit-1",
                "admission_record_id": "admission-1",
                "worker_run_id": "worker-1",
                "attempt_id": "attempt-1",
                "branch": "codex/audit-task",
                "base_sha": SHA,
                "outcome": "COMMITTED",
                "reason": "committed",
                "head_sha": "b" * 40,
                "changed_paths": ["docs/operations/result.md"],
            },
        ),
        "worker_scheduler_records": (
            {
                "attempt_id": "a" * 64,
                "admission_record_id": "b" * 64,
                "state": "EXECUTING",
                "reason": "running",
                "claim_id": "c" * 64,
                "worker_run_id": "d" * 64,
                "tier": "TERRA",
            },
            {
                "attempt_id": "e" * 64,
                "admission_record_id": "f" * 64,
                "state": "WAIT_LIMIT",
                "reason": "bounded",
                "claim_id": "1" * 64,
                "worker_run_id": "",
                "tier": "LUNA",
            },
        ),
        "concurrency_claims": (
            {
                "claim_id": "c" * 64,
                "attempt_id": "d" * 64,
                "task_id": "task-claim-1",
                "authority_key": "authority:claim-1",
                "task_spec_hash": "e" * 64,
                "base_sha": SHA,
                "lane": "Implementation",
                "allowed_paths": ["docs/operations/**"],
            },
        ),
        "worker_records": (
            {
                "run_id": "worker-1",
                "worktree": "/private/worktree/path",
                "spec": {
                    "task_id": "task-1",
                    "attempt_id": "attempt-1",
                    "lane": "Audit",
                    "objective": "Build read model",
                    "stop_boundary": "Stop at PR",
                    "base_sha": SHA,
                    "branch": "codex/audit-task",
                    "tier": "TERRA",
                    "context_text": "large private context",
                },
                "config": {
                    "tier": "TERRA",
                    "model": "gpt-test",
                    "reasoning_effort": "medium",
                },
                "status": "HANDOFF_READY",
                "summary": "read model ready",
                "failure_kind": "",
                "retry_after_seconds": 0,
            },
        ),
        "completion_records": (
            {
                "completion_id": "completion-1",
                "plan_id": "plan-0",
                "event_id": "event-0",
                "issue_number": 960,
                "attempt_id": "attempt-0",
                "worker_run_id": "worker-0",
                "status": "CLEANED",
                "handoff_digest": "secret-ish-detail-not-needed",
            },
        ),
        "external_claims": (
            {
                "issue_number": 613,
                "claimed_by": "ni-da-ba",
                "lane": "Implementation",
                "branch": "platform/613-aero",
                "pr_number": 762,
                "state": "active",
            },
        ),
        "artifact_records": (
            {
                "artifact_id": "dr70:key-2885",
                "kind": "INTERACTIVE_SPECIMEN",
                "source_sha": SHA,
                "title": "DR-70 review specimen",
                "description": "Interactive specimen",
                "file": None,
                "interactive": {
                    "specimen_kind": "minecraft-neoforge-review-world",
                    "parameters": {"island_key": 2885},
                    "preparation_entry_points": [
                        ":skyforge-neoforge-1211:dr70HydrologyReviewAcceptance"
                    ],
                    "launch_entry_point": ":skyforge-neoforge-1211:runDr70HumanReviewClient",
                    "review_actions": ["/tp @s 0 320 0"],
                    "associated_artifact_ids": [],
                },
                "artifact_digest": "artifact-digest",
            },
        ),
        "human_reviews": (
            {
                "review_id": "review-1",
                "gate_id": "dr-human-exploration-rereview",
                "artifact_id": "dr70:key-2885",
                "source_sha": SHA,
                "verdict": "CHANGES_REQUIRED",
                "positive_findings": ["real channel"],
                "findings": ["ocean biome"],
                "material_delta": "channel geomorphology",
                "next_boundary": "optimize platform first",
                "deferred_product_work": True,
                "prior_review_id": "review-0",
                "source": {
                    "issue_number": 754,
                    "comment_id": 1002,
                    "actor": "ni-da-ba",
                    "created_at": "2026-09-20T22:05:00Z",
                },
            },
        ),
        "runtime": {
            "status": "ok",
            "controller": "platform-v2",
            "runtime_mode": "hosted-v2-gated-execution",
            "state_digest": "state-1",
            "pending_event_count": 2,
            "production_execution_enabled": True,
            "production_execution_gate_digest": "gate-1",
            "production_execution_gate_blockers": [],
            "production_execution_driver": {"running": True, "last_disposition": "IDLE"},
            "production_execution_budget": {"worker_calls": 1},
        },
    }


class DevelopmentReadModelTest(unittest.TestCase):
    def test_snapshot_is_normalized_deterministic_and_hides_raw_context(self):
        first = build_development_snapshot(**sample_kwargs())
        second = build_development_snapshot(**sample_kwargs())
        self.assertEqual(first.digest, second.digest)
        raw = first.as_dict()
        self.assertEqual(raw["snapshot_digest"], first.digest)
        self.assertEqual(raw["roadmap"]["roadmap_id"], "skyforge-test-roadmap")
        self.assertEqual(raw["objectives"][0]["disposition"], "HUMAN_GATE")
        self.assertEqual(raw["artifact_count"], 1)
        self.assertEqual(raw["human_reviews"][0]["artifact_id"], "dr70:key-2885")
        self.assertEqual(
            raw["human_reviews"][0]["artifact"]["artifact_id"],
            "dr70:key-2885",
        )
        self.assertEqual(
            raw["human_reviews"][0]["artifact"]["interactive"]["parameters"]["island_key"],
            2885,
        )
        self.assertEqual(raw["execution"]["workers"][0]["branch"], "codex/audit-task")
        self.assertEqual(raw["execution"]["plan_count"], 2)
        self.assertEqual(raw["execution"]["admission_count"], 2)
        self.assertEqual(raw["execution"]["local_commit_count"], 1)
        self.assertEqual(raw["execution"]["worker_concurrency_limit"], 2)
        self.assertEqual(raw["execution"]["worker_scheduler_count"], 2)
        self.assertEqual(len(raw["execution"]["executing_attempts"]), 1)
        self.assertEqual(len(raw["execution"]["waiting_attempts"]), 1)
        self.assertEqual(
            raw["execution"]["executing_attempts"][0]["attempt_id"],
            "a" * 64,
        )
        self.assertEqual(raw["execution"]["plans"][1]["issue_number"], 962)
        self.assertEqual(
            raw["execution"]["local_commits"][0]["attempt_id"],
            "attempt-1",
        )
        self.assertEqual(raw["execution"]["concurrency_claim_count"], 1)
        self.assertEqual(
            raw["execution"]["concurrency_claims"][0]["claim_id"],
            "c" * 64,
        )
        self.assertEqual(
            raw["execution"]["concurrency_claims"][0]["allowed_paths"],
            ["docs/operations/**"],
        )

        encoded = str(raw)
        self.assertNotIn("must-not-leak", encoded)
        self.assertNotIn("large private context", encoded)
        self.assertNotIn("/private/worktree/path", encoded)
        self.assertNotIn("secret-ish-detail-not-needed", encoded)

    def test_material_review_change_changes_snapshot_digest(self):
        first = build_development_snapshot(**sample_kwargs())
        changed = sample_kwargs()
        reviews = list(changed["human_reviews"])
        review = copy.deepcopy(reviews[0])
        review["findings"] = ["ocean biome", "poor water placement"]
        reviews[0] = review
        changed["human_reviews"] = tuple(reviews)
        second = build_development_snapshot(**changed)
        self.assertNotEqual(first.digest, second.digest)

    def test_recent_histories_are_bounded_but_counts_remain_exact(self):
        values = sample_kwargs()
        objectives = []
        for index in range(60):
            item = copy.deepcopy(values["objective_records"][0])
            item["proposal_id"] = f"proposal-{index}"
            item["source"]["comment_id"] = 2000 + index
            objectives.append(item)
        values["objective_records"] = tuple(objectives)
        snapshot = build_development_snapshot(**values).as_dict()
        self.assertEqual(snapshot["objective_count"], 60)
        self.assertEqual(len(snapshot["objectives"]), 50)
        self.assertEqual(snapshot["objectives"][0]["proposal_id"], "proposal-10")
        self.assertEqual(snapshot["objectives"][-1]["proposal_id"], "proposal-59")

    def test_invalid_checkout_sha_fails_closed(self):
        values = sample_kwargs()
        values["checkout_head_sha"] = "not-a-sha"
        with self.assertRaises(ValueError):
            build_development_snapshot(**values)


if __name__ == "__main__":
    unittest.main()
