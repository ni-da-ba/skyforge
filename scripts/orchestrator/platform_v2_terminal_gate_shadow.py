"""Read-only live terminal-human-gate quiescence collector for Platform v2."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any, Mapping

import platform_v2_shadow_collector as base
from v2.events import DurableEvent
from v2.identity import canonical_digest
from v2.roadmap_shadow import ShadowRoadmapManifest, ShadowRoadmapState
from v2.terminal_gate import classify_terminal_gate_quiescence


MANIFEST_RELATIVE_PATH = Path("docs/agent-state/ORCHESTRATOR_ROADMAP.json")


def _read_manifest(code_root: Path) -> ShadowRoadmapManifest:
    path = code_root / MANIFEST_RELATIVE_PATH
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ValueError(f"accepted roadmap manifest is unreadable: {path}") from exc
    return ShadowRoadmapManifest.from_mapping(payload)


def collect_terminal_gate_shadow(
    *,
    state_root: Path,
    code_root: Path,
) -> dict[str, Any]:
    state = base.read_legacy_state(state_root)
    manifest = _read_manifest(code_root)
    roadmap = ShadowRoadmapState.from_legacy(state.get("roadmap"), manifest)

    pending_raw = state.get("pending_events") or []
    if not isinstance(pending_raw, list):
        raise ValueError("legacy pending_events must be a list")
    pending = tuple(DurableEvent.from_legacy_mapping(value) for value in pending_raw)

    decision = classify_terminal_gate_quiescence(
        manifest=manifest,
        state=roadmap,
        pending_events=pending,
        pending_decision=isinstance(state.get("pending_decision"), Mapping),
        pending_worker=isinstance(state.get("pending_worker"), Mapping),
        blocked_kind=bool(state.get("blocked_kind")),
    )
    body = {
        "schema_version": 1,
        "paused": bool(state.get("paused", False)),
        "pending_event_count": len(pending),
        "pending_event_ids": [event.event_id for event in pending],
        "decision": {
            "disposition": decision.disposition.value,
            "reason": decision.reason,
            "gate_ids": list(decision.gate_ids),
            "ordinary_event_ids": list(decision.ordinary_event_ids),
            "protected_event_ids": list(decision.protected_event_ids),
            "roadmap_state_digest": decision.roadmap_state_digest,
            "digest": decision.digest,
        },
    }
    return {**body, "digest": canonical_digest(body)}


def render(value: Mapping[str, Any]) -> str:
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Evaluate live terminal-human-gate ordinary-event quiescence read-only."
    )
    parser.add_argument("--state-root", required=True)
    parser.add_argument("--code-root", default=str(Path(__file__).resolve().parents[2]))
    args = parser.parse_args(argv)
    try:
        value = collect_terminal_gate_shadow(
            state_root=Path(args.state_root).resolve(),
            code_root=Path(args.code_root).resolve(),
        )
    except ValueError as exc:
        print(f"terminal gate shadow rejected: {exc}", file=sys.stderr)
        return 2
    print(render(value))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
