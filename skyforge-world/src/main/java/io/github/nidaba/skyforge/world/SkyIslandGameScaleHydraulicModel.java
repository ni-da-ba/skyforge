package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Explicit game-scale mapping for the steady open-channel candidate model.
 *
 * <p>Minecraft horizontal units map one-to-one to metres; normalized relief maps to the
 * descriptor's authored relief budget. Manning n and trapezoidal side slope are explicit model
 * assumptions. Relative discharge is scaled so the reach's maximum authored flow equals its
 * bankfull Manning capacity at the measured source-to-outlet grade. Source reaches use Manning
 * normal depth as their upstream boundary. This is a calibrated game model, not a claim that the
 * descriptor supplies physical rainfall or material measurements.
 */
public final class SkyIslandGameScaleHydraulicModel {
    public static final double MANNING_ROUGHNESS = 0.035;
    public static final double HORIZONTAL_METRES_PER_LOCAL_UNIT = 1.0;
    public static final double SIDE_SLOPE_HORIZONTAL_TO_VERTICAL = 2.0;
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
        double bottomWidthMetres = bankfullWidthMetres
                - 2.0 * SIDE_SLOPE_HORIZONTAL_TO_VERTICAL * bankfullDepthMetres;
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

        double bankEdge = bottomWidthMetres * 0.5
                + SIDE_SLOPE_HORIZONTAL_TO_VERTICAL * bankfullDepthMetres;
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
                        SIDE_SLOPE_HORIZONTAL_TO_VERTICAL,
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

        double[] candidateStations = candidate.stationsMetres();
        double[] candidateBeds = candidate.bedElevationsMetres();
        double sourceEnergySlope =
                (candidateBeds[0] - candidateBeds[1]) / (candidateStations[1] - candidateStations[0]);
        if (!(sourceEnergySlope > 0.0) || !Double.isFinite(sourceEnergySlope)) {
            throw new IllegalArgumentException(
                    "source normal depth requires a positive finite candidate-bed energy slope");
        }
        double bankfullDischarge = manningDischarge(
                bottomWidthMetres,
                SIDE_SLOPE_HORIZONTAL_TO_VERTICAL,
                bankfullDepthMetres,
                sourceEnergySlope,
                MANNING_ROUGHNESS);
        double[] discharges = Arrays.stream(relativeDischarges)
                .map(value -> bankfullDischarge
                        * Math.max(MINIMUM_RELATIVE_DISCHARGE, value)
                        / maximumRelativeDischarge)
                .toArray();
        SkyIslandStandardStepSolver.CrossSection section = trapezoidalSection(
                bottomWidthMetres, SIDE_SLOPE_HORIZONTAL_TO_VERTICAL);
        double sourceDepth = SkyIslandManningNormalDepthSolver.solve(
                discharges[0],
                MANNING_ROUGHNESS,
                sourceEnergySlope,
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
        SkyIslandHydraulicCandidateBuilder.Profile profile = candidate.solve(
                discharges,
                MANNING_ROUGHNESS,
                sourceDepth,
                SkyIslandStandardStepSolver.FlowRegime.SUBCRITICAL,
                SkyIslandStandardStepSolver.BoundaryLocation.UPSTREAM);
        return new SourceReachSolution(
                candidate,
                profile,
                discharges,
                MANNING_ROUGHNESS,
                minimumGrade,
                sourceEnergySlope,
                sourceDepth);
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
            double minimumBedSlope,
            double sourceEnergySlope,
            double sourceNormalDepthMetres) {
        public SourceReachSolution {
            candidate = Objects.requireNonNull(candidate, "candidate");
            profile = Objects.requireNonNull(profile, "profile");
            dischargesCubicMetresPerSecond = dischargesCubicMetresPerSecond.clone();
        }

        @Override
        public double[] dischargesCubicMetresPerSecond() {
            return dischargesCubicMetresPerSecond.clone();
        }
    }
}
