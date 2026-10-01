from __future__ import annotations

from dataclasses import replace
import json
from pathlib import Path
import tempfile
import unittest

import skyforge_orchestrator as legacy
from v2.decision import DecisionFreshnessObservation, SourcePRState
from v2.events import DurableEvent
from v2.quota import LocalBudgetObservation
from v2.task_authority import (
    TaskAuthorityDisposition,
    hydrate_task_authority,
)
from v2.task_event_composition import (
    TASK_AUTHORITY_EVENTS_RELATIVE_PATH,
    TaskAuthorityEventLedger,
    TaskAuthorityEventRecord,
    TaskAuthorityEventStore,
    build_task_pipeline_seed,
    capture_task_authority_event,
    compose_ordinary_task_request,
)


MAIN = "a" * 40
BODY = """AUDIT NEW TASK [SKYFORGE TASK AUTHORITY]
{"lane":"Implementation","objective":"Implement bounded feature","stop_boundary":"merge boundary","allowed_paths":["docs/operations/**"],"protected_paths":["scripts/orchestrator/**"],"auto_merge_eligible":false}
"""


def payload(*, actor="ni-da-ba", issue_number=900, comment_id=12345, body=BODY):
    return {
        "action": "created",
        "repository": {"full_name": "ni-da-ba/skyforge"},
        "issue": {
            "number": issue_number,
            "title": "Bounded task",
            "body": "Issue prose is context only.",
        },
        "comment": {
            "id": comment_id,
            "body": body,
            "created_at": "2026-09-18T03:00:00Z",
            "updated_at": "2026-09-18T03:00:00Z",
            "user": {"login": actor},
        },
    }


def task_event(raw=None) -> DurableEvent:
    raw = raw or payload()
    decision = legacy.classify_event(
        "issue_comment",
        raw,
        repo="ni-da-ba/skyforge",
        trusted_actors=("ni-da-ba",),
    )
    event = DurableEvent.from_legacy_mapping(decision.to_state())
    assert event.signal_kind == "task"
    return event


def live_issue():
    return {
        "number": 900,
        "state": "open",
        "title": "Bounded task",
        "body": "Issue prose is context only.",
    }


def live_comment():
    return {
        "id": 12345,
        "issue_url": "https://api.github.com/repos/ni-da-ba/skyforge/issues/900",
        "body": BODY,
        "user": {"login": "ni-da-ba"},
        "created_at": "2026-09-18T03:00:00Z",
        "updated_at": "2026-09-18T03:00:00Z",
    }


def captured(*, delivery_id="delivery-1"):
    raw = payload()
    event = task_event(raw)
    value = capture_task_authority_event(
        event=event,
        payload=raw,
        repo="ni-da-ba/skyforge",
        trusted_actors=("ni-da-ba",),
        delivery_id=delivery_id,
    )
    assert value is not None
    return event, value


def hydration(record):
    return hydrate_task_authority(
        reference=record.reference,
        issue=live_issue(),
        comment=live_comment(),
        trusted_actors=("ni-da-ba",),
    )


