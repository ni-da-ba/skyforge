# AUTH-0045 — Backend Material-Binding Application Contract

AUTH-0045 defines the final backend-neutral handoff from an AUTH-0044 discrete semantic winner to the concrete material binding already owned by a backend adapter.

It does not choose, name, store, compare, serialize, or derive a concrete backend material identity.

## Functional position

~~~text
AUTH-0038 stable binding key
        ↓
AUTH-0039 semantic binding request
        ↓
AUTH-0040 compatibility
        ↓
AUTH-0041 semantic ranking
        ↓
AUTH-0042 resolution provenance
        ↓
backend-owned concrete binding decision
        ↓
AUTH-0043 continuous expression allocation
        ↓
AUTH-0044 deterministic discrete semantic winner
        ↓
AUTH-0045 exact winner-key application contract
        ↓
adapter-owned concrete material placement
~~~

AUTH-0044 answers:

> Which stable semantic binding wins this exact authored point?

AUTH-0045 answers:

> Which already-resolved backend binding is the adapter allowed to apply to that winner?

The answer is exact:

~~~text
AUTH-0044 winner binding key
        → adapter-owned binding table
        → concrete backend material
~~~

No other semantic role, structural fallback, local sample coordinate, traversal order, or backend heuristic may replace the exact winner key while claiming conformance to AUTH-0045.

## Application envelope

SkyIslandMaterialBindingApplication retains:

- the exact AUTH-0044 realization;
- the exact winning stable AUTH-0038 binding key.

The envelope derives from a material-present AUTH-0044 realization only.

It rejects:

- non-material realization;
- a binding key that differs from the AUTH-0044 final winner.

The envelope exposes inherited semantic provenance for adapter diagnostics:

- winning semantic role;
- exact AUTH-0039 binding request;
- exact AUTH-0042 resolution decision.

It contains no concrete material identity.

## Opaque application seam

SkyIslandMaterialBindingApplicator accepts:

- one AUTH-0044 realization or validated AUTH-0045 application envelope;
- an adapter-owned mapping keyed by SkyIslandSemanticPaletteBindingKey.

The mapping value is an opaque generic backend value.

Skyforge does not:

- inspect the value;
- compare values;
- hash values;
- serialize values;
- derive values;
- choose among values;
- cache values;
- infer a fallback value.

The only operation Skyforge performs is exact winner-key lookup.

This keeps the dependency direction intact:

~~~text
Skyforge world semantics
        ↓
stable semantic binding key
        ↓
adapter-owned binding map
        ↓
opaque backend material value
~~~

The generic value crosses the method boundary transiently. It is never stored in authored world state.

## Stable binding reuse

AUTH-0038 already defines that one stable binding key represents one coherent backend binding decision.

AUTH-0045 preserves that rule by applying only through the stable key.

A conforming adapter should therefore maintain one stable mapping:

~~~text
binding key → concrete backend material
~~~

for the lifetime in which that authored world identity and binding decision remain valid.

AUTH-0045 does not prescribe:

- cache implementation;
- persistence format;
- registry handle type;
- block-state type;
- reload lifecycle;
- backend-specific invalidation policy.

Those remain adapter concerns.

## Final-winner authority

AUTH-0044 distinguishes:

- structural winner;
- final winner after conditioned realization.

AUTH-0045 applies the final winner only.

If alteration, hydrologic conditioning, or mineral-bearing structure wins the exact point, the backend must apply the binding associated with that conditioned winner.

It may not silently fall back to the structural matrix binding.

This preserves the AUTH-0043/AUTH-0044 conditioned-expression semantics through the backend boundary.

## Missing binding behavior

Material-present AUTH-0044 winners require a backend binding.

If the adapter-owned binding table does not contain the exact winner key, application fails.

Skyforge does not invent:

- a default block;
- a generic rock;
- a primary-matrix fallback;
- a nearest semantic role;
- a registry default.

A missing binding means the backend resolution/application pipeline is incomplete.

## Non-material state

Outside-island samples and authored cave void produce no AUTH-0045 application envelope.

The applicator returns no backend value for those samples and does not consult the adapter-owned binding table.

AUTH-0045 therefore cannot fill AUTH-0030 authored cave void.

