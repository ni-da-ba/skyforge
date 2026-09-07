# AUTH-0094 — Regional floating sky-river semantic plan

## Purpose

Skyforge's exceptional-regional-phenomena direction explicitly allows rare world features whose
meaning exceeds one island-local morphology/hydrology contract.

A literal floating river crossing open sky is the first concrete hydrologic consumer.

AUTH-0086 cannot be stretched to cover this case: AUTH-0086 intentionally owns coherent visible
hydrology inside one authored island. A genuinely cross-island watercourse must therefore be a
higher-order regional object rather than a fake island-local watershed.

AUTH-0094 closes only that missing authorship boundary.

## Public input

The public planner accepts exactly:

~~~text
SkyIslandPublishedAuthoredRealizationBinding
long phenomenonKey
~~~

The binding is the accepted AUTH-0087 exact regional publication/authorship gate.

The phenomenon key is an authored identity/variation axis after Content has decided that a floating
sky-river belongs in a playable region.

Content still owns whether such a phenomenon occurs and how rare it is.

## First-generation participation rule

AUTH-0094 requires at least two published islands.

For one-island regions, the planner returns no sky-river plan.

For multi-island regions, the first-generation participant pair is the exact canonical AUTH-0046
pair with greatest horizontal realized-center separation.

Exact ties preserve canonical association order because the planner scans that existing order and
does not replace an equal-distance winner.

This is a deliberately narrow deterministic rule for the first concrete phenomenon.

It is not a universal exceptional-feature siting algorithm.

## Source and sink orientation

The selected pair is oriented from source to sink by the upper nominal reference elevation:

~~~text
suspensionElevation + upperElevation
~~~

Higher reference elevation becomes source.

Equal elevations preserve canonical participant order.

This establishes ordered flow semantics without claiming a physical water simulation.

## Upper nominal reference points

The source/sink trajectory endpoints are the realized descriptor center X/Z at:

~~~text
Y = suspensionElevation + upperElevation
~~~

These are stable backend-neutral upper-envelope reference points.

They are not:

- exact terrain-surface intersections;
- a waterfall lip;
- a lake outlet;
- a Minecraft fluid coordinate;
- guaranteed physical attachment geometry.

A later integration milestone may connect accepted regional river provenance to exact local
AUTH-0086/AUTH-0091 hydrology where an actual attachment is required.

## 3D guide trajectory

The first-generation semantic trajectory contains exactly four ordered world-space guide points:

~~~text
source reference
1/3 open-sky control
2/3 open-sky control
sink reference
~~~

The open-sky controls follow linear source-to-sink elevation while adding one deterministic lateral
curve.

Curve magnitude is proportional to regional horizontal scale / participant nominal scale and is
seeded only by the phenomenon key.

The trajectory is therefore:

- deterministic;
- world-space;
- visibly non-straight in ordinary non-degenerate regions;
- translation covariant;
- uniform-scale covariant.

It is not a prescribed spline algorithm for the Minecraft renderer or fluid engine.

## Phenomenon identity

A plan retains:

- exact AUTH-0087 binding;
- publication identity;
- authored-world identity;
- phenomenon key;
- exact source AUTH-0046 association;
- exact sink AUTH-0046 association;
- ordered four-point guide trajectory.

The canonical token combines the exact publication/authored-world domains with the phenomenon key.

## Sparse exceptional-feature ownership

AUTH-0094 does not assign rarity.

Content / Experience owns:

- Regional / Unusual / Exceptional playable frequency;
- discovery value;
- rewards;
- hazards;
- progression consequences;
- whether floating rivers ship in a given experience profile.

Authorship only defines the backend-neutral world meaning once the phenomenon exists.

## Implementation ownership

Implementation owns:

- exact terrain attachment;
- Minecraft fluids or custom flow realization;
- support/fencing mechanics for suspended water;
- rendering/particles;
- collision;
- entity interaction;
- chunk lifecycle;
- persistence/synchronization;
- performance;
- shader-enhanced presentation.

Core phenomenon identity must remain representable without relying on shader-only truth, but
AUTH-0094 itself does not define presentation.

## Explicit non-contracts

AUTH-0094 does not define:

- physical discharge/flow rate;
- fluid pressure;
- exact river width/depth;
- water volume;
- block coordinates;
- local watershed ownership;
- exact source waterbody;
- exact sink waterbody;
- rarity/frequency;
- biome identity;
- loot/reward;
- damage/hazard;
- runtime spawn/lifecycle behavior;
- a generic anomaly framework for unrelated phenomena.

## Covariance

Uniform world translation of all participating realized descriptors shifts every guide point by the
same translation while retaining participant identity.

Uniform scaling of horizontal centers, suspension elevation, upper elevation, and nominal radii
scales every guide-point coordinate by the same factor while retaining participant identity and
trajectory parameters.

These are authorship invariants, not renderer requirements.

## Acceptance gate

Reject AUTH-0094 if:

- it accepts arbitrary unbound islands instead of one exact AUTH-0087 binding;
- a singleton region fabricates a second participant;
- source or sink loses exact AUTH-0046 provenance;
- participant choice depends on backend state or a new hidden random source;
- source/sink endpoint references are substituted;
- trajectory ordering is unstable;
- translation or uniform-scale covariance fails;
- island-local AUTH-0086 hydrology is relabeled as a cross-island physical path;
- Minecraft fluid/block/rendering/lifecycle policy enters `skyforge-world`;
- rarity, rewards, hazards, or progression are pulled into Authorship;
- the milestone grows into a universal exceptional-feature engine.

## Evidence target

The proof package demonstrates:

- exact AUTH-0087 regional provenance;
- deterministic farthest-pair participation;
- source/sink elevation orientation;
- singleton empty behavior;
- stable four-point ordered trajectory;
- lateral open-sky curvature;
- translation covariance;
- uniform-scale covariance;
- no backend/frequency/gameplay policy.

This is architecture/provenance evidence, not a visual spectacle gate.

## Next boundary

Do not generalize AUTH-0094 automatically.

A future exceptional phenomenon should earn its own narrow semantic contract when its concrete design
requires meaning that cannot reuse this sky-river plan.

Likewise, exact river-to-island hydrologic attachment should be added only when a realization
consumer needs a proven local interface.
