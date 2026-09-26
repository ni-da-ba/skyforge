package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Solves F2C bounded longitudinal profiles for transition-free ordinary C2 reaches.
 *
 * <p>The solver consumes only the F2B head-independent skeleton and the accepted D2 policy. It does
 * not mutate terrain or grant realization authority.
 */
public final class SkyIslandBoundedHydraulicProfilePlanner {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandBoundedHydraulicProfilePlanner() {}

    public static SkyIslandBoundedHydraulicProfilePlan plan(SkyIslandDescriptor descriptor) {
        return plan(
                descriptor,
                SkyIslandHydraulicGeometrySkeletonPlanner.plan(descriptor),
                SkyIslandPreHydrologicTerrainField.create(descriptor),
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked());
    }

    static SkyIslandBoundedHydraulicProfilePlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicGeometrySkeletonPlan skeleton,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(skeleton, "skeleton");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(policy, "policy");
        if (!descriptor.equals(skeleton.descriptor())) {
            throw new IllegalArgumentException("skeleton descriptor must match F2C descriptor");
        }

        Map<Integer, SkyIslandChannelTerminalFate> terminalFates = new HashMap<>();
        for (SkyIslandChannelTerminalFate fate :
                SkyIslandChannelTerminalFatePlanner.plan(
                        descriptor, skeleton.geomorphicNetwork())) {
            terminalFates.put(fate.channelTerminalCellIndex(), fate);
        }
        List<SkyIslandBoundedHydraulicReachOutcome> outcomes =
                new ArrayList<>(skeleton.reaches().size());
        for (SkyIslandHydraulicReachSkeleton reach : skeleton.reaches()) {
            List<SkyIslandQualifiedFluvialDeferralReason> reasons =
                    deferralReasons(skeleton.geomorphicNetwork(), terminalFates, reach);
            if (!reasons.isEmpty()) {
                outcomes.add(new SkyIslandBoundedHydraulicReachOutcome(
                        reach,
                        SkyIslandBoundedHydraulicReachStatus.TRANSITION_DEFERRED,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        reasons,
                        Optional.empty()));
                continue;
            }
            outcomes.add(solveOrdinaryReach(descriptor, reach, terrain, policy));
        }

