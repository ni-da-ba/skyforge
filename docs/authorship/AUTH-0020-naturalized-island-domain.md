# AUTH-0020 — Naturalized Island Domain

AUTH-0020 begins the post-hydrology terrain-naturalization front by replacing the diagnostic assumption of a perfectly circular island footprint with an explicit morphology-aware naturalized semantic domain.

## Dependency

~~~text
SkyIslandDescriptor
    -> primary morphology identity
    -> AUTH-0020 naturalized domain envelope
    -> later migration of island-local semantic fields
    -> later terrain / geology / ecology / hydrology consumers
~~~

AUTH-0020 is deliberately an inspectable candidate layer. It does not yet rewrite the accepted AUTH-0002 through AUTH-0019 outputs.

## Why this layer exists

AUTH-0002's first-generation interiority field uses normalized Euclidean radius. That was appropriate when the objective was to establish deterministic semantic fields, but it means every diagnostic island owns an exactly circular top-down domain regardless of morphology.

The limitation is now visually and architecturally significant:

- SPINE is semantically elongated but still bounded by a circle;
- LOBED has internal radial lobes but no corresponding outer shoulders;
- MASSIF, TABLELAND, and BASIN receive only elevation differences inside the same circular ownership mask;
- later geology, ecology, structures, and terrain realization would inherit an unnecessarily synthetic boundary if this assumption became permanent.

AUTH-0020 gives island shape an explicit semantic home before those later systems deepen.

## Geometry

The naturalized domain remains deterministic, continuous, backend-neutral, and star-shaped around the island-local semantic origin.

For a polar angle theta, the field derives a boundary radius from:

1. a morphology-family envelope;
2. broad deterministic three- and five-fold asymmetric inset terms;
3. erosion maturity and rock competence as controls on boundary irregularity.

The boundary never exceeds nominalRadius.

The first-generation envelopes are intentionally conservative:

- MASSIF — compact, mildly elliptical;
- TABLELAND — broad and nearly round;
- SPINE — strongly elongated along the existing semantic spine axis;
- BASIN — broad and nearly round so basin identity remains primarily internal;
- LOBED — broad five-shouldered outline aligned with the existing lobed morphology vocabulary.

No high-frequency coastline noise is introduced.

## Interiority

AUTH-0020 preserves the accepted AUTH-0002 semantic meaning of interiority:

- fully interior through normalized radial coordinate 0.70;
- smooth transition toward the local boundary;
- zero at and beyond the naturalized boundary.

Only the boundary geometry changes.

## Strong invariants

The domain must remain:

- deterministic for one descriptor;
- connected and star-shaped;
- finite and positive at every angle;
- contained within nominalRadius;
- smooth enough to avoid serrated procedural-noise coastlines;
- morphology-legible;
- independent of Minecraft/backend coordinates.

## Evidence

The deterministic authorship-naturalized-island-domain-v1 corpus uses two representatives from every built-in morphology family.

Each specimen renders:

- LEGACY CIRCLE — the circular AUTH-0002 ownership assumption;
- NATURALIZED DOMAIN — AUTH-0020 interiority;
- MORPHOLOGY IN DOMAIN — existing AUTH-0002 elevation tendency clipped by the candidate domain.

manifest.csv records minimum, maximum, and mean boundary-radius fractions plus approximate owned area relative to the old circle.

## Acceptance gate

Reject AUTH-0020 if:

- naturalized boundaries resemble high-frequency noise;
- ordinary compact morphologies become implausibly star-shaped;
- SPINE loses clear elongation;
- LOBED loses broad connected shoulders or becomes a sharp decorative star;
- the owned area collapses excessively;
- any boundary exceeds nominalRadius;
- deterministic identity changes fail to alter secondary boundary character;
- backend-specific concepts enter the model.

## Scope boundary

AUTH-0020 does **not** yet replace SkyIslandSemanticFieldSet.interiority().

That downstream migration should be a separate milestone because changing ownership affects climate edges, ecology, watersheds, retained water, coherent river realization, and any later terrain consumer simultaneously. The visual and statistical consequences must be reviewed first.

If accepted, AUTH-0021 should propagate the naturalized domain through island-local semantic fields and regenerate downstream authorship evidence under the new ownership geometry.
