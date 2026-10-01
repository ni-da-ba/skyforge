from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from v2 import (
    AcceptanceIdentity,
    FenceBusyError,
    FrozenTaskSpec,
    ReplayRecord,
    TaskAttemptIdentity,
    WriterFence,
)


class FrozenIdentityTest(unittest.TestCase):
    def test_mapping_key_order_does_not_change_spec_hash(self) -> None:
        first = FrozenTaskSpec.from_payload(
            task_id="DR-80",
            authority_key="issue:800",
            base_sha="a" * 40,
            spec_version=2,
            payload={"b": 2, "a": {"y": 2, "x": 1}},
        )
        second = FrozenTaskSpec.from_payload(
            task_id="DR-80",
            authority_key="issue:800",
            base_sha="a" * 40,
            spec_version=2,
            payload={"a": {"x": 1, "y": 2}, "b": 2},
        )
        self.assertEqual(first.spec_hash, second.spec_hash)
        self.assertEqual(first.canonical_payload, second.canonical_payload)

    def test_authoritative_content_change_changes_spec_hash(self) -> None:
        first = FrozenTaskSpec.from_payload(
            task_id="DR-80",
            authority_key="issue:800",
            base_sha="a" * 40,
            spec_version=2,
            payload={"objective": "one"},
        )
        second = FrozenTaskSpec.from_payload(
            task_id="DR-80",
            authority_key="issue:800",
            base_sha="a" * 40,
            spec_version=2,
            payload={"objective": "two"},
        )
        self.assertNotEqual(first.spec_hash, second.spec_hash)

    def test_attempt_identity_binds_spec_base_and_attempt_number(self) -> None:
        spec = FrozenTaskSpec.from_payload(
            task_id="DR-80",
            authority_key="issue:800",
            base_sha="a" * 40,
            spec_version=2,
            payload={"objective": "bounded"},
        )
        first = TaskAttemptIdentity.create(spec, attempt_number=1)
        repeat = TaskAttemptIdentity.create(spec, attempt_number=1)
        second = TaskAttemptIdentity.create(spec, attempt_number=2)
        self.assertEqual(first.attempt_id, repeat.attempt_id)
        self.assertNotEqual(first.attempt_id, second.attempt_id)
        self.assertEqual(first.base_sha, spec.base_sha)
        self.assertEqual(first.spec_hash, spec.spec_hash)

    def test_attempt_number_must_be_positive(self) -> None:
        spec = FrozenTaskSpec.from_payload(
            task_id="DR-80",
            authority_key="issue:800",
            base_sha="a" * 40,
            spec_version=2,
            payload={},
        )
        with self.assertRaises(ValueError):
            TaskAttemptIdentity.create(spec, attempt_number=0)


class AcceptanceIdentityTest(unittest.TestCase):
    def test_exact_match_requires_same_sha_and_spec(self) -> None:
        identity = AcceptanceIdentity(
            current_head_sha="b" * 40,
            required_evidence_sha="b" * 40,
            reviewed_sha="b" * 40,
            task_spec_hash="c" * 64,
            accepted_task_spec_hash="c" * 64,
        )
        self.assertTrue(identity.exact_match())

    def test_head_or_spec_movement_invalidates_acceptance(self) -> None:
        head_moved = AcceptanceIdentity(
            current_head_sha="d" * 40,
            required_evidence_sha="b" * 40,
            reviewed_sha="b" * 40,
            task_spec_hash="c" * 64,
            accepted_task_spec_hash="c" * 64,
        )
        spec_moved = AcceptanceIdentity(
            current_head_sha="b" * 40,
            required_evidence_sha="b" * 40,
            reviewed_sha="b" * 40,
            task_spec_hash="c" * 64,
            accepted_task_spec_hash="e" * 64,
        )
        self.assertFalse(head_moved.exact_match())
        self.assertFalse(spec_moved.exact_match())


class ReplayRecordTest(unittest.TestCase):
    def test_replay_record_is_stable_under_mapping_reordering(self) -> None:
        first = ReplayRecord.build(
            state_before={"b": 2, "a": 1},
            event={"kind": "task", "id": 7},
            expected_actions=[{"action": "dispatch", "lane": "Implementation"}],
            state_after={"pending": True, "attempt": 1},
        )
        second = ReplayRecord.build(
            state_before={"a": 1, "b": 2},
            event={"id": 7, "kind": "task"},
            expected_actions=[{"lane": "Implementation", "action": "dispatch"}],
            state_after={"attempt": 1, "pending": True},
        )
        self.assertEqual(first, second)

    def test_replay_record_changes_when_expected_action_changes(self) -> None:
        first = ReplayRecord.build(
            state_before={},
            event={"id": 7},
            expected_actions=["WAIT"],
            state_after={},
        )
        second = ReplayRecord.build(
            state_before={},
            event={"id": 7},
            expected_actions=["DISPATCH"],
            state_after={},
        )
        self.assertNotEqual(first.record_id, second.record_id)
        self.assertNotEqual(first.expected_action_digest, second.expected_action_digest)


class WriterFenceTest(unittest.TestCase):
    def test_fence_is_exclusive_and_reusable_after_release(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "writer.lock"
            first = WriterFence(path=path, controller_id="controller-a")
            second = WriterFence(path=path, controller_id="controller-b")
            first.acquire()
            try:
                with self.assertRaises(FenceBusyError):
                    second.acquire()
            finally:
                first.release()

            second.acquire()
            second.release()


class ProductionIsolationTest(unittest.TestCase):
    def test_production_runtime_does_not_import_v2(self) -> None:
        root = Path(__file__).resolve().parent
        candidates = [
            root / "skyforge_orchestrator.py",
            *sorted(root.glob("skyforge*_runtime.py")),
        ]
        for path in candidates:
            text = path.read_text(encoding="utf-8")
            self.assertNotIn("import v2", text, path.name)
            self.assertNotIn("from v2", text, path.name)


if __name__ == "__main__":
    unittest.main()
