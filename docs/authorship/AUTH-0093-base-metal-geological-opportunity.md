# AUTH-0093 — Base-metal geological opportunity

## Purpose

The current Bootstrap Province consumer requires geographically meaningful Iron, Copper, and Zinc:

- Iron must remain foundational and locally reliable enough to avoid first-flight soft lock;
- Copper and Zinc should become early post-flight regional specializations;
- Brass should then reward transport and heated processing rather than block first flight.

Accepted Authorship already provides generic mineral-bearing host structure through AUTH-0031 through
AUTH-0036, but deliberately stops before named mineral species. That generic contract cannot tell a
downstream resource planner whether one mineralized island is more favorable to Iron, Copper, or
Zinc.

AUTH-0093 closes only that semantic gap.

## Boundary

AUTH-0093 introduces three backend-neutral geological element families:

~~~text
IRON
COPPER
ZINC
~~~

These are real world-resource semantics, not Minecraft registry objects.

The public producer accepts exactly one authored SkyIslandDescriptor and internally reuses the fixed
AUTH-0033 material-family plan.

Callers cannot provide:

- a custom material grid;
- arbitrary mineral-bearing cells;
- an ore-placement threshold;
- availability class;
- deposit count;
- grade;
- physical volume;
- Minecraft ore/block identity.

## Common geological support

Every base-metal opportunity is strictly subordinate to accepted:

~~~text
AUTH-0033 MINERAL_BEARING_STRUCTURAL_HOST
~~~

If that support is zero, all three base-metal opportunities are exactly zero.

Element-specific affinity therefore cannot create free-floating ore noise.

Every opportunity is also bounded above by the accepted mineral-bearing structural membership at the
same exact AUTH-0033 cell.

## Host compatibility

AUTH-0093 distinguishes broad geological settings without attempting full ore-deposit simulation.

### Iron

Iron opportunity favors:

- coherent massive host;
- competent fabric-rich host;
- comparatively lower alteration and water conditioning.

### Copper

Copper opportunity favors:

- strongly altered host;
- water-conditioned host;
- structural/fabric participation.

### Zinc

Zinc opportunity favors:

- altered and water-conditioned host;
- fabric-rich structural context;
- a different broad elemental-affinity field from Copper.

These are visible-consequence semantic preferences. They are not claims that every real-world Iron,
Copper, or Zinc deposit follows one universal ore-genesis model.

## Broad elemental affinity

Each element receives one deterministic broad coherent affinity field in normalized island/depth
coordinates.

The field:

- is derived from stable authorship seed plus an element-specific domain constant;
- is evaluated at the fixed AUTH-0033 planning cells;
- uses normalized horizontal coordinates and semantic depth;
- cannot create opportunity without accepted mineral-bearing structural support;
- does not add high-frequency block-scale ore noise.

This gives neighboring/remote material regions a stable basis for base-metal differentiation while
preserving geology as the primary cause.

## Profile semantics

The profile retains:

- exact AUTH-0033 source plan;
- every active host cell in exact AUTH-0033 order;
- per-cell normalized Iron/Copper/Zinc opportunity;
- mineral-bearing cell count;
- mean and peak opportunity by element;
- relative mean-opportunity share by element.

Relative opportunity share is a comparative planning statistic only.

It is not:

- percentage ore composition;
- reserve tonnage;
- extraction yield;
- availability class;
- guaranteed deposit presence.

## Scale covariance

The affinity field uses normalized horizontal coordinates.

For a descriptor changed only by nominal radius, the fixed AUTH-0033 planning-cell identities and
normalized base-metal opportunity values remain unchanged, while physical spacing remains upstream
AUTH-0033 evidence.

AUTH-0093 therefore separates normalized geological character from eventual deposit scale.

## Downstream ownership

Content / Experience may later map these real geological opportunities to gameplay resource policy,
including:

- Iron bootstrap guarantees;
- Copper/Zinc post-flight specialization;
- availability classes;
- trade/salvage alternatives;
- progression and freight consequences.

Implementation may later realize accepted resource decisions as Minecraft/mod deposits.

AUTH-0093 does not decide any of those policies.

## Acceptance gate

Reject AUTH-0093 if:

- base-metal opportunity exists where accepted mineral-bearing structural support is zero;
- an opportunity exceeds its accepted mineral-bearing support;
- callers can choose a competing material grid or threshold;
- exact AUTH-0033 cell provenance/order is lost;
- the three element fields collapse into one identical field;
- radius-only scaling changes normalized opportunity;
- named Minecraft ores, registry keys, blocks, placement, loot, availability tiers, deposit counts,
  reserve volume, or progression policy enter Authorship.

## Evidence target

The proof package should demonstrate:

- exact AUTH-0033 provenance;
- strict subordination to mineral-bearing structural support;
- deterministic Iron/Copper/Zinc opportunity;
- non-collapsed elemental differentiation;
- normalized relative-share arithmetic;
- radius-only scale covariance;
- no backend/resource-tier policy.

This is semantic geology/resource-planning evidence, not ore-placement or gameplay-balance acceptance.

## Next boundary

AUTH-0093 does not by itself create a complete resource geography.

The next step should be consumer-driven:

- Content may use the profile to define Iron/Copper/Zinc eligibility and bootstrap/regional
  guarantees;
- a later Authorship milestone should add another geological resource family only when a concrete
  retained resource requires a distinct geological cause;
- petroleum, gemstones, Redstone-like resources, and exceptional materials should not be folded into
  this base-metal contract merely for completeness.
