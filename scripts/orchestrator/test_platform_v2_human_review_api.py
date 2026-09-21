from __future__ import annotations

import hashlib
import json
from http.server import ThreadingHTTPServer
from pathlib import Path
import subprocess
import tempfile
import threading
import urllib.error
import urllib.request
import unittest

import platform_v2_hosted_runtime as hosted
from test_platform_v2_development_read_api import API_TOKEN
from test_platform_v2_hosted_runtime import SECRET, write_legacy
from test_platform_v2_roadmap_service import gate, legacy_projection, manifest, task
from v2.human_review import (
    DevelopmentApiHumanReviewSource,
    HumanReviewStore,
    HumanReviewSubmission,
    HumanReviewVerdict,
)
from v2.human_review_command import (
    HumanReviewCommandPhase,
    HumanReviewCommandStore,
    prepare_human_review_command,
)
from v2.objective_intake import ObjectiveCompileDisposition, compile_objective
from v2.program_progression import (
    ProgramContinuationLedger,
    ProgramContinuationSession,
    ProgramContinuationStore,
    ProgramSessionDisposition,
)
from v2.program_projection import load_program_projection
from v2.roadmap_service import RoadmapAuthorityLedger, RoadmapAuthorityStore


WRITE_TOKEN = "development-write-token-test-0123456789abcdef"
WRITE_ACTOR = "ni-da-ba"
ROADMAP_ID = "skyforge-dressed-region-convergence-v3"


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(root), *args],
        check=True,
        text=True,
        capture_output=True,
    )
    return result.stdout.strip()


def review_manifest():
    # Keep the accepted roadmap identity so objective-intake compilation exercises
    # the same target alias/selection path as DR-70.
    base = manifest(
        [
            task("repair", 754, max_runs=1),
            gate("review", prereq=("repair",), message="Review exact specimen."),
            task("later", 900, priority=10, prereq=("review",)),
        ]
    )
    raw = {
        "schema_version": 1,
        "roadmap_id": ROADMAP_ID,
        "enabled": True,
        "max_auto_claims_per_utc_day": 6,
        "nodes": [
            task("repair", 754, max_runs=1),
            gate("review", prereq=("repair",), message="Review exact specimen."),
            task("later", 900, priority=10, prereq=("review",)),
        ],
    }
    from v2.roadmap_shadow import ShadowRoadmapManifest
    return ShadowRoadmapManifest.from_mapping(raw), raw


