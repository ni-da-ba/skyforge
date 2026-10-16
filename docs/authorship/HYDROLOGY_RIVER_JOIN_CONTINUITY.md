# River join continuity investigation

Status: diagnostic work under #1452; the owner accepted the key-77 F4K specimen only as an early proof of concept on 2026-10-15. Joins between river patches remain an observed visual defect.

## Concrete source hypothesis

Both the candidate terrain sampler and the qualified terrain/water sampler select the closest polyline segment and interpolate its station. The station mapping can be discontinuous across an inside-bend bisector even though distance to the polyline is continuous.

For a route (0,0) -> (10,0) -> (10,10), a bed descending 0.1 metre per metre, and a query around (8,2), the selected stations approach 8 and 12 metres from opposite sides. The limiting bed jump is therefore 0.4 metre. Making the query separation approach zero does not remove it. This is a geometric interpolation defect upstream of Minecraft, rather than evidence of a hydraulic solver residual.

SkyIslandHydrologyJoinDiagnosticsTest characterizes this exact limit and measures finite perturbations at key-77 bend bisectors at 1e-3 and 1e-6 local units. The CSV separates terrain and stage jumps and includes wetness and coordinates. Large differences persisting at the smaller separation indicate a seam; these are diagnostic observations, not new acceptance tolerances. The natural probes do not prove the cause of every visual defect or measure all possible seams.

## Trusted references and use

- [USACE HEC-RAS cross-section geometry](https://www.hec.usace.army.mil/confluence/rasdocs/ras1dtechref/6.4/basic-data-requirements/geometric-data/cross-section-geometry): flow-aligned cross-section placement, explicit reach lengths and geometry changes.
- [USACE HEC-RAS cross-section interpolation](https://www.hec.usace.army.mil/confluence/rasdocs/rasum/6.3/entering-and-editing-geometric-data/cross-section-interpolation): connected bank/invert correspondences and linear interpolation between bounding cross sections. This is useful precedent for a coherent corridor surface; it does not prescribe Skyforge's specific discretization.
- [LocationTech JTS offset curves](https://locationtech.github.io/jts/javadoc/org/locationtech/jts/operation/buffer/OffsetCurve.html): offset geometry can split at self-intersections and close approaches. A generic polyline buffer alone does not define a unique hydraulic station or water-surface interpolation.

## Next acceptance target

Use the measured seam locations to choose a connected cross-section/corridor representation with consistent interpolation for bed and water. Require exact preservation of supplied centerline station values, continuity across neighboring section boundaries, explicit handling of folded or self-overlapping corridors, and unchanged established hydraulic benchmark results. Verify integer water connectivity separately, then return a materially changed specimen for visual review.

Do not change hydraulic tolerances, topology, drainage fate, confluence/cascade authority, or F4C/F4D ownership to hide a geometric interpolation defect. The legacy profile-dependent influence envelope also needs comparison with the coupled candidate's actual valley footprint; it is a separate source hypothesis until measured.

## Baseline measurement note

The first focused run passed the exact analytic 0.4 m characterization and all ten retained hydraulic/control tests, but had zero eligible natural bend probes. That does not establish that the key-77 visual defect is caused by a bend seam, and must not be called a continuity pass. The diagnostic now exports the full accepted centerline geometry and station values to distinguish straight routes, skipped bends and influence-envelope issues before selecting a production repair.

Additional primary guidance: [USACE RAS Mapper interpolation surfaces](https://www.hec.usace.army.mil/confluence/rasdocs/rmum/latest/mapping-results/managing-results-maps) describes triangulated regions bounded by centerline, banks, edge lines and cross sections. This is a concrete reference for spatially mapping solved profiles; it does not justify switching to a 2D flow solver.

## World-frame diagnostic

F4E maps stage as original compiled surface plus (semantic stage minus original semantic terrain) times relief. It preserves local depth, but the offset between compiled and semantic original terrain may vary spatially. `measuresWorldFrameCrossSectionStageVariation` separately reports semantic stage, projected stage, and offset variation across three wet points in each interior cross section using the exact F4K geometry parameters. This is a measurement, not a change to the accepted delta/depth-preserving mapping or evidence of voxel connectivity.

## Measured conclusion and proposed contract boundary — 2026-10-15

Focused run [38828422251](https://github.com/ni-da-ba/skyforge/actions/runs/38828422251), job 116741695936, passes both diagnostics on production-identical head 12251e7914f3f2d91da2ab5004d4c1fbbde43d73:

- Both key-77 centerlines are straight (35 and 24 samples), so the latent right-angle seam is not the cause of this specimen's observed joins.
- All 55 interior cross sections have three wet sampled points; none is incomplete.
- Maximum semantic cross-section stage span: 0.000000000 m.
- Maximum projected world-stage span: 1.410379951 m.
- Maximum world-frame offset span: 1.410379951 m.

The world bridge introduces transverse stage variation before voxelization, despite preserving pointwise hydraulic depth. This is a measured model-to-realization inconsistency; it is not proof that every visible seam has this cause. USACE explicitly assumes a horizontal stage across a 1D cross section: [hydraulic reference](https://www.hec.usace.army.mil/confluence/rasdocs/ras1dtechref/6.0/theoretical-basis-for-one-dimensional-and-two-dimensional-hydrodynamic-calculations/1d-unsteady-flow-hydrodynamics/implicit-finite-difference-scheme/momentum-equation0).

Recommended next work is an association-specific continuous candidate/solve in a coherent world-elevation frame, with bed/banks and water evaluated together before quantization. Preserve exact association and ownership, removal-only terrain, underside/thickness admission, dry/rejected controls, and hydraulic benchmark tolerances. Verify transverse stage as well as longitudinal grade, then integer connectivity, then visual review.

**Decision resolved on 2026-10-15:** the owner approved superseding F4B's pointwise compiled-surface elevation authority and F4E's preservation of the old placement-free depth, where required by an association-specific coherent bed/water solve. The retained safety bounds above remain mandatory; this is not permission for a Minecraft-only clamp.

## Approved implementation

See [Association-bound world frame](HYDROLOGY_ASSOCIATED_WORLD_FRAME.md) for the implemented physical-terrain candidate, retained limits and pending runtime/visual evidence. No further architectural approval is pending for this bounded change.

## Subsequent approved boundary

The owner approved precisely bounded connected water above original ground in #1452 comment
6205986655. PR #1490 replaces the old solid-only ceiling for associated candidates with connected
qualified bed-to-stage intervals, retaining removal limits and foreign-ownership protections.
Excavation/normal-capacity trials remain archived, not active fixes. See
[the world-frame contract](HYDROLOGY_ASSOCIATED_WORLD_FRAME.md) for implementation and evidence.
The next human gate is visual judgment after machine validation, not another ownership decision.
