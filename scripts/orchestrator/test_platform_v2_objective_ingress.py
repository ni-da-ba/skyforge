import hashlib
import hmac
import json
from pathlib import Path
import tempfile
import unittest

import platform_v2_hosted_runtime as hosted
from v2.objective_ingress import OBJECTIVE_MARKER, ObjectiveProposalStore
from v2.objective_intake import load_manifest


SECRET = "opt1b-test-secret-0123456789abcdef"
REPO_ROOT = Path(__file__).resolve().parents[2]


def signed_headers(payload: bytes, *, delivery: str) -> dict[str, str]:
    digest = hmac.new(SECRET.encode(), payload, hashlib.sha256).hexdigest()
    return {
        "X-Github-Event": "issue_comment",
        "X-Github-Delivery": delivery,
        "X-Hub-Signature-256": "sha256=" + digest,
    }


def objective_payload(*, actor: str = "ni-da-ba", comment_id: int = 9001, body: str | None = None) -> bytes:
    return json.dumps({
        "action": "created",
        "repository": {"full_name": "ni-da-ba/skyforge"},
        "issue": {"number": 929},
        "comment": {
            "id": comment_id,
            "body": body or f"{OBJECTIVE_MARKER}\nContinue DR-70",
            "created_at": "2026-09-18T22:10:00Z",
            "updated_at": "2026-09-18T22:10:00Z",
            "user": {"login": actor},
        },
    }, separators=(",", ":")).encode()


def prepare_root(root: Path) -> None:
    manifest_dir = root / "docs/agent-state"
    manifest_dir.mkdir(parents=True)
    manifest_source = REPO_ROOT / "docs/agent-state/ORCHESTRATOR_ROADMAP.json"
    (manifest_dir / "ORCHESTRATOR_ROADMAP.json").write_text(
        manifest_source.read_text(encoding="utf-8"), encoding="utf-8"
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
                "dr-human-exploration-review": {"reason": "historical changes required"},
                "dr-human-exploration-rereview": {"reason": "human rereview required"},
            },
            "active": None,
            "claims_day": "2026-09-18",
            "claims_today": 0,
        },
    }
    state_dir = root / ".skyforge-orchestrator"
    state_dir.mkdir(parents=True)
    payload = json.dumps(state, sort_keys=True, indent=2) + "\n"
    (state_dir / "state.json").write_text(payload, encoding="utf-8")
    (state_dir / "state.json.bak").write_text(payload, encoding="utf-8")


class ObjectiveIngressRuntimeTest(unittest.TestCase):
    def runtime(self, root: Path) -> hosted.HostedV2Substrate:
        prepare_root(root)
        return hosted.HostedV2Substrate(
            root,
            repo="ni-da-ba/skyforge",
            require_webhook_secret=True,
            startup_reconcile=True,
            webhook_secret=SECRET,
            trusted_actors=("ni-da-ba",),
        )

    def test_signed_continue_dr70_persists_human_gate_proposal_without_task_authority(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            payload = objective_payload()
            status, response = runtime.handle_webhook(
                headers=signed_headers(payload, delivery="objective-1"), raw=payload
            )
            self.assertEqual(status, 202)
            self.assertTrue(response["accepted"])
            self.assertTrue(response["objective"])
            self.assertTrue(response["objective_recorded"])
            self.assertEqual(response["objective_disposition"], "HUMAN_GATE")
            self.assertFalse(response["executable_task_authority"])
            self.assertFalse(response["task_authority_recorded"])
            self.assertEqual(len(runtime.task_authority_store.load().records), 0)
            self.assertEqual(len(runtime.state.inbox.pending_events), 0)

            ledger = ObjectiveProposalStore.for_root(root).load()
            self.assertEqual(len(ledger.records), 1)
            record = ledger.records[0]
            self.assertEqual(record.source.objective_text, "Continue DR-70")
            self.assertEqual(record.compiled.human_gate.node_id, "dr-human-exploration-rereview")
            self.assertFalse(record.compiled.as_dict()["executable_task_authority"])

            health = runtime.health_snapshot()
            self.assertTrue(health["objective_intake_enabled"])
            self.assertEqual(health["objective_proposal_count"], 1)
            self.assertEqual(health["latest_objective_disposition"], "HUMAN_GATE")
            self.assertEqual(health["latest_objective_proposal_id"], record.proposal_id)

    def test_duplicate_delivery_and_redelivery_do_not_duplicate_proposal(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            payload = objective_payload()
            first = runtime.handle_webhook(
                headers=signed_headers(payload, delivery="objective-1"), raw=payload
            )
            duplicate = runtime.handle_webhook(
                headers=signed_headers(payload, delivery="objective-1"), raw=payload
            )
            redelivery = runtime.handle_webhook(
                headers=signed_headers(payload, delivery="objective-2"), raw=payload
            )
            self.assertEqual(first[0], 202)
            self.assertEqual(duplicate[0], 200)
            self.assertTrue(duplicate[1]["duplicate"])
            self.assertEqual(redelivery[0], 202)
            self.assertFalse(redelivery[1]["objective_recorded"])
            self.assertEqual(len(ObjectiveProposalStore.for_root(root).load().records), 1)
            self.assertEqual(len(runtime.task_authority_store.load().records), 0)
            self.assertEqual(len(runtime.state.inbox.pending_events), 0)

    def test_untrusted_objective_marker_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            payload = objective_payload(actor="mallory")
            status, response = runtime.handle_webhook(
                headers=signed_headers(payload, delivery="objective-bad"), raw=payload
            )
            self.assertEqual(status, 409)
            self.assertIn("objective actor is not trusted", response["error"])
            self.assertEqual(len(ObjectiveProposalStore.for_root(root).load().records), 0)

    def test_ordinary_comment_is_not_objective_ingress(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            payload = objective_payload(body="Continue DR-70")
            status, response = runtime.handle_webhook(
                headers=signed_headers(payload, delivery="ordinary-comment"), raw=payload
            )
            self.assertEqual(status, 202)
            self.assertNotIn("objective", response)
            self.assertEqual(len(ObjectiveProposalStore.for_root(root).load().records), 0)
            self.assertEqual(len(runtime.task_authority_store.load().records), 0)


if __name__ == "__main__":
    unittest.main()
