# ADR-0028: Explicit Morphology Provider Contract

- **Status:** Proposed pending implementation and local acceptance
- **Date:** 2026-08-29
- **Work item:** SF-IMP-0024

## Context

SF-IMP-0022 and SF-IMP-0023 prove that Skyforge can continuously hybridize the five accepted built-in primary morphology families and can blend their family-aware secondary geography without losing closure, topology, deterministic identity, or visual coherence.

Those work items also expose the structural information composition actually needs. A morphology is not usefully extensible if a third party can only return an opaque final density field: hybridization and enrichment need access to the provider's shared footprint residual, directional frame, primary upper/depth factors, and—when supported—an organized secondary-morphology factor.

The existing `SkyIslandPrimaryMorphologyProvider` is package-internal and intentionally hard-wired to built-in families. It was a seam, not an ABI.

The project now needs an explicit provider contract so future mods, authored extensions, and eventually data-driven definitions can introduce morphology types that are not members of the built-in enum. The same contract will later be consumed by island-chain/group composition.

## Decision direction

SF-IMP-0024 introduces a public recipe-layer provider SPI with no global mutable registry.

A provider has a stable namespaced `MorphologyProviderId` such as `skyforge:massif` or `example:crescent`.

A provider compiles a `PrimaryMorphologyContribution` containing:

- the provider's accepted/validated `CompiledSkyIslandVolume` primary body;
- explicit node handles for the shared signed footprint residual;
- normalized along/across directional fields;
- an optional lobe-directional field when the provider exposes one;
- the positive primary upper-profile factor;
- the positive underside-depth factor.

The explicit handles decouple composition from hard-coded node names. Built-in providers may continue to use the existing canonical names, while external providers may use any graph-local identifiers they choose.

A provider may additionally compile a `SecondaryMorphologyContribution` containing a two-dimensional multiplicative factor graph plus a declared finite positive minimum/maximum envelope. Providers that do not yet define organized secondary geography may return no secondary contribution; later composition may use a neutral factor of one for that provider.

## Registry model

Provider registration is explicit and immutable after construction. `SkyIslandMorphologyProviderRegistry` is built from a set of providers and resolves by stable provider ID.

The registry:

- rejects duplicate IDs;
- exposes IDs in canonical sorted order independent of registration order;
- performs no implicit classpath scanning;
- contains no process-global mutable state.

Plugin/loader integration is deferred. A future Minecraft or application layer can discover providers and construct a registry without changing the deterministic recipe contract.

## Built-in compatibility

All five accepted built-in families receive provider adapters using IDs:

- `skyforge:massif`
- `skyforge:tableland`
- `skyforge:spine`
- `skyforge:basin`
- `skyforge:lobed`

The built-in provider primary output must remain graph-byte-identical to the accepted SF-IMP-0018 primary family recipe for the same descriptor and family.

The built-in secondary contribution must be derived from the accepted SF-IMP-0020 family-aware carrier rather than re-implementing its formulas. Its factor graph must use the accepted analytical positive envelope.

## Provider validation boundary

The provider SPI does not trust declarations blindly. Contribution constructors validate basic structural contracts immediately:

- referenced structural nodes exist in the supplied graphs;
- required graph dimensionality is correct;
- secondary factor bounds are finite, strictly positive, and ordered;
- provider IDs are canonical and filesystem/provenance-safe.

Finite closure, connectedness, domain clearance, and other geometric properties remain evidence/acceptance responsibilities. Later registry policies may attach certification metadata, but SF-IMP-0024 will not pretend topology can be proven from an interface declaration alone.

## Acceptance requirements

SF-IMP-0024 must demonstrate:

1. stable namespaced provider identifiers and deterministic ordering;
2. duplicate provider registration is rejected;
3. all five built-ins are available through the public registry;
4. built-in provider primary graphs are byte-identical to accepted SF-IMP-0018 primary graphs across the established seed corpus;
5. built-in structural handles resolve successfully and identify composition-relevant fields without hard-coded consumer names;
6. built-in secondary factors come from the accepted SF-IMP-0020 carrier and retain their accepted positive envelopes;
7. a provider not represented in `MorphologyFamily` can be registered and resolved;
8. malformed contributions and invalid secondary envelopes fail early;
9. existing SF-IMP-0022/SF-IMP-0023 APIs and accepted graph outputs remain unchanged.

## Next proof after the contract

Once this SPI is accepted, the next provider work item should refactor hybrid composition to accept provider IDs/registry entries directly and prove at least one genuinely non-built-in morphology can hybridize and enrich with a built-in provider. That will be the bridge from provider registration to mixed user-authored island chains and groups.

## Deferred work

SF-IMP-0024 does not yet:

- define classpath/service-loader discovery;
- define Minecraft/NeoForge registration hooks;
- define data-authored morphology JSON or a morphology DSL;
- guarantee arbitrary provider topology from declarations alone;
- promote provider selection into descriptor schema 3;
- place multiple islands into chains, groups, provinces, or archipelagos.