def prepare_root(root: Path):
    subprocess.run(["git", "init", "-b", "main"], cwd=root, check=True, capture_output=True)
    git(root, "config", "user.email", "test@example.invalid")
    git(root, "config", "user.name", "Skyforge Review API Test")
    (root / ".gitignore").write_text(
        ".skyforge-platform-v2/\n.skyforge-orchestrator/\n",
        encoding="utf-8",
    )
    docs = root / "docs" / "agent-state"
    docs.mkdir(parents=True)
    m, raw_manifest = review_manifest()
    (docs / "ORCHESTRATOR_ROADMAP.json").write_text(
        json.dumps(raw_manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    (docs / "REVIEW_ARTIFACTS.json").write_text(
        json.dumps({"schema_version": 1, "artifacts": []}) + "\n",
        encoding="utf-8",
    )
    (root / "tracked.txt").write_text("review specimen source\n", encoding="utf-8")
    git(root, "add", ".gitignore", "tracked.txt", "docs/agent-state/ORCHESTRATOR_ROADMAP.json", "docs/agent-state/REVIEW_ARTIFACTS.json")
    git(root, "commit", "-m", "review source")
    source_sha = git(root, "rev-parse", "HEAD")

    artifact_raw = {
        "schema_version": 1,
        "artifacts": [
            {
                "artifact_id": "interactive:test",
                "kind": "INTERACTIVE_SPECIMEN",
                "source_sha": source_sha,
                "title": "Review specimen",
                "description": "Exact interactive test specimen",
                "interactive": {
                    "specimen_kind": "test-world",
                    "parameters": {"specimen_key": 42},
                    "preparation_entry_points": [":test:prepare"],
                    "launch_entry_point": ":test:launch",
                    "review_actions": ["inspect exact specimen"],
                    "associated_artifact_ids": [],
                },
            }
        ],
    }
    (docs / "REVIEW_ARTIFACTS.json").write_text(
        json.dumps(artifact_raw, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    git(root, "add", "docs/agent-state/REVIEW_ARTIFACTS.json")
    git(root, "commit", "-m", "register specimen")

    write_legacy(root)
    ledger = RoadmapAuthorityLedger.from_legacy_projection(
        legacy_projection(
            m,
            completed={"repair": 1},
            blocked={"review": "awaiting human review"},
        ),
        m,
    )
    RoadmapAuthorityStore.for_root(root).save(ledger)
    return m, source_sha


def install_program_gate(root: Path):
    roadmap_path = root / "docs/agent-state/ORCHESTRATOR_ROADMAP.json"
    digest = hashlib.sha256(roadmap_path.read_bytes()).hexdigest()
    projection_path = root / "docs/agent-state/PROGRAM_PROGRESSION.json"
    projection_path.write_text(
        json.dumps(
            {
                "schema_version": 1,
                "program_id": "test-program",
                "semantic_sources": [
                    {
                        "path": "docs/agent-state/ORCHESTRATOR_ROADMAP.json",
                        "sha256": digest,
                    }
                ],
                "nodes": [
                    {
                        "id": "program-review",
                        "kind": "human_gate",
                        "message": "Review the program-level platform gate.",
                        "prerequisites": [],
                        "review_gate_id": "program-review",
                    }
                ],
            },
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    git(root, "add", "docs/agent-state/PROGRAM_PROGRESSION.json")
    git(root, "commit", "-m", "program gate fixture")
    projection = load_program_projection(root)
    parent = "f" * 64
    session = ProgramContinuationSession(
        parent_proposal_id=parent,
        invocation_proposal_ids=(parent,),
        program_id=projection.program_id,
        projection_digest=projection.digest,
        current_node_id="program-review",
        disposition=ProgramSessionDisposition.WAIT_HUMAN,
        reason="program progression waits for explicit owner review",
        gate_id="program-review",
    )
    ProgramContinuationStore.for_root(root).save(
        ProgramContinuationLedger((session,))
    )
    return projection


def payload(source_sha: str, *, request_id="review-request-0001", verdict="ACCEPTED"):
    return {
        "request_id": request_id,
        "gate_id": "review",
        "artifact_id": "interactive:test",
        "source_sha": source_sha,
        "verdict": verdict,
        "findings": ["reviewed exact specimen"],
        "positive_findings": ["expected behavior visible"],
        "material_delta": "new qualified interactive specimen",
        "next_boundary": "continue accepted roadmap",
        "deferred_product_work": verdict == "CHANGES_REQUIRED",
        "prior_review_id": None,
    }


class HumanReviewApiTest(unittest.TestCase):
    def runtime(self, root: Path):
        m, source_sha = prepare_root(root)
        runtime = hosted.HostedV2Substrate(
            root,
            repo="ni-da-ba/skyforge",
            require_webhook_secret=True,
            startup_reconcile=True,
            webhook_secret=SECRET,
            development_api_token=API_TOKEN,
            development_write_token=WRITE_TOKEN,
            development_write_actor=WRITE_ACTOR,
            trusted_actors=(WRITE_ACTOR,),
        )
        return runtime, m, source_sha

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
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}",
            "X-Skyforge-Client": client,
        }
        request = urllib.request.Request(
            base + "/api/v1/human-reviews",
            data=raw,
            method="POST",
            headers=headers,
        )
        try:
            with urllib.request.urlopen(request, timeout=3) as response:
                return response.status, json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            return exc.code, json.loads(exc.read().decode("utf-8"))

    def test_accepted_review_completes_gate_and_is_visible_to_read_api(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, m, source_sha = self.runtime(root)
            base = self.serve(runtime)
            status, result = self.post(base, payload(source_sha))
            self.assertEqual(status, 202)
            self.assertEqual(result["command_phase"], "RECONCILED")
            self.assertEqual(result["roadmap_disposition"], "ACCEPTED")
            self.assertEqual(result["actor"], WRITE_ACTOR)

            road = RoadmapAuthorityStore.for_root(root).load()
            self.assertEqual(dict(road.completed_runs)["review"], 1)
            self.assertIsNone(road.block_for("review"))
            reviews = HumanReviewStore.for_root(root).load()
            self.assertEqual(len(reviews.records), 1)
            self.assertEqual(reviews.records[0].source.actor, WRITE_ACTOR)

            request = urllib.request.Request(
                base + "/api/v1/development-state",
                headers={"Authorization": f"Bearer {API_TOKEN}"},
            )
            with urllib.request.urlopen(request, timeout=3) as response:
                state = json.loads(response.read().decode("utf-8"))
            self.assertEqual(state["roadmap"]["completed_runs"]["review"], 1)
            self.assertNotIn("review", state["roadmap"]["blocked_nodes"])
            self.assertEqual(state["human_reviews"][-1]["source"]["kind"], "DEVELOPMENT_API")
            self.assertEqual(state["human_reviews"][-1]["source"]["client"], "operations-console")

            compiled = compile_objective(f"Continue {ROADMAP_ID}", root=root)
            self.assertEqual(compiled.disposition, ObjectiveCompileDisposition.CANDIDATE_TASK)
            self.assertEqual(compiled.candidate_task.node_id, "later")

    def test_changes_required_stays_blocked_and_read_projection_names_review(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _m, source_sha = self.runtime(root)
            base = self.serve(runtime)
            status, result = self.post(base, payload(source_sha, verdict="CHANGES_REQUIRED"))
            self.assertEqual(status, 202)
            self.assertEqual(result["roadmap_disposition"], "CHANGES_REQUIRED")
            road = RoadmapAuthorityStore.for_root(root).load()
            self.assertNotIn("review", dict(road.completed_runs))
            self.assertIn(result["review_id"], road.block_for("review").reason)
            state = runtime.development_snapshot()
            self.assertIn("review", state["roadmap"]["blocked_nodes"])
            self.assertIn(result["review_id"], state["roadmap"]["blocked_nodes"]["review"]["reason"])

    def test_active_program_gate_review_persists_without_mutating_dr_roadmap(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _m, source_sha = prepare_root(root)
            projection = install_program_gate(root)
            runtime = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                development_api_token=API_TOKEN,
                development_write_token=WRITE_TOKEN,
                development_write_actor=WRITE_ACTOR,
                trusted_actors=(WRITE_ACTOR,),
            )
            base = self.serve(runtime)
            road_before = RoadmapAuthorityStore.for_root(root).load().digest
            body = payload(source_sha, request_id="program-review-request-0001")
            body["gate_id"] = "program-review"
            body["material_delta"] = "exact program platform-gate evidence"
            body["next_boundary"] = "resume bounded product work"

            status, result = self.post(base, body)
            self.assertEqual(status, 202)
            self.assertEqual(result["command_phase"], "RECONCILED")
            self.assertEqual(result["authority_scope"], "PROGRAM")
            self.assertEqual(result["roadmap_disposition"], "ACCEPTED")
            self.assertEqual(
                RoadmapAuthorityStore.for_root(root).load().digest,
                road_before,
                "program review must not mutate the subordinate DR roadmap",
            )
            reviews = HumanReviewStore.for_root(root).load()
            self.assertEqual(reviews.latest_for_gate("program-review").verdict.value, "ACCEPTED")
            command = HumanReviewCommandStore.for_root(root).load().get(
                "program-review-request-0001"
            )
            self.assertEqual(command.authority_scope.value, "PROGRAM")
            self.assertEqual(command.roadmap_before_digest, projection.digest)
            self.assertEqual(command.roadmap_after_digest, projection.digest)

    def test_inactive_program_gate_cannot_be_preaccepted(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _m, source_sha = prepare_root(root)
            install_program_gate(root)
            store = ProgramContinuationStore.for_root(root)
            active = store.load().active
            store.save(
                ProgramContinuationLedger(
                    (
                        ProgramContinuationSession(
                            parent_proposal_id=active.parent_proposal_id,
                            invocation_proposal_ids=active.invocation_proposal_ids,
                            program_id=active.program_id,
                            projection_digest=active.projection_digest,
                            current_node_id=active.current_node_id,
                            disposition=ProgramSessionDisposition.ADVANCING,
                            reason="not at human boundary",
                            gate_id="",
                        ),
                    )
                )
            )
            runtime = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                development_api_token=API_TOKEN,
                development_write_token=WRITE_TOKEN,
                development_write_actor=WRITE_ACTOR,
                trusted_actors=(WRITE_ACTOR,),
            )
            base = self.serve(runtime)
            body = payload(source_sha, request_id="program-review-request-0002")
            body["gate_id"] = "program-review"
            status, result = self.post(base, body)
            self.assertEqual(status, 409)
            self.assertIn("neither an active program gate", result["error"])
            self.assertIsNone(
                HumanReviewStore.for_root(root).load().latest_for_gate("program-review")
            )

    def test_read_token_cannot_write_and_write_token_does_not_gain_read_access(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _m, source_sha = self.runtime(root)
            base = self.serve(runtime)
            status, _ = self.post(base, payload(source_sha), token=API_TOKEN)
            self.assertEqual(status, 401)
            request = urllib.request.Request(
                base + "/api/v1/development-state",
                headers={"Authorization": f"Bearer {WRITE_TOKEN}"},
            )
            with self.assertRaises(urllib.error.HTTPError) as raised:
                urllib.request.urlopen(request, timeout=3)
            self.assertEqual(raised.exception.code, 401)

    def test_client_cannot_spoof_actor_or_source(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _m, source_sha = self.runtime(root)
            base = self.serve(runtime)
            body = payload(source_sha)
            body["actor"] = "mallory"
            status, result = self.post(base, body)
            self.assertEqual(status, 400)
            self.assertIn("server-bound", result["error"])
            self.assertEqual(len(HumanReviewStore.for_root(root).load().records), 0)

    def test_unregistered_or_source_mismatched_artifact_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _m, source_sha = self.runtime(root)
            base = self.serve(runtime)
            missing = payload(source_sha)
            missing["artifact_id"] = "missing:test"
            status, _ = self.post(base, missing)
            self.assertEqual(status, 409)

        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _m, source_sha = self.runtime(root)
            base = self.serve(runtime)
            mismatch = payload(source_sha)
            mismatch["source_sha"] = "b" * 40
            status, result = self.post(base, mismatch)
            self.assertEqual(status, 409)
            self.assertIn("does not match registered artifact", result["error"])

    def test_identical_request_replays_idempotently_and_conflict_is_409(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _m, source_sha = self.runtime(root)
            wake_calls = []
            runtime._signal_execution_driver = lambda: wake_calls.append("wake")
            base = self.serve(runtime)
            body = payload(source_sha)
            first_status, first = self.post(base, body)
            second_status, second = self.post(base, body)
            self.assertEqual(first_status, 202)
            self.assertEqual(second_status, 200)
            self.assertFalse(first["idempotent_replay"])
            self.assertTrue(second["idempotent_replay"])
            self.assertEqual(first["review_id"], second["review_id"])
            self.assertEqual(len(HumanReviewStore.for_root(root).load().records), 1)
            self.assertEqual(
                wake_calls,
                ["wake"],
                "idempotent replay must not re-wake the execution driver",
            )

            conflict = dict(body)
            conflict["findings"] = ["different judgment"]
            status, result = self.post(base, conflict)
            self.assertEqual(status, 409)
            self.assertIn("conflicting payload", result["error"])

    def test_x4_rejects_unchanged_repeated_artifact(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            runtime, _m, source_sha = self.runtime(root)
            base = self.serve(runtime)
            existing = HumanReviewSubmission(
                source=DevelopmentApiHumanReviewSource(
                    repo="ni-da-ba/skyforge",
                    request_id="prior-review-0001",
                    actor=WRITE_ACTOR,
                    client="operations-console",
                    submitted_at="2026-09-20T23:00:00Z",
                ),
                gate_id="review",
                artifact_id="interactive:test",
                source_sha=source_sha,
                verdict=HumanReviewVerdict.CHANGES_REQUIRED,
                findings=("prior issue",),
                positive_findings=(),
                material_delta="prior material delta",
                next_boundary="repair artifact",
                deferred_product_work=False,
            )
            runtime.human_review_store.capture(existing)
            body = payload(source_sha, request_id="review-request-0002")
            body["prior_review_id"] = existing.review_id
            body["material_delta"] = "different description but same artifact"
            status, result = self.post(base, body)
            self.assertEqual(status, 409)
            self.assertEqual(result["repeat_review_disposition"], "BLOCKED_UNCHANGED_ARTIFACT")

    def test_runtime_startup_finishes_crash_interrupted_review_reconciliation(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            m, source_sha = prepare_root(root)
            review = HumanReviewSubmission(
                source=DevelopmentApiHumanReviewSource(
                    repo="ni-da-ba/skyforge",
                    request_id="restart-review-0001",
                    actor=WRITE_ACTOR,
                    client="operations-console",
                    submitted_at="2026-09-20T23:40:00Z",
                ),
                gate_id="review",
                artifact_id="interactive:test",
                source_sha=source_sha,
                verdict=HumanReviewVerdict.ACCEPTED,
                findings=("accepted exact specimen",),
                positive_findings=("expected result visible",),
                material_delta="new qualified specimen",
                next_boundary="continue roadmap",
                deferred_product_work=False,
            )
            command_store = HumanReviewCommandStore.for_root(root)
            review_store = HumanReviewStore.for_root(root)
            roadmap_store = RoadmapAuthorityStore.for_root(root)
            prepared = prepare_human_review_command(
                store=command_store,
                review=review,
                roadmap=roadmap_store.load(),
                manifest=m,
            )
            review_store.capture(review)
            command_store.put(
                prepared.with_phase(HumanReviewCommandPhase.REVIEW_PERSISTED)
            )

            restarted = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                development_api_token=API_TOKEN,
                development_write_token=WRITE_TOKEN,
                development_write_actor=WRITE_ACTOR,
                trusted_actors=(WRITE_ACTOR,),
            )
            self.assertEqual(
                restarted.startup_human_review_command_reconciliations,
                1,
            )
            recovered = command_store.load().get("restart-review-0001")
            self.assertEqual(recovered.phase, HumanReviewCommandPhase.RECONCILED)
            self.assertEqual(
                dict(roadmap_store.load().completed_runs)["review"],
                1,
            )

    def test_write_api_unconfigured_is_503_and_read_api_still_works(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            _m, source_sha = prepare_root(root)
            runtime = hosted.HostedV2Substrate(
                root,
                repo="ni-da-ba/skyforge",
                require_webhook_secret=True,
                startup_reconcile=True,
                webhook_secret=SECRET,
                development_api_token=API_TOKEN,
                trusted_actors=(WRITE_ACTOR,),
            )
            base = self.serve(runtime)

            status, result = self.post(base, payload(source_sha), token=WRITE_TOKEN)
            self.assertEqual(status, 503)
            self.assertEqual(result["error"], "development write API is not configured")
            self.assertFalse(runtime.health_snapshot()["development_write_api_enabled"])

            request = urllib.request.Request(
                base + "/api/v1/development-state",
                headers={"Authorization": f"Bearer {API_TOKEN}"},
            )
            with urllib.request.urlopen(request, timeout=3) as response:
                self.assertEqual(response.status, 200)

    def test_write_configuration_requires_distinct_trusted_actor_pair(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            with self.assertRaisesRegex(RuntimeError, "configured together"):
                hosted.HostedV2Substrate(
                    root,
                    repo="ni-da-ba/skyforge",
                    require_webhook_secret=True,
                    startup_reconcile=True,
                    webhook_secret=SECRET,
                    development_write_token=WRITE_TOKEN,
                    trusted_actors=(WRITE_ACTOR,),
                )
            with self.assertRaisesRegex(RuntimeError, "trusted configured actor"):
                hosted.HostedV2Substrate(
                    root,
                    repo="ni-da-ba/skyforge",
                    require_webhook_secret=True,
                    startup_reconcile=True,
                    webhook_secret=SECRET,
                    development_write_token=WRITE_TOKEN,
                    development_write_actor="mallory",
                    trusted_actors=(WRITE_ACTOR,),
                )


if __name__ == "__main__":
    unittest.main()
