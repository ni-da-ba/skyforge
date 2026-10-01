# Skyforge Platform v2 control plane

This directory contains the **inert** Platform v2 implementation tracked by #767.

## Authority boundary

The current hosted controller does not import this package. Platform v2 currently has no GitHub,
Codex, deployment, state-file, merge, or repository mutation authority.

Release 1 established independently tested safety primitives:

- frozen task/spec/attempt identity and exact-SHA acceptance identity;
- durable remote-effect identities;
- PR lifecycle and mechanical transition rules;
- single-writer fencing and controller ownership epochs;
- an evidence-backed legacy replay/parity corpus.

Release 2 is now building the explicit pure controller core behind that boundary. The first slice
projects the current JSON state into immutable typed state, preserves the current primary/backup JSON
store contract behind an adapter, and evaluates managed-PR observations with a side-effect-free
reducer.

The intended progression remains:

```text
legacy/current durable state + durable repository observation
    -> typed v2 state/event
    -> deterministic pure transition plan
    -> later durable effect/outbox layer
    -> later read-only shadow
    -> only after preservation and parity gates: canary mutation authority
```

No v2 module may become production-reachable merely by being merged. Live shadow/canary wiring is a
separate migration release with its own preservation, replay, and single-writer gates. DR-70 and
other product/human-review authority remain independent of this control-plane migration.
