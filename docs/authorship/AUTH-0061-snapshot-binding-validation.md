# AUTH-0061 — Snapshot binding and stale-validation handoff

## Purpose

AUTH-0061 defines the backend-neutral preparation/commit handoff around one exact AUTH-0060 active
snapshot.

AUTH-0060 establishes which admitted publication view is active. A downstream adapter may need to
prepare work from that snapshot and only later reach a backend-visible commit boundary.

AUTH-0061 answers:

> Which exact snapshot was used to prepare this work, and is that same snapshot still current in the
> activation state being checked?

It does not claim to make a backend commit atomic.

## Binding capability

`SkyIslandPublishedWorldSnapshotBinding` schema 1 captures one exact
`SkyIslandPublishedWorldSnapshot`.

The binding retains:

- the snapshot identity;
- the exact admitted AUTH-0059 view carried by that snapshot;
- all proof-carrying query behavior of the snapshot.

There is no time-to-live and no clock field.

"Binding" is therefore more precise than "lease": the capability is a durable statement of
preparation provenance, not a promise that activation will remain unchanged for some duration.

## Explicit capture

`SkyIslandPublishedWorldSnapshotBinder.bind(activationState)` captures only the exact snapshot
active in the supplied AUTH-0060 activation state.

Binding an inactive state fails explicitly.

The binder does not:

- activate a view;
- select a publication;
- refresh an older binding;
- search for a newer snapshot;
- mutate activation state.

## Stable visible identity

The binding token is:

    sfbinding:v<schema>:<exact snapshot canonical token>

No extra random or hidden lease identifier is manufactured.

The exact AUTH-0060 snapshot identity therefore remains visible in the handoff identity.

## Preparation semantics

Preparation queries execute against the captured snapshot:

    binding.query(region)

They do not consult whatever activation state may be current later.

This is deliberate.

If activation changes after preparation begins, the prepared work remains attributable to the exact
snapshot from which it was derived.

Silently refreshing the binding would destroy that provenance and could mix work prepared from two
different publication views.

## Validation states

Validation is explicit:

    binder.validate(binding, activationState)

The result is one of exactly three states.

### CURRENT

`CURRENT` means the exact binding snapshot identity equals the exact snapshot identity active in the
supplied activation state.

A CURRENT result must name that exact identity.

### STALE

`STALE` means the supplied activation state is active, but its exact snapshot identity differs from
the binding.

A STALE result names the different current identity.

It does not automatically rebind.

### INACTIVE

`INACTIVE` means the supplied activation state contains no active snapshot.

No current snapshot identity is present.

INACTIVE is distinct from STALE because the downstream decision may differ between "another world
snapshot is now active" and "there is no active world snapshot."

## Validation result invariants

`SkyIslandPublishedWorldBindingValidation` independently enforces its state/identity relationship:

- CURRENT requires the exact bound snapshot identity;
- STALE requires a present, different snapshot identity;
- INACTIVE requires no current snapshot identity.

Impossible validation tuples fail construction.

This prevents callers from fabricating contradictory status metadata.

## Require-current gate

`requireCurrent()` succeeds only for CURRENT.

STALE and INACTIVE fail explicitly and include the bound/current identities in the error message.

This is the intended backend handoff gate:

1. capture binding;
2. prepare using only the binding;
3. acquire/read the adapter's current activation state according to that adapter's synchronization
   model;
4. validate the binding against that state;
5. commit backend-visible work only if the adapter's own synchronization protocol makes the
   validation-to-commit interval safe.

## Important atomicity boundary

AUTH-0061 does **not** claim that:

    validate(binding, state) == CURRENT

guarantees a mutable external activation reference cannot change immediately afterward.

That guarantee cannot be provided by an immutable backend-neutral value object.

A concrete backend must place validation and backend commit under an appropriate synchronization or
transaction boundary if atomic currentness is required.

AUTH-0061 provides the exact identity/provenance predicate that such a boundary must enforce.

## No hidden refresh

A binding captured from snapshot S20 remains bound to S20 even after activation changes to S21.

Its preparation queries continue to use S20.

Validation against S21 returns STALE.

There is intentionally no:

- `refresh()`;
- `latest()`;
- `rebindIfStale()`;
- implicit retry.

A caller that wants to prepare against S21 must explicitly bind S21 and restart or otherwise
reconcile its own work.

## Query provenance

Because a binding delegates to the exact captured AUTH-0060 snapshot, query hits retain the complete
upstream chain:

    binding
      -> snapshot ID
      -> AUTH-0059 view identity
      -> AUTH-0058 publication ID
      -> world-volume ID
      -> support certificate

AUTH-0061 introduces no new spatial selection semantics.

## Immutability

Binding and validation results are immutable values.

The binder itself has no stored activation state.

Therefore one binder instance cannot silently change the meaning of an existing binding.

## Explicit non-goals

AUTH-0061 does not:

- provide locking;
- provide compare-and-swap on a mutable global activation reference;
- guarantee atomic validation-plus-commit;
- define a clock-based lease or expiration interval;
- refresh/rebind stale work;
- retry preparation;
- choose publication revisions;
- activate snapshots;
- mutate backend state;
- serialize binding state;
- map semantics to Minecraft BlockState;
- load chunks;
- write terrain;
- define NeoForge lifecycle behavior.

## Acceptance gate

Reject AUTH-0061 if:

- inactive activation can be bound;
- a binding can change its snapshot after capture;
- preparation queries consult a later activation state;
- validation can report CURRENT for a different snapshot ID;
- validation can report STALE without a different active ID;
- validation can report INACTIVE while naming a current ID;
- STALE silently refreshes or rebinds;
- CURRENT/STALE/INACTIVE are conflated;
- `requireCurrent()` permits STALE or INACTIVE;
- the binding identity obscures the exact snapshot provenance;
- the contract implies atomic backend commit guarantees it cannot enforce;
- Minecraft or NeoForge types enter the contract.

## Visual evidence

AUTH-0061 uses a 1280×720 (16:9) architecture/proof atlas with six panels:

- `CAPTURE_EXACT`;
- `CURRENT_VALIDATION`;
- `STALE_NO_REFRESH`;
- `INACTIVE_DISTINCT`;
- `CAPTURED_QUERY`;
- `STATUS_INVARIANTS`.

Evidence records:

- exact active-snapshot capture;
- CURRENT validation against the original activation state;
- stale detection after explicit AUTH-0060 replacement;
- continued query behavior from the captured old snapshot;
- distinct INACTIVE validation;
- rejection of impossible validation tuples.

The atlas is architecture/proof evidence rather than an aesthetic morphology gate.

## Next boundary

A likely AUTH-0062 direction is **prepared-work provenance / commit-intent identity**.

It should define a backend-neutral immutable envelope that binds a unit of prepared work to:

- one exact AUTH-0061 snapshot binding;
- an explicit deterministic work identity or region identity;
- any proof required before a backend adapter accepts that prepared work for commit.

It must preserve the same rule: no backend mutation is considered safe merely because preparation
succeeded, and no stale binding may be silently refreshed.
