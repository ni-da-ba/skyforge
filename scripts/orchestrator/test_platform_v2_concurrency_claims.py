from __future__ import annotations

import hashlib
from pathlib import Path
import tempfile
import unittest

from v2.concurrency_claims import (
    ConcurrencyClaimDisposition,
    ConcurrencyClaimLedger,
    ConcurrencyClaimRecord,
    ConcurrencyClaimStore,
    ConcurrencyRetireDisposition,
    acquire_concurrency_claim,
    classify_concurrency_claim,
    retire_concurrency_claim,
)
from v2.path_scope import (
    normalize_mutation_scope,
    overlapping_scope_pairs,
    path_is_allowed,
    scope_contains,
    scopes_overlap,
)
from v2.state_store import StateStoreError
from v2.worker_provider import FrozenWorkerSpec, WorkerTier


def digest(label: str) -> str:
    return hashlib.sha256(label.encode("utf-8")).hexdigest()


def worker(
    name: str,
    *paths: str,
    task_id: str | None = None,
    authority_key: str | None = None,
    attempt_id: str | None = None,
    protected_paths: tuple[str, ...] = (),
) -> FrozenWorkerSpec:
    task = task_id or f"task-{name}"
    authority = authority_key or f"authority:{name}"
    return FrozenWorkerSpec(
        task_id=task,
        authority_key=authority,
        task_spec_hash=digest(f"spec:{name}"),
        attempt_id=attempt_id or digest(f"attempt:{name}"),
        lane="Implementation",
        objective=f"implement {name}",
        stop_boundary=f"stop after {name}",
        base_sha="a" * 40,
        allowed_paths=tuple(paths),
        protected_paths=protected_paths,
        tier=WorkerTier.TERRA,
    )


class PathScopeTest(unittest.TestCase):
    def test_exact_and_subtree_scope_semantics(self):
        self.assertEqual(normalize_mutation_scope("docs/a.md"), "docs/a.md")
        self.assertEqual(normalize_mutation_scope("docs/a/**"), "docs/a/**")
        self.assertTrue(scope_contains("docs/a/b.md", "docs/a/**"))
        self.assertTrue(scope_contains("docs/a/b/**", "docs/a/**"))
        self.assertFalse(scope_contains("docs/a", "docs/a/**"))
        self.assertFalse(scope_contains("docs/ab/x.md", "docs/a/**"))
        self.assertTrue(path_is_allowed("docs/a/b.md", ("docs/a/**",)))
        self.assertFalse(path_is_allowed("docs/ab/b.md", ("docs/a/**",)))

    def test_overlap_matrix_is_exact_and_prefix_safe(self):
        cases = (
            ("docs/a.md", "docs/b.md", False),
            ("docs/a.md", "docs/a.md", True),
            ("docs/**", "docs/a.md", True),
            ("scripts/orchestrator/**", "scripts/orchestrator/x.py", True),
            ("docs/a/**", "docs/ab/**", False),
            ("docs/a/**", "docs/a/b/**", True),
            ("docs/a/file", "docs/a/**", True),
        )
        for left, right, expected in cases:
            with self.subTest(left=left, right=right):
                self.assertEqual(scopes_overlap(left, right), expected)
                self.assertEqual(scopes_overlap(right, left), expected)

        self.assertEqual(
            overlapping_scope_pairs(
                ("docs/a/**", "src/a.py"),
                ("docs/a/b/**", "src/b.py"),
            ),
            (("docs/a/**", "docs/a/b/**"),),
        )

    def test_unsupported_scope_syntax_fails_closed(self):
        for value in (
            "/etc/passwd",
            "./src/x.py",
            "../secret",
            "src/../secret",
            "C:/tmp/x",
            "src/*.py",
            "src/**/x.py",
            "src/[ab].py",
            "src/?",
        ):
            with self.subTest(value=value), self.assertRaises(ValueError):
                normalize_mutation_scope(value)


