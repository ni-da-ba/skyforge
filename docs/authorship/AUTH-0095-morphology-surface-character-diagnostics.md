# AUTH-0095 — Morphology surface-character diagnostics

## Purpose

SF-IMP-0083 / issue #284 is realizing the remaining built-in AUTH-0083 seed/scale matrix in
Minecraft. Human follow-ups #267 and #283 ask whether:

- Massif's repeated rises/falls are a systemic traversal-quality problem or one specimen;
- Tableland consistently expresses broader tables/benches than Massif.

AUTH-0083 already measures mean upper neighboring elevation difference and mean upper second
difference. Those means are useful but insufficient for these consumers: the same mean can arise from
many short-period undulations or from a smaller number of coherent ridges and transitions.

AUTH-0095 adds only the missing descriptive measurement resolution.

It does **not** tune any morphology family and it does **not** define aesthetic or traversal
thresholds.

## Exact source boundary

AUTH-0095 consumes the exact built-in portion of the accepted AUTH-0083 production morphology
visual-review corpus:

- five built-in families;
- three deterministic MEDIUM seeds per family;
- SMALL and LARGE at the canonical Skyforge seed;
- 25 exact member IDs total.

Hybrids and the external-provider axis remain part of AUTH-0083 but are outside AUTH-0095 because the
concrete SF-IMP-0083 consumer currently owns only the built-in seed/scale matrix.

Every specimen continues to compile through the accepted provider-neutral production morphology
compiler.

## Canonical normalized sampling

AUTH-0083's review grid spans:

```text
X,Z = [-2R, +2R]
97 samples per horizontal axis
```

Therefore the adjacent horizontal sample spacing is exactly:

```text
4R / 96 = R / 24
```

for SMALL, MEDIUM, and LARGE.

AUTH-0095 reuses this exact grid. It introduces no competing sampling resolution.

## Threshold-free measurements

For each exact built-in specimen AUTH-0095 records:

### Global upper relief

- upper relief range / nominal radius;
- upper elevation standard deviation / nominal radius.

### Local gradient distribution

At occupied interior samples with all four cardinal neighbors occupied, central differences produce a
dimensionless rise/run gradient magnitude.

AUTH-0095 records deterministic rank quantiles:

- P50;
- P75;
- P90;
- P95.

These are measurements, not walkability limits.

### Local curvature distribution

Using the same occupied interior samples, AUTH-0095 measures the absolute horizontal Laplacian of the
upper surface multiplied by nominal radius. The result is dimensionless under uniform scale.

AUTH-0095 records P50 / P75 / P90 / P95.

This is a local surface-change diagnostic, not a cliff or lumpiness class.

### Fixed normalized lag spectrum

Mean absolute upper-elevation difference is measured at grid lags:

- 1 = R/24;
- 2 = R/12;
- 4 = R/6;
- 8 = R/3.

Every value is divided by nominal radius.

The resulting spectrum helps distinguish short-period relief from relief expressed across larger
horizontal spans without inventing a frequency or roughness threshold.

### Complete local-window relief

For occupied centers whose complete square neighborhood remains inside the island, AUTH-0095 measures
maximum minus minimum upper elevation over:

- 3x3 samples — full span R/12;
- 5x5 samples — full span R/6;
- 9x9 samples — full span R/3.

For each window scale AUTH-0095 records median and P90 range / nominal radius.

These distributions can expose whether broad areas remain locally coherent, but AUTH-0095 does not
label them benches, plateaus, terraces, or traversable surfaces.

## Quantile convention

Quantiles use the same deterministic rank convention already present in reference evidence:

```text
index = floor(q * (N - 1))
```

after ascending sort.

No interpolation or random sampling is introduced.

## Ownership boundaries

Authorship owns:

- deterministic backend-neutral morphology diagnostics;
- correlation of those diagnostics with accepted morphology intent;
- any later evidence-backed morphology-family tuning.

Implementation owns:

- exact Minecraft carriers for SF-IMP-0083;
- block-space slopes and traversal consequences;
- persistence and client/runtime evidence.

Human review under #214/#267/#283 owns the actual judgment that a morphology is attractive,
distinctive, or pleasant to traverse.

AUTH-0095 therefore emits no:

- walkable/not-walkable class;
- slope limit;
- plateau/bench threshold;
- lumpiness score with a pass/fail boundary;
- Minecraft block-step interpretation;
- family retuning.

## Evidence integration

The AUTH-0083 generator already computes every exact production specimen. AUTH-0095 reuses the same
generated `SuspendedVolumeEvidence` in-memory and adds:

```text
production-morphology-visual-review-v1/surface-character.csv
```

This avoids a second expensive 25-member volume-generation pass and guarantees that AUTH-0095
measurements refer to the exact same reference specimens used by AUTH-0083 and the Minecraft handoff.

## Acceptance

Reject AUTH-0095 if:

- it invents another specimen matrix or sampling resolution;
- any built-in AUTH-0083 member is missing or substituted;
- normalized measurements fail uniform scale covariance;
- gradient/curvature quantiles are non-finite or unordered;
- a walkability/plateau/lumpiness/aesthetic threshold enters code;
- Minecraft/NeoForge ontology enters the reference or semantic contract;
- evidence is generated independently from the exact AUTH-0083 specimen generation path.

## Downstream decision

AUTH-0095 does not predetermine #267 or #283.

After SF-IMP-0083 supplies the matching Minecraft matrix, compare:

1. within-family seed variation;
2. same-family physical scale variation;
3. Massif versus Tableland;
4. measured surface-character distributions versus human traversal/visual judgment.

Only then should Authorship decide whether a morphology tuning milestone is justified.
