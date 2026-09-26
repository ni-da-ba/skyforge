package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Solves the local bounded-head compatibility problem across finite F3A confluence legs.
 *
 * <p>One shared node variable enforces exact confluence head continuity. Every incident finite
 * boundary remains a separate variable with the same D2-derived pointwise envelope used by F2C.
 * Directional difference constraints preserve non-climbing ordinary flow across each finite leg.
 * CASCADE-touching legs are explicitly deferred to a later combined transition solve.
 */
public final class SkyIslandConfluenceHeadCompatibilityPlanner {
    private static final double EPSILON = 1.0e-10;

    private SkyIslandConfluenceHeadCompatibilityPlanner() {}

    public static SkyIslandConfluenceHeadCompatibilityPlan plan(
            SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandHydraulicTransitionGeometryEvidencePlan geometry =
                SkyIslandHydraulicTransitionGeometryEvidencePlanner.plan(descriptor);
        return plan(
                descriptor,
                geometry,
                SkyIslandPreHydrologicTerrainField.create(descriptor),
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked());
    }

    static SkyIslandConfluenceHeadCompatibilityPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicTransitionGeometryEvidencePlan geometry,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(policy, "policy");
        if (!descriptor.equals(geometry.descriptor())) {
            throw new IllegalArgumentException(
                    "transition geometry descriptor must match confluence descriptor");
        }

        Map<Long, SkyIslandHydraulicReachSkeleton> reaches =
                indexReaches(geometry.topology().skeletonPlan().reaches());
        List<SkyIslandConfluenceHeadCompatibilityOutcome> outcomes =
                new ArrayList<>(geometry.confluences().size());

        for (SkyIslandHydraulicConfluenceGeometryCandidate confluence :
                geometry.confluences()) {
            outcomes.add(solve(descriptor, confluence, reaches, terrain, policy));
        }