class ConcurrencyClaimPolicyTest(unittest.TestCase):
    def test_disjoint_real_worker_scopes_can_coexist(self):
        first = worker("hydrology", "src/hydrology/**")
        second = worker("canopy", "src/canopy/**")
        one = acquire_concurrency_claim(ConcurrencyClaimLedger(), first)
        self.assertEqual(one.decision.disposition, ConcurrencyClaimDisposition.ADMIT)
        two = acquire_concurrency_claim(one.ledger, second)
        self.assertEqual(two.decision.disposition, ConcurrencyClaimDisposition.ADMIT)
        self.assertEqual(len(two.ledger.active), 2)

    def test_independent_acquisition_order_is_semantically_commutative(self):
        first = worker("hydrology", "src/hydrology/**")
        second = worker("canopy", "src/canopy/**")
        left = acquire_concurrency_claim(
            acquire_concurrency_claim(ConcurrencyClaimLedger(), first).ledger,
            second,
        ).ledger
        right = acquire_concurrency_claim(
            acquire_concurrency_claim(ConcurrencyClaimLedger(), second).ledger,
            first,
        ).ledger
        self.assertEqual(left, right)
        self.assertEqual(left.digest, right.digest)

    def test_overlap_blocks_with_exact_conflict_identity_and_scopes(self):
        existing = worker("hydrology", "src/hydrology/**")
        proposed = worker("channel", "src/hydrology/channel.py")
        ledger = acquire_concurrency_claim(ConcurrencyClaimLedger(), existing).ledger
        result = acquire_concurrency_claim(ledger, proposed)
        self.assertEqual(result.decision.disposition, ConcurrencyClaimDisposition.CONFLICT)
        self.assertEqual(result.ledger, ledger)
        self.assertEqual(len(result.decision.conflicts), 1)
        conflict = result.decision.conflicts[0]
        self.assertEqual(conflict.attempt_id, existing.attempt_id)
        self.assertEqual(
            conflict.overlapping_scopes,
            (("src/hydrology/**", "src/hydrology/channel.py"),),
        )

    def test_same_task_or_authority_serializes_even_when_paths_are_disjoint(self):
        original = worker("a", "docs/a/**", task_id="shared-task", authority_key="authority:a")
        same_task = worker("b", "docs/b/**", task_id="shared-task", authority_key="authority:b")
        same_authority = worker("c", "docs/c/**", task_id="other-task", authority_key="authority:a")
        ledger = acquire_concurrency_claim(ConcurrencyClaimLedger(), original).ledger
        for proposed in (same_task, same_authority):
            with self.subTest(proposed=proposed.task_id):
                decision = classify_concurrency_claim(ledger, proposed)
                self.assertEqual(decision.disposition, ConcurrencyClaimDisposition.CONFLICT)
                self.assertEqual(len(decision.conflicts), 1)
                self.assertEqual(decision.conflicts[0].attempt_id, original.attempt_id)

    def test_exact_replay_is_idempotent_but_same_attempt_drift_conflicts(self):
        original = worker("a", "docs/a/**")
        ledger = acquire_concurrency_claim(ConcurrencyClaimLedger(), original).ledger
        replay = acquire_concurrency_claim(ledger, original)
        self.assertEqual(replay.decision.disposition, ConcurrencyClaimDisposition.ALREADY_ACTIVE)
        self.assertEqual(replay.ledger, ledger)

        drifted = worker(
            "drifted",
            "docs/elsewhere/**",
            task_id=original.task_id,
            authority_key=original.authority_key,
            attempt_id=original.attempt_id,
        )
        conflict = acquire_concurrency_claim(ledger, drifted)
        self.assertEqual(conflict.decision.disposition, ConcurrencyClaimDisposition.CONFLICT)
        self.assertEqual(conflict.ledger, ledger)

    def test_unsupported_frozen_scope_is_blocked_not_normalized_into_authority(self):
        malformed = worker("bad", "src/*.py")
        decision = classify_concurrency_claim(ConcurrencyClaimLedger(), malformed)
        self.assertEqual(
            decision.disposition,
            ConcurrencyClaimDisposition.BLOCKED_UNSUPPORTED_SCOPE,
        )
        self.assertIsNone(decision.proposed_claim)

    def test_protected_paths_do_not_become_mutation_locks(self):
        first = worker(
            "a",
            "docs/a/**",
            protected_paths=("scripts/orchestrator/**",),
        )
        second = worker(
            "b",
            "docs/b/**",
            protected_paths=("scripts/orchestrator/**",),
        )
        ledger = acquire_concurrency_claim(ConcurrencyClaimLedger(), first).ledger
        result = acquire_concurrency_claim(ledger, second)
        self.assertEqual(result.decision.disposition, ConcurrencyClaimDisposition.ADMIT)

    def test_retirement_tombstone_blocks_stale_reacquire_and_is_idempotent(self):
        original = worker("a", "docs/a/**")
        active = acquire_concurrency_claim(ConcurrencyClaimLedger(), original).ledger
        retired = retire_concurrency_claim(active, original.attempt_id)
        self.assertEqual(retired.disposition, ConcurrencyRetireDisposition.RETIRED)
        self.assertEqual(len(retired.ledger.active), 0)
        self.assertEqual(len(retired.ledger.retired), 1)

        replay = retire_concurrency_claim(retired.ledger, original.attempt_id)
        self.assertEqual(replay.disposition, ConcurrencyRetireDisposition.ALREADY_RETIRED)
        self.assertEqual(replay.ledger, retired.ledger)

        stale = acquire_concurrency_claim(retired.ledger, original)
        self.assertEqual(stale.decision.disposition, ConcurrencyClaimDisposition.RETIRED_REPLAY)
        self.assertEqual(stale.ledger, retired.ledger)

        rebound = worker(
            "rebound",
            "docs/b/**",
            task_id=original.task_id,
            authority_key=original.authority_key,
            attempt_id=original.attempt_id,
        )
        conflict = acquire_concurrency_claim(retired.ledger, rebound)
        self.assertEqual(conflict.decision.disposition, ConcurrencyClaimDisposition.CONFLICT)


    def test_retired_task_may_start_new_attempt_without_resurrecting_old_attempt(self):
        original = worker("a", "docs/a/**")
        ledger = acquire_concurrency_claim(ConcurrencyClaimLedger(), original).ledger
        retired = retire_concurrency_claim(ledger, original.attempt_id).ledger

        replacement = worker(
            "replacement",
            "docs/a/**",
            task_id=original.task_id,
            authority_key=original.authority_key,
        )
        result = acquire_concurrency_claim(retired, replacement)
        self.assertEqual(result.decision.disposition, ConcurrencyClaimDisposition.ADMIT)
        self.assertEqual(
            tuple(value.attempt_id for value in result.ledger.active),
            (replacement.attempt_id,),
        )
        self.assertIsNotNone(result.ledger.retired_for_attempt(original.attempt_id))

    def test_ledger_rejects_overlapping_active_claims_even_if_bypassing_acquire(self):
        first = ConcurrencyClaimRecord.from_worker(worker("a", "docs/a/**"))
        second = ConcurrencyClaimRecord.from_worker(worker("b", "docs/a/file.md"))
        with self.assertRaisesRegex(ValueError, "overlapping"):
            ConcurrencyClaimLedger(active=(first, second))

    def test_retiring_one_attempt_does_not_touch_independent_claim(self):
        first = worker("a", "docs/a/**")
        second = worker("b", "docs/b/**")
        ledger = acquire_concurrency_claim(ConcurrencyClaimLedger(), first).ledger
        ledger = acquire_concurrency_claim(ledger, second).ledger
        retired = retire_concurrency_claim(ledger, first.attempt_id)
        self.assertEqual(
            tuple(value.attempt_id for value in retired.ledger.active),
            (second.attempt_id,),
        )


