# AUTH-0029 — Cave Exposure Connection Geometry

AUTH-0029 converts accepted AUTH-0028 cave exposure intent into actual backend-neutral boundary-connection geometry.

It does not modify Minecraft, invoke carving, or replace the accepted AUTH-0025 internal cave system.

## Dependency

~~~text
AUTH-0028 exposure intent
    -> accepted cave-side anchor
    -> accepted exterior side
    -> AUTH-0029 connection geometry
        -> geology-scored mouth position
        -> curved boundary corridor
        -> tapered mouth scale
    -> future cave-volume composition
    -> AUTH-0027 physical realization
    -> backend carving
~~~

## Intent anchor is not entrance geometry

AUTH-0028 deliberately projected the exterior boundary anchor at the same x/z as the selected cave anchor.

That line was evidence of intent only.

AUTH-0029 preserves:

- cave system identity;
- cave-side anchor;
- accepted UPPER_SURFACE or UNDERSIDE side.

It may move the final mouth modestly in x/z when a nearby boundary route is geologically better.

## Protected baseline

Every accepted intent receives an explicit baseline candidate:

~~~text
cave-side anchor
    -> straight semantic projection
    -> AUTH-0028 boundary anchor
~~~

The planner searches alternatives but never removes that candidate.

This gives AUTH-0029 a strict naturalization invariant:

> A curved or horizontally shifted entrance may only replace the projected baseline when the scored route is at least as geologically supportable after its geometry penalties.

Curvature is therefore not a decorative goal.

## Candidate search

For each exposure intent, the planner evaluates a small deterministic set of:

- boundary-mouth offsets in eight directions;
- three bounded offset magnitudes;
- negative, zero, and positive lateral bend variants.

The search phase is derived from stable island authorship seed, cave-system identity, and accepted exposure side.

The candidate family is finite and deterministic.

## Ownership

Every candidate mouth, control point, and sampled centerline point must remain inside current naturalized horizontal island ownership.

The semantic depth coordinate must remain within [0, 1].

A route that leaves authored island ownership is rejected before geological scoring.

## Geological support

AUTH-0029 evaluates each candidate through AUTH-0022 continuous geology.

Samples nearer the exterior boundary receive greater route-selection weight than samples near the already-accepted cave anchor. This is deliberate: all candidates share nearly the same cave-side origin, while the authorship choice AUTH-0029 is actually making is how that cave reaches the exterior.

Upper-surface routes favor:

- fracture intensity;
- void-formation potential;
- groundwater potential;
- weak bulk competence;
- local exterior exposure at the mouth.

Underside routes favor:

- fracture intensity;
- void-formation potential;
- weak bulk competence;
- descriptor exposure tendency;
- local exterior exposure at the mouth.

The accepted AUTH-0028 score remains a smaller common-cause term.

## Geometry penalties

Candidate score includes bounded penalties for:

- mouth displacement from the projected AUTH-0028 boundary anchor;
- lateral curvature.

This prevents the search from chasing small geological-score improvements with large unnecessary geometric detours.

## Mouth scale

Connection thickness is subordinate to the AUTH-0025 source primitive.

The cave-side radius derives from the source chamber/passage scale.

The connection then tapers continuously toward the exterior mouth.

Weathering and boundary proximity influence the taper ratio, but the first-generation mouth remains narrower than the cave-side connection.

This avoids turning accepted exposure into a large surface crater or underside aperture by default.

## Sampled representation

Each accepted connection contains 15 backend-neutral SkyIslandCavePassagePoint samples.

The points encode:

- semantic subsurface position;
- horizontal radius;
- semantic-depth radius.

They are authored geometry, not Minecraft block coordinates.

## Cardinality

AUTH-0029 preserves AUTH-0028's sparse first-generation policy exactly:

- one accepted exposure intent -> one connection geometry;
- sealed cave system -> no connection geometry;
- at most one connection per cave system.

AUTH-0029 cannot create a second entrance.

## Evidence

The `authorship-cave-exposure-geometry-v1` corpus reuses the accepted cave representatives:

- competent massif key 2332;
- weak basin key 653;
- permeable lobed key 1051;
- hydrologic massif key 2211;
- eroded tableland key 1439;
- spine key 3670.

Each specimen renders:

- EXPOSURE INTENT — AUTH-0028 projected intent;
- CONNECTION SECTION — AUTH-0029 curved/tapered connection;
- TOP-DOWN MOUTH — projected intent anchor versus realized mouth;
- SUPPORT — selected geological support versus straight baseline, mouth offset, and curvature.

Sealed/no-cave specimens remain explicit negative controls.

`manifest.csv` records:

- cave systems;
- AUTH-0028 exposure intents;
- AUTH-0029 connection count;
- side;
- steering support;
- straight-baseline support;
- normalized mouth offset;
- normalized maximum centerline deviation;
- cave-side and mouth radius fractions.

## Acceptance gate

Reject AUTH-0029 if:

- connection count differs from AUTH-0028 accepted intent count;
- sealed systems receive connection geometry;
- accepted exposure side changes;
- connection starts anywhere other than the AUTH-0028 cave-side anchor;
- centerline leaves naturalized ownership;
- upper/underside routes reverse semantic-depth direction;
- mouth thickness widens relative to cave-side connection;
- steered geometry has lower geological support than the protected straight baseline;
- all accepted connections remain mechanically vertical despite viable better routes;
- mouth offsets become large relative to island radius;
- Minecraft, BlockPos, carve masks, or NeoForge concepts enter the world-layer geometry.

## Parallel implementation boundary

The Minecraft implementation agent may continue the narrow **sealed authored-cave proof** using merged AUTH-0026 and AUTH-0027.

AUTH-0029 should not be consumed by that proof yet.

A future cave-volume-composition milestone should union AUTH-0029 connection geometry into the authored cave-volume field. Only then should exterior openings become backend-realizable authored void.

## Next milestone

If AUTH-0029 is accepted, the next Skyforge milestone should compose its connection geometry into a continuous exterior-connected cave-volume field while preserving AUTH-0026 provenance and sign convention.

That milestone should prove:

- continuous overlap with the existing cave volume;
- positive authored void reaches exactly the accepted semantic boundary;
- sealed systems remain unchanged;
- no connection expands beyond naturalized ownership.

Only after that composition is accepted should the Minecraft adapter be permitted to realize authored exterior openings.
