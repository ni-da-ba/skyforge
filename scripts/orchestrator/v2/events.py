"""Durable event identity for the inert Platform v2 controller core."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import re
from typing import Any, Mapping

EVENT_KEY_PREFIX = "sha256:"
PROTECTED_AUTHORITY_SIGNAL_KINDS = frozenset(
    {"task", "restart_recommended", "human_gate", "loop_risk"}
)


def _optional_text(value: Any, label: str) -> str | None:
    if value is None:
        return None
    if not isinstance(value, str):
        raise ValueError(f"{label} must be a string or null")
    return value


@dataclass(frozen=True)
class DurableEvent:
    """Typed equivalent of the current durable EventDecision state record."""

    actionable: bool
    reason: str
    event: str
    action: str | None = None
    head_sha: str | None = None
    pr_number: int | str | None = None
    observed_at: str | None = None
    source_id: str | None = None
    signal_kind: str | None = None
    signal_text: str | None = None

    @classmethod
    def from_legacy_mapping(cls, value: Any) -> "DurableEvent":
        if not isinstance(value, Mapping):
            raise ValueError("durable event must be an object")

        actionable = value.get("actionable", False)
        if not isinstance(actionable, bool):
            raise ValueError("durable event actionable must be a boolean")

        pr_number = value.get("pr_number")
        if isinstance(pr_number, bool) or (
            pr_number is not None and not isinstance(pr_number, (int, str))
        ):
            raise ValueError("durable event pr_number must be an integer, string, or null")

        reason = str(value.get("reason") or "")
        event = str(value.get("event") or "")

        return cls(
            actionable=actionable,
            reason=reason,
            event=event,
            action=_optional_text(value.get("action"), "durable event action"),
            head_sha=_optional_text(value.get("head_sha"), "durable event head_sha"),
            pr_number=pr_number,
            observed_at=_optional_text(value.get("observed_at"), "durable event observed_at"),
            source_id=_optional_text(value.get("source_id"), "durable event source_id"),
            signal_kind=_optional_text(value.get("signal_kind"), "durable event signal_kind"),
            signal_text=_optional_text(value.get("signal_text"), "durable event signal_text"),
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "actionable": self.actionable,
            "reason": self.reason,
            "event": self.event,
            "action": self.action,
            "head_sha": self.head_sha,
            "pr_number": self.pr_number,
            "observed_at": self.observed_at,
            "source_id": self.source_id,
            "signal_kind": self.signal_kind,
            "signal_text": self.signal_text,
        }

    def identity_payload(self) -> dict[str, Any]:
        payload = self.as_dict()
        payload.pop("observed_at", None)
        return payload

    @property
    def event_id(self) -> str:
        # Compatibility boundary: the production controller's durable replay key is
        # SHA-256 over json.dumps(..., sort_keys=True, separators=(",", ":")) with
        # Python's default ensure_ascii=True.  Do not substitute the general v2
        # canonical_digest here; Unicode escaping is part of the already-persisted
        # legacy identity contract.
        encoded = json.dumps(
            self.identity_payload(),
            sort_keys=True,
            separators=(",", ":"),
        ).encode("utf-8")
        return EVENT_KEY_PREFIX + hashlib.sha256(encoded).hexdigest()

    @property
    def protected_authority(self) -> bool:
        return self.signal_kind in PROTECTED_AUTHORITY_SIGNAL_KINDS

    @property
    def task_issue_number(self) -> int | None:
        if self.signal_kind != "task" or self.pr_number is None:
            return None
        try:
            return int(self.pr_number)
        except (TypeError, ValueError):
            return None


_EVENT_ID_RE = re.compile(r"^sha256:[0-9a-f]{64}$")


def normalize_legacy_event_key(value: Any) -> str:
    """Upgrade a pre-hash persisted event key using current schema semantics.

    Unknown historical values are preserved rather than discarded, matching the
    current controller's fail-closed replay behavior.
    """

    text = str(value or "")
    if _EVENT_ID_RE.fullmatch(text):
        return text
    try:
        payload = json.loads(text)
    except (TypeError, ValueError, json.JSONDecodeError):
        return text
    if not isinstance(payload, dict):
        return text
    return DurableEvent.from_legacy_mapping(payload).event_id
