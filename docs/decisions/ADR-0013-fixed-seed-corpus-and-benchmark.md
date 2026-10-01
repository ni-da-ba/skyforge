# ADR-0013: Fixed-seed evidence corpus and reference benchmark v1

**Status:** Accepted  
**Date:** 2026-08-03  
**Ticket:** SF-IMP-0009

## Context

The signal-free island, stable seed derivation, bounded planar signal, and seeded identity gate have
all passed independently. Sprint One still requires one durable end-to-end corpus that proves the
complete fixed seed suite at the standard 1024 by 1024 resolution, makes its images easy to review,
and records the cost of the deliberately clear reference evaluator before optimization begins.

The benchmark must not turn incidental hardware performance into a semantic acceptance rule.
Likewise, review images must not replace the exact grids, graphs, and measurements that define the
accepted result.

## Corpus decision

`fixed-seed-island-v1` contains the six full-amplitude seeds accepted in ADR-0012, in this stable
order:

1. `Long.MIN_VALUE` (`seed-min`)
2. `-1` (`seed-negative-one`)
3. `0` (`seed-zero`)
4. `1` (`seed-one`)
5. `0x534b59464f524745` (`seed-skyforge`)
6. `Long.MAX_VALUE` (`seed-max`)

Every member uses the canonical signal-free descriptor controls with signal amplitude `1.0` and is
sampled on the standard inclusive 1024 by 1024 square. Each member emits the complete evidence
package defined by ADR-0010.

The golden corpus pins 49 paths: one deterministic corpus manifest plus, for each member, its
descriptor, height graph, density graph, height grid, land-mask grid, slope grid, and both exact
cross-sections. The corpus manifest also records exact hexadecimal statistics and morphology
metrics, so its hash closes the statistics and identity contract. Any missing, additional, or
changed canonical path fails verification.

PNG files remain review projections and are excluded from the golden path set for the cross-JDK
reason recorded by ADR-0010. The generated HTML atlas presents every member's height, mask, slope,
and two center cross-sections side by side. It is an explainability surface, not normative data.

## Benchmark decision

The reference benchmark times one forward evaluation of each member's canonical height grid. It
records sample count, wall-clock nanoseconds, and samples per second per member and in aggregate.
Sampling is timed separately from slope derivation, metrics, PNG encoding, filesystem writes, and
checksum verification.

The report records Java version and vendor, VM, operating system and architecture, available
processors, maximum heap, engine version, and the exact measurement method. It deliberately uses no
performance threshold and does not enter the golden checksum set. Later evaluators may compare
against it only after proving raw-bit differential identity with the reference evaluator.

## Reproduction and publication

`./gradlew :skyforge-reference:fixedSeedCorpus` regenerates all six packages, the corpus manifest,
benchmark report, checksum listing, and visual atlas, then fails if any canonical hash differs from
the checked-in v1 golden resource.

Java 25 CI executes that command from a clean checkout in addition to the ordinary test suite and
publishes the complete generated directory as a workflow artifact. This artifact is the reviewable
record for that run; the checked-in checksum resource and corpus manifest rules are the durable
normative contract.

## Consequences

- Sprint One has one end-to-end command that checks semantic identity and exact numerical drift.
- Every accepted seed is inspectable visually and through canonical graphs and measurements.
- Benchmark numbers can inform optimization without becoming an arbitrary compatibility promise.
- Future corpus changes require a new version or an explicit decision explaining every drift.
- Minecraft, NeoForge, caching, graph optimization, and secondary morphology remain outside this
  ticket.
