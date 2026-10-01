# AUTH-0043 — Backend-Neutral Material Expression Allocation

AUTH-0043 defines how accepted material roles are allowed to express spatially after stable request, compatibility, ranking, and resolution provenance have already been established.

It remains backend-neutral.

No Minecraft block id, registry key, resource location, named rock, mineral species, or concrete backend material identity enters the world model.

## Functional position

~~~text
AUTH-0037 local support + expression ceiling
        ↓
AUTH-0038 stable binding coherence
        ↓
AUTH-0039 stable material request
        ↓
AUTH-0040 compatibility
        ↓
AUTH-0041 semantic ranking
        ↓
AUTH-0042 resolution decision provenance
        ↓
AUTH-0043 local expression allocation
        ↓
future deterministic spatial realization / backend block placement
~~~

AUTH-0042 answers:

> Which semantic binding decision is accepted, and was backend identity needed to break a true semantic tie?

AUTH-0043 answers:

> At this local authored sample, how much structural-matrix share and conditioned expression is available to that accepted binding?

## Two different allocation semantics

AUTH-0043 deliberately does not normalize every material role into one arbitrary budget.

The roles represent two different geological functions.

### Structural matrix partition

PRIMARY_MATRIX and optional SECONDARY_MATRIX describe what the host body is structurally made of.

They form one exact partition:

~~~text
secondaryShare = localSupport * localExpressionCeiling
primaryShare   = 1 - secondaryShare
~~~

If SECONDARY_MATRIX is absent:

~~~text
primaryShare   = 1
secondaryShare = 0
~~~

The accepted AUTH-0037 secondary ceiling is below 0.50, so required PRIMARY_MATRIX remains the local structural majority.

PRIMARY_MATRIX is the residual structural host and therefore does not shrink according to its own support scalar.

Its support remains provenance describing why that host channel won the primary semantic role.

### Conditioned expression claims

The following roles describe geological systems acting on or through the host:

- ALTERATION_OVERPRINT;
- HYDROLOGIC_CONDITIONING;
- MINERAL_BEARING_STRUCTURE.

For each present role:

~~~text
conditionedClaim = localSupport * localExpressionCeiling
~~~

Each claim is independently bounded by the accepted AUTH-0037 local ceiling.

Conditioned claims are not normalized against:

- the primary/secondary structural matrix partition;
- other conditioned claims.

That is intentional.

An altered, water-conditioned, mineral-bearing region may legitimately carry several simultaneous geological claims.

AUTH-0043 records those authored claims without prematurely deciding which single concrete voxel material wins when claims overlap.

## Why conditioned claims may overlap

A final block can only contain one concrete material state, but semantic geology can contain several simultaneous influences.

Collapsing all conditioned roles into one sum at AUTH-0043 would introduce arbitrary competition before a spatial realization policy exists.

Therefore AUTH-0043 preserves overlap explicitly.

A later milestone must define deterministic spatial realization/conflict semantics that translate these claims into actual discrete expression while respecting:

- stable binding coherence;
- local ceilings;
- structural host continuity;
- conditioned-system continuity;
- no sample/chunk traversal dependence.

## Resolution-decision provenance

Every SkyIslandMaterialExpressionAllocation retains:

- exact local AUTH-0039 request use;
- exact stable AUTH-0042 resolution decision;
- expression mode;
- local target expression.

The allocation validates that the AUTH-0042 decision belongs to the exact same stable request.

This means local expression may vary continuously while the resolved semantic binding decision remains stable over its AUTH-0038 coherence domain.

## Backend identity boundary

AUTH-0043 uses the stable binding key and AUTH-0042 semantic decision.

It does not need the backend's concrete material identity.

A backend can externally maintain:

~~~text
AUTH-0038 binding key
        → concrete backend material
~~~

while Skyforge independently evaluates:

~~~text
AUTH-0038 binding key
        + local AUTH-0037 expression state
        → AUTH-0043 local expression allocation
~~~

This keeps authored spatial expression independent of backend registry mechanics.

## Non-material samples

Outside-island samples and authored cave void receive no material-expression allocations.

AUTH-0043 does not fill authored void.

## Evidence

The authorship-material-expression-allocation-v1 corpus uses the canonical six representatives at semantic depth 0.52.

Panels:

- PRIMARY SHARE;
- SECONDARY SHARE;
- ALTERATION CLAIM;
- WATER CLAIM;
- MINERAL CLAIM;
- CONDITIONED OVERLAP.

PRIMARY and SECONDARY should form smooth complementary structural host patterns.

Conditioned panels should reproduce their accepted broader geological systems without being normalized away by simultaneous claims.

The overlap panel explicitly reveals locations where more than one conditioned system can express.

The manifest records:

- mean/minimum primary share;
- mean/maximum secondary share;
- mean/maximum conditioned claims;
- conditioned overlap samples;
- matrix-budget violations;
- ceiling violations;
- unique stable resolution decisions encountered.

## Acceptance gate

Reject AUTH-0043 if:

- structural matrix shares do not sum to exactly 1;
- PRIMARY_MATRIX disappears from material-present samples;
- secondary share exceeds its AUTH-0037 local ceiling;
- conditioned claims exceed their AUTH-0037 local ceilings;
- conditioned claims are silently normalized against unrelated roles;
- local expression modifies the stable AUTH-0042 decision;
- one allocation points at a decision for another request;
- outside or authored void receives allocation state;
- backend material identity enters the world model;
- visual evidence shows matrix-partition discontinuity unrelated to upstream semantics;
- visual evidence loses conditioned-system overlap;
- evidence reports any matrix-budget or ceiling violation.

## Parallel implementation boundary

AUTH-0043 changes no cave, carver, persistence, mutation, Minecraft block, or registry behavior.

It does not perform final stochastic or discrete block placement.

## Next milestone

If AUTH-0043 is accepted, the next authorship milestone should define **deterministic discrete material-expression realization**.

That layer should convert:

- exact primary/secondary structural matrix shares;
- independent conditioned claims;
- stable AUTH-0038 binding keys;

into one deterministic local semantic winner/placement state without depending on chunk or sample traversal order.

The output should still be backend-neutral: a semantic binding key/role wins the point, and the backend later maps that stable binding key to its concrete material.
