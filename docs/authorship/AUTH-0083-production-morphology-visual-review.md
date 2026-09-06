# AUTH-0083 — Production morphology visual-quality review corpus

## Purpose

AUTH-0083 is the first authorship work item after the 2026-09-06 program audit ended the recursive
publication/provenance tranche at AUTH-0081.

Its concrete downstream consumer is **visual-quality issue #214** and the Minecraft Implementation
Agent.

AUTH-0083 does not add a new semantic-core protocol. It exercises the existing production
provider-neutral morphology compiler and creates deterministic reference specimens that can be
rendered both by the backend-neutral reference harness and by Minecraft.

The question is practical:

> Does the accepted morphology architecture produce islands that are visually legible, varied,
> coherent above and below, and suitable for production Minecraft realization?

## Why this gate exists

The current technical Minecraft showcase deliberately used primitive showcase terrain and therefore
did not represent the complete production morphology stack.

That showcase nevertheless established an important program fact:

> semantic authorship is ahead of player-visible realization.

Earlier morphology ADRs already prove:

- five primary families;
- bounded local detail;
- organized secondary upper geography;
- family-aware upper secondary geography;
- pairwise hybrids;
- provider composition;
- multi-island hierarchy.

They also establish the present underside caveat: family-aware secondary morphology changes the
upper surface while preserving the accepted detailed underside. Issue #214 therefore needs evidence
before deciding whether primary-family underside form + bounded detail is sufficient.

## Concrete specimen matrix

AUTH-0083 defines exactly **41 isolated semantic specimens**.

### Built-in families — 25

Every built-in family:

- Massif;
- Tableland;
- Spine;
- Basin;
- Lobed;

is reviewed at the canonical medium scale with three deterministic seeds:

- `Long.MIN_VALUE`;
- `0`;
- the canonical Skyforge seed.

Each family is additionally reviewed at:

- small scale;
- large scale;

using the canonical Skyforge seed.

This gives each built-in family three seeds and three physical scales without generating every
possible seed/scale cross-product.

### Built-in hybrids — 10

Every unordered pair of the five built-in families is reviewed at:

- 50/50 canonical provider blend;
- full bounded detail;
- full secondary morphology;
- medium scale;
- canonical Skyforge seed.

The midpoint is selected because it is the strongest test of whether both parent vocabularies remain
legible without one trivially dominating.

### External-provider axis — 6

The accepted `reference:crescent` proof provider is used as the external-provider axis:

- one standalone crescent endpoint;
- one 50/50 crescent-to-built-in blend for every built-in family.

This is a production-SPI review axis, not a proposal that the reference crescent belongs in the
shipping game.

## Production compilation path

Every member compiles through:

- `SkyIslandMorphologySpec`;
- `SkyIslandMorphologySpecCompiler`;
- `SkyIslandMorphologyProviderRegistry`.

AUTH-0083 deliberately does not call old family-specific proof recipes directly.

The corpus therefore exercises the provider-neutral morphology path used by planned islands.

## Reference views

The reference atlas generates for each specimen:

1. suspension-plane planform/silhouette;
2. upper-surface elevation;
3. dedicated underside elevation;
4. isometric occupancy;
5. east-west section;
6. north-south section.

These reference views are diagnostic, not substitutes for Minecraft player-view review.

## Minecraft handoff

The generated `minecraft-handoff.csv` gives Implementation the same deterministic member IDs,
scale, seed, and morphology spec.

For every selected specimen Minecraft review requires:

- above view;
- horizon/approach view;
- below view;
- short orbit-and-underside spectator/aircraft route.

The Implementation Agent should preserve the member ID in screenshot/video evidence so reference and
Minecraft artifacts can be compared specimen-for-specimen.

## Measured diagnostics — no thresholds yet

AUTH-0083 records topology metrics already used by suspended-volume evidence plus new
scale-normalized diagnostics:

- occupied horizontal columns;
- minimum / fifth-percentile / mean / maximum thickness;
- maximum neighboring thickness jump;
- mean upper neighboring elevation difference;
- mean underside neighboring elevation difference;
- mean upper second difference;
- mean underside second difference;
- half-turn occupancy mismatch;
- half-turn thickness difference;
- upper-surface / underside-depth Pearson correlation.

These metrics are intended to expose candidate pathologies such as:

- pinched thin shelves;
- extreme local thickness discontinuity;
- high-frequency roughness;
- excessive repetition/symmetry;
- top/underside lockstep.

**AUTH-0083 does not encode aesthetic rejection thresholds.**

Thresholds must be chosen only after correlating measurements with human review of the reference and
Minecraft atlases. This follows issue #214's requirement that thresholds be evidence-driven rather
than invented upfront.

Disconnected solid components and domain-face clipping remain existing hard topology failures and
do not need aesthetic calibration.

## Macro / meso / micro review

Human review must explicitly separate:

### Macro

- distant family identity;
- overall mass and silhouette;
- asymmetry;
- top/underside relationship.

### Meso

- ridges;
- basins;
- shelves;
- terraces;
- rims;
- valleys;
- major secondary forms.

### Micro

- bounded local roughness;
- cliff breakup;
- small relief.

Micro variation may improve surfaces but cannot compensate for missing macro or mesoscale geography.

## Underside decision gate

AUTH-0083 does **not** add an underside-secondary vocabulary.

After reference and Minecraft review, answer:

> Is primary-family morphology + existing bounded underside detail sufficient for production
> quality?

If yes:

- retain the simpler underside architecture;
- tune realization/material/cave interaction instead of adding grammar.

If no:

- the next morphology authorship item may define an explicit secondary underside grammar;
- candidate tendencies include keel, buttress, shelf, scarp, terrace, deep taper, hanging spur, and
  fractured apron;
- any such tendency must correlate with primary family, scale, lithology/structural character,
  upper relief, or local thickness where useful;
- top and underside must continue to read as one geological object.

The human atlas is the evidence required to choose between those outcomes.

## Regional-context phase

AUTH-0083 intentionally isolates single-island form.

Issue #214 also requires sparse, cluster, chain, Hub, and Arc contexts. Those test a different failure
class:

- neighboring-island repetition;
- regional silhouette hierarchy;
- family distribution;
- spacing and layering;
- visual roofing/occlusion;
- multi-island approach routes.

The next #214 phase should reuse the accepted group/archipelago planners and exact AUTH-0083
specimen identities where appropriate rather than folding regional composition into this isolated
corpus.

## Acceptance

AUTH-0083 is acceptable when:

1. the exact 41-member matrix is deterministic and complete;
2. all members compile through the production morphology-spec compiler;
3. all members use full detail and full secondary morphology;
4. the atlas generator emits reference views, topology metrics, diagnostics, and Minecraft handoff;
5. no aesthetic threshold is hard-coded before human evidence;
6. no Minecraft/NeoForge type enters semantic or recipe layers;
7. issue #214 can use the exact IDs to compare semantic and Minecraft realization.

Human visual approval of the morphology itself is **not** required to merge the corpus machinery.
That review is the consumer of AUTH-0083 and determines the next authorship change.

## Program direction after AUTH-0083

Do not return to generic publication recursion.

Near-term world-semantic priorities after the #214 morphology decision are:

1. regional morphology/context review;
2. hydrology realization intent, especially fluid-role admissibility;
3. geology-to-visible-palette expression;
4. ecology/geography coupling;
5. deterministic Bootstrap Province convergence.
