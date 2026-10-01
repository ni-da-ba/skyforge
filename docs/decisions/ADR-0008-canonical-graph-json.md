# ADR-0008: Canonical graph JSON v1

**Status:** Accepted  
**Date:** 2026-08-03  
**Ticket:** SF-IMP-0004

## Context

Skyforge requires one human-readable graph representation whose exact UTF-8 bytes are stable for
structurally identical graphs. The representation must preserve raw binary64 values, reject schema
drift explicitly, and remain independent of graph declaration order.

## Decision

Canonical graph JSON schema version 1 has these rules:

- The root members are emitted in the fixed order `schemaVersion`, `output`, `nodes`.
- Nodes are sorted by `NodeId.value()` using Java `String.compareTo`; node declaration order is not
  semantic.
- Each node's members use a fixed, kind-specific order. Arithmetic input order remains semantic and
  is preserved.
- Graph value types, node kinds, axes, and operators use explicit lowercase external identifiers.
- Finite binary64 parameters are JSON strings in the exact form returned by `Double.toHexString`.
  This preserves every accepted finite value, including the sign of zero.
- Strings use canonical minimal JSON escaping and must contain valid Unicode scalar sequences.
- The canonical document has no insignificant whitespace, byte-order mark, or trailing newline and
  is encoded as UTF-8.
- The reader may accept insignificant JSON whitespace and member ordering, but rejects duplicate or
  unknown members, missing members, unknown identifiers, noncanonical binary64 strings, malformed
  UTF-8, and unsupported schema versions. The reconstructed graph must also pass kernel validation.

The kernel uses a small schema-specific codec and no general serialization dependency. This keeps
the canonical rules explicit, auditable, and within the kernel's Java-standard-library boundary.

## Consequences

- Structurally identical graphs serialize to identical bytes even if nodes were declared in a
  different order.
- Reading and rewriting any accepted document produces canonical bytes.
- Extending node schemas or changing canonicalization requires a new schema version and decision.
- This decision does not promise identical evaluation or encoding across future Skyforge versions;
  such compatibility remains explicitly deferred by the v0.1 determinism contract.
