# ADR-0025: Descriptor Schema 2 Semantic Morphology Controls

- **Status:** Accepted
- **Date:** 2026-08-28
- **Accepted:** 2026-08-28
- **Work item:** SF-IMP-0021

## Context

SF-IMP-0018 through SF-IMP-0020 establish five stable built-in primary morphology families and prove that bounded detail plus family-aware secondary morphology can preserve their topology and semantic identity. The remaining architectural mismatch is that morphology family selection and secondary-relief strength still live in recipe-layer APIs rather than the semantic model.

The current `SkyIslandVolumeDescriptor` schema 1 is already part of accepted evidence. Its constructor shape, validation behavior, descriptor JSON, and all existing generated artifacts must remain reproducible.

The built-in family vocabulary must also remain extensible in principle. Massif, Tableland, Spine, Basin, and Lobed are the first accepted semantic families, not a claim that all future sky islands must belong to one of five permanent categories. Future work may add additional built-ins, hybrid families, or user-defined/custom morphology providers. SF-IMP-0021 therefore promotes only the stable semantic controls needed by the current engine and does not attempt to design the future custom-provider ABI.

## Decision

Descriptor schema 2 extends the existing `SkyIslandVolumeDescriptor` lineage with:

- one model-layer built-in morphology-family value;
- independent bounded-detail amplitude;
- independent family-aware secondary-morphology amplitude.

The existing schema-1 `signalAmplitude` field remains the stored local-detail amplitude for source compatibility. Schema 2 exposes a semantic alias `detailAmplitude()` and adds `secondaryMorphologyAmplitude` as a separate control.

The schema-1 constructor remains available with its existing argument list. It creates a schema-1 descriptor with no semantic family and with the historical coupled secondary amplitude equal to `signalAmplitude`. Schema-1 descriptor JSON remains byte-identical to the accepted format.

Schema 2 requires a built-in family and validates both amplitudes in `[0,1]`. Recipe version 7 compiles schema 2 directly, selects the corresponding accepted family automatically, applies bounded detail at `detailAmplitude`, and applies family-aware secondary morphology at `secondaryMorphologyAmplitude`.

Recipe version 7 is implemented as a semantic adapter over the accepted SF-IMP-0020 graph construction. It compiles a full-amplitude carrier and rewrites only the named local-detail and secondary-morphology amplitude constants. The graph topology and accepted morphology formulas therefore remain unchanged.

## Extensibility boundary

The model-layer built-in family enum is intentionally described as a built-in vocabulary. It is not the future extension point for arbitrary user-authored island types.

A future extension mechanism may use named morphology providers, registries, composition graphs, data-driven definitions, or another explicit plugin boundary. That mechanism must be designed separately so user-defined morphology does not require editing a closed enum or weakening deterministic provenance. Schema 2 therefore does not add a free-form `customType` string, provider class name, graph reference, or backend-specific identifier.

## Compatibility requirements

SF-IMP-0021 preserves:

1. all schema-1 constructor call sites without source edits;
2. schema-1 validation and ridge-azimuth canonicalization;
3. schema-1 descriptor JSON byte identity;
4. all existing recipe outputs when invoked through schema-1 APIs;
5. graph schemas 1 through 3;
6. all v0.1 and v0.2 accepted golden artifacts.

Schema 2 additionally proves:

1. family selection is carried in the semantic descriptor rather than a recipe argument;
2. all five built-in families compile through the same descriptor-driven API;
3. detail amplitude changes the bounded detail layer without changing selected family semantics;
4. secondary-morphology amplitude changes the family-aware upper-relief layer without changing the accepted underside detail layer;
5. zero secondary amplitude preserves the selected primary plus local detail without family-aware upper relief;
6. zero detail amplitude preserves the signal-free underside while still allowing family-aware upper relief;
7. same descriptor values and seed remain deterministic;
8. topology and footprint invariants remain unchanged across the accepted family matrix.

## Local acceptance record

On 2026-08-28, `scripts\verify-sf-imp-0021.bat` completed successfully on Java 25.

The dedicated verifier covers:

- schema-1 compatibility and schema-2 model validation;
- canonical descriptor serialization, including exact schema-1 byte preservation;
- recipe-version-7 differential identity against the accepted SF-IMP-0020 full-amplitude graphs for every built-in family;
- independent detail-only and secondary-only control behavior;
- deterministic compilation;
- the fifteen-member full-resolution semantic-control matrix.

The full-resolution matrix evaluates all five built-in families across three independent control modes: secondary-only, detail-only, and mixed amplitudes. Every specimen is required to retain positive occupancy, exactly one face-connected solid component, zero domain-face contacts, at least 48 world units of sampled clearance, and the accepted SF-IMP-0018 primary-footprint sign.

A repository-wide `gradlew.bat check` also completed successfully on the exact PR head submitted for merge. PR #21 was then merged into `main` without any file-content delta beyond the merge commit. SF-IMP-0021 is therefore accepted as the descriptor-schema-2 semantic morphology-control boundary.

## Deferred work

SF-IMP-0021 does not define:

- custom/user-authored morphology providers;
- family hybridization;
- runtime registration or plugin discovery;
- arbitrary morphology graphs in descriptors;
- backward parsing of unknown future built-in family identifiers;
- Minecraft/NeoForge realization.

Those are intentionally separate architectural problems.