        return new SkyIslandBoundedHydraulicProfilePlan(descriptor, skeleton, outcomes);
    }

    static SkyIslandBoundedHydraulicReachOutcome solveOrdinaryReach(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachSkeleton reach,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(reach, "reach");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(policy, "policy");

        SkyIslandSemanticChannelReach semantic = reach.geomorphicRoute().semanticReach();
        if (semantic.profiles().stream()
                .anyMatch(profile -> profile.kind() == SkyIslandChannelProfileKind.CASCADE)) {
            throw new IllegalArgumentException("ordinary F2C solve cannot consume CASCADE profiles");
        }

        double relief = descriptor.reliefBudget();
        if (!Double.isFinite(relief) || relief <= 0.0) {
            throw new IllegalArgumentException("descriptor relief budget must be finite and positive");
        }

        SkyIslandGeomorphicProfileLimits limits = policy.limits(semantic);
        List<SkyIslandHydraulicGeometrySkeletonSample> samples = reach.samples();
        double[] target = new double[samples.size()];
        double[] weight = quadratureWeights(samples);
        double[] lower = new double[samples.size()];
        double[] upper = new double[samples.size()];

        List<SkyIslandLocalPosition> points = reach.centerline().points();
        for (int i = 0; i < samples.size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample sample = samples.get(i);
            SkyIslandChannelProfileKind profileKind =
                    profileKind(semantic.profiles(), sample.stationFraction());
            double valleyHalfWidth =
                    sample.bankfullHalfWidth() * valleyMultiplier(profileKind);
            double fullValleyWidth = 2.0 * valleyHalfWidth;
            Vector tangent = tangent(points, i);
            Vector normal = new Vector(-tangent.z(), tangent.x());

            double centerTerrain = sample.terrainElevation() * relief;
            double depth = sample.waterDepthPotential() * relief;
            double leftValleyTerrain =
                    terrain.sample(offset(sample.position(), normal, valleyHalfWidth)) * relief;
            double rightValleyTerrain =
                    terrain.sample(offset(sample.position(), normal, -valleyHalfWidth)) * relief;
            double leftBankTerrain =
                    terrain.sample(offset(sample.position(), normal, sample.bankfullHalfWidth()))
                            * relief;
            double rightBankTerrain =
                    terrain.sample(offset(sample.position(), normal, -sample.bankfullHalfWidth()))
                            * relief;

            double maximumLowering =
                    limits.maximumCenterlineLoweringPotential() * relief;
            double lowerHead = depth;
            lowerHead = Math.max(
                    lowerHead,
                    centerTerrain - maximumLowering + depth);
            lowerHead = Math.max(
                    lowerHead,
                    leftValleyTerrain
                            - limits.maximumLateralRecoveryGrade() * valleyHalfWidth
                            + depth);
            lowerHead = Math.max(
                    lowerHead,
                    rightValleyTerrain
                            - limits.maximumLateralRecoveryGrade() * valleyHalfWidth
                            + depth);
            lowerHead = Math.max(
                    lowerHead,
                    leftValleyTerrain
                            - limits.maximumReliefToValleyWidthRatio() * fullValleyWidth
                            + depth);
            lowerHead = Math.max(
                    lowerHead,
                    rightValleyTerrain
                            - limits.maximumReliefToValleyWidthRatio() * fullValleyWidth
                            + depth);

            double upperHead = Math.min(
                    relief,
                    Math.min(leftBankTerrain, rightBankTerrain)
                            + limits.maximumBankContainmentDeficitWorldUnits());

            target[i] = sample.preferredWaterSurfacePotential() * relief;
            lower[i] = lowerHead;
            upper[i] = upperHead;

            if (lowerHead > upperHead + EPSILON) {
                return failed(
                        reach,
                        SkyIslandBoundedHydraulicReachStatus.INFEASIBLE,
                        Optional.empty(),
                        "D2-derived pointwise head interval is empty at sample " + i);
            }
            if (lowerHead > upperHead) {
                // Roundoff-only inversion: collapse to the common admissible boundary.
                double common = 0.5 * (lowerHead + upperHead);
                lower[i] = common;
                upper[i] = common;
            }
        }

        List<SkyIslandHydraulicDifferenceConstraint> differences =
                longitudinalConstraints(reach, limits);
        SkyIslandHydraulicBoundedQpProblem problem =
                new SkyIslandHydraulicBoundedQpProblem(
                        target, weight, lower, upper, differences);
        SkyIslandHydraulicQpResult solve =
                SkyIslandHydraulicBoundedQpSolver.solve(problem);

        if (solve.status() == SkyIslandHydraulicQpStatus.INFEASIBLE) {
            return failed(
                    reach,
                    SkyIslandBoundedHydraulicReachStatus.INFEASIBLE,
                    Optional.of(solve),
                    solve.diagnostic().orElse("bounded hydraulic QP is infeasible"));
        }
        if (solve.status() == SkyIslandHydraulicQpStatus.NUMERICAL_FAILURE) {
            return failed(
                    reach,
                    SkyIslandBoundedHydraulicReachStatus.NUMERICAL_FAILURE,
                    Optional.of(solve),
                    solve.diagnostic().orElse("bounded hydraulic QP numerical failure"));
        }

        SkyIslandHydraulicReachGeometry geometry =
                reconstruct(reach, solve.solution(), relief);
        SkyIslandGeomorphicReachDiagnostics diagnostics =
                SkyIslandGeomorphicReachDiagnosticsPlanner.measureReach(
                        descriptor, geometry, terrain);
        SkyIslandGeomorphicReachQualification qualification =
                SkyIslandGeomorphicQualificationEvaluator.evaluate(diagnostics, policy);
        SkyIslandBoundedHydraulicReachStatus status =
                qualification.accepted()
                        ? SkyIslandBoundedHydraulicReachStatus.SOLVED_QUALIFIED
                        : SkyIslandBoundedHydraulicReachStatus.SOLVED_REJECTED;

        return new SkyIslandBoundedHydraulicReachOutcome(
                reach,
                status,
                Optional.of(solve),
                Optional.of(geometry),
                Optional.of(qualification),
                List.of(),
                Optional.empty());
    }

    private static List<SkyIslandHydraulicDifferenceConstraint> longitudinalConstraints(
            SkyIslandHydraulicReachSkeleton reach,
            SkyIslandGeomorphicProfileLimits limits) {
        List<SkyIslandHydraulicDifferenceConstraint> result =
                new ArrayList<>(Math.max(0, reach.samples().size() - 1));
        int start = reach.geomorphicRoute().semanticReach().startCellIndex();
        int end = reach.geomorphicRoute().semanticReach().endCellIndex();
        for (int i = 0; i + 1 < reach.samples().size(); i++) {
            double ds =
                    reach.samples().get(i + 1).arcLength()
                            - reach.samples().get(i).arcLength();
            if (!(ds > 0.0)) {
                throw new IllegalStateException("F2B skeleton arc length must increase strictly");
            }
            result.add(new SkyIslandHydraulicDifferenceConstraint(
                    start + "->" + end + ":grade:" + i,
                    i,
                    i + 1,
                    0.0,
                    limits.maximumLongitudinalGrade() * ds));
        }
        return List.copyOf(result);
    }

    private static double[] quadratureWeights(
            List<SkyIslandHydraulicGeometrySkeletonSample> samples) {
        if (samples.size() < 2) {
            throw new IllegalArgumentException("bounded hydraulic reach needs at least two samples");
        }
        double[] result = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            double length;
            if (i == 0) {
                length = 0.5 * (samples.get(1).arcLength() - samples.get(0).arcLength());
            } else if (i == samples.size() - 1) {
                length = 0.5
                        * (samples.get(i).arcLength()
                                - samples.get(i - 1).arcLength());
            } else {
                length = 0.5
                        * (samples.get(i + 1).arcLength()
                                - samples.get(i - 1).arcLength());
            }
            if (!(length > 0.0) || !Double.isFinite(length)) {
                throw new IllegalStateException("F2C quadrature weights must be finite and positive");
            }
            result[i] = length;
        }
        return result;
    }

    private static SkyIslandHydraulicReachGeometry reconstruct(
            SkyIslandHydraulicReachSkeleton reach,
            double[] solvedHead,
            double relief) {
        if (solvedHead.length != reach.samples().size()) {
            throw new IllegalArgumentException("solved head count must match F2B skeleton samples");
        }

        List<SkyIslandHydraulicGeometrySample> samples =
                new ArrayList<>(reach.samples().size());
        double maximumLowering = 0.0;
        double totalLowering = 0.0;
        double maximumSlopePotentialPerWorld = 0.0;

        for (int i = 0; i < reach.samples().size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample source = reach.samples().get(i);
            double surfacePotential = solvedHead[i] / relief;
            double bedPotential =
                    (solvedHead[i] - source.waterDepthPotential() * relief) / relief;
            if (surfacePotential < -EPSILON
                    || surfacePotential > 1.0 + EPSILON
                    || bedPotential < -EPSILON
                    || bedPotential >= surfacePotential) {
                throw new IllegalStateException(
                        "bounded world-space solution escaped authored vertical domain");
            }
            surfacePotential = clamp01(surfacePotential);
            bedPotential = clamp01(bedPotential);
            double lowering =
                    Math.max(0.0, source.terrainElevation() - bedPotential);
            maximumLowering = Math.max(maximumLowering, lowering);
            totalLowering += lowering;

            if (i > 0) {
                double ds =
                        source.arcLength()
                                - reach.samples().get(i - 1).arcLength();
                maximumSlopePotentialPerWorld = Math.max(
                        maximumSlopePotentialPerWorld,
                        Math.abs(
                                        surfacePotential
                                                - samples.get(i - 1).waterSurfacePotential())
                                / ds);
            }

            samples.add(new SkyIslandHydraulicGeometrySample(
                    source.position(),
                    source.stationFraction(),
                    source.relativeDischarge(),
                    source.bankfullHalfWidth(),
                    source.waterDepthPotential(),
                    source.terrainElevation(),
                    surfacePotential,
                    bedPotential,
                    lowering));
        }

        return new SkyIslandHydraulicReachGeometry(
                reach.geomorphicRoute(),
                reach.centerline(),
                samples,
                reach.pathLength(),
                maximumLowering,
                totalLowering / samples.size(),
                maximumSlopePotentialPerWorld,
                reach.maximumBankfullHalfWidth(),
                reach.maximumWaterDepthPotential());
    }

    private static SkyIslandBoundedHydraulicReachOutcome failed(
            SkyIslandHydraulicReachSkeleton reach,
            SkyIslandBoundedHydraulicReachStatus status,
            Optional<SkyIslandHydraulicQpResult> solve,
            String diagnostic) {
        return new SkyIslandBoundedHydraulicReachOutcome(
                reach,
                status,
                solve,
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.of(diagnostic));
    }

    private static List<SkyIslandQualifiedFluvialDeferralReason> deferralReasons(
            SkyIslandGeomorphicChannelNetworkPlan network,
            Map<Integer, SkyIslandChannelTerminalFate> terminalFates,
            SkyIslandHydraulicReachSkeleton reach) {
        SkyIslandSemanticChannelReach semantic =
                reach.geomorphicRoute().semanticReach();
        List<SkyIslandQualifiedFluvialDeferralReason> reasons = new ArrayList<>();
        SkyIslandGeomorphicNetworkNode startNode =
                network.requireNode(semantic.startCellIndex());
        SkyIslandGeomorphicNetworkNode endNode =
                network.requireNode(semantic.endCellIndex());

        if (startNode.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE
                || endNode.kind() == SkyIslandGeomorphicNetworkNodeKind.CONFLUENCE) {
            reasons.add(
                    SkyIslandQualifiedFluvialDeferralReason.CONFLUENCE_TRANSITION_REQUIRED);
        }
        if (semantic.profiles().stream()
                .anyMatch(profile -> profile.kind() == SkyIslandChannelProfileKind.CASCADE)) {
            reasons.add(
                    SkyIslandQualifiedFluvialDeferralReason.CASCADE_TRANSITION_REQUIRED);
        }

        if (endNode.kind() == SkyIslandGeomorphicNetworkNodeKind.TERMINAL) {
            SkyIslandChannelTerminalFate fate = terminalFates.get(endNode.cellIndex());
            if (fate == null) {
                throw new IllegalStateException(
                        "missing explicit watershed fate for channel terminal " + endNode.cellIndex());
            }
            switch (fate.kind()) {
                case EDGE_OUTLET -> {
                    // Edge discharge is the only ordinary free-terminal fate in F2C.
                }
                case RETAINED_OPEN_WATER -> reasons.add(
                        SkyIslandQualifiedFluvialDeferralReason
                                .RETAINED_WATER_TRANSITION_REQUIRED);
                case RETAINED_WETLAND -> reasons.add(
                        SkyIslandQualifiedFluvialDeferralReason
                                .WETLAND_TRANSITION_REQUIRED);
                case UNRESOLVED -> reasons.add(
                        SkyIslandQualifiedFluvialDeferralReason
                                .UNRESOLVED_TERMINAL_FATE);
            }
        }
        return List.copyOf(reasons);
    }

    private static SkyIslandChannelProfileKind profileKind(
            List<SkyIslandChannelProfile> profiles,
            double stationFraction) {
        int index =
                Math.min(
                        profiles.size() - 1,
                        (int)
                                Math.floor(
                                        Math.max(0.0, Math.min(0.999999999, stationFraction))
                                                * profiles.size()));
        return profiles.get(index).kind();
    }

    private static double valleyMultiplier(SkyIslandChannelProfileKind kind) {
        return switch (kind) {
            case ALLUVIAL -> 3.5;
            case INCISED -> 2.5;
            case CASCADE -> throw new IllegalArgumentException(
                    "ordinary F2C profile cannot use CASCADE valley geometry");
        };
    }

    private static Vector tangent(
            List<SkyIslandLocalPosition> points,
            int index) {
        SkyIslandLocalPosition a =
                index == 0 ? points.getFirst() : points.get(index - 1);
        SkyIslandLocalPosition b =
                index == points.size() - 1 ? points.getLast() : points.get(index + 1);
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double length = Math.hypot(dx, dz);
        if (length <= EPSILON) {
            return new Vector(1.0, 0.0);
        }
        return new Vector(dx / length, dz / length);
    }

    private static SkyIslandLocalPosition offset(
            SkyIslandLocalPosition position,
            Vector normal,
            double distance) {
        return new SkyIslandLocalPosition(
                position.x() + normal.x() * distance,
                position.z() + normal.z() * distance);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record Vector(double x, double z) {}
}
