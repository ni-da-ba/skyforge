from __future__ import annotations

import json
from http.server import ThreadingHTTPServer
from pathlib import Path
import tempfile
import threading
import urllib.error
import urllib.request
import unittest

import platform_v2_hosted_runtime as hosted
from test_platform_v2_development_read_api import API_TOKEN
from test_platform_v2_human_review_api import (
    WRITE_ACTOR,
    WRITE_TOKEN,
    prepare_root,
)
from v2.objective_command import (
    ObjectiveCommandPhase,
    ObjectiveCommandStore,
    prepare_objective_command,
)
from v2.objective_ingress import (
    DevelopmentApiObjectiveSource,
    ObjectiveProposalStore,
)


class WakeCounter:
    def __init__(self) -> None:
        self.count = 0

    def wake(self) -> None:
        self.count += 1


class ObjectiveApiTest(unittest.TestCase):
    def runtime(self, root: Path):
        _manifest, _source_sha = prepare_root(root)
        runtime = hosted.HostedV2Substrate(
            root,
            repo="ni-da-ba/skyforge",
            require_webhook_secret=True,
            startup_reconcile=True,
            webhook_secret="objective-api-secret-0123456789abcdef",
            development_api_token=API_TOKEN,
            development_write_token=WRITE_TOKEN,
            development_write_actor=WRITE_ACTOR,
            trusted_actors=(WRITE_ACTOR,),
        )
        return runtime

    def serve(self, runtime: hosted.HostedV2Substrate) -> str:
        hosted.Handler.runtime = runtime
        server = ThreadingHTTPServer(("127.0.0.1", 0), hosted.Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        self.addCleanup(thread.join, 2)
        return f"http://127.0.0.1:{server.server_address[1]}"

    def post(self, base: str, body: dict, *, token=WRITE_TOKEN, client="operations-console"):
        raw = json.dumps(body, separators=(",", ":")).encode("utf-8")
        request = urllib.request.Request(
            base + "/api/v1/objectives",
            data=raw,
            method="POST",
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {token}",
                "X-Skyforge-Client": client,
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=3) as response:
                return response.status, json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            return exc.code, json.loads(exc.read().decode("utf-8"))

    def test_objective_command_is_durable_visible_and_never_task_authority(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            base = self.serve(runtime)
            status, result = self.post(
                base,
                {
                    "request_id": "objective-request-1001",
                    "objective": "Investigate landing gear",
                },
            )
            self.assertEqual(status, 202)
            self.assertEqual(result["command_phase"], "RECONCILED")
            self.assertEqual(result["objective_disposition"], "READ_ONLY")
            self.assertFalse(result["executable_task_authority"])
            self.assertFalse(result["task_authority_recorded"])
            self.assertEqual(result["actor"], WRITE_ACTOR)
            self.assertEqual(len(runtime.task_authority_store.load().records), 0)
            self.assertEqual(len(runtime.state.inbox.pending_events), 0)

            request = urllib.request.Request(
                base + "/api/v1/development-state",
                headers={"Authorization": f"Bearer {API_TOKEN}"},
            )
            with urllib.request.urlopen(request, timeout=3) as response:
                state = json.loads(response.read().decode("utf-8"))
            objective = state["objectives"][-1]
            self.assertEqual(objective["proposal_id"], result["proposal_id"])
            self.assertEqual(objective["source"]["kind"], "DEVELOPMENT_API")
            self.assertEqual(objective["source"]["request_id"], "objective-request-1001")
            self.assertEqual(objective["source"]["client"], "operations-console")
            self.assertEqual(objective["source"]["actor"], WRITE_ACTOR)

    def test_objective_trace_get_is_authenticated_and_matches_backend_projector(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            base = self.serve(runtime)
            status, submitted = self.post(
                base,
                {
                    "request_id": "objective-trace-1001",
                    "objective": "Investigate landing gear",
                },
            )
            self.assertEqual(status, 202)
            correlation_id = submitted["proposal_id"]

            expected_status, expected = runtime.handle_objective_trace(
                f"Bearer {API_TOKEN}",
                correlation_id,
            )
            self.assertEqual(expected_status, 200)
            request = urllib.request.Request(
                base + f"/api/v1/objectives/{correlation_id}/trace",
                headers={"Authorization": f"Bearer {API_TOKEN}"},
            )
            with urllib.request.urlopen(request, timeout=3) as response:
                actual = json.loads(response.read().decode("utf-8"))
            self.assertEqual(actual, expected)
            self.assertEqual(actual["correlation_id"], correlation_id)
            self.assertEqual(actual["terminal_stage"], "OBJECTIVE")
            self.assertEqual(actual["stages"][0]["identities"]["proposal_id"], correlation_id)

            denied = urllib.request.Request(
                base + f"/api/v1/objectives/{correlation_id}/trace"
            )
            with self.assertRaises(urllib.error.HTTPError) as raised:
                urllib.request.urlopen(denied, timeout=3)
            self.assertEqual(raised.exception.code, 401)

            missing = urllib.request.Request(
                base + f"/api/v1/objectives/{'0' * 64}/trace",
                headers={"Authorization": f"Bearer {API_TOKEN}"},
            )
            with self.assertRaises(urllib.error.HTTPError) as raised:
                urllib.request.urlopen(missing, timeout=3)
            self.assertEqual(raised.exception.code, 404)

    def test_objective_lifecycle_control_is_exact_durable_and_idempotent(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            _status, submitted = runtime.handle_objective_submit(
                f"Bearer {WRITE_TOKEN}",
                {
                    "request_id": "objective-control-http-seed",
                    "objective": "Investigate landing gear",
                },
                client="test",
            )
            proposal_id = submitted["proposal_id"]
            base = self.serve(runtime)
            body = json.dumps(
                {
                    "request_id": "objective-control-http-0001",
                    "proposal_id": proposal_id,
                    "operation": "PAUSE",
                    "reason": "bounded operator hold",
                },
                separators=(",", ":"),
            ).encode("utf-8")
            def invoke(payload=body):
                request = urllib.request.Request(
                    base + "/api/v1/objective-controls",
                    data=payload,
                    method="POST",
                    headers={
                        "Content-Type": "application/json",
                        "Authorization": f"Bearer {WRITE_TOKEN}",
                        "X-Skyforge-Client": "operations-console",
                    },
                )
                try:
                    with urllib.request.urlopen(request, timeout=3) as response:
                        return response.status, json.loads(response.read().decode("utf-8"))
                except urllib.error.HTTPError as exc:
                    return exc.code, json.loads(exc.read().decode("utf-8"))

            first_status, first = invoke()
            replay_status, replay = invoke()
            self.assertEqual(first_status, 202)
            self.assertEqual(replay_status, 200)
            self.assertEqual(first["proposal_id"], proposal_id)
            self.assertEqual(first["state"], "PAUSED")
            self.assertFalse(first["idempotent_replay"])
            self.assertTrue(replay["idempotent_replay"])

            changed = json.dumps(
                {
                    "request_id": "objective-control-http-0001",
                    "proposal_id": proposal_id,
                    "operation": "CANCEL",
                    "reason": "changed replay",
                },
                separators=(",", ":"),
            ).encode("utf-8")
            conflict_status, conflict = invoke(changed)
            self.assertEqual(conflict_status, 409)
            self.assertIn("replay changed payload", conflict["error"])

    def test_continue_objective_compiles_against_current_human_gate_without_bypass(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            base = self.serve(runtime)
            status, result = self.post(
                base,
                {
                    "request_id": "objective-request-1002",
                    "objective": "Continue skyforge-dressed-region-convergence-v3",
                },
            )
            self.assertEqual(status, 202)
            self.assertEqual(result["objective_disposition"], "BLOCKED")
            self.assertIn(
                "external producer authority remains active",
                result["reason"],
            )
            self.assertIsNone(result["human_gate"])
            self.assertIsNone(result["candidate_task"])
            self.assertEqual(len(runtime.task_authority_store.load().records), 0)

    def test_continue_skyforge_is_typed_program_parent_and_wakes_once(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            wake = WakeCounter()
            runtime.execution_driver = wake
            base = self.serve(runtime)
            status, result = self.post(
                base,
                {
                    "request_id": "objective-program-1001",
                    "objective": "Continue Skyforge",
                },
            )
            self.assertEqual(status, 202)
            self.assertEqual(result["objective_disposition"], "PROGRAM_CONTINUE")
            self.assertFalse(result["executable_task_authority"])
            self.assertFalse(result["task_authority_recorded"])
            self.assertIsNone(result["candidate_task"])
            self.assertIsNone(result["human_gate"])
            self.assertEqual(wake.count, 1)
            self.assertEqual(len(runtime.task_authority_store.load().records), 0)
            self.assertEqual(len(runtime.state.inbox.pending_events), 0)

    def test_identical_request_is_idempotent_conflict_is_409_and_wakes_once(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            wake = WakeCounter()
            runtime.execution_driver = wake
            base = self.serve(runtime)
            body = {
                "request_id": "objective-request-1003",
                "objective": "Investigate landing gear",
            }
            first_status, first = self.post(base, body)
            second_status, second = self.post(base, body)
            self.assertEqual(first_status, 202)
            self.assertEqual(second_status, 200)
            self.assertFalse(first["idempotent_replay"])
            self.assertTrue(second["idempotent_replay"])
            self.assertEqual(first["proposal_id"], second["proposal_id"])
            self.assertEqual(wake.count, 1)
            self.assertEqual(len(ObjectiveProposalStore.for_root(root).load().records), 1)

            conflict = dict(body)
            conflict["objective"] = "Fix landing gear"
            status, result = self.post(base, conflict)
            self.assertEqual(status, 409)
            self.assertIn("conflicting payload", result["error"])
            self.assertEqual(wake.count, 1)

    def test_read_token_cannot_write_and_actor_cannot_be_spoofed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            base = self.serve(runtime)
            body = {
                "request_id": "objective-request-1004",
                "objective": "Investigate landing gear",
            }
            status, _ = self.post(base, body, token=API_TOKEN)
            self.assertEqual(status, 401)

            spoof = dict(body)
            spoof["actor"] = "mallory"
            status, result = self.post(base, spoof)
            self.assertEqual(status, 400)
            self.assertIn("unsupported fields", result["error"])
            self.assertIn("actor", result["unsupported_fields"])
            self.assertEqual(len(ObjectiveProposalStore.for_root(root).load().records), 0)

    def test_invalid_or_non_json_payload_fails_before_domain_mutation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime = self.runtime(root)
            base = self.serve(runtime)
            status, result = self.post(
                base,
                {"request_id": "short", "objective": "Investigate landing gear"},
            )
            self.assertEqual(status, 400)
            self.assertIn("request_id", result["error"])
            self.assertEqual(len(ObjectiveProposalStore.for_root(root).load().records), 0)

            request = urllib.request.Request(
                base + "/api/v1/objectives",
                data=b"not-json",
                method="POST",
                headers={
                    "Content-Type": "text/plain",
                    "Authorization": f"Bearer {WRITE_TOKEN}",
                },
            )
            with self.assertRaises(urllib.error.HTTPError) as raised:
                urllib.request.urlopen(request, timeout=3)
            self.assertEqual(raised.exception.code, 415)

    def test_runtime_startup_finishes_crash_interrupted_objective_command(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            self.runtime(root)
            commands = ObjectiveCommandStore.for_root(root)
            proposals = ObjectiveProposalStore.for_root(root)
            source = DevelopmentApiObjectiveSource(
                repo="ni-da-ba/skyforge",
                request_id="objective-restart-1005",
                actor=WRITE_ACTOR,
                client="operations-console",
                submitted_at="2026-09-21T00:30:00Z",
                objective_text="Investigate landing gear",
            )
            prepared = prepare_objective_command(
                store=commands,
                source=source,
                root=root,
            )
            proposals.capture_record(prepared.proposal)
            commands.put(
                prepared.with_phase(ObjectiveCommandPhase.PROPOSAL_PERSISTED)
            )

            restarted = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret="objective-api-secret-0123456789abcdef",
                development_api_token=API_TOKEN,
                development_write_token=WRITE_TOKEN,
                development_write_actor=WRITE_ACTOR,
                trusted_actors=(WRITE_ACTOR,),
            )
            self.assertEqual(restarted.startup_objective_command_reconciliations, 1)
            recovered = commands.load().get("objective-restart-1005")
            self.assertEqual(recovered.phase, ObjectiveCommandPhase.RECONCILED)
            self.assertEqual(len(proposals.load().records), 1)


if __name__ == "__main__":
    unittest.main()
