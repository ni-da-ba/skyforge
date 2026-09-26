package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Solves local bounded head compatibility across authored finite CASCADE intervals.
 *
 * <p>F3C removes the ordinary longitudinal-grade constraint only across the explicitly authored
 * CASCADE interval. The upstream/downstream boundary states remain subject to the same D2-derived
 * ordinary-side pointwise envelopes used by F2C/F3B. The discontinuity is constrained to be
 * non-climbing and may not exceed the accumulated authored downhill drop carried by the owned
 * CASCADE profiles.
 *
 * <p>CASCADE runs touching a semantic reach boundary remain explicitly coupled to source,
 * confluence, terminal, or basin transition ownership and are not solved locally here.
 */
public final class SkyIslandCascadeHeadCompatibilityPlanner {
    private static final double EPSILON = 1.0e-10;

    private SkyIslandCascadeHeadCompatibilityPlanner() {}

    public static SkyIslandCascadeHeadCompatibilityPlan plan(
            SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandHydraulicTransitionGeometryEvidencePlan geometry =
                SkyIslandHydraulicTransitionGeometryEvidencePlanner.plan(descriptor);
        return plan(
                descriptor,
                geometry,
                SkyIslandPreHydrologicTerrainField.create(descriptor),
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked(),
                SkyIslandWatershedPlanner.plan(descriptor));
    }

