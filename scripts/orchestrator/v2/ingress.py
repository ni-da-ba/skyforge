"""Hosted Platform-v2 ingress compatibility contracts.

This module deliberately imports only the legacy core classifier module, never any
runtime extension/monkey-patch module.  Classification parity can therefore be retained
while hosted execution is migrated behind explicit v2 boundaries.
"""

from __future__ import annotations

import hashlib
import hmac
from typing import Any, Iterable, Mapping

from .events import DurableEvent, PROTECTED_AUTHORITY_SIGNAL_KINDS


def verify_github_signature(
    secret: str,
    payload: bytes,
    signature: str | None,
) -> bool:
    if not isinstance(secret, str) or not secret:
        return False
    if not isinstance(payload, bytes):
        raise ValueError("payload must be bytes")
    if not isinstance(signature, str) or not signature.startswith("sha256="):
        return False
    expected = "sha256=" + hmac.new(
        secret.encode("utf-8"),
        payload,
        hashlib.sha256,
    ).hexdigest()
    return hmac.compare_digest(expected, signature)


def classify_legacy_compatible_event(
    event: str,
    payload: Mapping[str, Any],
    *,
    repo: str,
    trusted_actors: Iterable[str],
) -> DurableEvent:
    if not isinstance(payload, Mapping):
        raise ValueError("webhook payload must be an object")

    # Explicit compatibility adapter only.  Importing skyforge_orchestrator has no
    # install-time side effects; hosted patch modules are intentionally forbidden.
    import skyforge_orchestrator as legacy_core

    decision = legacy_core.classify_event(
        event,
        dict(payload),
        repo=repo,
        trusted_actors=tuple(trusted_actors),
    )
    return DurableEvent.from_legacy_mapping(decision.to_state())


def reclassify_legacy_compatible_audit_event(event: DurableEvent) -> DurableEvent:
    """Re-evaluate persisted protected Audit prose through the current parser.

    V2 persists event records across runtime upgrades. If a legacy-compatible parser bug
    overstated protected authority before the upgrade, the serialized signal_kind must not
    remain authoritative forever. Preserve the physical event and only rewrite stale
    restart/human-gate/loop-risk classifications; explicit task authority is untouched.
    """
    if (
        event.action != "audit_signal"
        or event.signal_kind not in PROTECTED_AUTHORITY_SIGNAL_KINDS - {"task"}
        or not event.signal_text
    ):
        return event

    import skyforge_orchestrator as legacy_core

    current_kind = legacy_core._audit_signal_kind(event.signal_text.lower()) or "audit"
    if current_kind == event.signal_kind:
        return event
    raw = event.as_dict()
    raw["signal_kind"] = current_kind
    return DurableEvent.from_legacy_mapping(raw)


def classify_legacy_compatible_control(
    event: str,
    payload: Mapping[str, Any],
    *,
    trusted_actors: Iterable[str],
) -> str | None:
    if not isinstance(payload, Mapping):
        raise ValueError("webhook payload must be an object")

    import skyforge_orchestrator as legacy_core

    return legacy_core.classify_control_command(
        event,
        dict(payload),
        trusted_actors=tuple(trusted_actors),
    )
