# AUTH-0059 — Publication-set backend-view admission

## Purpose

AUTH-0059 defines how one or more immutable AUTH-0058 compiled-world publications become a single
backend-neutral query view without losing publication identity, deterministic ordering, or
proof-grade support isolation.

AUTH-0058 answered when one compiled regional world is safe to expose as a publication capability.
AUTH-0059 answers the next question:

> When may several such publications coexist in one backend query view, and how are version
> replacement and region queries made explicit?

The answer remains fail-closed.

## Inputs

AUTH-0059 consumes only complete `SkyIslandCompiledWorldPublication` objects.

It does not accept:

- raw `SkyIslandWorldCatalog`;
- raw `SkyIslandWorldCatalogSupportBundle`;
- unaccepted convergence reports;
- plans or requests;
- backend-specific chunk/runtime state.

Every input publication therefore already carries the AUTH-0056 → AUTH-0057 → AUTH-0058 proof
chain.

## View identity

`SkyIslandPublishedWorldView` schema 1 contains an immutable canonical publication set.

The view identity is the ordered list:

    SkyIslandCompiledWorldPublicationId...

Publication ordering is canonical:

    unsigned archipelagoRootSeed ascending

Caller list order is not semantic.

Exactly one publication may be selected for each regional root.

AUTH-0059 therefore rejects a set containing two publication revisions for the same root rather than
inventing a "newest wins" rule.

## One publication per regional root

A publication root is a regional realization identity domain, not merely a sort key.

If two inputs have the same `archipelagoRootSeed`, AUTH-0059 requires the caller to select exactly
one of them before constructing the view.

This prevents ambiguous state such as:

    root R revision 3
    root R revision 4

from silently becoming:

    revision 4 because 4 > 3

Selection policy remains explicit.

## Cross-publication physical isolation

Different publication roots may coexist only when every pair of proof-grade certified support bounds
from different publications is disjoint.

AUTH-0059 evaluates:

    certificate.supportBounds()

not the broad world-catalog query reservation.

This distinction is critical.

### Broad query overlap is permitted

Backend query bounds are intentionally conservative. Two publications may have intersecting query
bounds while their certified physical support remains disjoint.

Such a pair is admitted.

The canonical regression uses one-member publications separated so that:

- broad query bounds intersect;
- certified support bounds do not intersect;
- the publication view is admitted.

### Certified support overlap fails closed

If certified support from two different publications overlaps or even touches, AUTH-0059 rejects the
view.

No implicit composition, stacking, precedence, clipping, or ownership rule is inferred from:

- publication order;
- root seed;
- revision;
- query bounds;
- list position.

If intentional cross-publication composition is ever required, it needs its own explicit downstream
policy.

## Flattened proof-carrying entries

`SkyIslandPublishedWorldEntry` binds:

- the exact AUTH-0058 publication;
- one exact world-catalog volume from that publication;
- that exact volume's support certificate.

Construction verifies:

- the volume exists in the publication catalog;
- the supplied volume equals the exact catalog volume;
- the support certificate equals the exact publication certificate;
- the certificate binds the exact volume.

The entry therefore retains both query geometry and proof/provenance.

## Deterministic entry order

Flattened entries use:

1. canonical publication order by unsigned regional root;
2. original AUTH-0057 plan-order catalog volume order inside each publication.

No global resort by world coordinates occurs.

That preserves the deterministic regional hierarchy already established upstream.

## Backend-neutral query

`SkyIslandPublishedWorldView.query(WorldBounds)` scans the immutable flattened entry set in
canonical order.

A hit is selected when the volume's broad query reservation intersects the requested region.

The returned value is `SkyIslandPublishedWorldEntry`, not a bare volume.

Each hit therefore retains:

- publication identity/revision;
- world-volume identity;
- exact support certificate.

Certified support bounds remain separate from conservative query bounds.

## Explicit publication replacement

Version replacement uses compare-and-replace:

    view.replace(expectedCurrentPublicationId, replacementPublication)

The caller must name the exact currently expected publication.

The replacement must:

- preserve the same regional root;
- use a strictly greater publication revision.

If the expected current publication is absent, replacement fails as stale.

The view never scans for "highest revision" and never performs implicit conflict resolution.

## Replacement re-admission

Replacement does not mutate the existing view.

It constructs and fully re-admits a new view.

This means the replacement is rechecked for:

- one-publication-per-root uniqueness;
- full certification;
- cross-publication certified-support isolation;
- canonical ordering.

A replacement that moves or changes the regional publication so that its certified support now
overlaps another selected publication is rejected.

The original view remains unchanged.

## Immutability

All publication lists, flattened entries, query results, and replacement results are immutable.

AUTH-0059 has no in-place update API.

This makes view identity suitable for later cache/runtime binding without hidden publication-set
mutation.

## Explicit non-goals

AUTH-0059 does not:

- choose the newest publication automatically;
- maintain a persistent publication registry;
- resolve intentional overlap;
- compose two publication roots;
- assign ownership priority;
- change query reservations;
- compile or recompile volumes;
- re-run planning, synthesis, convergence, or publication;
- map material semantics to Minecraft BlockState;
- discover or load chunks;
- mutate terrain;
- define save/reload persistence.

## Acceptance gate

Reject AUTH-0059 if:

- caller list order changes view identity;
- multiple revisions of one regional root are silently accepted;
- revision comparison selects a winner implicitly;
- broad query-bound overlap alone rejects an otherwise proof-disjoint pair;
- certified cross-publication support overlap is admitted;
- a query result loses publication identity;
- a query result loses its exact support certificate;
- entry construction can forge a different same-ID world volume;
- replacement can change regional root;
- replacement can keep or lower revision;
- stale expected-current identity can replace another revision;
- replacement bypasses full re-admission;
- the original view mutates after replacement;
- Minecraft or NeoForge types enter the contract.

## Visual evidence

AUTH-0059 uses a 1280×720 (16:9) architecture/proof atlas with six panels:

- `CANONICAL_VIEW`;
- `QUERY_BOUNDS_VS_SUPPORT`;
- `DUPLICATE_ROOT_BLOCKED`;
- `SUPPORT_OVERLAP_BLOCKED`;
- `QUERY_PROVENANCE`;
- `EXPLICIT_REPLACEMENT`.

Evidence records:

- canonical publication and flattened-entry order;
- admitted broad-query overlap with certified-support separation;
- duplicate-root fail-closed behavior;
- certified-support overlap fail-closed behavior;
- publication/certificate provenance retained by query hits;
- explicit monotonic compare-and-replace plus replacement re-admission.

The atlas is architecture/proof evidence rather than an aesthetic morphology gate.

## Next boundary

A likely AUTH-0060 direction is **backend-view snapshot identity / publication activation**.

It should define how one admitted AUTH-0059 view becomes an immutable activated snapshot suitable for
binding to a downstream runtime, including explicit snapshot identity and stale-view replacement
semantics, without yet performing Minecraft terrain mutation.
