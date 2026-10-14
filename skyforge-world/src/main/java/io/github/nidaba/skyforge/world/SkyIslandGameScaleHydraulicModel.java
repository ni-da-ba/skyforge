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

        double[] candidateStations = candidate.stationsMetres();
        double[] candidateBeds = candidate.bedElevationsMetres();
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
        double[] containedDepthLimits = maximumContainedDepths(
                candidate,
                points,
                candidateBeds,
                maximumHalfWidth * HORIZONTAL_METRES_PER_LOCAL_UNIT,
                limits.maximumBankContainmentDeficitWorldUnits());
        HydraulicProfile hydraulic = calibrateBankfullContainedProfile(
                candidate,
                discharges,
                section,
                containedDepthLimits);
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

    /**
     * Finds the largest positive scale of the authored relative discharge profile whose standard-step
     * solution remains below the lower candidate bank at every station. Manning normal depth supplies the
     * source boundary at each trial; the source Froude number selects the physically consistent
     * gradually-varied-flow branch. This is a monotone bracket-and-bisect calibration, not a
     * correction to the hydraulic equations.
     */
    private static HydraulicProfile calibrateBankfullContainedProfile(
            SkyIslandHydraulicCandidateBuilder.Candidate candidate,
            double[] capacityScaledDischarges,
            SkyIslandStandardStepSolver.CrossSection section,
            double[] containedDepthLimits) {
        double lowerScale = 0.0;
        double upperScale = 1.0;
        HydraulicProfile best = null;
        for (int shrink = 0; shrink < 40; shrink++) {
            double trialScale = upperScale;
            HydraulicProfile trial = tryProfile(
                    candidate, capacityScaledDischarges, section, trialScale);
            if (trial != null && fitsWithinBankLimits(trial.profile(), containedDepthLimits)) {
                lowerScale = trialScale;
                best = trial;
                break;
            }
            upperScale *= 0.5;
        }
        if (best == null) {
            throw new IllegalArgumentException(
                    "no positive source-normal-depth profile fits within candidate bank elevations");
        }

        double infeasibleScale = 1.0;
        for (int iteration = 0; iteration < 40; iteration++) {
            double trialScale = (lowerScale + infeasibleScale) * 0.5;
            HydraulicProfile trial = tryProfile(
                    candidate, capacityScaledDischarges, section, trialScale);
            if (trial != null && fitsWithinBankLimits(trial.profile(), containedDepthLimits)) {
                lowerScale = trialScale;
                best = trial;
            } else {
                infeasibleScale = trialScale;
            }
        }
        return best;
    }

    private static HydraulicProfile tryProfile(
            SkyIslandHydraulicCandidateBuilder.Candidate candidate,
            double[] capacityScaledDischarges,
            SkyIslandStandardStepSolver.CrossSection section,
            double dischargeScale) {
        double[] discharges = Arrays.stream(capacityScaledDischarges)
                .map(value -> value * dischargeScale)
                .toArray();
        try {
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
                return null;
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
        } catch (IllegalArgumentException noRegimeConsistentProfile) {
            return null;
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

    private static boolean fitsWithinBankLimits(
            SkyIslandHydraulicCandidateBuilder.Profile profile,
            double[] maximumContainedDepthsMetres) {
        double[] depths = profile.depthsMetres();
        if (depths.length != maximumContainedDepthsMetres.length) {
            throw new IllegalArgumentException("bank limits must align with hydraulic profile stations");
        }
        for (int i = 0; i < depths.length; i++) {
            if (depths[i] > maximumContainedDepthsMetres[i] + 1.0e-9) {
                return false;
            }
        }
        return true;
    }

    private static double[] maximumContainedDepths(
            SkyIslandHydraulicCandidateBuilder.Candidate candidate,
            List<SkyIslandLocalPosition> points,
            double[] beds,
            double bankProbeDistance,
            double allowedDeficitMetres) {
        double[] limits = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            SkyIslandLocalPosition before = points.get(i == 0 ? 0 : i - 1);
            SkyIslandLocalPosition after = points.get(i == points.size() - 1 ? i : i + 1);
            double dx = after.x() - before.x();
            double dz = after.z() - before.z();
            double length = Math.hypot(dx, dz);
            if (!(length > 0.0)) {
                throw new IllegalArgumentException("channel tangent must be finite and non-zero");
            }
            double normalX = -dz / length;
            double normalZ = dx / length;
            SkyIslandLocalPosition leftBank = new SkyIslandLocalPosition(
                    points.get(i).x() + normalX * bankProbeDistance,
                    points.get(i).z() + normalZ * bankProbeDistance);
            SkyIslandLocalPosition rightBank = new SkyIslandLocalPosition(
                    points.get(i).x() - normalX * bankProbeDistance,
                    points.get(i).z() - normalZ * bankProbeDistance);
            double lowerBank = Math.min(
                    candidate.sampleTerrainMetres(leftBank),
                    candidate.sampleTerrainMetres(rightBank));
            limits[i] = lowerBank - beds[i] + allowedDeficitMetres;
            if (!Double.isFinite(limits[i])) {
                throw new IllegalArgumentException("candidate bank depth limit must be finite");
            }
        }
        return limits;
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
