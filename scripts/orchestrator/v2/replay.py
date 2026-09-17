"""Stable replay records for old-controller versus v2 parity work."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from .identity import canonical_digest


@dataclass(frozen=True)
class ReplayRecord:
    """Digest-only record of one deterministic controller transition."""

    record_id: str
    state_before_digest: str
    event_digest: str
    expected_action_digest: str
    state_after_digest: str

    @classmethod
    def build(
        cls,
        *,
        state_before: Any,
        event: Any,
        expected_actions: Any,
        state_after: Any,
    ) -> "ReplayRecord":
        state_before_digest = canonical_digest(state_before)
        event_digest = canonical_digest(event)
        expected_action_digest = canonical_digest(expected_actions)
        state_after_digest = canonical_digest(state_after)
        record_id = canonical_digest(
            {
                "event": event_digest,
                "expected_actions": expected_action_digest,
                "state_after": state_after_digest,
                "state_before": state_before_digest,
            }
        )
        return cls(
            record_id=record_id,
            state_before_digest=state_before_digest,
            event_digest=event_digest,
            expected_action_digest=expected_action_digest,
            state_after_digest=state_after_digest,
        )