class TaskEventCaptureTest(unittest.TestCase):
    def test_capture_binds_exact_legacy_compatible_event_without_changing_id(self):
        raw = payload()
        event = task_event(raw)
        legacy_decision = legacy.classify_event(
            "issue_comment",
            raw,
            repo="ni-da-ba/skyforge",
            trusted_actors=("ni-da-ba",),
        )
        before = event.event_id
        record = capture_task_authority_event(
            event=event,
            payload=raw,
            repo="ni-da-ba/skyforge",
            trusted_actors=("ni-da-ba",),
            delivery_id="delivery-1",
        )
        self.assertEqual(event.event_id, before)
        self.assertEqual(event.event_id, legacy._event_key(legacy_decision))
        self.assertEqual(record.event_id, event.event_id)
        self.assertEqual(record.reference.actor, "ni-da-ba")
        self.assertEqual(record.reference.comment_id, 12345)
        self.assertEqual(record.reference.updated_at, "2026-09-18T03:00:00Z")

    def test_non_task_event_has_no_authority_record(self):
        event = DurableEvent(True, "main advanced", "push", head_sha=MAIN)
        self.assertIsNone(
            capture_task_authority_event(
                event=event,
                payload={},
                repo="ni-da-ba/skyforge",
                trusted_actors=("ni-da-ba",),
            )
        )

    def test_capture_rejects_untrusted_actor(self):
        event = task_event(payload())
        with self.assertRaisesRegex(ValueError, "not trusted"):
            capture_task_authority_event(
                event=event,
                payload=payload(actor="mallory"),
                repo="ni-da-ba/skyforge",
                trusted_actors=("ni-da-ba",),
            )

    def test_capture_rejects_event_payload_identity_drift(self):
        event = task_event(payload())
        cases = [
            payload(issue_number=901),
            payload(comment_id=99999),
            payload(body=BODY + "\nchanged"),
        ]
        for raw in cases:
            with self.subTest(raw=raw), self.assertRaises(ValueError):
                capture_task_authority_event(
                    event=event,
                    payload=raw,
                    repo="ni-da-ba/skyforge",
                    trusted_actors=("ni-da-ba",),
                )

        created_drift = payload()
        created_drift["comment"]["created_at"] = "2026-09-18T03:01:00Z"
        with self.assertRaisesRegex(ValueError, "observed_at"):
            capture_task_authority_event(
                event=event,
                payload=created_drift,
                repo="ni-da-ba/skyforge",
                trusted_actors=("ni-da-ba",),
            )

    def test_capture_requires_created_issue_comment_task_shape(self):
        event = task_event(payload())
        raw = payload()
        raw["action"] = "edited"
        with self.assertRaisesRegex(ValueError, "newly-created"):
            capture_task_authority_event(
                event=event,
                payload=raw,
                repo="ni-da-ba/skyforge",
                trusted_actors=("ni-da-ba",),
            )


class TaskAuthorityLedgerTest(unittest.TestCase):
    def test_identical_semantic_redelivery_is_idempotent_across_delivery_ids(self):
        _, first = captured(delivery_id="delivery-1")
        _, second = captured(delivery_id="delivery-2")
        ledger = TaskAuthorityEventLedger().put(first)
        replayed = ledger.put(second)
        self.assertEqual(replayed, ledger)
        self.assertEqual(replayed.get(first.event_id).delivery_id, "delivery-1")

    def test_conflicting_same_event_id_is_rejected(self):
        _, first = captured()
        conflict_ref = replace(first.reference, actor="other-trusted")
        conflict = TaskAuthorityEventRecord(
            event_id=first.event_id,
            reference=conflict_ref,
            delivery_id="delivery-2",
        )
        with self.assertRaisesRegex(ValueError, "conflicting"):
            TaskAuthorityEventLedger().put(first).put(conflict)

    def test_store_is_restart_safe_and_never_writes_legacy_state(self):
        _, record = captured()
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            legacy_dir = root / ".skyforge-orchestrator"
            legacy_dir.mkdir()
            legacy_state = legacy_dir / "state.json"
            legacy_state.write_text('{"sentinel":"legacy"}\n', encoding="utf-8")

            store = TaskAuthorityEventStore.for_root(root)
            store.capture(record)
            reloaded = TaskAuthorityEventStore.for_root(root).load()

            self.assertEqual(reloaded.get(record.event_id), record)
            self.assertEqual(
                legacy_state.read_text(encoding="utf-8"),
                '{"sentinel":"legacy"}\n',
            )
            self.assertTrue((root / TASK_AUTHORITY_EVENTS_RELATIVE_PATH).exists())
            self.assertTrue(
                (root / ".skyforge-platform-v2/task-authority-events.json.bak").exists()
            )

    def test_corrupt_reference_digest_fails_closed(self):
        _, record = captured()
        raw = record.as_dict()
        raw["reference"]["digest"] = "0" * 64
        with self.assertRaisesRegex(ValueError, "digest mismatch"):
            TaskAuthorityEventRecord.from_mapping(raw)