A backend may represent absence according to its own placement API, but that representation is not a concrete material binding chosen by AUTH-0045.

## Backend identity boundary

AUTH-0045 world-layer state contains only:

- AUTH-0044 semantic realization;
- stable AUTH-0038 binding key;
- inherited semantic request/resolution provenance.

Concrete identity remains adapter-owned.

Examples that remain forbidden in skyforge-world include:

- Minecraft Block or BlockState;
- registry objects;
- ResourceLocation;
- vanilla block ids;
- modded block ids;
- reference-backend material enums;
- backend palette tokens.

A reference backend may use such concrete tokens outside skyforge-world to prove the seam.

## Determinism and order independence

AUTH-0045 introduces no randomness and no spatial selection.

For a fixed:

- AUTH-0044 realization;
- stable binding map;

application is a pure exact-key lookup.

Therefore:

- repeated application returns the same adapter-supplied value;
- sample traversal order cannot change which key is requested;
- candidate encounter order cannot change which key is requested;
- chunk ownership cannot change which key is requested.

Any instability in the concrete mapping itself belongs to the backend and violates stable AUTH-0038 binding reuse.

## Evidence

The AUTH-0045 reference proof should exercise the canonical six representatives at semantic depth 0.52 with an adapter-owned reference material table.

The proof should record:

- material-present samples;
- applied samples;
- authored-void applications;
- missing bindings;
- stable-key reuse violations;
- repeated-application mismatches;
- unique winning binding keys;
- unique concrete reference materials;
- conditioned-winner applications.

The reference concrete material identity must exist only in skyforge-reference.

The expected invariant is:

~~~text
material-present samples = applied samples
void applications = 0
missing bindings = 0
reuse violations = 0
repeat mismatches = 0
~~~

A compact atlas may show the AUTH-0044 semantic winner beside the reference material result to demonstrate that one stable winner-key domain reuses one concrete adapter-owned binding.

The atlas is diagnostic only. AUTH-0045 changes no authored geometry or semantic winner field, so no new morphology judgment is required.

## Acceptance gate

Reject AUTH-0045 if:

- non-material state produces an application envelope;
- authored cave void receives a concrete material application;
- an application key can differ from the AUTH-0044 final winner key;
- conditioned final winners are replaced by structural bindings;
- a missing binding silently falls back;
- lookup depends on local sample or traversal coordinates rather than the stable key;
- backend material identity is stored in authored world state;
- skyforge-world imports a concrete backend API;
- repeated application with one stable binding table can change the applied value;
- one stable binding key can be applied through a different key;
- reference evidence reports missing bindings, void applications, reuse violations, or repeat mismatches.

## Parallel implementation boundary

AUTH-0045 changes no Minecraft cave, carver, persistence, mutation, chunk-writing, registry, or placement behavior.

The implementation lane may consume the contract later, but concrete Minecraft material resolution and block placement remain adapter work.

AUTH-0045 does not modify or supersede the implementation agent's existing native surface/cave integration work.

## Next milestone

If AUTH-0045 is accepted, the next authorship milestone should define an **authored-island realization association contract**.

The native authorship hierarchy and the established compiled world-volume hierarchy are intentionally different compatibility surfaces:

~~~text
native authorship:
World -> Province -> Cluster -> Island -> SkyIslandDescriptor

compiled realization:
archipelago -> group -> member -> SkyIslandWorldVolume
~~~

AUTH-0001 deliberately kept authored island identity free of placement coordinates, while the accepted world-volume path predates the native authorship lane and carries concrete compiled placement.

No accepted contract currently states which one native-authored island is realized by which one compiled world volume.

A world-space material sampler must not infer that association from:

- list position;
- geometry seed;
- nominal radius;
- morphology family;
- nearest center;
- encounter order;
- backend chunk ownership.

Therefore the next milestone should first make the association explicit, deterministic, immutable, and backend-neutral.

Only after that association is accepted should a following milestone define the world-space sampling bridge:

~~~text
world point
    -> associated authored island + compiled volume
    -> island-local horizontal position
    -> authoritative semantic depth
    -> AUTH-0044 realization
    -> AUTH-0045 application key
~~~

That later bridge should still stop before concrete backend material identity or block placement.
