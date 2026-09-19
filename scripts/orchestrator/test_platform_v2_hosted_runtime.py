from __future__ import annotations

import hashlib
import hmac
import json
from pathlib import Path
import tempfile
import unittest

import platform_v2_hosted_runtime as hosted
import skyforge_orchestrator as legacy_core
from v2.events import DurableEvent
from v2.hosted_state import HostedIngressState, HostedStateStore
from v2.inbox import InboxState
from v2.ingress import (
    classify_legacy_compatible_event,
    reclassify_legacy_compatible_audit_event,
    verify_github_signature,
)


SECRET = "r5b-test-secret-0123456789abcdef"
MAIN = "a" * 40


def legacy_state() -> dict:
    return {
        "paused": False,
        "blocked_kind": None,
        "pending_worker": None,
        "pending_decision": None,
        "managed": {},
        "pending_events": [],
        "external_producer_claims": {
            "613": {
                "issue_number": 613,
                "claimed_by": "ni-da-ba",
                "state": "active",
                "lane": "Implementation",
                "branch": "platform/613-aero-moment-contract",
                "pr_number": 762,
            },
            "754": {
                "issue_number": 754,
                "claimed_by": "ni-da-ba",
                "state": "active",
                "lane": "Implementation",
                "branch": "implementation/754-dr70-human-visible-repair",
                "pr_number": 769,
            },
        },
        "human_gate_records": {
            "issue:349:implementation": {
                "token": "opaque-human-gate-token",
                "target": "349",
                "seeded_from_github": False,
            }
        },
        "roadmap": {
            "roadmap_id": "skyforge-dressed-region-convergence-v3",
            "active": None,
            "blocked_nodes": {
                "dr-human-exploration-rereview": {
                    "reason": "Machines must not self-pass this re-review."
                }
            },
            "manifest_fingerprint": "f" * 64,
        },
    }


def write_legacy(root: Path) -> bytes:
    state_dir = root / ".skyforge-orchestrator"
    state_dir.mkdir(parents=True)
    payload = json.dumps(legacy_state(), indent=2, sort_keys=True).encode() + b"\n"
    (state_dir / "state.json").write_bytes(payload)
    (state_dir / "state.json.bak").write_bytes(payload)
    return payload


def signed_headers(
    payload: bytes,
    *,
    event: str,
    delivery: str,
) -> dict[str, str]:
    digest = hmac.new(SECRET.encode(), payload, hashlib.sha256).hexdigest()
    return {
        "X-GitHub-Event": event,
        "X-GitHub-Delivery": delivery,
        "X-Hub-Signature-256": "sha256=" + digest,
    }


def push_payload() -> bytes:
    return json.dumps(
        {
            "ref": "refs/heads/main",
            "after": MAIN,
            "repository": {"full_name": "ni-da-ba/skyforge"},
        },
        separators=(",", ":"),
    ).encode()


class IngressCompatibilityTest(unittest.TestCase):
    def test_hmac_verification_is_exact(self):
        payload = b'{"hello":"world"}'
        signature = "sha256=" + hmac.new(
            SECRET.encode(), payload, hashlib.sha256
        ).hexdigest()
        self.assertTrue(verify_github_signature(SECRET, payload, signature))
        self.assertFalse(verify_github_signature(SECRET, payload + b"x", signature))
        self.assertFalse(verify_github_signature(SECRET, payload, "sha1=bad"))
        self.assertFalse(verify_github_signature(SECRET, payload, None))

    def test_compatibility_adapter_preserves_legacy_event_identity(self):
        payload = json.loads(push_payload())
        legacy = legacy_core.classify_event(
            "push",
            payload,
            repo="ni-da-ba/skyforge",
            trusted_actors=("ni-da-ba",),
        )
        v2 = classify_legacy_compatible_event(
            "push",
            payload,
            repo="ni-da-ba/skyforge",
            trusted_actors=("ni-da-ba",),
        )
        self.assertEqual(v2.as_dict(), legacy.to_state())
        self.assertEqual(v2.event_id, legacy_core._event_key(legacy))

    def test_persisted_false_protected_audit_signal_is_downgraded(self):
        event = DurableEvent(
            actionable=True,
            reason="Audit/watchdog orchestration signal",
            event="issue_comment",
            action="audit_signal",
            pr_number=767,
            source_id="5736816842",
            signal_kind="human_gate",
            signal_text=(
                "OPT-1 production exercise succeeded. The accepted HUMAN_GATE remained human "
                "and was not self-approved."
            ),
        )
        migrated = reclassify_legacy_compatible_audit_event(event)
        self.assertEqual(migrated.signal_kind, "audit")
        self.assertEqual(migrated.signal_text, event.signal_text)
        self.assertEqual(migrated.source_id, event.source_id)

        explicit = DurableEvent.from_legacy_mapping(
            {**event.as_dict(), "signal_text": "AUDIT HUMAN GATE — owner review required"}
        )
        self.assertEqual(
            reclassify_legacy_compatible_audit_event(explicit).signal_kind,
            "human_gate",
        )


