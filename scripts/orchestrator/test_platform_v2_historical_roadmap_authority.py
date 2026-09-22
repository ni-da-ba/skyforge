from __future__ import annotations

import json
from pathlib import Path
import shutil
import tempfile
import unittest

from test_platform_v2_scope_promotion import REPO_ROOT
from v2.historical_roadmap_authority import (
    _POST_REFRESH_FINGERPRINT,
    _PRE_REFRESH_FINGERPRINT,
    migrate_canonical_roadmap_authority,
)
from v2.objective_intake import load_authoritative_roadmap_state, load_manifest
from v2.roadmap_service import RoadmapAuthorityStore


def prepare_root(root: Path, *, fingerprint: str = _PRE_REFRESH_FINGERPRINT, active=None) -> None:
    manifest_dst = root / "docs/agent-state/ORCHESTRATOR_ROADMAP.json"
    manifest_dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(REPO_ROOT / "docs/agent-state/ORCHESTRATOR_ROADMAP.json", manifest_dst)

    manifest = load_manifest(root)
    if manifest.fingerprint != _POST_REFRESH_FINGERPRINT:
        raise AssertionError("test fixture expected current accepted DR-70 manifest")

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
            "manifest_fingerprint": fingerprint,
            "completed_runs": {
                "dr-00-canonical-specimen-lock": 1,
                "dr-10-starting-cluster-closure": 1,
                "dr-20-visible-hydrology": 1,
                "dr-30-structure-reintegration": 1,
                "dr-40-production-ecology": 1,
                "dr-50-integrated-dressed-region": 1,
                "dr-60-exploration-review-packet": 1,
                "dr-65-canonical-hydrology-authorship": 1,
                "dr-70-human-review-repair": 2,
            },
            "blocked_nodes": {
                "dr-human-exploration-review": {
                    "reason": "historical DR-60 changes required"
                },
                "dr-human-exploration-rereview": {
                    "reason": "historical DR-70 changes required"
                },
            },
            "active": active,
            "claims_day": "2026-09-22",
            "claims_today": 0,
        },
    }
    state_dir = root / ".skyforge-orchestrator"
    state_dir.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(state, sort_keys=True, indent=2) + "\n"
    (state_dir / "state.json").write_text(payload, encoding="utf-8")
    (state_dir / "state.json.bak").write_text(payload, encoding="utf-8")


class HistoricalRoadmapAuthorityMigrationTest(unittest.TestCase):
    def test_exact_predecessor_materializes_current_v2_ledger_once(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            store = RoadmapAuthorityStore.for_root(root)

            self.assertEqual(
                migrate_canonical_roadmap_authority(
                    root=root,
                    repo="ni-da-ba/skyforge",
                    store=store,
                ),
                1,
            )
            self.assertEqual(
                migrate_canonical_roadmap_authority(
                    root=root,
                    repo="ni-da-ba/skyforge",
                    store=store,
                ),
                0,
            )

            manifest = load_manifest(root)
            ledger = store.load()
            self.assertEqual(ledger.manifest_fingerprint, manifest.fingerprint)
            self.assertEqual(dict(ledger.completed_runs)["dr-70-human-review-repair"], 2)
            self.assertEqual(
                {record.node_id for record in ledger.blocked_nodes},
                {
                    "dr-human-exploration-review",
                    "dr-human-exploration-rereview",
                },
            )
            self.assertIsNone(ledger.active)

            state = load_authoritative_roadmap_state(root, manifest)
            self.assertEqual(state.manifest_fingerprint, manifest.fingerprint)

    def test_unexpected_predecessor_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root, fingerprint="f" * 64)
            with self.assertRaisesRegex(RuntimeError, "accepted predecessor"):
                migrate_canonical_roadmap_authority(
                    root=root,
                    repo="ni-da-ba/skyforge",
                )
            self.assertFalse(
                RoadmapAuthorityStore.for_root(root).adapter.path.exists()
            )

    def test_active_legacy_authority_fails_closed(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(
                root,
                active={
                    "node_id": "dr-70-human-review-repair",
                    "issue_number": 754,
                },
            )
            with self.assertRaisesRegex(RuntimeError, "active legacy roadmap authority"):
                migrate_canonical_roadmap_authority(
                    root=root,
                    repo="ni-da-ba/skyforge",
                )
            self.assertFalse(
                RoadmapAuthorityStore.for_root(root).adapter.path.exists()
            )

    def test_other_repository_is_untouched(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            prepare_root(root)
            self.assertEqual(
                migrate_canonical_roadmap_authority(
                    root=root,
                    repo="example/other",
                ),
                0,
            )
            self.assertFalse(
                RoadmapAuthorityStore.for_root(root).adapter.path.exists()
            )


if __name__ == "__main__":
    unittest.main()
