"""One-time canonical migration from the pre-DR70-refresh legacy roadmap projection."""

from __future__ import annotations

from pathlib import Path

from .cutover import LegacyOperationalProjection
from .objective_intake import load_manifest
from .roadmap_service import (
    RoadmapAuthorityLedger,
    RoadmapAuthorityStore,
    RoadmapBlockRecord,
)
from .state_store import JsonStateStoreAdapter


_CANONICAL_REPO = "ni-da-ba/skyforge"
_PRE_REFRESH_FINGERPRINT = (
    "a984844f566c3083e1d578e2ab6c20392b5b71603555c90e452bd45f75c59070"
)
_POST_REFRESH_FINGERPRINT = (
    "0501fa72f6773adf0d980c30d865215fbb9bd0fb3e65082c3a2dec88ff158b22"
)


def migrate_canonical_roadmap_authority(
    *,
    root: Path,
    repo: str,
    store: RoadmapAuthorityStore | None = None,
) -> int:
    """Materialize canonical V2 roadmap authority across the accepted DR-70 refresh."""

    root = Path(root).resolve()
    if str(repo).strip() != _CANONICAL_REPO:
        return 0

    target_store = store or RoadmapAuthorityStore.for_root(root)
    if target_store.adapter.path.is_file():
        return 0

    manifest_path = root / "docs/agent-state/ORCHESTRATOR_ROADMAP.json"
    if not manifest_path.is_file():
        # Minimal unit/integration roots legitimately omit product roadmap authority.
        return 0

    manifest = load_manifest(root)
    if manifest.fingerprint != _POST_REFRESH_FINGERPRINT:
        return 0

    snapshot = JsonStateStoreAdapter.for_legacy_root(root).load()
    projection = LegacyOperationalProjection.from_legacy_mapping(snapshot.as_dict())
    legacy = projection.roadmap

    if legacy.roadmap_id != manifest.roadmap_id:
        raise RuntimeError("canonical roadmap migration roadmap_id drifted")
    if legacy.manifest_fingerprint != _PRE_REFRESH_FINGERPRINT:
        raise RuntimeError(
            "canonical roadmap migration legacy fingerprint is not the accepted predecessor"
        )
    if legacy.active is not None:
        raise RuntimeError(
            "canonical roadmap migration refuses active legacy roadmap authority"
        )

    by_id = {node.node_id: node for node in manifest.nodes}
    completed: list[tuple[str, int]] = []
    for node_id, count in legacy.completed_runs.items():
        node = by_id.get(node_id)
        if node is None:
            raise RuntimeError(
                f"canonical roadmap migration completed node disappeared: {node_id}"
            )
        if count > node.max_runs:
            raise RuntimeError(
                f"canonical roadmap migration completed count exceeds max_runs: {node_id}"
            )
        completed.append((node_id, count))

    blocked: list[RoadmapBlockRecord] = []
    for node_id, raw in legacy.blocked_nodes.items():
        if node_id not in by_id:
            raise RuntimeError(
                f"canonical roadmap migration blocked node disappeared: {node_id}"
            )
        if not isinstance(raw, dict):
            raise RuntimeError(
                f"canonical roadmap migration blocked node is malformed: {node_id}"
            )
        reason = str(raw.get("reason") or "").strip()
        if not reason:
            raise RuntimeError(
                f"canonical roadmap migration blocked node lacks reason: {node_id}"
            )
        blocked.append(RoadmapBlockRecord(node_id=node_id, reason=reason))

    ledger = RoadmapAuthorityLedger(
        roadmap_id=manifest.roadmap_id,
        manifest_fingerprint=manifest.fingerprint,
        completed_runs=tuple(completed),
        blocked_nodes=tuple(blocked),
        active=None,
        claims_day=legacy.claims_day,
        claims_today=legacy.claims_today,
        human_gate_records=tuple(projection.human_gate_records),
    )
    target_store.save(ledger)
    return 1
