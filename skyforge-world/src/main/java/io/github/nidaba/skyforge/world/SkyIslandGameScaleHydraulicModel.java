package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Explicit game-scale mapping for the steady open-channel candidate model.
 *
 * <p>Minecraft horizontal units map one-to-one to metres; normalized relief maps to the
 * descriptor's authored relief budget. Manning n and trapezoidal bank slope are explicit model
 * assumptions. The slope may steepen from its 2H:1V maximum to preserve a positive bottom width
 * within the authored bankfull top width. Relative discharge is scaled so the reach's maximum
 * authored flow equals its bankfull Manning capacity at the measured source-to-outlet grade.
 * Source reaches use Manning normal depth as their upstream boundary, with flow regime
 * classified from the normal-depth Froude number. This is a calibrated game model, not a claim that
 * the descriptor supplies physical rainfall or material measurements.
 */
public final class SkyIslandGameScaleHydraulicModel {
    public static final double MANNING_ROUGHNESS = 0.035;
    public static final double HORIZONTAL_METRES_PER_LOCAL_UNIT = 1.0;
    public static final double MAXIMUM_SIDE_SLOPE_HORIZONTAL_TO_VERTICAL = 2.0;
    private static final double MINIMUM_BANKFULL_BOTTOM_WIDTH_FRACTION = 0.10;
    private static final double GRAVITY_METRES_PER_SECOND_SQUARED = 9.81;
    private static final double MINIMUM_BED_SLOPE = 1.0e-5;
    private static final double MINIMUM_RELATIVE_DISCHARGE = 0.015;
    private static final double VALLEY_BLEND_WIDTH_FACTOR = 2.0;

    private SkyIslandGameScaleHydraulicModel() {}

    /**
     * Solves one source-controlled reach using the head-independent C2 geometry and the source
     * normal-depth condition. The returned route, bed, and profile are all tied to the same
     * candidate geometry.
     */
    public static SourceReachSolution solveSourceReach(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachSkeleton reach,
            SkyIslandSemanticField terrain) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(reach, "reach");
        Objects.requireNonNull(terrain, "terrain");
        double verticalScale = descriptor.reliefBudget();
        double[] relativeDischarges = reach.samples().stream()
                .mapToDouble(SkyIslandHydraulicGeometrySkeletonSample::relativeDischarge)
                .toArray();
        double maximumRelativeDischarge = Arrays.stream(relativeDischarges).max().orElseThrow();
        if (!(maximumRelativeDischarge > 0.0)) {
            throw new IllegalArgumentException("source reach must have positive authored discharge");
        }

        double maximumHalfWidth = reach.samples().stream()
                .mapToDouble(SkyIslandHydraulicGeometrySkeletonSample::bankfullHalfWidth)
                .max()
                .orElseThrow();
        double maximumDepthPotential = reach.samples().stream()
                .mapToDouble(SkyIslandHydraulicGeometrySkeletonSample::waterDepthPotential)
                .max()
                .orElseThrow();
        double bankfullWidthMetres = 2.0 * maximumHalfWidth * HORIZONTAL_METRES_PER_LOCAL_UNIT;
        double bankfullDepthMetres = maximumDepthPotential * verticalScale;
        double sideSlope = Math.min(
                MAXIMUM_SIDE_SLOPE_HORIZONTAL_TO_VERTICAL,
                (1.0 - MINIMUM_BANKFULL_BOTTOM_WIDTH_FRACTION)
                        * bankfullWidthMetres / (2.0 * bankfullDepthMetres));
        double bottomWidthMetres = bankfullWidthMetres
                - 2.0 * sideSlope * bankfullDepthMetres;
        if (!(bottomWidthMetres > 0.0) || !Double.isFinite(bottomWidthMetres)) {
            throw new IllegalArgumentException(
                    "authored bankfull width and depth cannot contain the calibrated trapezoidal section");
        }
        double routeLengthMetres = reach.centerline().pathLength() * HORIZONTAL_METRES_PER_LOCAL_UNIT;
        double startElevationMetres = terrain.sample(reach.centerline().points().getFirst()) * verticalScale;
        double endElevationMetres = terrain.sample(reach.centerline().points().getLast()) * verticalScale;
        double measuredGrade = (startElevationMetres - endElevationMetres) / routeLengthMetres;
        double minimumGrade = Math.max(MINIMUM_BED_SLOPE, measuredGrade);

        SkyIslandGeomorphicQualificationPolicy policy =
                SkyIslandGeomorphicQualificationPolicy.firstEvidenceBacked();
        SkyIslandGeomorphicProfileLimits limits = policy.limits(
                reach.geomorphicRoute().semanticReach());
        double maximumTerrainEditMetres =
                limits.maximumCenterlineLoweringPotential() * verticalScale;