class ConcurrencyClaimPersistenceTest(unittest.TestCase):
    def test_store_restart_round_trip_and_mirrored_state_are_exact(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            store = ConcurrencyClaimStore.for_root(root)
            ledger = acquire_concurrency_claim(
                ConcurrencyClaimLedger(), worker("a", "docs/a/**")
            ).ledger
            store.save(ledger)
            reloaded = ConcurrencyClaimStore.for_root(root).load()
            self.assertEqual(reloaded, ledger)
            self.assertEqual(store.adapter.path.read_bytes(), store.adapter.backup_path.read_bytes())


    def test_persisted_acquire_and_retire_survive_restart(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            store = ConcurrencyClaimStore.for_root(root)
            spec = worker("persisted", "docs/persisted/**")

            acquired = store.acquire(spec)
            self.assertEqual(
                acquired.decision.disposition,
                ConcurrencyClaimDisposition.ADMIT,
            )
            restarted = ConcurrencyClaimStore.for_root(root)
            self.assertEqual(restarted.load(), acquired.ledger)

            retired = restarted.retire(spec.attempt_id)
            self.assertEqual(
                retired.disposition,
                ConcurrencyRetireDisposition.RETIRED,
            )
            restarted_again = ConcurrencyClaimStore.for_root(root)
            self.assertEqual(restarted_again.load(), retired.ledger)

            stale = restarted_again.acquire(spec)
            self.assertEqual(
                stale.decision.disposition,
                ConcurrencyClaimDisposition.RETIRED_REPLAY,
            )
            self.assertEqual(stale.ledger, retired.ledger)

    def test_corrupt_primary_recovers_from_backup_but_both_corrupt_fail_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            store = ConcurrencyClaimStore.for_root(root)
            ledger = acquire_concurrency_claim(
                ConcurrencyClaimLedger(), worker("a", "docs/a/**")
            ).ledger
            store.save(ledger)
            store.adapter.path.write_text("not-json", encoding="utf-8")
            self.assertEqual(store.load(), ledger)
            store.adapter.path.write_text("not-json", encoding="utf-8")
            store.adapter.backup_path.write_text("also-not-json", encoding="utf-8")
            with self.assertRaises(StateStoreError):
                store.load()

    def test_tampered_claim_identity_fails_closed(self):
        raw = ConcurrencyClaimRecord.from_worker(worker("a", "docs/a/**")).as_dict()
        raw["allowed_paths"] = ["docs/b/**"]
        with self.assertRaisesRegex(ValueError, "identity mismatch"):
            ConcurrencyClaimRecord.from_mapping(raw)


if __name__ == "__main__":
    unittest.main()
