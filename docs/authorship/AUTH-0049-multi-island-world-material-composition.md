# AUTH-0049 — Multi-Island World Material Composition

AUTH-0049 composes the accepted AUTH-0048 multi-island ownership contract with the accepted AUTH-0047 world-space authored-material sampler.

It is deliberately thin.

It introduces:

- no new spatial heuristic;
- no new geology;
- no new material role;
- no new cave behavior;
- no concrete backend material identity;
- no Minecraft mutation behavior.

## Composition order

~~~text
AUTH-0046 explicit association catalog
        ↓
AUTH-0048 exact multi-island ownership
        ↓
NONE / UNIQUE / AMBIGUOUS
        ↓
only UNIQUE continues
        ↓
AUTH-0047 exact world-space authored-material sample
        ↓
AUTH-0045 stable semantic application identity
~~~

Ownership is always resolved before any AUTH-0042 material decision is requested.

Material availability therefore cannot influence which island owns a point.

## Outcomes

### NONE

AUTH-0048 reports no native authored owner.

AUTH-0049 returns:

- ownership status NONE;
- no AUTH-0047 sample;
- no material;
- no authored void sample;
- no AUTH-0045 application;
- no application key.

The material decision provider is not invoked.

### UNIQUE

AUTH-0048 reports exactly one native authored owner.

AUTH-0049 invokes AUTH-0047 using:

- the same world coordinate;
- that exact AUTH-0046 association;
- the supplied AUTH-0042 material decision provider.

The resulting AUTH-0047 sample must retain:

- the same world coordinate;
- the same unique association;
- the same recovered semantic point already established by AUTH-0048;
- physical interior;
- native authored ownership.

UNIQUE may produce either:

#### Authored material

The AUTH-0047 sample contains a final AUTH-0044 winner and AUTH-0045 application key.

#### Authored cave void

The AUTH-0047 sample remains present and native-owned, but:

- materialPresent = false;
- authoredVoid = true;
- no AUTH-0045 application exists;
- the material decision provider is not invoked because AUTH-0039 has no material requests for authored void.

AUTH-0049 therefore does not confuse "no material" with "no island owner."

### AMBIGUOUS

AUTH-0048 reports more than one native authored owner.

AUTH-0049 returns:

- ownership status AMBIGUOUS;
- no AUTH-0047 sample;
- no material application;
- no application key.

The material decision provider is not invoked.

AUTH-0049 does not attempt to resolve ambiguity using:

- candidate material compatibility;
- available backend blocks;
- material rank;
- nearest center;
- catalog order;
- backend encounter order.

Spatial ambiguity remains a spatial/layout problem.

## Result envelope

SkyIslandWorldAuthoredMaterialComposition contains:

- the exact AUTH-0048 ownership selection;
- an AUTH-0047 sample only for UNIQUE.

Its constructor rejects:

- UNIQUE without a sample;
- NONE/AMBIGUOUS with a sample;
- a sample at another world coordinate;
- a sample from another association;
- a sample with a different recovered semantic point;
- a UNIQUE sample that is not both physically interior and native-owned.

This prevents callers from manually fabricating a composition result that bypasses AUTH-0048.

## Composer

SkyIslandWorldAuthoredMaterialComposer is constructed from one AUTH-0046 association catalog.

It prebuilds:

- one AUTH-0048 ownership resolver;
- one AUTH-0047 sampler per explicit association.

For each query:

~~~text
ownership = AUTH-0048.resolve(worldPoint)

if ownership != UNIQUE:
    return composition(ownership, no sample)

owner = ownership.uniqueOwner
sample = AUTH-0047(owner.association).sample(worldPoint, decisionProvider)
return composition(ownership, sample)
~~~

The composer does not search for a second association after ownership is known.

## Decision-provider isolation

The AUTH-0042 material decision provider is semantically downstream of ownership.

Acceptance requires:

