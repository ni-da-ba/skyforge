# AUTH-0048 — Multi-Island Authored-Realization Ownership

AUTH-0048 defines the backend-neutral composition boundary for selecting which explicit AUTH-0046 authored-realization association owns one abstract Skyforge world-space point.

It resolves ownership only.

It does not:

- choose a concrete backend material;
- place blocks;
- mutate terrain;
- invoke Minecraft chunk ownership;
- infer associations;
- silently rank genuine overlap.

## Dependency

~~~text
AUTH-0046 authored-realization catalog
        +
world-space Coordinate3
        ↓
AUTH-0048 ownership resolver
        ↓
conservative associated-volume candidates
        ↓
exact compiled physical occupants
        ↓
current native authored-domain owners
        ↓
NONE / UNIQUE / AMBIGUOUS
~~~

A later composition layer may feed a UNIQUE owner into AUTH-0047 world-space authored-material sampling.

AUTH-0048 deliberately stops before that step.

## Why this boundary is necessary

AUTH-0047 intentionally samples one explicit association at a time.

That is the correct single-island contract, but a world may contain:

- many islands;
- overlapping conservative bounds;
- same-X/Z vertically stacked islands;
- compiled physical fringes that extend beyond the current native authored domain;
- genuinely overlapping authored realizations.

A backend must not answer those cases by:

- taking the first catalog item;
- choosing the closest center;
- choosing the closest suspension elevation;
- comparing unrelated seeds;
- using chunk encounter order;
- allowing a material table to decide spatial ownership.

AUTH-0048 makes those forbidden shortcuts unnecessary.

## Three composition stages

One world query produces three distinct sets.

### 1. Conservative candidates

An explicit AUTH-0046 association is a conservative candidate when its realized SkyIslandWorldVolume bounds contain the world point.

Bounds are used only for culling.

They are not proof of physical occupancy and carry no ranking meaning.

Multiple conservative candidates are normal.

This is especially important for same-X/Z stacked islands whose broad reservation bounds may overlap vertically.

### 2. Exact physical occupants

For each conservative candidate:

~~~text
localX = worldX - realized.centerX
localZ = worldZ - realized.centerZ
physicalY = worldY
~~~

AUTH-0048 then uses:

- SkyIslandCompiledVolumeColumnField;
- SkyIslandSemanticDepthRealizationTransform.

A candidate is an exact physical occupant only when the authoritative compiled upper/underside column contains physical Y and therefore yields a semantic subsurface position.

Conservative-bound overlap without exact physical overlap is not ambiguity.

### 3. Native authored owners

Exact physical occupancy is still not sufficient to claim native authorship.

The recovered island-local point is tested against the current authoritative SkyIslandSemanticFieldSet interiority domain.

AUTH-0021 makes the AUTH-0020 morphology-aware naturalized domain the current ownership geometry.

Therefore:

~~~text
exact compiled physical occupant
        +
current native interiority > 0
        =
native authored owner
~~~

A compiled physical fringe outside native ownership remains physical but unowned by native authorship.

This distinction prevents legacy or independently compiled solid extent from silently expanding the native authored island.

## Ownership status

AUTH-0048 exposes exactly three outcomes.

### NONE

No native authored owner exists at the world point.

This includes:

- empty sky;
- conservative bounds with no exact physical occupant;
- exact physical compiled fringe outside every native authored domain.

### UNIQUE

Exactly one native authored owner exists.

Only UNIQUE exposes a single authoritative owner.

### AMBIGUOUS

More than one native authored owner exists.

AUTH-0048 does not break the tie.

Ambiguity is data that a higher-level layout/admission policy must prevent or explicitly resolve before generation becomes authoritative.

No nearest-center or list-order fallback is permitted.

## Stacked-volume isolation

Same-X/Z islands are a first-class case.

Two associated islands may share identical horizontal centers while occupying different vertical bands.

Broad conservative bounds may include both at one query point.

AUTH-0048 requires:

~~~text
same X/Z
    + lower island physical Y
    -> lower exact occupant only

same X/Z
    + upper island physical Y
    -> upper exact occupant only

same X/Z
    + physical gap
    -> no exact occupant
~~~

Catalog order is irrelevant.

This provides a backend-neutral semantic analogue of the stacked-volume isolation already proven in the implementation lane without importing Minecraft runtime state into skyforge-world.

## Native ownership versus authored cave void

AUTH-0048 resolves island ownership before material/cave state.

An AUTH-0030 authored cave void is still inside the native authored domain.

Therefore a cave point remains owned by that island even though AUTH-0047 later returns:

~~~text
authoredOwned = true
materialPresent = false
~~~

Another overlapping association must not become owner merely because the first island authored a void at that point.

This avoids ownership changing as a side effect of cave carving or material realization.

## Physical overlap versus authored overlap

