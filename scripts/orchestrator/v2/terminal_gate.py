"""Pure terminal-human-gate quiescence policy for Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Iterable

from .events import DurableEvent
from .identity import canonical_digest
from .roadmap_shadow import (
    RoadmapNodeKind,
    ShadowRoadmapManifest,
    ShadowRoadmapState,
    select_shadow_next_node,
)


class TerminalGateDisposition(str, Enum):
    DELEGATE = "DELEGATE"
    QUIESCE_ORDINARY = "QUIESCE_ORDINARY"


@dataclass(frozen=True)
class TerminalGateDecision:
    disposition: TerminalGateDisposition
    reason: str
    gate_ids: tuple[str, ...]
    ordinary_event_ids: tuple[str, ...]
    protected_event_ids: tuple[str, ...]
    roadmap_state_digest: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "gate_ids": list(self.gate_ids),
                "ordinary_event_ids": list(self.ordinary_event_ids),
                "protected_event_ids": list(self.protected_event_ids),
                "roadmap_state_digest": self.roadmap_state_digest,
            }
        )


def classify_terminal_gate_quiescence(
    *,
    manifest: ShadowRoadmapManifest,
    state: ShadowRoadmapState,
    pending_events: Iterable[DurableEvent],
    pending_decision: bool,
    pending_worker: bool,
    blocked_kind: bool,
) -> TerminalGateDecision:
    for value, label in (
        (pending_decision, "pending_decision"),
        (pending_worker, "pending_worker"),
        (blocked_kind, "blocked_kind"),
    ):
        if not isinstance(value, bool):
            raise ValueError(f"{label} must be boolean")

    events = tuple(pending_events)
    by_id = {node.node_id: node for node in manifest.nodes}
    blocked = set(state.blocked_nodes)
    gate_ids = tuple(
        sorted(
            node_id
            for node_id in blocked
            if by_id[node_id].kind is RoadmapNodeKind.GATE
        )
    )
    task_ids = tuple(
        sorted(
            node_id
            for node_id in blocked
            if by_id[node_id].kind is RoadmapNodeKind.TASK
        )
    )

    def result(
        disposition: TerminalGateDisposition,
        reason: str,
        *,
        ordinary: tuple[str, ...] = (),
        protected: tuple[str, ...] = (),
    ) -> TerminalGateDecision:
        return TerminalGateDecision(
            disposition=disposition,
            reason=reason,
            gate_ids=gate_ids,
            ordinary_event_ids=ordinary,
            protected_event_ids=protected,
            roadmap_state_digest=state.digest,
        )

    if state.active is not None:
        return result(
            TerminalGateDisposition.DELEGATE,
            "roadmap node is active",
        )
    if not gate_ids:
        return result(
            TerminalGateDisposition.DELEGATE,
            "no blocked human gate",
        )
    if task_ids:
        return result(
            TerminalGateDisposition.DELEGATE,
            "blocked roadmap task requires ordinary roadmap reconciliation",
        )

    next_node = select_shadow_next_node(
        manifest,
        completed_runs=dict(state.completed_runs),
        blocked_nodes=blocked,
    )
    if next_node is not None:
        return result(
            TerminalGateDisposition.DELEGATE,
            f"eligible roadmap node remains: {next_node.node_id}",
        )

    if pending_decision or pending_worker or blocked_kind:
        return result(
            TerminalGateDisposition.DELEGATE,
            "controller work is already in flight or blocked",
        )

    protected = tuple(
        event.event_id
        for event in events
        if event.event == "roadmap" or event.protected_authority
    )
    if protected:
        return result(
            TerminalGateDisposition.DELEGATE,
            "protected authority pending; terminal gate may not retire it",
            protected=protected,
        )

    ordinary = tuple(event.event_id for event in events)
    return result(
        TerminalGateDisposition.QUIESCE_ORDINARY,
        "terminal human gate latched; ordinary lifecycle noise is model-free",
        ordinary=ordinary,
    )