class HostedSubstrateTest(unittest.TestCase):
    def make_runtime(self, root: Path) -> hosted.HostedV2Substrate:
        write_legacy(root)
        return hosted.HostedV2Substrate(
            root,
            repo="ni-da-ba/skyforge",
            require_webhook_secret=True,
            startup_reconcile=True,
            webhook_secret=SECRET,
            trusted_actors=("ni-da-ba",),
        )

    def test_signed_actionable_delivery_is_durable_and_read_only(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            original_legacy = write_legacy(root)
            runtime = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                trusted_actors=("ni-da-ba",),
            )
            payload = push_payload()
            status, response = runtime.handle_webhook(
                headers=signed_headers(payload, event="push", delivery="delivery-1"),
                raw=payload,
            )
            self.assertEqual(status, 202)
            self.assertTrue(response["accepted"])
            self.assertFalse(response["mutation_authority"])
            self.assertEqual(len(runtime.state.inbox.pending_events), 1)
            self.assertEqual(
                (root / ".skyforge-orchestrator/state.json").read_bytes(),
                original_legacy,
            )

            primary = root / ".skyforge-platform-v2/hosted-state.json"
            backup = root / ".skyforge-platform-v2/hosted-state.json.bak"
            self.assertTrue(primary.is_file())
            self.assertEqual(primary.read_bytes(), backup.read_bytes())

            health = runtime.health_snapshot()
            self.assertFalse(health["mutation_authority"])
            self.assertFalse(health["worker_dispatch_enabled"])
            self.assertFalse(health["remote_effect_execution_enabled"])
            self.assertEqual(health["projected_external_claim_count"], 2)
            self.assertEqual(
                health["projected_roadmap_id"],
                "skyforge-dressed-region-convergence-v3",
            )

    def test_real_github_header_casing_is_case_insensitive(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.make_runtime(root)
            payload = push_payload()
            canonical = signed_headers(payload, event="push", delivery="delivery-real-case")
            headers = {
                "X-Github-Event": canonical["X-GitHub-Event"],
                "X-Github-Delivery": canonical["X-GitHub-Delivery"],
                "X-Hub-Signature-256": canonical["X-Hub-Signature-256"],
            }

            status, response = runtime.handle_webhook(headers=headers, raw=payload)

            self.assertEqual(status, 202)
            self.assertTrue(response["accepted"])
            self.assertEqual(len(runtime.state.inbox.pending_events), 1)
            self.assertIn("delivery-real-case", runtime.state.seen_deliveries)

    def test_duplicate_delivery_and_semantic_redelivery_do_not_duplicate_event(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.make_runtime(root)
            payload = push_payload()
            headers = signed_headers(payload, event="push", delivery="delivery-1")

            first_status, _ = runtime.handle_webhook(headers=headers, raw=payload)
            second_status, second = runtime.handle_webhook(headers=headers, raw=payload)
            third_headers = signed_headers(
                payload,
                event="push",
                delivery="delivery-2",
            )
            third_status, third = runtime.handle_webhook(
                headers=third_headers,
                raw=payload,
            )

            self.assertEqual(first_status, 202)
            self.assertEqual(second_status, 200)
            self.assertTrue(second["duplicate"])
            self.assertEqual(third_status, 202)
            self.assertTrue(third["semantic_replay_suppressed"])
            self.assertEqual(len(runtime.state.inbox.pending_events), 1)
            self.assertEqual(runtime.state.accepted_deliveries, 2)

    def test_invalid_signature_and_repo_mismatch_do_not_advance_state(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.make_runtime(root)
            before = runtime.state.digest
            payload = push_payload()

            status, _ = runtime.handle_webhook(
                headers={
                    "X-GitHub-Event": "push",
                    "X-GitHub-Delivery": "bad-signature",
                    "X-Hub-Signature-256": "sha256=" + "0" * 64,
                },
                raw=payload,
            )
            self.assertEqual(status, 403)
            self.assertEqual(runtime.state.digest, before)

            wrong = json.dumps(
                {
                    "ref": "refs/heads/main",
                    "after": MAIN,
                    "repository": {"full_name": "somewhere/else"},
                }
            ).encode()
            status, _ = runtime.handle_webhook(
                headers=signed_headers(
                    wrong,
                    event="push",
                    delivery="wrong-repo",
                ),
                raw=wrong,
            )
            self.assertEqual(status, 400)
            self.assertEqual(runtime.state.digest, before)

    def test_trusted_control_is_identified_but_cannot_mutate_legacy_or_v2_authority(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            original = write_legacy(root)
            runtime = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=False,
                webhook_secret=SECRET,
                trusted_actors=("ni-da-ba",),
            )
            payload = json.dumps(
                {
                    "action": "created",
                    "repository": {"full_name": "ni-da-ba/skyforge"},
                    "comment": {
                        "id": 1001,
                        "body": "/skyforge-pause",
                        "user": {"login": "ni-da-ba"},
                    },
                }
            ).encode()
            headers = signed_headers(
                payload,
                event="issue_comment",
                delivery="control-1",
            )
            status, response = runtime.handle_webhook(
                headers=headers,
                raw=payload,
            )
            self.assertEqual(status, 202)
            self.assertEqual(response["control"], "pause")
            self.assertTrue(response["read_only"])
            self.assertFalse(response["mutation_authority"])
            self.assertEqual(runtime.state.rejected_controls, 1)
            self.assertEqual(len(runtime.state.inbox.pending_events), 0)
            self.assertEqual(
                (root / ".skyforge-orchestrator/state.json").read_bytes(),
                original,
            )

            status, response = runtime.handle_webhook(
                headers=headers,
                raw=payload,
            )
            self.assertEqual(status, 200)
            self.assertTrue(response["duplicate"])
            self.assertEqual(runtime.state.rejected_controls, 1)

    def test_restart_reloads_exact_hosted_state(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.make_runtime(root)
            payload = push_payload()
            runtime.handle_webhook(
                headers=signed_headers(payload, event="push", delivery="delivery-1"),
                raw=payload,
            )
            digest = runtime.state.digest
            reloaded = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                trusted_actors=("ni-da-ba",),
            )
            self.assertEqual(reloaded.state.digest, digest)
            self.assertEqual(len(reloaded.state.inbox.pending_events), 1)

    def test_restart_migrates_stale_false_protected_audit_signal_once(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_legacy(root)
            stale = DurableEvent(
                actionable=True,
                reason="Audit/watchdog orchestration signal",
                event="issue_comment",
                action="audit_signal",
                pr_number=767,
                source_id="5736816842",
                signal_kind="human_gate",
                signal_text="OPT-2 accepted; earlier HUMAN_GATE remained human and unmodified.",
            )
            HostedStateStore.for_root(root).save(
                HostedIngressState(inbox=InboxState(pending_events=(stale,)))
            )

            migrated = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                trusted_actors=("ni-da-ba",),
            )
            self.assertEqual(migrated.startup_audit_signal_reclassifications, 1)
            self.assertEqual(migrated.state.inbox.pending_events[0].signal_kind, "audit")
            self.assertEqual(migrated.health_snapshot()["startup_audit_signal_reclassifications"], 1)

            restarted = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                trusted_actors=("ni-da-ba",),
            )
            self.assertEqual(restarted.startup_audit_signal_reclassifications, 0)
            self.assertEqual(restarted.state.inbox.pending_events[0].signal_kind, "audit")

    def test_cli_accepts_existing_systemd_argument_shape_but_rejects_auto_merge(self):
        args = hosted.parse_args(
            [
                "--root", ".",
                "--repo", "ni-da-ba/skyforge",
                "--bind", "127.0.0.1",
                "--port", "3000",
                "--require-webhook-secret",
                "--startup-reconcile",
            ]
        )
        self.assertEqual(args.port, 3000)
        self.assertTrue(args.require_webhook_secret)
        self.assertTrue(args.startup_reconcile)
        with self.assertRaises(SystemExit):
            hosted.parse_args(["--auto-merge"])


class SelfRestartHandoffTest(unittest.TestCase):
    def test_legacy_refresh_provides_none_writer_restart_interval(self):
        root = Path(__file__).resolve().parents[2]
        core = (root / "scripts/orchestrator/skyforge_orchestrator.py").read_text()
        service = (root / "deploy/orchestrator/skyforge-orchestrator.service.in").read_text()

        self.assertIn("def sync_main(self)", core)
        self.assertIn("runtime_changes", core)
        self.assertIn("self._request_runtime_restart", core)
        self.assertIn("os._exit(75)", core)
        self.assertIn("Restart=on-failure", service)
        self.assertIn(
            "scripts/orchestrator/skyforge_control_plane_runtime.py",
            service,
        )


class HostedMutationSurfaceTest(unittest.TestCase):
    def test_hosted_substrate_has_no_remote_mutation_surface(self):
        root = Path(__file__).resolve().parent
        runtime_source = (root / "platform_v2_hosted_runtime.py").read_text()
        state_source = (root / "v2/hosted_state.py").read_text()
        combined = runtime_source + "\n" + state_source

        for forbidden in (
            "subprocess",
            "WriterFence",
            "merge_pr(",
            "create_pr(",
            "update_issue",
            "gh api",
            "git push",
            "Codex",
            "._worker",
        ):
            self.assertNotIn(forbidden, combined)

        for forbidden_import in (
            "skyforge_control_plane_runtime",
            "skyforge_roadmap_runtime",
            "skyforge_worker_retry_runtime",
        ):
            self.assertNotIn(forbidden_import, combined)


if __name__ == "__main__":
    unittest.main()
