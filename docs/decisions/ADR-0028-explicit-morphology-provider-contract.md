# ADR-0028: Explicit Morphology Provider Contract

- **Status:** Implemented through provider-neutral hybrid proof; local acceptance pending
- **Date:** 2026-08-29
- **Work item:** SF-IMP-0024

## Context

SF-IMP-0022 and SF-IMP-0023 prove that Skyforge can continuously hybridize the five accepted built-in primary morphology families and can blend their family-aware secondary geography without losing closure, topology, deterministic identity, or visual coherence.

Those work items also expose the structural information composition actually needs. A morphology is not usefully extensible if a third party can only return an opaque final density field: hybridization and enrichment need access to the provider's shared footprint residual, directional frame, primary upper/depth factors, and—when supported—an organized secondary-morphology factor.

The existing `SkyIslandPrimaryMorphologyProvider` is package-internal and intentionally hard-wired to built-in families. It was a seam, not an ABI.

The project now needs an explicit provider contract so future mods, authored extensions, and eventually data-driven definitions can introduce morphology types that are not members of the built-in enum. The same contract will later be consumed by island-chain/group composition.

## Decision

SF-IMP-0024 introduces a public recipe-layer provider SPI with no global mutable registry.

A provider has a stable namespaced `MorphologyProviderId` such as `skyforge:massif` or `reference:crescent`.

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

The built-in secondary contribution is derived from the accepted SF-IMP-0020 family-aware carrier rather than re-implementing its formulas. Its factor graph retains the accepted analytical positive envelope.

## Provider-neutral hybrid composition

The work item also proves that the SPI is sufficient for real composition rather than stopping at registration.

`MorphologyProviderBlend` supplies a canonical weighted pair of provider IDs using the same decimal-complement identity discipline introduced for built-in hybrids in SF-IMP-0023.

`ProviderHybridMorphologySkyIslandVolumeRecipe`:

1. resolves both providers from an explicit immutable registry;
2. compiles each provider's `PrimaryMorphologyContribution`;
3. namespaces the provider graphs without assuming provider-local node names;
4. blends the declared footprint residual, along/across frame, upper-profile factor, and underside-depth factor;
5. rebuilds upper and underside from one shared blended footprint;
6. rebuilds density as their exact intersection.

The compiler does not inspect or switch on `MorphologyFamily`. Endpoint weights delegate to the selected provider's exact primary graph bytes.

## Genuine non-built-in proof provider

`skyforge-reference` implements `ReferenceCrescentMorphologyProvider` with ID `reference:crescent`. It is deliberately outside the recipes module and does not delegate primary geometry to any built-in family.

Its primary footprint bends the normalized transverse coordinate quadratically as longitudinal position moves away from the center, producing a simply connected crescent/boomerang-like body. The transform is intentionally hole-free so this provider can test visible non-enum morphology without weakening the current connected-volume invariant.

The reference provider also supplies its own optional positive secondary ridge factor, demonstrating that a consumer can implement both halves of the public SPI.

## Provider validation boundary

The provider SPI does not trust declarations blindly. Contribution constructors validate basic structural contracts immediately:

- referenced structural nodes exist in the supplied graphs;
- required graph dimensionality is correct;
- secondary factor bounds are finite, strictly positive, and ordered;
- provider IDs are canonical and provenance-safe.

Finite closure, connectedness, domain clearance, and other geometric properties remain evidence/acceptance responsibilities. Later registry policies may attach certification metadata, but SF-IMP-0024 does not pretend topology can be proven from an interface declaration alone.

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
9. existing SF-IMP-0022/SF-IMP-0023 APIs and accepted graph outputs remain unchanged;
10. a genuinely non-built-in provider implemented by the reference module produces a finite connected suspended primary volume across all three established seeds;
11. that custom provider hybridizes with every accepted built-in family without enum knowledge in the hybrid compiler;
12. provider-hybrid endpoints preserve exact provider primary graph bytes and reversed decimal weights preserve canonical graph identity;
13. every custom↔built-in midpoint preserves one exact shared upper/underside footprint, one connected component, zero face contacts, and at least 48 world units sampled clearance on the canonical domain.

## Acceptance corpus

The first full-resolution custom-provider corpus uses:

- standalone `reference:crescent` at all three established seeds;
- `reference:crescent` midpoint-hybridized with each of the five accepted built-in providers at all three established seeds.

This yields 18 full-resolution provider specimens before visual progression review. A later visual atlas will use the stable Skyforge seed and 25/50/75-percent blend weights for each custom↔built-in pairing.

## Architectural implication for island groups

Island-chain/group generation should depend on stable provider identities and registry snapshots rather than built-in enums. A future chain entry can therefore select:

- one built-in provider;
- one registered custom provider;
- or a provider-composition/hybrid specification.

This keeps group placement orthogonal to the morphology implementation that generated each member island.

## Deferred work

SF-IMP-0024 does not yet:

- define classpath/service-loader discovery;
- define Minecraft/NeoForge registration hooks;
- define data-authored morphology JSON or a morphology DSL;
- guarantee arbitrary provider topology from declarations alone;
- promote provider selection into descriptor schema 3;
- place multiple islands into chains, groups, provinces, or archipelagos;
- apply blended secondary morphology across custom↔built-in hybrids (the SPI exposes the required contribution, but enriched provider-hybrid composition remains the next composition step).
