from __future__ import annotations

import json
from pathlib import Path
import tempfile
import unittest

from v2.historical_human_reviews import migrate_canonical_historical_human_reviews
from v2.human_review import HumanReviewStore, HumanReviewVerdict


SOURCE_SHA = "a318f1b1ffb22805087eac52b67035d041a03fb6"


def write_artifact(root: Path, *, source_sha: str = SOURCE_SHA) -> None:
    path = root / "docs/agent-state/REVIEW_ARTIFACTS.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(
            {
                "schema_version": 1,
                "artifacts": [
                    {
                        "artifact_id": "dr70:key-2885",
                        "kind": "INTERACTIVE_SPECIMEN",
                        "source_sha": source_sha,
                        "title": "DR-70 key-2885 hydrology review specimen",
                        "description": (
                            "Qualified interactive Minecraft specimen used for the "
                            "2026-09-20 DR-70 human hydrology review that returned "
                            "CHANGES_REQUIRED."
                        ),
                        "interactive": {
                            "specimen_kind": "minecraft-neoforge-review-world",
                            "parameters": {
                                "world_seed_hex": "0x534B59464F524745",
                                "province_key": 8,
                                "cluster_key": 81,
                                "island_key": 2885,
                                "suspension_y": 220.0,
                            },
                            "preparation_entry_points": [
                                ":skyforge-neoforge-1211:dr70HydrologyReviewAcceptance"
                            ],
                            "launch_entry_point": (
                                ":skyforge-neoforge-1211:runDr70HumanReviewClient"
                            ),
                            "review_actions": [
                                "/gamemode creative",
                                "/tp @s 0 320 0",
                            ],
                            "associated_artifact_ids": [],
                        },
                    }
                ],
            }
        ),
        encoding="utf-8",
    )


class HistoricalHumanReviewMigrationTest(unittest.TestCase):
    def test_key_2885_review_backfills_exactly_once_with_github_provenance(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_artifact(root)
            store = HumanReviewStore.for_root(root)

            self.assertEqual(
                migrate_canonical_historical_human_reviews(
                    root=root,
                    repo="ni-da-ba/skyforge",
                    store=store,
                ),
                1,
            )
            self.assertEqual(
                migrate_canonical_historical_human_reviews(
                    root=root,
                    repo="ni-da-ba/skyforge",
                    store=store,
                ),
                0,
            )

            ledger = store.load()
            self.assertEqual(len(ledger.records), 1)
            review = ledger.records[0]
            self.assertEqual(review.gate_id, "dr-human-exploration-rereview")
            self.assertEqual(review.artifact_id, "dr70:key-2885")
            self.assertEqual(review.source_sha, SOURCE_SHA)
            self.assertEqual(review.verdict, HumanReviewVerdict.CHANGES_REQUIRED)
            self.assertTrue(review.deferred_product_work)
            self.assertEqual(review.source.issue_number, 754)
            self.assertEqual(review.source.comment_id, 5752994015)
            self.assertEqual(review.source.actor, "ni-da-ba")
            self.assertEqual(review.source.created_at, "2026-09-20T22:02:53Z")
            self.assertEqual(review.source.updated_at, "2026-09-20T22:02:53Z")
            self.assertTrue(
                any("ocean biome" in finding for finding in review.findings)
            )
            self.assertTrue(
                any("visibly legible water channel" in finding for finding in review.positive_findings)
            )

    def test_key_2885_source_sha_drift_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_artifact(root, source_sha="b" * 40)
            with self.assertRaisesRegex(RuntimeError, "source SHA drifted"):
                migrate_canonical_historical_human_reviews(
                    root=root,
                    repo="ni-da-ba/skyforge",
                )

    def test_other_repository_is_not_mutated(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            write_artifact(root)
            self.assertEqual(
                migrate_canonical_historical_human_reviews(
                    root=root,
                    repo="example/other",
                ),
                0,
            )
            self.assertFalse(
                (root / ".skyforge-platform-v2/human-reviews.json").exists()
            )


if __name__ == "__main__":
    unittest.main()