Two exact compiled physical volumes may overlap while only one native domain owns the queried local position.

That case is UNIQUE, not AMBIGUOUS.

AUTH-0048 therefore distinguishes:

~~~text
physical overlap
        !=
native authored overlap
~~~

Only multiple current native owners produce AMBIGUOUS.

## Deterministic ordering

SkyIslandAuthoredRealizationCatalog already canonicalizes explicit AUTH-0046 associations by stable association token.

AUTH-0048 preserves that canonical order for diagnostics.

Ordering is not ranking.

Reversing the caller's catalog input list must not change:

- candidate identity order;
- exact occupants;
- authored owners;
- ownership status;
- unique owner.

## World/local floating-point contract

As in AUTH-0047, world coordinates are authoritative binary64 inputs.

AUTH-0048 performs ordinary subtraction against each realized center.

The selection envelope validates that every candidate's local X/Z and physical Y correspond bit-exactly to the queried world coordinate under that association's world/local transform.

AUTH-0048 does not attempt to reconstruct an earlier arbitrary semantic double.

## Public API

### SkyIslandAuthoredRealizationOwnershipResolver

Consumes one immutable AUTH-0046 association catalog and resolves one Coordinate3.

The resolver prebuilds per-association:

- compiled physical column/depth transform;
- current native semantic interiority field.

### SkyIslandAuthoredRealizationOwnershipCandidate

Represents one conservative candidate.

It distinguishes:

- conservative-only;
- exact physical interior;
- native authored owner.

A candidate cannot claim authored ownership without exact physical interior.

### SkyIslandAuthoredRealizationOwnershipSelection

Carries:

- world position;
- canonical conservative candidates;
- exact physical occupants;
- native authored owners;
- derived NONE / UNIQUE / AMBIGUOUS status;
- optional unique owner.

A forged candidate whose world/local frame does not match the selection world point is rejected.

## Evidence

The AUTH-0048 diagnostic corpus should emphasize composition rather than individual morphology.

Its primary atlas is a Z/Y vertical slice at X=0 containing:

- a same-X/Z stacked pair with deliberately overlapping conservative bounds;
- a deliberately co-located true-overlap pair.

Panels show:

- CONSERVATIVE — count/state from associated bounds;
- PHYSICAL — exact compiled physical occupants;
- AUTHORED — current native owners;
- RESOLUTION — NONE, unique owner identity, or AMBIGUOUS.

The corpus manifest records:

- sampled points;
- points with multiple conservative candidates;
- points with multiple exact physical occupants;
- unique authored ownership;
- ambiguous authored ownership;
- exact physical but native-unowned points;
- stacked cross-contamination violations;
- order-dependence violations.

A second targeted proof verifies that a two-physical/one-native point resolves UNIQUE.

The atlas is diagnostic rather than an aesthetic morphology gate.

## Acceptance gate

Reject AUTH-0048 if:

- an association is discovered rather than supplied through AUTH-0046;
- bounds alone establish ownership;
- nearest center or nearest suspension elevation ranks candidates;
- catalog or backend encounter order ranks candidates;
- exact physical membership is replaced with reservation/bounds membership;
- compiled physical fringe expands current native ownership;
- same-X/Z stacked volumes contaminate one another by Y;
- authored cave void relinquishes island ownership;
- a genuine multiple-native-owner point is silently reduced to one owner;
- reverse catalog input changes the result;
- Minecraft, NeoForge, BlockPos, chunk, registry, or BlockState identity enters skyforge-world.

## Parallel implementation boundary

AUTH-0048 changes no Minecraft behavior.

It does not alter:

- terrain writer admission;
- exact-volume mutation fences;
- carvers;
- structures;
- lakes;
- decorators;
- persistence;
- save/reload;
- client synchronization.

The implementation lane should continue independently.

Once AUTH-0048 is accepted, a future adapter may use the native query chain approximately as:

~~~text
backend point
    -> abstract Skyforge Coordinate3
    -> AUTH-0048 ownership
    -> require UNIQUE
    -> AUTH-0047 sample on that exact association
    -> AUTH-0045 application key
    -> adapter-owned concrete material binding
    -> existing backend mutation authority
~~~

The adapter must still define how AMBIGUOUS is treated operationally.

The safest default is fail-closed/no native material application until layout/admission policy proves ambiguity cannot occur for accepted production volumes.

## Next milestone

If AUTH-0048 is accepted, the natural next authorship milestone is **AUTH-0049: multi-island world material composition**.

AUTH-0049 should compose AUTH-0048 and AUTH-0047 without adding new spatial heuristics:

- NONE -> no authored material sample;
- UNIQUE -> invoke AUTH-0047 for the exact unique association;
- AMBIGUOUS -> explicit ambiguous result, no silent material application.

That would complete the native world-query path from a multi-island authored world to stable semantic material application identity while remaining backend-neutral.
