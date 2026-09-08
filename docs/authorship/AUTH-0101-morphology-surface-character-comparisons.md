# AUTH-0101 — Morphology surface-character comparative evidence

## Purpose

SF-IMP-0083 / PR #358 has reached its required human morphology gate with machine-green exact
Minecraft carriers.

Two open consumers now require direct comparison rather than isolated per-specimen measurements:

- issue #267 asks whether Massif's repeated rises/falls persist across physical scale strongly enough
  to justify morphology tuning;
- issue #283 asks whether Tableland is consistently distinct from Massif rather than merely differing
  in one reviewed specimen.

AUTH-0095 already supplies the necessary threshold-free per-specimen measurements over the exact
AUTH-0083 built-in corpus. It intentionally does not assemble those values into the exact comparisons
now requested by the human gate.

AUTH-0101 adds only that missing comparison layer.

It does **not** change morphology and it does **not** decide either issue.

## Exact source boundary

AUTH-0101 reuses the exact AUTH-0095 diagnostics generated in the existing AUTH-0083 production
morphology visual-review pass.

No new:

- morphology specimen;
- seed;
- scale;
- sampling grid;
- metric;
- random source;
- Minecraft carrier

is introduced.

Every comparison is a signed difference between two exact accepted AUTH-0095 profiles:

```text
delta = candidate - baseline
```

The sign is descriptive only.

## Fixed comparison set

The first comparison file contains exactly four consumer-requested rows.

### #283 — Massif versus Tableland across canonical MEDIUM seeds

```text
Massif MEDIUM seed-min      -> Tableland MEDIUM seed-min
Massif MEDIUM seed-zero     -> Tableland MEDIUM seed-zero
Massif MEDIUM seed-skyforge -> Tableland MEDIUM seed-skyforge
```

These rows expose whether the already-accepted surface-character measurements differ consistently or
variably across the exact deterministic seed set.

They do not declare which family is better, flatter, more traversable, more plateau-like, or more
aesthetically successful.

### #267 — Massif scale comparison at canonical Skyforge seed

```text
Massif MEDIUM seed-skyforge -> Massif LARGE seed-skyforge
```

AUTH-0095 already proves normalized scale covariance. AUTH-0101 makes that consequence explicit at the
consumer boundary: normalized authored surface-character deltas should collapse to numerical
tolerance.

Therefore, if the human Minecraft review perceives a meaningful MEDIUM-versus-LARGE traversal change
while AUTH-0101 remains scale-covariant, that difference belongs to physical/block-space realization
or player-scale perception rather than a change in normalized Authorship morphology intent.

AUTH-0101 does not decide whether such a perceived change is desirable.

## Compared measurements

For every fixed pair, AUTH-0101 exposes candidate-minus-baseline deltas for every normalized AUTH-0095
surface-character measurement:

- global upper relief range;
- upper elevation standard deviation;
- gradient P50 / P75 / P90 / P95;
- curvature P50 / P75 / P90 / P95;
- fixed-lag mean differences at R/24, R/12, R/6, R/3;
- 3x3 local-window median / P90 range;
- 5x5 local-window median / P90 range;
- 9x9 local-window median / P90 range.

No measurements are combined into a composite score.

## Evidence integration

The existing production morphology visual-review generator now additionally emits:

```text
production-morphology-visual-review-v1/surface-character-comparisons.csv
```

The file is emitted from the same in-memory exact member results that generate AUTH-0095
`surface-character.csv`.

This avoids a second 41-member corpus pass and prevents comparison evidence from drifting onto a
different specimen matrix or sampling resolution.

## Ownership boundaries

Authorship owns:

- exact deterministic signed comparison evidence;
- later morphology tuning only if the human/Implementation evidence demonstrates a concrete
  authored-intent defect.

Implementation owns:

- Minecraft block-space realization;
- physical traversal consequences;
- carrier/persistence/runtime correctness.

Human review under #214/#267/#283 owns:

- whether Massif traversal feels acceptable;
- whether Tableland reads sufficiently differently from Massif;
- whether any observed difference warrants tuning.

## Explicit non-goals

AUTH-0101 defines no:

- lumpiness score;
- walkability threshold;
- slope limit;
- plateau/bench classifier;
- family pass/fail;
- aesthetic score;
- better/worse ranking;
- retuned morphology parameter;
- Minecraft block-step interpretation.

## Acceptance

Reject AUTH-0101 if:

- it generates any specimen independently of the accepted AUTH-0083/AUTH-0095 pass;
- its comparison member IDs differ from the fixed #267/#283 consumers above;
- any delta is not exactly candidate minus baseline;
- normalized Massif MEDIUM/LARGE differences exceed accepted numerical scale-covariance tolerance;
- a composite score, threshold, family verdict, or tuning change enters code;
- Minecraft/NeoForge ontology enters the reference comparison contract.

## Downstream decision

AUTH-0101 is evidence for the already-open human gate, not a substitute for it.

After acceptance:

1. attach the compact comparison evidence to #267 and #283;
2. perform the seven-world human review already prepared by SF-IMP-0083;
3. if human findings and comparison evidence identify a concrete authored-intent defect, open a narrow
   morphology tuning milestone;
4. otherwise accept SF-IMP-0083 without changing morphology.
