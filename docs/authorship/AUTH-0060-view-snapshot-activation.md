# AUTH-0060 — Backend-view snapshot identity and explicit activation

## Purpose

AUTH-0060 defines the backend-neutral activation boundary for one already-admitted AUTH-0059
publication view.

AUTH-0059 establishes a deterministic, proof-carrying publication set that is safe to query as a
coherent view. AUTH-0060 does not make that view more correct and does not perform backend work.
Instead it gives downstream systems an explicit answer to two state questions:

1. Which exact admitted view is currently selected for runtime binding?
2. How may that selection be replaced without stale or implicit activation?

The answer is an immutable snapshot capability plus immutable compare-and-swap activation state.

## Snapshot identity

`SkyIslandPublishedWorldSnapshotId` schema 1 contains:

    schemaVersion
    snapshotRevision
    viewIdentity[]

where `viewIdentity` is the exact canonical AUTH-0059 ordered list of
`SkyIslandCompiledWorldPublicationId` values.

### Snapshot revision

`snapshotRevision` is a positive explicit activation-version axis.

It is not:

- an AUTH-0058 publication revision;
- a graph digest;
- a world-content hash;
- a backend tick;
- a persistence sequence number.

Publication revisions version individual regional publications.

Snapshot revision versions activation generations of the whole admitted publication view.

The two axes remain intentionally separate.

### Exact view identity

Snapshot identity carries the full AUTH-0059 publication-set identity rather than only a revision.

Therefore two different admitted views with the same snapshot revision still have different snapshot
identities.

This prevents a bare activation revision from being misinterpreted as sufficient content identity.

### Canonical order validation

The snapshot ID independently validates that its publication identities are in strict unsigned-root
order.

Thus a forged ID cannot encode the same publication set in a different caller order.

Duplicate roots or noncanonical ordering are rejected structurally.

### Canonical token

The stable diagnostic/cache token is:

    sfviewsnap:v<schema>:<16-hex snapshot revision>:<count>:<publication token>...

The token retains the exact publication identities visibly.

It is intentionally not a digest.

## Snapshot capability

`SkyIslandPublishedWorldSnapshot` binds:

- one `SkyIslandPublishedWorldSnapshotId`;
- one exact `SkyIslandPublishedWorldView`.

Construction requires:

    snapshotId.viewIdentity == view.viewIdentity

A snapshot ID from another admitted view cannot be attached to the supplied view.

The snapshot exposes:

- publication count;
- volume count;
- proof-carrying region query delegation.

Queries are delegated to the exact AUTH-0059 view and therefore preserve:

- publication identity;
- world-volume identity;
- support certificate;
- deterministic order.

## Activation state

`SkyIslandPublishedWorldActivationState` is immutable.

It contains either:

    INACTIVE

or:

    ACTIVE(exact SkyIslandPublishedWorldSnapshot)

There is no global singleton and no in-place mutation.

Every activation operation returns a new state.

## Initial activation

Initial activation is explicit:

    inactive.activateInitial(view, snapshotRevision)

It is allowed only on an inactive state.

Attempting initial activation on an already active state is rejected.

The original inactive state remains inactive.

## Replacement

Replacement is explicit compare-and-swap:

    state.replace(
        expectedCurrentSnapshotId,
        replacementView,
        replacementSnapshotRevision)

The exact expected snapshot identity must still be active.

A stale expected identity is rejected even if:

- it references the same regional roots;
- it references an older version of the same publication set;
- the requested replacement revision is otherwise valid.

### Monotonic snapshot revision

Replacement snapshot revision must strictly increase relative to the active snapshot revision.

This protects the activation sequence from revision reuse and ABA-style ambiguity.

AUTH-0060 does not infer a replacement revision.

### Replacement view

The replacement is already an admitted `SkyIslandPublishedWorldView`.

AUTH-0060 therefore does not repeat AUTH-0059 publication-set admission.

Instead it binds a new snapshot identity to that exact already-admitted view.

If publication selection or physical support changed, AUTH-0059 must have accepted those changes
before AUTH-0060 can activate them.

## Immutability and stale-state behavior

Successful replacement returns a new active state.

The prior state remains bound to the prior snapshot.

This makes stale readers observable rather than silently mutating their state underneath them.

A subsequent replacement that names the old snapshot ID against the new state fails.

## Query behavior

An inactive state cannot be queried.

An active state delegates to its exact snapshot.

The returned entries are equal to querying the bound AUTH-0059 view directly.

AUTH-0060 therefore introduces no new spatial semantics.

## Explicit non-goals

AUTH-0060 does not:

- choose an AUTH-0058 publication revision;
- admit publication sets;
- alter cross-publication overlap policy;
- compile or recompile world volumes;
- run planning, synthesis, or convergence;
- maintain a process-global activation registry;
- serialize activation state;
- persist snapshots across restart;
- load or discover chunks;
- map semantic materials to Minecraft BlockState;
- mutate terrain;
- invoke NeoForge lifecycle hooks.

"Active" means selected by this backend-neutral capability state only.

It does not mean that a Minecraft world has been modified.

## Acceptance gate

Reject AUTH-0060 if:

- snapshot identity omits the exact AUTH-0059 view identity;
- a noncanonical view-identity order can be encoded;
- snapshot revision can be zero or negative;
- a snapshot can bind an ID from a different view;
- activation mutates the prior state;
- initial activation can overwrite an active snapshot;
- replacement does not require the exact expected current ID;
- stale expected identity can replace the current snapshot;
- replacement can reuse or lower snapshot revision;
- an inactive state can be queried;
- active query results differ from the bound AUTH-0059 view;
- publication revision and snapshot revision are conflated;
- Minecraft or NeoForge types enter the contract.

## Visual evidence

AUTH-0060 uses a 1280×720 (16:9) architecture/proof atlas with six panels:

- `SNAPSHOT_BINDING`;
- `CANONICAL_IDENTITY`;
- `INITIAL_ACTIVATION`;
- `MONOTONIC_REPLACEMENT`;
- `STALE_CAS_BLOCKED`;
- `QUERY_DELEGATION`.

Evidence records:

- exact snapshot/view identity binding;
- unsigned canonical publication ordering inside snapshot identity;
- explicit inactive-to-active transition with immutable prior state;
- separate publication versus snapshot version axes;
- strict monotonic compare-and-swap replacement;
- stale and invalid activation rejection;
- exact query delegation.

The atlas is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0061 direction is **runtime binding lease / snapshot handoff contract**.

It should define how a downstream adapter binds to one exact active AUTH-0060 snapshot and proves it
has not gone stale before committing backend-visible work, without yet defining Minecraft material
mapping or terrain mutation itself.
