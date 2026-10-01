import hashlib
import hmac
import json
from pathlib import Path
import tempfile
import unittest

import platform_v2_hosted_runtime as hosted
from v2.human_review import HUMAN_REVIEW_MARKER, HumanReviewStore


SECRET = "human-review-test-secret-0123456789abcdef"
SOURCE_SHA = "a318f1b1ffb22805087eac52b67035d041a03fb6"


def signed_headers(payload: bytes, *, delivery: str) -> dict[str, str]:
    digest = hmac.new(SECRET.encode(), payload, hashlib.sha256).hexdigest()
    return {
        "X-Github-Event": "issue_comment",
        "X-Github-Delivery": delivery,
        "X-Hub-Signature-256": "sha256=" + digest,
    }


def review_body() -> str:
    review = {
        "gate_id": "dr-human-exploration-rereview",
        "artifact_id": "dr70:key-2885",
        "source_sha": SOURCE_SHA,
        "verdict": "CHANGES_REQUIRED",
        "positive_findings": [
            "a real visibly legible water channel is present",
        ],
        "findings": [
            "review area presents as an ocean biome",
            "water and surrounding block/material placement are visually poor",
            "selected showcase island is very small",
        ],
        "material_delta": (
            "AUTH-0105 fluvial terrain response now produces an actual channel "
            "rather than water painted onto an unchanged hillside"
        ),
        "next_boundary": (
            "defer DR-70 and execute the pre-Bootstrap platform optimization gate first"
        ),
        "deferred_product_work": True,
        "prior_review_id": "dr70-2026-09-19-hydrology-rereview",
    }
    return HUMAN_REVIEW_MARKER + "\n" + json.dumps(review, sort_keys=True)


def review_payload(
    *,
    actor: str = "ni-da-ba",
    comment_id: int = 5752994015,
    body: str | None = None,
) -> bytes:
    return json.dumps(
        {
            "action": "created",
            "repository": {"full_name": "ni-da-ba/skyforge"},
            "issue": {"number": 754},
            "comment": {
                "id": comment_id,
                "body": body or review_body(),
                "created_at": "2026-09-20T22:02:00Z",
                "updated_at": "2026-09-20T22:02:00Z",
                "user": {"login": actor},
            },
        },
        separators=(",", ":"),
    ).encode()


def write_legacy(root: Path) -> None:
    state = {
        "paused": False,
        "blocked_kind": None,
        "pending_worker": None,
        "pending_decision": None,
        "managed": {},
        "pending_events": [],
        "external_producer_claims": {},
        "human_gate_records": {
            "issue:754:implementation": {
                "token": "prior-dr70-gate",
                "target": "754",
                "seeded_from_github": False,
            }
        },
        "roadmap": {
            "roadmap_id": "skyforge-dressed-region-convergence-v3",
            "manifest_fingerprint": "f" * 64,
            "completed_runs": {},
            "blocked_nodes": {
                "dr-human-exploration-rereview": {"reason": "owner review required"}
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


class HumanReviewIngressRuntimeTest(unittest.TestCase):
    def runtime(self, root: Path) -> hosted.HostedV2Substrate:
        write_legacy(root)
        return hosted.HostedV2Substrate(
            root,
            repo="ni-da-ba/skyforge",
            require_webhook_secret=True,
            startup_reconcile=True,
            webhook_secret=SECRET,
            trusted_actors=("ni-da-ba",),
        )

    def test_signed_changes_required_review_is_durable_and_visible(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            payload = review_payload()
            status, response = runtime.handle_webhook(
                headers=signed_headers(payload, delivery="review-1"),
                raw=payload,
            )
            self.assertEqual(status, 202)
            self.assertTrue(response["accepted"])
            self.assertTrue(response["human_review"])
            self.assertTrue(response["human_review_recorded"])
            self.assertEqual(response["verdict"], "CHANGES_REQUIRED")
            self.assertTrue(response["deferred_product_work"])
            self.assertEqual(len(runtime.state.inbox.pending_events), 0)

            ledger = HumanReviewStore.for_root(root).load()
            self.assertEqual(len(ledger.records), 1)
            record = ledger.records[0]
            self.assertEqual(record.source_sha, SOURCE_SHA)
            self.assertEqual(record.artifact_id, "dr70:key-2885")
            self.assertEqual(
                record.gate_id, "dr-human-exploration-rereview"
            )
            self.assertIn("ocean biome", record.findings[0])
            self.assertIn("water channel", record.positive_findings[0])

            health = runtime.health_snapshot()
            self.assertTrue(health["human_review_ingress_enabled"])
            self.assertEqual(health["human_review_count"], 1)
            self.assertEqual(health["latest_human_review_id"], record.review_id)
            self.assertEqual(
                health["latest_human_review_verdict"], "CHANGES_REQUIRED"
            )
            self.assertEqual(
                health["latest_human_review_artifact_id"], "dr70:key-2885"
            )

    def test_review_survives_runtime_restart_without_conversation_state(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            payload = review_payload()
            status, _ = runtime.handle_webhook(
                headers=signed_headers(payload, delivery="review-1"),
                raw=payload,
            )
            self.assertEqual(status, 202)
            review_id = HumanReviewStore.for_root(root).load().records[0].review_id

            restarted = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                trusted_actors=("ni-da-ba",),
            )
            ledger = restarted.human_review_store.load()
            self.assertEqual(len(ledger.records), 1)
            self.assertEqual(ledger.records[0].review_id, review_id)
            self.assertEqual(
                restarted.health_snapshot()["latest_human_review_next_boundary"],
                "defer DR-70 and execute the pre-Bootstrap platform optimization gate first",
            )

    def test_duplicate_delivery_and_redelivery_do_not_duplicate_review(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            payload = review_payload()
            first = runtime.handle_webhook(
                headers=signed_headers(payload, delivery="review-1"), raw=payload
            )
            duplicate = runtime.handle_webhook(
                headers=signed_headers(payload, delivery="review-1"), raw=payload
            )
            redelivery = runtime.handle_webhook(
                headers=signed_headers(payload, delivery="review-2"), raw=payload
            )
            self.assertEqual(first[0], 202)
            self.assertEqual(duplicate[0], 200)
            self.assertTrue(duplicate[1]["duplicate"])
            self.assertEqual(redelivery[0], 202)
            self.assertFalse(redelivery[1]["human_review_recorded"])
            self.assertEqual(len(HumanReviewStore.for_root(root).load().records), 1)

    def test_untrusted_and_malformed_reviews_fail_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)

            untrusted = review_payload(actor="mallory")
            status, response = runtime.handle_webhook(
                headers=signed_headers(untrusted, delivery="review-bad-actor"),
                raw=untrusted,
            )
            self.assertEqual(status, 409)
            self.assertIn("review actor is not trusted", response["error"])

            malformed = review_payload(body=HUMAN_REVIEW_MARKER + "\n{}")
            status, response = runtime.handle_webhook(
                headers=signed_headers(malformed, delivery="review-malformed"),
                raw=malformed,
            )
            self.assertEqual(status, 409)
            self.assertIn("human review ingress rejected", response["error"])
            self.assertEqual(len(HumanReviewStore.for_root(root).load().records), 0)


if __name__ == "__main__":
    unittest.main()