    static SkyIslandCascadeHeadCompatibilityPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicTransitionGeometryEvidencePlan geometry,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy,
            SkyIslandWatershedPlan watershed) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(watershed, "watershed");
        if (!descriptor.equals(geometry.descriptor())) {
            throw new IllegalArgumentException(
                    "transition geometry descriptor must match cascade descriptor");
        }

        Map<Long, SkyIslandHydraulicReachSkeleton> reaches =
                indexReaches(geometry.topology().skeletonPlan().reaches());
        Map<Integer, SkyIslandWatershedCell> cells = new HashMap<>();
        for (SkyIslandWatershedCell cell : watershed.cells()) {
            cells.put(cell.index(), cell);
        }

        List<SkyIslandCascadeHeadCompatibilityOutcome> outcomes =
                new ArrayList<>(geometry.cascades().size());
        for (SkyIslandHydraulicCascadeGeometryCandidate cascade : geometry.cascades()) {
            outcomes.add(solve(
                    descriptor,
                    cascade,
                    requireReach(reaches, cascade.transitionSite()),
                    cells,
                    terrain,
                    policy));
        }

        return new SkyIslandCascadeHeadCompatibilityPlan(
                descriptor, geometry, outcomes);
    }

    private static SkyIslandCascadeHeadCompatibilityOutcome solve(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicCascadeGeometryCandidate geometry,
            SkyIslandHydraulicReachSkeleton reach,
            Map<Integer, SkyIslandWatershedCell> cells,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        SkyIslandHydraulicCascadeTransitionSite site = geometry.transitionSite();
        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
        List<SkyIslandChannelProfile> profiles = semantic.profiles();

        double authoredMaximumDrop =
                authoredCascadeDropWorldUnits(
                        descriptor, profiles, site, cells);

        if (site.firstProfileIndex() == 0
                || site.lastProfileIndexExclusive() == profiles.size()) {
            return new SkyIslandCascadeHeadCompatibilityOutcome(
                    geometry,
                    SkyIslandCascadeHeadCompatibilityStatus.BOUNDARY_COUPLED,
                    authoredMaximumDrop,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(
                            "CASCADE run touches semantic reach boundary and requires combined transition ownership"));
        }

        SkyIslandChannelProfileKind upstreamKind =
                profiles.get(site.firstProfileIndex() - 1).kind();
        SkyIslandChannelProfileKind downstreamKind =
                profiles.get(site.lastProfileIndexExclusive()).kind();
        if (upstreamKind == SkyIslandChannelProfileKind.CASCADE
                || downstreamKind == SkyIslandChannelProfileKind.CASCADE) {
            throw new IllegalStateException(
                    "maximal CASCADE run must be bounded by ordinary profiles");
        }

        List<SkyIslandLocalPosition> points = geometry.centerlinePoints();
        Direction upstreamDirection =
                direction(points.get(0), points.get(1));
        Direction downstreamDirection =
                direction(points.get(points.size() - 2), points.getLast());

        SkyIslandGeomorphicProfileLimits limits = policy.limits(semantic);
        SkyIslandHydraulicHeadEnvelope upstream =
                SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKind(
                        descriptor,
                        upstreamKind,
                        site.upstreamBoundary().position(),
                        site.upstreamBoundary().bankfullHalfWidth(),
                        site.upstreamBoundary().waterDepthPotential(),
                        site.upstreamBoundary().terrainElevation(),
                        -upstreamDirection.z(),
                        upstreamDirection.x(),
                        terrain,
                        limits);
        SkyIslandHydraulicHeadEnvelope downstream =
                SkyIslandHydraulicHeadEnvelopePlanner.evaluateForKind(
                        descriptor,
                        downstreamKind,
                        site.downstreamBoundary().position(),
                        site.downstreamBoundary().bankfullHalfWidth(),
                        site.downstreamBoundary().waterDepthPotential(),
                        site.downstreamBoundary().terrainElevation(),
                        -downstreamDirection.z(),
                        downstreamDirection.x(),
                        terrain,
                        limits);

        if (!upstream.feasible(EPSILON) || !downstream.feasible(EPSILON)) {
            return new SkyIslandCascadeHeadCompatibilityOutcome(
                    geometry,
                    SkyIslandCascadeHeadCompatibilityStatus.INFEASIBLE,
                    authoredMaximumDrop,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(
                            "ordinary-side D2 pointwise head envelope is empty at CASCADE boundary"));
        }

        double[] lower = {
            normalizedLower(upstream), normalizedLower(downstream)
        };
        double[] upper = {
            normalizedUpper(upstream), normalizedUpper(downstream)
        };
        double halfLength = 0.5 * geometry.pathLength();
        SkyIslandHydraulicBoundedQpProblem problem =
                new SkyIslandHydraulicBoundedQpProblem(
                        new double[] {upstream.targetHead(), downstream.targetHead()},
                        new double[] {halfLength, halfLength},
                        lower,
                        upper,
                        List.of(new SkyIslandHydraulicDifferenceConstraint(
                                "cascade:"
                                        + semantic.startCellIndex()
                                        + "->"
                                        + semantic.endCellIndex()
                                        + ":"
                                        + site.firstProfileIndex()
                                        + "-"
                                        + site.lastProfileIndexExclusive(),
                                0,
                                1,
                                0.0,
                                authoredMaximumDrop)));

        SkyIslandHydraulicQpResult solve =
                SkyIslandHydraulicBoundedQpSolver.solve(problem);
        if (solve.status() == SkyIslandHydraulicQpStatus.INFEASIBLE) {
            return new SkyIslandCascadeHeadCompatibilityOutcome(
                    geometry,
                    SkyIslandCascadeHeadCompatibilityStatus.INFEASIBLE,
                    authoredMaximumDrop,
                    Optional.of(solve),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    solve.diagnostic());
        }
        if (solve.status() == SkyIslandHydraulicQpStatus.NUMERICAL_FAILURE) {
            return new SkyIslandCascadeHeadCompatibilityOutcome(
                    geometry,
                    SkyIslandCascadeHeadCompatibilityStatus.NUMERICAL_FAILURE,
                    authoredMaximumDrop,
                    Optional.of(solve),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    solve.diagnostic());
        }

        double upstreamHead = solve.solution()[0];
        double downstreamHead = solve.solution()[1];
        double drop = upstreamHead - downstreamHead;
        return new SkyIslandCascadeHeadCompatibilityOutcome(
                geometry,
                SkyIslandCascadeHeadCompatibilityStatus.SOLVED,
                authoredMaximumDrop,
                Optional.of(solve),
                Optional.of(upstreamHead),
                Optional.of(downstreamHead),
                Optional.of(drop),
                Optional.empty());
    }

    private static double authoredCascadeDropWorldUnits(
            SkyIslandDescriptor descriptor,
            List<SkyIslandChannelProfile> profiles,
            SkyIslandHydraulicCascadeTransitionSite site,
            Map<Integer, SkyIslandWatershedCell> cells) {
        double dropPotential = 0.0;
        for (int i = site.firstProfileIndex();
                i < site.lastProfileIndexExclusive();
                i++) {
            SkyIslandChannelProfile profile = profiles.get(i);
            if (profile.kind() != SkyIslandChannelProfileKind.CASCADE) {
                throw new IllegalStateException(
                        "F3 CASCADE ownership interval contains ordinary profile");
            }
            SkyIslandChannelSegment segment = profile.segment();
            SkyIslandWatershedCell source = requireCell(cells, segment.sourceCellIndex());
            SkyIslandWatershedCell downstream =
                    requireCell(cells, segment.downstreamCellIndex());
            dropPotential +=
                    Math.max(
                            0.0,
                            source.surfacePotential()
                                    - downstream.surfacePotential());
        }
        return dropPotential * descriptor.reliefBudget();
    }

    private static double normalizedLower(SkyIslandHydraulicHeadEnvelope envelope) {
        return envelope.lowerHead() <= envelope.upperHead()
                ? envelope.lowerHead()
                : 0.5 * (envelope.lowerHead() + envelope.upperHead());
    }

    private static double normalizedUpper(SkyIslandHydraulicHeadEnvelope envelope) {
        return envelope.lowerHead() <= envelope.upperHead()
                ? envelope.upperHead()
                : 0.5 * (envelope.lowerHead() + envelope.upperHead());
    }

    private static Direction direction(
            SkyIslandLocalPosition upstream,
            SkyIslandLocalPosition downstream) {
        double dx = downstream.x() - upstream.x();
        double dz = downstream.z() - upstream.z();
        double length = Math.hypot(dx, dz);
        if (!(length > EPSILON)) {
            throw new IllegalStateException(
                    "CASCADE transition boundary tangent must be non-zero");
        }
        return new Direction(dx / length, dz / length);
    }

    private static Map<Long, SkyIslandHydraulicReachSkeleton> indexReaches(
            List<SkyIslandHydraulicReachSkeleton> reaches) {
        Map<Long, SkyIslandHydraulicReachSkeleton> result = new HashMap<>();
        for (SkyIslandHydraulicReachSkeleton reach : reaches) {
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            long key = identity(semantic.startCellIndex(), semantic.endCellIndex());
            if (result.put(key, reach) != null) {
                throw new IllegalStateException("duplicate F2B reach identity");
            }
        }
        return result;
    }

    private static SkyIslandHydraulicReachSkeleton requireReach(
            Map<Long, SkyIslandHydraulicReachSkeleton> reaches,
            SkyIslandHydraulicCascadeTransitionSite site) {
        SkyIslandHydraulicReachSkeleton reach =
                reaches.get(identity(
                        site.reachStartCellIndex(),
                        site.reachEndCellIndex()));
        if (reach == null) {
            throw new IllegalStateException(
                    "missing F2B CASCADE reach "
                            + site.reachStartCellIndex()
                            + "->"
                            + site.reachEndCellIndex());
        }
        return reach;
    }

    private static SkyIslandWatershedCell requireCell(
            Map<Integer, SkyIslandWatershedCell> cells,
            int index) {
        SkyIslandWatershedCell cell = cells.get(index);
        if (cell == null) {
            throw new IllegalStateException(
                    "missing watershed cell " + index);
        }
        return cell;
    }

    private static long identity(int start, int end) {
        return ((long) start << 32) ^ Integer.toUnsignedLong(end);
    }

    private record Direction(double x, double z) {}
}
