# AUTH-0040 — Backend-Neutral Material Capabilities and Compatibility Constraints

AUTH-0040 defines how Skyforge determines whether a prospective concrete material is semantically suitable for an AUTH-0039 material-binding request.

It remains backend-neutral.

A backend retains candidate identity. The Skyforge world layer sees only a semantic capability profile.

No Minecraft block id, registry key, resource location, tag, named rock, mineral species, or concrete material enters the authored world model.

## Functional position

The material pipeline is now:

~~~text
AUTH-0036 compositional lithologic realization
        ↓
AUTH-0037 semantic role eligibility + local expression limits
        ↓
AUTH-0038 stable coherence/binding key
        ↓
AUTH-0039 stable backend-neutral binding request
        ↓
AUTH-0040 capability constraints + compatibility assessment
        ↓
future backend candidate resolver
        ↓
concrete backend material binding
~~~

AUTH-0039 answers:

> What stable material function is being requested?

AUTH-0040 answers:

> What must a proposed material be capable of doing to satisfy that request?

AUTH-0040 does not choose the material.

## Capability vocabulary

SkyIslandMaterialCapability defines five backend-neutral axes:

- HOST_MATRIX_SUITABILITY;
- FABRIC_EXPRESSIVENESS;
- ALTERATION_OVERPRINT_SUITABILITY;
- HYDROLOGIC_CONDITIONING_SUITABILITY;
- STRUCTURAL_ACCENT_SUITABILITY.

A capability profile advertises one normalized value in [0, 1] for each axis.

The backend owns the candidate's concrete identity and is responsible for translating backend facts into this semantic profile.

## Hard compatibility floors

AUTH-0040 defines the following initial hard floors:

| Request semantic state | Hard requirement |
| --- | --- |
| PRIMARY_MATRIX | HOST_MATRIX_SUITABILITY >= 0.75 |
| PRIMARY_MATRIX sourced from FABRIC_RICH_MATRIX | also FABRIC_EXPRESSIVENESS >= 0.65 |
| SECONDARY_MATRIX | HOST_MATRIX_SUITABILITY >= 0.55 |
| SECONDARY_MATRIX sourced from FABRIC_RICH_MATRIX | also FABRIC_EXPRESSIVENESS >= 0.60 |
| ALTERATION_OVERPRINT | ALTERATION_OVERPRINT_SUITABILITY >= 0.65 |
| HYDROLOGIC_CONDITIONING | HYDROLOGIC_CONDITIONING_SUITABILITY >= 0.65 |
| MINERAL_BEARING_STRUCTURE | STRUCTURAL_ACCENT_SUITABILITY >= 0.70 |

These are hard semantic compatibility constraints.

They are not ranking weights.

A profile either satisfies every required floor or it is incompatible with that request.

## Why primary and secondary host floors differ

PRIMARY_MATRIX is the required structural host role.

Its concrete binding must be strongly suitable as the dominant matrix.

SECONDARY_MATRIX is optional and bounded by AUTH-0037 to a local expression ceiling below 0.50.

It therefore receives a lower host-matrix floor while remaining structurally credible.

Fabric-source matrix roles additionally require fabric expressiveness because a backend candidate that cannot communicate fabric/layering would erase accepted upstream semantic distinction.

## Conditioned roles

Conditioned roles are not forced to advertise host-matrix suitability in AUTH-0040.

That is deliberate.

AUTH-0037 already bounds alteration, hydrologic, and mineral-bearing roles below dominant-matrix replacement levels.

AUTH-0040 therefore evaluates whether the material can perform the requested conditioned function, not whether it could independently serve as the island's structural host.

A later concrete backend placement policy may impose additional backend-specific constraints if replacement mechanics require them.

## Geological context

AUTH-0039 assemblage and contact context remains attached to every request.

AUTH-0040 does not silently vary hard capability floors according to:

- assemblage count;
- assemblage kind;
- contact kind;
- contact breadth;
- sample position.

This keeps compatibility deterministic and reviewable.

A backend resolver may use stable AUTH-0039 context for ranking among already-compatible candidates later, but it may not reinterpret an incompatible candidate as compatible by hidden contextual preference.

## Compatibility assessment

SkyIslandMaterialCompatibilityEvaluator compares one capability profile against the deterministic constraint set for one AUTH-0039 request.

The assessment contains one audited evaluation per hard requirement:

- capability;
- required minimum;
- advertised value;
- signed margin;
- pass/fail state.

The overall request is compatible only if every hard requirement passes.

minimumMargin identifies the tightest hard capability margin.

failedRequirementCount makes rejection auditable.

## Candidate identity boundary

SkyIslandMaterialCapabilityProfile intentionally contains no candidate identifier.

This prevents backend identities from leaking into the world model.

A future Minecraft resolver may hold a mapping such as:

~~~text
Minecraft candidate identity
        +
SkyIslandMaterialCapabilityProfile
        ↓
AUTH-0040 compatibility evaluator
        ↓
compatible candidates only
        ↓
backend-specific ranking/selection
~~~

Only the adapter owns the Minecraft identity.

## Evidence

The authorship-material-capability-compatibility-v1 corpus uses the canonical six representatives at semantic depth 0.52.

It evaluates six diagnostic backend-neutral capability profiles:

- MATRIX;
- FABRIC;
- ALTERATION;
- WATER;
- ACCENT;
- GENERALIST.

These are evidence probes, not concrete materials.

Each panel shows how many local AUTH-0039 requests the diagnostic profile can satisfy.

Expected behavior:

- MATRIX strongly selects massive matrix requests;
- FABRIC satisfies both credible host-matrix and fabric-expression requirements;
- ALTERATION selects alteration requests;
- WATER selects hydrologic requests;
- ACCENT selects mineral-bearing structural requests;
- GENERALIST satisfies every local request.

The visual gate rejects compatibility rules that collapse all specialty profiles into the same coverage or that leave valid request regions impossible to satisfy.

## Acceptance gate

Reject AUTH-0040 if:

- capability profiles contain backend material identity;
- compatibility depends on sample traversal order;
- one request produces non-deterministic hard constraints;
- hard requirements are duplicated or unordered;
- a fabric-source matrix request can pass with inadequate fabric expressiveness;
- a primary matrix request can pass with inadequate host-matrix suitability;
- conditioned roles can pass without their matching specialty capability;
- assessment pass/fail disagrees with individual hard requirements;
- request geological context secretly changes hard floors;
- a unit-capability generalist fails any accepted request;
- backend-specific material or registry concepts enter the world layer;
- visual evidence shows specialty profiles behaving indistinguishably.

## Parallel implementation boundary

AUTH-0040 changes no cave, carver, persistence, mutation, or Minecraft material behavior.

The implementation lane should not infer concrete block suitability from AUTH-0040 until a deliberate backend capability-advertisement layer exists.

## Next milestone

If AUTH-0040 is accepted, the next authorship milestone should define **backend-neutral compatible-candidate ranking semantics**.

That milestone should answer how a resolver orders multiple candidates that all pass AUTH-0040 hard constraints while preserving:

- deterministic choice;
- request coherence;
- semantic specialization;
- controlled variety;
- no dependence on sample traversal order.

Concrete Minecraft candidate identities should still remain adapter-owned.
