# ADR-0026: Primary Morphology Hybridization and Provider Seam

- **Status:** Proposed pending local numerical and visual acceptance
- **Date:** 2026-08-28
- **Work item:** SF-IMP-0022

## Context

SF-IMP-0018 through SF-IMP-0021 establish five accepted built-in sky-island morphology families,
family-aware secondary geography, and descriptor schema 2. The built-in family vocabulary is useful
but intentionally not exhaustive. Future island chains and groups should be able to contain both
controlled blends of accepted built-ins and eventually explicit user/mod-defined morphology.

Those are related but distinct capabilities. Hybridization can be proven now using accepted geometry.
A public custom-provider ABI should not be frozen until Skyforge has evidence for what a morphology
provider must expose and which invariants the engine can enforce.

## Decision direction

SF-IMP-0022 proves **primary morphology hybridization** at the recipe layer before adding another
semantic descriptor schema.

A hybrid contains two distinct accepted built-in primary families and one canonical blend weight in
`[0,1]`. The pair is canonicalized by stable family identifier so `(A,B,w)` and `(B,A,1-w)` represent
the same hybrid request.

The first hybrid construction does not average two finished solids. Instead it namespaces the two
accepted parent primary graphs and blends their structural fields:

- shared signed footprint residual;
- normalized directional frame;
- lobe-directional field;
- positive upper-family factor;
- positive underside-depth factor.

The hybrid then rebuilds its upper and underside from one shared blended signed footprint residual.
This preserves exact closure semantics: upper and underside meet on the same hybrid silhouette.

Because every accepted parent footprint residual decreases outward from the common center, a convex
blend of the two residuals is expected to remain star-shaped about that center. Sampled connectedness,
closure, and boundary clearance remain hard acceptance gates rather than assumptions.

## Compatibility boundary

Descriptor schema 2 is unchanged by SF-IMP-0022. Hybrid selection remains recipe-layer state while
its behavior is being proven, following the same evidence-first discipline used before schema-2 family
promotion.

Blend endpoints must reproduce the corresponding accepted parent surfaces numerically. Existing
built-in recipes and all schema-1/schema-2 APIs remain unchanged.

## Internal provider seam

SF-IMP-0022 also introduces a package-internal primary-morphology provider contract used by the hybrid
compiler to obtain accepted parent primary graphs. Built-in providers delegate to the existing
`MorphologyFamilySkyIslandVolumeRecipe`; the accepted family formulas are not duplicated.

This provider seam is deliberately **not** a public plugin ABI. Its purpose is to stop new composition
code from assuming that every morphology must forever come from one closed switch statement.

A later custom/provider work item may promote a stable provider contract with identifiers,
registration/discovery, deterministic provenance, capability declarations, and validation rules. It
must decide how providers prove or declare finite closure, common-rim semantics, determinism,
coordinate frames, enrichment compatibility, and topology expectations.

## Acceptance requirements

SF-IMP-0022 must demonstrate:

1. canonical pair ordering: `(A,B,w)` equals `(B,A,1-w)` as a hybrid specification;
2. endpoint weight `0` reproduces parent A numerically and weight `1` reproduces parent B numerically;
3. every midpoint hybrid differs materially from both parents in silhouette and/or vertical profile;
4. upper and underside use one exact shared hybrid footprint sign;
5. density remains the exact positive-inside intersection of hybrid upper and underside;
6. deterministic graph identity for the same descriptor, pair, and weight;
7. positive occupancy, exactly one face-connected component, zero domain-face contacts, and at least
   48 world units sampled clearance across the accepted hybrid corpus;
8. all ten unordered pairs of the five built-in families are exercised;
9. human review confirms that interpolation produces coherent intermediate suspended landforms rather
   than abrupt parent switching or obvious pathological geometry.

## Initial corpus

Numerical acceptance uses the ten unordered built-in family pairs at midpoint weight `0.5` across the
three established family root seeds: 30 full-resolution specimens on the canonical
`193 x 129 x 193` domain.

Endpoint and symmetry properties are tested separately at recipe/reference resolution. Human review
uses a lighter atlas with representative `0.25`, `0.50`, and `0.75` blends so progression between
parents can be inspected directly.

## Deferred work

SF-IMP-0022 does not yet:

- expose hybrids in descriptor schema 3;
- blend family-aware secondary-morphology vocabularies;
- define public custom morphology registration;
- load arbitrary morphology definitions from data files or mods;
- define island-chain placement, inter-island relationships, provinces, or archipelago generation;
- weaken the finite suspended-volume topology gates for exotic custom providers.

Those are subsequent consumers of a proven hybrid primary-morphology contract.
