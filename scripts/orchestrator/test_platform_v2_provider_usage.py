from __future__ import annotations

from pathlib import Path
import tempfile
import unittest

from v2.provider_usage import (
    ProviderSpendKind,
    ProviderUsageLedger,
    ProviderUsageStore,
)


class ProviderUsageTest(unittest.TestCase):
    def test_reservation_is_idempotent_across_restart(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td)
            store=ProviderUsageStore.for_root(root)
            first,created=store.reserve(
                day="2026-09-18",
                kind=ProviderSpendKind.CLASSIFIER,
                spend_id="request:"+"a"*64,
                daily_limit=2,
            )
            self.assertTrue(created)
            self.assertEqual(first.count(day="2026-09-18",kind=ProviderSpendKind.CLASSIFIER),1)

            restarted=ProviderUsageStore.for_root(root)
            second,created_again=restarted.reserve(
                day="2026-09-18",
                kind=ProviderSpendKind.CLASSIFIER,
                spend_id="request:"+"a"*64,
                daily_limit=2,
            )
            self.assertFalse(created_again)
            self.assertEqual(second,first)
            self.assertEqual(
                store.adapter.path.read_bytes(),
                store.adapter.backup_path.read_bytes(),
            )

    def test_daily_limit_blocks_new_spend_but_not_existing_identity(self):
        ledger=ProviderUsageLedger()
        ledger,created=ledger.reserve(
            day="2026-09-18",
            kind=ProviderSpendKind.WORKER,
            spend_id="attempt:"+"b"*64,
            daily_limit=1,
        )
        self.assertTrue(created)
        same,created=ledger.reserve(
            day="2026-09-18",
            kind=ProviderSpendKind.WORKER,
            spend_id="attempt:"+"b"*64,
            daily_limit=1,
        )
        self.assertFalse(created)
        self.assertEqual(same,ledger)
        blocked,created=ledger.reserve(
            day="2026-09-18",
            kind=ProviderSpendKind.WORKER,
            spend_id="attempt:"+"c"*64,
            daily_limit=1,
        )
        self.assertFalse(created)
        self.assertEqual(blocked,ledger)

    def test_new_utc_day_has_fresh_budget_without_erasing_history(self):
        ledger=ProviderUsageLedger()
        ledger,_=ledger.reserve(
            day="2026-09-18",
            kind=ProviderSpendKind.CLASSIFIER,
            spend_id="request:a",
            daily_limit=1,
        )
        self.assertEqual(
            ledger.budget(day="2026-09-18",kind=ProviderSpendKind.CLASSIFIER,daily_limit=1).calls_remaining,
            0,
        )
        self.assertEqual(
            ledger.budget(day="2026-09-19",kind=ProviderSpendKind.CLASSIFIER,daily_limit=1).calls_used,
            0,
        )
        tomorrow,created=ledger.reserve(
            day="2026-09-19",
            kind=ProviderSpendKind.CLASSIFIER,
            spend_id="request:b",
            daily_limit=1,
        )
        self.assertTrue(created)
        self.assertEqual(len(tomorrow.reservations),2)

    def test_classifier_and_worker_budgets_are_independent(self):
        ledger=ProviderUsageLedger()
        ledger,_=ledger.reserve(
            day="2026-09-18",kind=ProviderSpendKind.CLASSIFIER,
            spend_id="same-semantic-id",daily_limit=1,
        )
        ledger,_=ledger.reserve(
            day="2026-09-18",kind=ProviderSpendKind.WORKER,
            spend_id="same-semantic-id",daily_limit=1,
        )
        self.assertEqual(len(ledger.reservations),2)

    def test_tampered_reservation_id_fails_closed(self):
        ledger=ProviderUsageLedger()
        ledger,_=ledger.reserve(
            day="2026-09-18",kind=ProviderSpendKind.CLASSIFIER,
            spend_id="request:a",daily_limit=1,
        )
        raw=ledger.as_dict()
        raw["reservations"][0]["reservation_id"]="0"*64
        with self.assertRaises(ValueError):
            ProviderUsageLedger.from_mapping(raw)


if __name__ == "__main__":
    unittest.main()