        return new SkyIslandConfluenceHeadCompatibilityPlan(
                descriptor, geometry, outcomes);
    }

    private static SkyIslandConfluenceHeadCompatibilityOutcome solve(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicConfluenceGeometryCandidate confluence,
            Map<Long, SkyIslandHydraulicReachSkeleton> reaches,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        double relief = descriptor.reliefBudget();

        for (SkyIslandHydraulicTransitionLegGeometry leg : confluence.legs()) {
            SkyIslandHydraulicReachSkeleton reach = requireReach(reaches, leg);
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            SkyIslandChannelProfileKind nodeKind =
                    SkyIslandHydraulicHeadEnvelopePlanner.profileKind(
                            semantic.profiles(),
                            leg.nodeBoundary().stationFraction());
            SkyIslandChannelProfileKind finiteKind =
                    SkyIslandHydraulicHeadEnvelopePlanner.profileKind(
                            semantic.profiles(),
                            leg.finiteBoundary().stationFraction());
            if (nodeKind == SkyIslandChannelProfileKind.CASCADE
                    || finiteKind == SkyIslandChannelProfileKind.CASCADE) {
                return new SkyIslandConfluenceHeadCompatibilityOutcome(
                        confluence,
                        SkyIslandConfluenceHeadCompatibilityStatus.CASCADE_COUPLED,
                        0.0,
                        relief,
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        Optional.of(
                                "finite confluence leg touches authored CASCADE profile and requires combined transition solve"));
            }
        }

        int variableCount = confluence.legs().size() + 1;
        double[] target = new double[variableCount];
        double[] weight = new double[variableCount];
        double[] lower = new double[variableCount];
        double[] upper = new double[variableCount];
        List<SkyIslandHydraulicDifferenceConstraint> differences =
                new ArrayList<>(confluence.legs().size());

        double nodeLower = Double.NEGATIVE_INFINITY;
        double nodeUpper = Double.POSITIVE_INFINITY;
        double nodeTargetWeighted = 0.0;
        double nodeWeight = 0.0;

        for (int legIndex = 0; legIndex < confluence.legs().size(); legIndex++) {
            SkyIslandHydraulicTransitionLegGeometry leg =
                    confluence.legs().get(legIndex);
            SkyIslandHydraulicReachSkeleton reach = requireReach(reaches, leg);
            SkyIslandSemanticChannelReach semantic =
                    reach.geomorphicRoute().semanticReach();
            Direction direction = downstreamDirection(leg);
            double normalX = -direction.z();
            double normalZ = direction.x();

            SkyIslandHydraulicHeadEnvelope nodeEnvelope =
                    SkyIslandHydraulicHeadEnvelopePlanner.evaluate(
                            descriptor,
                            semantic,
                            leg.nodeBoundary().stationFraction(),
                            leg.nodeBoundary().position(),
                            leg.nodeBoundary().bankfullHalfWidth(),
                            leg.nodeBoundary().waterDepthPotential(),
                            leg.nodeBoundary().terrainElevation(),
                            normalX,
                            normalZ,
                            terrain,
                            policy);
            SkyIslandHydraulicHeadEnvelope finiteEnvelope =
                    SkyIslandHydraulicHeadEnvelopePlanner.evaluate(
                            descriptor,
                            semantic,
                            leg.finiteBoundary().stationFraction(),
                            leg.finiteBoundary().position(),
                            leg.finiteBoundary().bankfullHalfWidth(),
                            leg.finiteBoundary().waterDepthPotential(),
                            leg.finiteBoundary().terrainElevation(),
                            normalX,
                            normalZ,
                            terrain,
                            policy);

            if (!nodeEnvelope.feasible(EPSILON)
                    || !finiteEnvelope.feasible(EPSILON)) {
                return new SkyIslandConfluenceHeadCompatibilityOutcome(
                        confluence,
                        SkyIslandConfluenceHeadCompatibilityStatus.INFEASIBLE,
                        Math.max(nodeLower, nodeEnvelope.lowerHead()),
                        Math.min(nodeUpper, nodeEnvelope.upperHead()),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        Optional.of(
                                "D2-derived pointwise head interval is empty on finite confluence leg"));
            }

            double legWeight = 0.5 * leg.retreatLength();
            nodeWeight += legWeight;
            nodeTargetWeighted += legWeight * nodeEnvelope.targetHead();
            nodeLower = Math.max(nodeLower, normalizedLower(nodeEnvelope));
            nodeUpper = Math.min(nodeUpper, normalizedUpper(nodeEnvelope));

            int variable = legIndex + 1;
            target[variable] = finiteEnvelope.targetHead();
            weight[variable] = legWeight;
            lower[variable] = normalizedLower(finiteEnvelope);
            upper[variable] = normalizedUpper(finiteEnvelope);

            double maximumDrop =
                    policy.limits(semantic).maximumLongitudinalGrade()
                            * leg.retreatLength();
            if (leg.nodeBoundary().role()
                    == SkyIslandHydraulicTransitionBoundaryRole.INCOMING) {
                differences.add(new SkyIslandHydraulicDifferenceConstraint(
                        "confluence:"
                                + confluence.transitionSite().nodeCellIndex()
                                + ":incoming:"
                                + semantic.startCellIndex()
                                + "->"
                                + semantic.endCellIndex(),
                        variable,
                        0,
                        0.0,
                        maximumDrop));
            } else {
                differences.add(new SkyIslandHydraulicDifferenceConstraint(
                        "confluence:"
                                + confluence.transitionSite().nodeCellIndex()
                                + ":outgoing:"
                                + semantic.startCellIndex()
                                + "->"
                                + semantic.endCellIndex(),
                        0,
                        variable,
                        0.0,
                        maximumDrop));
            }
        }

        if (!(nodeWeight > 0.0)) {
            throw new IllegalStateException("confluence node quadrature weight must be positive");
        }
        if (nodeLower > nodeUpper + EPSILON) {
            return new SkyIslandConfluenceHeadCompatibilityOutcome(
                    confluence,
                    SkyIslandConfluenceHeadCompatibilityStatus.INFEASIBLE,
                    nodeLower,
                    nodeUpper,
                    Optional.empty(),
                    Optional.empty(),
                    List.of(),
                    Optional.of(
                            "incident D2 head envelopes have no common shared-node interval"));
        }
        if (nodeLower > nodeUpper) {
            double common = 0.5 * (nodeLower + nodeUpper);
            nodeLower = common;
            nodeUpper = common;
        }

        target[0] = nodeTargetWeighted / nodeWeight;
        weight[0] = nodeWeight;
        lower[0] = nodeLower;
        upper[0] = nodeUpper;

        SkyIslandHydraulicBoundedQpProblem problem =
                new SkyIslandHydraulicBoundedQpProblem(
                        target, weight, lower, upper, differences);
        SkyIslandHydraulicQpResult solve =
                SkyIslandHydraulicBoundedQpSolver.solve(problem);

        if (solve.status() == SkyIslandHydraulicQpStatus.INFEASIBLE) {
            return new SkyIslandConfluenceHeadCompatibilityOutcome(
                    confluence,
                    SkyIslandConfluenceHeadCompatibilityStatus.INFEASIBLE,
                    nodeLower,
                    nodeUpper,
                    Optional.of(solve),
                    Optional.empty(),
                    List.of(),
                    solve.diagnostic());
        }
        if (solve.status() == SkyIslandHydraulicQpStatus.NUMERICAL_FAILURE) {
            return new SkyIslandConfluenceHeadCompatibilityOutcome(
                    confluence,
                    SkyIslandConfluenceHeadCompatibilityStatus.NUMERICAL_FAILURE,
                    nodeLower,
                    nodeUpper,
                    Optional.of(solve),
                    Optional.empty(),
                    List.of(),
                    solve.diagnostic());
        }

        double[] solution = solve.solution();
        List<SkyIslandConfluenceLegHeadSolution> legSolutions =
                new ArrayList<>(confluence.legs().size());
        for (int i = 0; i < confluence.legs().size(); i++) {
            legSolutions.add(new SkyIslandConfluenceLegHeadSolution(
                    confluence.legs().get(i), solution[i + 1]));
        }
        return new SkyIslandConfluenceHeadCompatibilityOutcome(
                confluence,
                SkyIslandConfluenceHeadCompatibilityStatus.SOLVED,
                nodeLower,
                nodeUpper,
                Optional.of(solve),
                Optional.of(solution[0]),
                legSolutions,
                Optional.empty());
    }

    private static double normalizedLower(SkyIslandHydraulicHeadEnvelope envelope) {
        if (envelope.lowerHead() <= envelope.upperHead()) {
            return envelope.lowerHead();
        }
        return 0.5 * (envelope.lowerHead() + envelope.upperHead());
    }

    private static double normalizedUpper(SkyIslandHydraulicHeadEnvelope envelope) {
        if (envelope.lowerHead() <= envelope.upperHead()) {
            return envelope.upperHead();
        }
        return 0.5 * (envelope.lowerHead() + envelope.upperHead());
    }

    private static Direction downstreamDirection(
            SkyIslandHydraulicTransitionLegGeometry leg) {
        SkyIslandLocalPosition upstream;
        SkyIslandLocalPosition downstream;
        if (leg.nodeBoundary().role()
                == SkyIslandHydraulicTransitionBoundaryRole.INCOMING) {
            upstream = leg.finiteBoundary().position();
            downstream = leg.nodeBoundary().position();
        } else {
            upstream = leg.nodeBoundary().position();
            downstream = leg.finiteBoundary().position();
        }
        double dx = downstream.x() - upstream.x();
        double dz = downstream.z() - upstream.z();
        double length = Math.hypot(dx, dz);
        if (!(length > EPSILON)) {
            throw new IllegalStateException(
                    "finite confluence leg must have non-zero chord direction");
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
            SkyIslandHydraulicTransitionLegGeometry leg) {
        int start = leg.nodeBoundary().reachStartCellIndex();
        int end = leg.nodeBoundary().reachEndCellIndex();
        SkyIslandHydraulicReachSkeleton reach = reaches.get(identity(start, end));
        if (reach == null) {
            throw new IllegalStateException(
                    "missing F2B confluence reach " + start + "->" + end);
        }
        return reach;
    }

    private static long identity(int start, int end) {
        return ((long) start << 32) ^ Integer.toUnsignedLong(end);
    }

    private record Direction(double x, double z) {}
}
