"""Canonical repository-relative path and mutation-scope semantics for Platform v2."""

from __future__ import annotations

import re
from typing import Any, Iterable

_DRIVE_RE = re.compile(r"^[A-Za-z]:/")
_UNSUPPORTED_GLOB_CHARS = frozenset("?[]{}")


def _required(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} must be non-empty text")
    return value.strip().replace("\\", "/")


def normalize_repository_path(value: Any, *, label: str = "path") -> str:
    """Normalize one exact repository-relative path; globs are forbidden."""
    text = _required(value, label)
    if text.startswith("/") or text.startswith("./") or _DRIVE_RE.match(text):
        raise ValueError(f"{label} must be a normalized repository-relative path")
    if any(part in {"", ".", ".."} for part in text.split("/")):
        raise ValueError(f"{label} must be a normalized repository-relative path")
    if "*" in text or any(ch in text for ch in _UNSUPPORTED_GLOB_CHARS):
        raise ValueError(f"{label} must be an exact repository-relative path")
    return text


def normalize_mutation_scope(value: Any, *, label: str = "path scope") -> str:
    """Normalize an exact path or the one accepted subtree form: path/**."""
    text = _required(value, label)
    subtree = text.endswith("/**")
    base = text[:-3] if subtree else text
    normalized = normalize_repository_path(base, label=label)
    if not subtree and "*" in text:
        raise ValueError(f"{label} supports only a trailing /** scope wildcard")
    if any(ch in text for ch in _UNSUPPORTED_GLOB_CHARS):
        raise ValueError(f"{label} contains unsupported wildcard syntax")
    if subtree and "*" in base:
        raise ValueError(f"{label} contains unsupported wildcard syntax")
    return normalized + "/**" if subtree else normalized


def normalize_mutation_scopes(
    values: Iterable[Any],
    *,
    label: str = "path scopes",
    require_nonempty: bool = True,
) -> tuple[str, ...]:
    normalized = tuple(
        sorted({normalize_mutation_scope(value, label=f"{label} item") for value in values})
    )
    if require_nonempty and not normalized:
        raise ValueError(f"{label} must not be empty")
    return normalized


def _subtree_prefix(scope: str) -> str | None:
    normalized = normalize_mutation_scope(scope)
    return normalized[:-3] if normalized.endswith("/**") else None


def scope_contains(candidate: str, authority: str) -> bool:
    """Return whether candidate mutation scope is wholly inside authority scope."""
    child = normalize_mutation_scope(candidate, label="candidate scope")
    parent = normalize_mutation_scope(authority, label="authority scope")
    if child == parent:
        return True
    parent_prefix = _subtree_prefix(parent)
    if parent_prefix is None:
        return False
    child_prefix = _subtree_prefix(child)
    if child_prefix is not None:
        return child_prefix.startswith(parent_prefix + "/")
    return child.startswith(parent_prefix + "/")


def path_is_allowed(path: str, scopes: Iterable[str]) -> bool:
    exact = normalize_repository_path(path, label="repository path")
    return any(scope_contains(exact, scope) for scope in scopes)


def scopes_overlap(left: str, right: str) -> bool:
    """Return whether two accepted mutation scopes can authorize a common path."""
    a = normalize_mutation_scope(left, label="left scope")
    b = normalize_mutation_scope(right, label="right scope")
    if a == b:
        return True
    a_prefix = _subtree_prefix(a)
    b_prefix = _subtree_prefix(b)
    if a_prefix is None and b_prefix is None:
        return False
    if a_prefix is not None and b_prefix is None:
        return b.startswith(a_prefix + "/")
    if a_prefix is None and b_prefix is not None:
        return a.startswith(b_prefix + "/")
    assert a_prefix is not None and b_prefix is not None
    return (
        a_prefix.startswith(b_prefix + "/")
        or b_prefix.startswith(a_prefix + "/")
    )


def overlapping_scope_pairs(
    left: Iterable[str],
    right: Iterable[str],
) -> tuple[tuple[str, str], ...]:
    pairs = {
        (
            normalize_mutation_scope(a, label="left scope"),
            normalize_mutation_scope(b, label="right scope"),
        )
        for a in left
        for b in right
        if scopes_overlap(a, b)
    }
    return tuple(sorted(pairs))