class TaskPipelineCompositionTest(unittest.TestCase):
    def test_fresh_hydration_builds_exact_classifier_seed(self):
        event, record = captured()
        hydrated = hydration(record)
        self.assertEqual(
            hydrated.disposition,
            TaskAuthorityDisposition.EXECUTABLE_V2,
        )
        seed = build_task_pipeline_seed(
            record=record,
            hydration=hydrated,
            current_main=MAIN,
        )
        semantic = seed.classifier_request.semantic_input
        self.assertEqual(seed.classifier_request.instructions_version, 2)
        self.assertEqual(semantic["event_keys"], [event.event_id])
        self.assertEqual(semantic["authority_event_keys"], [event.event_id])
        self.assertEqual(semantic["task_issue_numbers"], [900])
        self.assertEqual(
            semantic["task_authority"]["authority_digest"],
            hydrated.authority.digest,
        )
        self.assertEqual(
            semantic["task_authority"]["allowed_paths"],
            ["docs/operations/**"],
        )
        self.assertIn("Issue prose is context only", semantic["task_context"])

    def test_seed_rejects_non_executable_legacy_directive(self):
        raw = payload(body="AUDIT NEW TASK\nLegacy task without typed authority.")
        event = task_event(raw)
        record = capture_task_authority_event(
            event=event,
            payload=raw,
            repo="ni-da-ba/skyforge",
            trusted_actors=("ni-da-ba",),
        )
        live = live_comment()
        live["body"] = raw["comment"]["body"]
        result = hydrate_task_authority(
            reference=record.reference,
            issue=live_issue(),
            comment=live,
            trusted_actors=("ni-da-ba",),
        )
        self.assertEqual(
            result.disposition,
            TaskAuthorityDisposition.NOT_EXECUTABLE_V2,
        )
        with self.assertRaisesRegex(ValueError, "not executable"):
            build_task_pipeline_seed(
                record=record,
                hydration=result,
                current_main=MAIN,
            )

    def test_composer_passes_exact_authority_and_event_into_r5c6(self):
        event, record = captured()
        hydrated = hydration(record)
        seed = build_task_pipeline_seed(
            record=record,
            hydration=hydrated,
            current_main=MAIN,
        )
        request = compose_ordinary_task_request(
            seed=seed,
            hydration=hydrated,
            freshness=DecisionFreshnessObservation(
                current_main=MAIN,
                source_pr_state=SourcePRState.UNKNOWN,
            ),
            external_claims=(),
            provider_quota=None,
            local_budget=LocalBudgetObservation(0, 2),
            attempt_number=1,
            repo="ni-da-ba/skyforge",
            pr_title="Bounded generated task",
            pr_body="Managed by Platform v2.",
            controller_comment_body="Managed draft PR created.",
        )
        self.assertIs(request.authority, hydrated.authority)
        self.assertEqual(request.event_keys, (event.event_id,))
        self.assertEqual(request.authority_event_keys, (event.event_id,))
        self.assertEqual(request.ordinary_event_keys, ())
        self.assertEqual(request.controller_comment_issue, 900)
        self.assertEqual(
            request.classifier_request.request_id,
            seed.classifier_request.request_id,
        )

    def test_composer_rejects_stale_main_or_mismatched_hydration(self):
        _, record = captured()
        hydrated = hydration(record)
        seed = build_task_pipeline_seed(
            record=record,
            hydration=hydrated,
            current_main=MAIN,
        )
        with self.assertRaisesRegex(ValueError, "freshness"):
            compose_ordinary_task_request(
                seed=seed,
                hydration=hydrated,
                freshness=DecisionFreshnessObservation(current_main="b" * 40),
                external_claims=(),
                provider_quota=None,
                local_budget=LocalBudgetObservation(0, 2),
                attempt_number=1,
                repo="ni-da-ba/skyforge",
                pr_title="x",
                pr_body="y",
            )

    def test_hosted_runtime_composes_capture_but_not_execution(self):
        root = Path(__file__).resolve().parent
        source = (root / "platform_v2_hosted_runtime.py").read_text(encoding="utf-8")
        self.assertIn("TaskAuthorityEventStore", source)
        self.assertIn("capture_task_authority_event", source)
        for forbidden in (
            "compose_ordinary_task_request",
            "advance_ordinary_pipeline",
            "WriterFence",
            "CodexWorkerProvider",
        ):
            self.assertNotIn(forbidden, source)


if __name__ == "__main__":
    unittest.main()