- NONE -> zero provider calls;
- AMBIGUOUS -> zero provider calls;
- UNIQUE authored void -> zero provider calls;
- UNIQUE material -> provider calls only for requests belonging to the exact unique owner's stable binding identities.

This guards against future implementations accidentally using material resolvability to rank islands.

## Determinism

AUTH-0049 inherits deterministic identity ordering and ownership from AUTH-0048.

Reversing the caller's input association list cannot change:

- ownership status;
- unique owner;
- whether AUTH-0047 runs;
- final AUTH-0044 winner;
- AUTH-0045 application key.

No new traversal-order dependency is introduced.

## Evidence

The AUTH-0049 corpus is scenario-oriented rather than morphology-oriented.

Canonical scenarios include:

- EMPTY SKY — NONE;
- UNIQUE MATERIAL;
- UNIQUE AUTHORED VOID;
- TRUE OVERLAP — AMBIGUOUS;
- STACK LOWER — UNIQUE;
- STACK UPPER — UNIQUE.

The manifest records for every scenario:

- ownership status;
- conservative candidate count;
- exact physical occupant count;
- authored owner count;
- AUTH-0047 sample presence;
- material presence;
- authored-void state;
- AUTH-0045 application presence;
- material decision provider calls;
- selected authored island identity where unique.

Acceptance requires:

- NONE and AMBIGUOUS have no sample/application and zero provider calls;
- unique authored void has a sample, no application, and zero provider calls;
- unique material has one exact owner and an application;
- stacked lower/upper select different explicit associations;
- no scenario allows material decisions to change ownership.

The visual diagnostic is a scenario matrix, not a new aesthetic morphology gate.

## Parallel implementation boundary

AUTH-0049 remains entirely in backend-neutral world/reference modules.

It changes no Minecraft:

- BlockPos conversion;
- chunk querying;
- registry binding;
- BlockState resolution;
- terrain mutation;
- carvers;
- structures;
- persistence;
- save/reload;
- client state.

A future Minecraft adapter may now consume the complete native semantic path:

~~~text
BlockPos / backend coordinate
        ↓ adapter coordinate conversion
Skyforge Coordinate3
        ↓
AUTH-0049
        ↓
NONE          -> no authored material application
UNIQUE void   -> preserve authored void semantics
UNIQUE material -> AUTH-0045 stable application key
AMBIGUOUS     -> fail closed / no authored material application
        ↓
adapter-owned concrete material binding
        ↓
existing exact-volume mutation authority
~~~

The adapter must not reinterpret AMBIGUOUS as first/nearest owner.

## Acceptance gate

Reject AUTH-0049 if:

- material decisions are consulted before AUTH-0048 ownership;
- NONE or AMBIGUOUS invokes AUTH-0047;
- authored void loses its UNIQUE owner;
- material availability influences owner selection;
- a UNIQUE result can contain a sample from another association;
- a UNIQUE result can contain a sample at another recovered semantic point;
- concrete backend material identity enters skyforge-world;
- Minecraft/NeoForge types enter the composition contract.

## Next milestone

After AUTH-0049, the native world-query chain is complete through stable semantic material application identity.

The next authorship milestone should not add another adapter-shaped wrapper merely for continuity.

A useful next boundary should return to **world authorship policy**: preventing or admitting true authored-volume overlap at planning time.

That would let AUTH-0048 AMBIGUOUS become a deliberately constrained exceptional state rather than merely a safely reported runtime possibility.

A likely AUTH-0050 direction is therefore:

**authored-realization overlap admission / separation policy**

with explicit guarantees for:

- same-X/Z intentional stacking;
- minimum vertical separation where non-overlap is required;
- conservative bounds allowed to overlap;
- native authored volumes prohibited from true overlap unless an explicit composition mode permits it;
- deterministic diagnostics for rejected/accepted placement plans.

This should remain backend-neutral and should reuse AUTH-0048 as the exact overlap oracle.