        double bankEdge = bottomWidthMetres * 0.5 + sideSlope * bankfullDepthMetres;
        double valleyHalfWidthMetres = bankEdge
                + Math.max(bottomWidthMetres, bankfullDepthMetres) * VALLEY_BLEND_WIDTH_FACTOR;
        SkyIslandHydraulicCandidateBuilder.Configuration configuration =
                new SkyIslandHydraulicCandidateBuilder.Configuration(
                        HORIZONTAL_METRES_PER_LOCAL_UNIT,
                        verticalScale,
                        0.0,
                        minimumGrade,
                        0.0,
                        bottomWidthMetres,
                        sideSlope,
                        bankfullDepthMetres,
                        valleyHalfWidthMetres,
                        maximumTerrainEditMetres);

        List<SkyIslandLocalPosition> points = reach.centerline().points();
        SkyIslandGeomorphicCandidateRoute route = new SkyIslandGeomorphicCandidateRoute(
                points,
                reach.geomorphicRoute().route().totalCost(),
                reach.centerline().pathLength(),
                reach.geomorphicRoute().route().maxGuidanceDeviation(),
                reach.geomorphicRoute().route().uphillStepFraction(),
                reach.geomorphicRoute().route().ridgeSampleFraction(),
                reach.geomorphicRoute().route().meanValleyFloorAdvantage(),
                reach.geomorphicRoute().route().maxUphillStep());
        SkyIslandHydraulicCandidateBuilder.Candidate candidate =
                SkyIslandHydraulicCandidateBuilder.build(route, terrain, configuration);
        double[] candidateBeds = candidate.bedElevationsMetres();
        double bankProbeDistance = maximumHalfWidth * HORIZONTAL_METRES_PER_LOCAL_UNIT;
        double[] candidateStations = candidate.stationsMetres();
        double sourceEnergySlope =
                (candidateBeds[0] - candidateBeds[1]) / (candidateStations[1] - candidateStations[0]);
        if (!(sourceEnergySlope > 0.0) || !Double.isFinite(sourceEnergySlope)) {
            throw new IllegalArgumentException(
                    "source normal depth requires a positive finite candidate-bed energy slope");
        }
        double limitingBankfullSlope = Double.POSITIVE_INFINITY;
        for (int i = 1; i < candidateBeds.length; i++) {
            double localSlope = (candidateBeds[i - 1] - candidateBeds[i])
                    / (candidateStations[i] - candidateStations[i - 1]);
            limitingBankfullSlope = Math.min(limitingBankfullSlope, localSlope);
        }
        if (!(limitingBankfullSlope > 0.0) || !Double.isFinite(limitingBankfullSlope)) {
            throw new IllegalArgumentException(
                    "bankfull capacity requires positive finite grades throughout the candidate reach");
        }
        double bankfullDischarge = manningDischarge(
                bottomWidthMetres,
                sideSlope,
                bankfullDepthMetres,
                limitingBankfullSlope,
                MANNING_ROUGHNESS);
        double[] discharges = Arrays.stream(relativeDischarges)
                .map(value -> bankfullDischarge
                        * Math.max(MINIMUM_RELATIVE_DISCHARGE, value)
                        / maximumRelativeDischarge)
                .toArray();
        SkyIslandStandardStepSolver.CrossSection section = trapezoidalSection(
                bottomWidthMetres, sideSlope);
        HydraulicProfile hydraulic = solveSourceNormalProfile(candidate, discharges, section);
        double requiredIncision = requiredBankIncision(
                candidate,
                points,
                candidateBeds,
                hydraulic.profile(),
                bankProbeDistance,
                limits.maximumBankContainmentDeficitWorldUnits(),
                configuration.bankfullDepthMetres());
        if (requiredIncision > 1.0e-8) {
            configuration = new SkyIslandHydraulicCandidateBuilder.Configuration(
                    configuration.horizontalMetresPerLocalUnit(),
                    configuration.verticalMetresPerNormalizedUnit(),
                    configuration.verticalDatumMetres(),
                    configuration.minimumBedSlope(),
                    configuration.upstreamIncisionMetres() + requiredIncision,
                    configuration.bottomWidthMetres(),
                    configuration.sideSlopeHorizontalToVertical(),
                    configuration.bankfullDepthMetres(),
                    configuration.valleyHalfWidthMetres(),
                    configuration.maximumTerrainEditMetres());
            candidate = SkyIslandHydraulicCandidateBuilder.build(route, terrain, configuration);
            candidateBeds = candidate.bedElevationsMetres();
            hydraulic = solveSourceNormalProfile(candidate, discharges, section);
            double remainingBankDeficit = maximumBankDeficit(
                    candidate,
                    points,
                    candidateBeds,
                    hydraulic.profile(),
                    bankProbeDistance);
            if (remainingBankDeficit
                    > limits.maximumBankContainmentDeficitWorldUnits() + 1.0e-8) {
                throw new IllegalArgumentException(
                        "candidate channel remains overbank after bounded incision; deficit="
                                + remainingBankDeficit
                                + " world units");
            }
        }
        return new SourceReachSolution(
                candidate,
                hydraulic.profile(),
                hydraulic.discharges(),
                MANNING_ROUGHNESS,
                sideSlope,
                minimumGrade,
                sourceEnergySlope,
                hydraulic.sourceNormalDepth(),
                hydraulic.sourceFroudeNumber(),
                hydraulic.flowRegime());
    }

    /** Solves the source-normal-depth boundary and classifies its regime by Froude number. */
    private static HydraulicProfile solveSourceNormalProfile(
            SkyIslandHydraulicCandidateBuilder.Candidate candidate,
            double[] discharges,
            SkyIslandStandardStepSolver.CrossSection section) {
        double sourceNormalDepth = SkyIslandManningNormalDepthSolver.solve(
                discharges[0],
                MANNING_ROUGHNESS,
                candidateSourceSlope(candidate),
                new SkyIslandManningNormalDepthSolver.CrossSection() {
                    @Override
                    public double area(double depthMetres) {
                        return section.area(depthMetres);
                    }

                    @Override
                    public double wettedPerimeter(double depthMetres) {
                        return section.wettedPerimeter(depthMetres);
                    }
                });
        double area = section.area(sourceNormalDepth);
        double froudeSquared = discharges[0] * discharges[0] * section.topWidth(sourceNormalDepth)
                / (GRAVITY_METRES_PER_SECOND_SQUARED * area * area * area);
        if (!(froudeSquared > 0.0) || !Double.isFinite(froudeSquared)
                || Math.abs(froudeSquared - 1.0) <= 1.0e-10) {
            throw new IllegalArgumentException(
                    "source normal depth is critical or numerically indistinguishable from critical flow");
        }
        SkyIslandStandardStepSolver.FlowRegime regime = froudeSquared < 1.0
                ? SkyIslandStandardStepSolver.FlowRegime.SUBCRITICAL
                : SkyIslandStandardStepSolver.FlowRegime.SUPERCRITICAL;
        SkyIslandHydraulicCandidateBuilder.Profile profile = candidate.solve(
                discharges,
                MANNING_ROUGHNESS,
                sourceNormalDepth,
                regime,
                SkyIslandStandardStepSolver.BoundaryLocation.UPSTREAM);
        return new HydraulicProfile(
                profile, discharges, sourceNormalDepth, Math.sqrt(froudeSquared), regime);
    }

    /**
     * Computes the bounded incision needed only where the existing ground, rather than the
     * constructed bankfull section, controls the lower-bank elevation. If the constructed section
     * itself is too small, lowering it cannot repair containment and the candidate fails closed.
     */
    private static double requiredBankIncision(
            SkyIslandHydraulicCandidateBuilder.Candidate candidate,
            List<SkyIslandLocalPosition> points,
            double[] beds,
            SkyIslandHydraulicCandidateBuilder.Profile profile,
            double bankProbeDistance,
            double allowedDeficit,
            double bankfullDepth) {
        double[] stages = profile.waterSurfaceElevationsMetres();
        double required = 0.0;
        for (int i = 0; i < points.size(); i++) {
            SkyIslandLocalPosition[] banks = bankProbes(points, i, bankProbeDistance);
            double lowerBank = Math.min(
                    candidate.sampleTerrainMetres(banks[0]),
                    candidate.sampleTerrainMetres(banks[1]));
            double constructedBank = beds[i] + bankfullDepth;
            double deficit = stages[i] - lowerBank - allowedDeficit;
            if (deficit <= 0.0) {
                continue;
            }
            if (lowerBank >= constructedBank - 1.0e-8) {
                throw new IllegalArgumentException(
                        "standard-step water exceeds the constructed bankfull section by "
                                + deficit
                                + " metres; changing bed datum cannot restore containment");
            }
            required = Math.max(required, deficit);
        }
        return required;
    }

    private static double maximumBankDeficit(
            SkyIslandHydraulicCandidateBuilder.Candidate candidate,
            List<SkyIslandLocalPosition> points,
            double[] beds,
            SkyIslandHydraulicCandidateBuilder.Profile profile,
            double bankProbeDistance) {
        double[] stages = profile.waterSurfaceElevationsMetres();
        double maximum = 0.0;
        for (int i = 0; i < points.size(); i++) {
            SkyIslandLocalPosition[] banks = bankProbes(points, i, bankProbeDistance);
            double lowerBank = Math.min(
                    candidate.sampleTerrainMetres(banks[0]),
                    candidate.sampleTerrainMetres(banks[1]));
            maximum = Math.max(maximum, stages[i] - lowerBank);
        }
        return maximum;
    }

    private static SkyIslandLocalPosition[] bankProbes(
            List<SkyIslandLocalPosition> points,
            int index,
            double distance) {
        SkyIslandLocalPosition before = points.get(index == 0 ? 0 : index - 1);
        SkyIslandLocalPosition after =
                points.get(index == points.size() - 1 ? index : index + 1);
        double dx = after.x() - before.x();
        double dz = after.z() - before.z();
        double length = Math.hypot(dx, dz);
        if (!(length > 0.0)) {
            throw new IllegalArgumentException("channel tangent must be finite and non-zero");
        }
        double normalX = -dz / length;
        double normalZ = dx / length;
        return new SkyIslandLocalPosition[] {
            new SkyIslandLocalPosition(
                    points.get(index).x() + normalX * distance,
                    points.get(index).z() + normalZ * distance),
            new SkyIslandLocalPosition(
                    points.get(index).x() - normalX * distance,
                    points.get(index).z() - normalZ * distance)
        };
    }

    private record HydraulicProfile(
            SkyIslandHydraulicCandidateBuilder.Profile profile,
            double[] discharges,
            double sourceNormalDepth,
            double sourceFroudeNumber,
            SkyIslandStandardStepSolver.FlowRegime flowRegime) {
        private HydraulicProfile {
            profile = Objects.requireNonNull(profile, "profile");
            discharges = discharges.clone();
            flowRegime = Objects.requireNonNull(flowRegime, "flowRegime");
        }
    }

    private static double candidateSourceSlope(SkyIslandHydraulicCandidateBuilder.Candidate candidate) {
        double[] stations = candidate.stationsMetres();
        double[] beds = candidate.bedElevationsMetres();
        double slope = (beds[0] - beds[1]) / (stations[1] - stations[0]);
        if (!(slope > 0.0) || !Double.isFinite(slope)) {
            throw new IllegalArgumentException(
                    "source normal depth requires a positive finite candidate-bed energy slope");
        }
        return slope;
    }



    private static double manningDischarge(
            double bottomWidth,
            double sideSlope,
            double depth,
            double bedSlope,
            double roughness) {
        double area = bottomWidth * depth + sideSlope * depth * depth;
        double perimeter = bottomWidth + 2.0 * depth * Math.hypot(1.0, sideSlope);
        double hydraulicRadius = area / perimeter;
        return area * Math.pow(hydraulicRadius, 2.0 / 3.0) * Math.sqrt(bedSlope) / roughness;
    }

    private static SkyIslandStandardStepSolver.CrossSection trapezoidalSection(
            double bottomWidth,
            double sideSlope) {
        return new SkyIslandStandardStepSolver.CrossSection() {
            @Override
            public double area(double depthMetres) {
                return bottomWidth * depthMetres + sideSlope * depthMetres * depthMetres;
            }

            @Override
            public double wettedPerimeter(double depthMetres) {
                return bottomWidth + 2.0 * depthMetres * Math.hypot(1.0, sideSlope);
            }

            @Override
            public double topWidth(double depthMetres) {
                return bottomWidth + 2.0 * sideSlope * depthMetres;
            }
        };
    }

    public record SourceReachSolution(
            SkyIslandHydraulicCandidateBuilder.Candidate candidate,
            SkyIslandHydraulicCandidateBuilder.Profile profile,
            double[] dischargesCubicMetresPerSecond,
            double manningRoughness,
            double sideSlopeHorizontalToVertical,
            double minimumBedSlope,
            double sourceEnergySlope,
            double sourceNormalDepthMetres,
            double sourceFroudeNumber,
            SkyIslandStandardStepSolver.FlowRegime flowRegime) {
        public SourceReachSolution {
            candidate = Objects.requireNonNull(candidate, "candidate");
            profile = Objects.requireNonNull(profile, "profile");
            flowRegime = Objects.requireNonNull(flowRegime, "flowRegime");
            dischargesCubicMetresPerSecond = dischargesCubicMetresPerSecond.clone();
        }

        @Override
        public double[] dischargesCubicMetresPerSecond() {
            return dischargesCubicMetresPerSecond.clone();
        }
    }
}
