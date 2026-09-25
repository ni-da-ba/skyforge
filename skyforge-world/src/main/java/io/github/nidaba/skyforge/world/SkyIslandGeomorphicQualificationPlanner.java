package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Measures and rejects implausible candidate channel geometry before terrain authoring.
 *
 * <p>This planner cannot mutate terrain. Its only output is objective evidence plus an accept/reject
 * decision under an explicit calibration policy.
 */
public final class SkyIslandGeomorphicQualificationPlanner {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandGeomorphicQualificationPlanner() {}

    public static SkyIslandGeomorphicQualificationPlan qualify(SkyIslandDescriptor descriptor) {
        return qualify(
                descriptor,
                SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor),
                SkyIslandPreHydrologicTerrainField.create(descriptor),
                SkyIslandGeomorphicQualificationPolicy.provisional());
    }

    static SkyIslandGeomorphicQualificationPlan qualify(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicChannelNetworkPlan hydraulic,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(hydraulic, "hydraulic");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(policy, "policy");
        if (!descriptor.equals(hydraulic.descriptor())) {
            throw new IllegalArgumentException("hydraulic plan descriptor must match qualification descriptor");
        }

        List<SkyIslandGeomorphicReachQualification> reaches = hydraulic.reaches().stream()
                .map(reach -> qualifyReach(descriptor, reach, terrain, policy))
                .sorted(Comparator
                        .comparingInt((SkyIslandGeomorphicReachQualification qualification) ->
                                qualification.hydraulicReach()
                                        .geomorphicRoute()
                                        .semanticReach()
                                        .startCellIndex())
                        .thenComparingInt(qualification ->
                                qualification.hydraulicReach()
                                        .geomorphicRoute()
                                        .semanticReach()
                                        .endCellIndex()))
                .toList();

        return new SkyIslandGeomorphicQualificationPlan(descriptor, hydraulic, policy, reaches);
    }

    static SkyIslandGeomorphicReachQualification qualifyReach(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachGeometry reach,
            SkyIslandSemanticField terrain,
            SkyIslandGeomorphicQualificationPolicy policy) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(reach, "reach");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(policy, "policy");

        List<SkyIslandHydraulicGeometrySample> samples = reach.samples();
        List<SkyIslandLocalPosition> points = reach.geomorphicRoute().route().points();
        List<SkyIslandChannelProfile> profiles = reach.geomorphicRoute().semanticReach().profiles();
        if (samples.size() != points.size()) {
            throw new IllegalArgumentException("hydraulic samples and route points must align");
        }

        Set<SkyIslandGeomorphicQualificationViolation> violations =
                EnumSet.noneOf(SkyIslandGeomorphicQualificationViolation.class);

        double reliefBudget = descriptor.reliefBudget();
        double maximumLoweringPotential = 0.0;
        double maximumLoweringWorld = 0.0;
        double maximumLateralGrade = 0.0;
        double maximumCurvatureWidthRatio = 0.0;
        double maximumLongitudinalGrade = 0.0;
        double excavationVolumeProxy = 0.0;
        double weightedBurdenNumerator = 0.0;
        double weightedBurdenDenominator = 0.0;

        for (int i = 0; i < samples.size(); i++) {
            SkyIslandHydraulicGeometrySample sample = samples.get(i);
            SkyIslandChannelProfileKind kind = profileKind(profiles, sample.stationFraction());
            maximumLoweringPotential =
                    Math.max(maximumLoweringPotential, sample.requiredCenterlineLowering());
            double loweringWorld = sample.requiredCenterlineLowering() * reliefBudget;
            maximumLoweringWorld = Math.max(maximumLoweringWorld, loweringWorld);
            if (sample.requiredCenterlineLowering()
                    > policy.maxCenterlineLoweringPotential(kind) + EPSILON) {
                violations.add(SkyIslandGeomorphicQualificationViolation.CENTERLINE_LOWERING);
            }

            Vector tangent = tangent(points, i);
            Vector normal = new Vector(-tangent.z(), tangent.x());
            double valleyHalfWidth =
                    sample.bankfullHalfWidth() * valleyMultiplier(kind);
            double leftTerrain = terrain.sample(offset(sample.position(), normal, valleyHalfWidth));
            double rightTerrain = terrain.sample(offset(sample.position(), normal, -valleyHalfWidth));
            double leftRise =
                    Math.max(0.0, leftTerrain - sample.bedElevationPotential()) * reliefBudget;
            double rightRise =
                    Math.max(0.0, rightTerrain - sample.bedElevationPotential()) * reliefBudget;
            double lateralGrade = Math.max(leftRise, rightRise) / valleyHalfWidth;
            maximumLateralGrade = Math.max(maximumLateralGrade, lateralGrade);
            if (lateralGrade > policy.maxLateralRecoveryGrade(kind) + EPSILON) {
                violations.add(SkyIslandGeomorphicQualificationViolation.LATERAL_RECOVERY_GRADE);
            }

            if (i > 0) {
                double ds = distance(points.get(i - 1), points.get(i));
                double previousLowering =
                        samples.get(i - 1).requiredCenterlineLowering() * reliefBudget;
                double previousWidth = 2.0 * samples.get(i - 1).bankfullHalfWidth();
                double currentArea = loweringWorld * 2.0 * sample.bankfullHalfWidth();
                double previousArea = previousLowering * previousWidth;
                excavationVolumeProxy += 0.5 * (previousArea + currentArea) * ds;

                double meanWidth = 0.5 * (previousWidth + 2.0 * sample.bankfullHalfWidth());
                weightedBurdenNumerator +=
                        0.5
                                * (samples.get(i - 1).requiredCenterlineLowering()
                                        + sample.requiredCenterlineLowering())
                                * meanWidth
                                * ds;
                weightedBurdenDenominator += meanWidth * ds;

                double longitudinalGrade =
                        Math.abs(
                                        sample.waterSurfacePotential()
                                                - samples.get(i - 1).waterSurfacePotential())
                                * reliefBudget
                                / Math.max(ds, EPSILON);
                maximumLongitudinalGrade =
                        Math.max(maximumLongitudinalGrade, longitudinalGrade);
                if (longitudinalGrade > policy.maxLongitudinalGrade(kind) + EPSILON) {
                    violations.add(SkyIslandGeomorphicQualificationViolation.LONGITUDINAL_GRADE);
                }
            }

            if (i > 0 && i + 1 < points.size()) {
                double curvature = curvature(points.get(i - 1), points.get(i), points.get(i + 1));
                double ratio = curvature * 2.0 * sample.bankfullHalfWidth();
                maximumCurvatureWidthRatio = Math.max(maximumCurvatureWidthRatio, ratio);
                if (ratio > policy.maxCurvatureWidthRatio(kind) + EPSILON) {
                    violations.add(SkyIslandGeomorphicQualificationViolation.CURVATURE_TO_WIDTH);
                }
            }
        }

        double burden = weightedBurdenDenominator <= EPSILON
                ? 0.0
                : weightedBurdenNumerator / weightedBurdenDenominator;
        if (burden > policy.maxNormalizedExcavationBurden() + EPSILON) {
            violations.add(SkyIslandGeomorphicQualificationViolation.EXCAVATION_BURDEN);
        }

        double ridgeFraction = reach.geomorphicRoute().route().ridgeSampleFraction();
        if (ridgeFraction > policy.maxRidgeSampleFraction() + EPSILON) {
            violations.add(SkyIslandGeomorphicQualificationViolation.RIDGE_OCCUPANCY);
        }

        List<SkyIslandGeomorphicQualificationViolation> orderedViolations =
                violations.stream().sorted(Comparator.comparing(Enum::ordinal)).toList();
        return new SkyIslandGeomorphicReachQualification(
                reach,
                maximumLoweringPotential,
                maximumLoweringWorld,
                maximumLateralGrade,
                burden,
                maximumCurvatureWidthRatio,
                ridgeFraction,
                maximumLongitudinalGrade,
                excavationVolumeProxy,
                orderedViolations);
    }

    private static SkyIslandChannelProfileKind profileKind(
            List<SkyIslandChannelProfile> profiles,
            double stationFraction) {
        int index = Math.min(
                profiles.size() - 1,
                (int) Math.floor(Math.max(0.0, Math.min(0.999999999, stationFraction)) * profiles.size()));
        return profiles.get(index).kind();
    }

    private static double valleyMultiplier(SkyIslandChannelProfileKind kind) {
        return switch (kind) {
            case ALLUVIAL -> 3.5;
            case INCISED -> 2.5;
            case CASCADE -> 1.8;
        };
    }

    private static Vector tangent(List<SkyIslandLocalPosition> points, int index) {
        SkyIslandLocalPosition a = index == 0 ? points.get(0) : points.get(index - 1);
        SkyIslandLocalPosition b =
                index == points.size() - 1 ? points.get(points.size() - 1) : points.get(index + 1);
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        double length = Math.hypot(dx, dz);
        if (length <= EPSILON) {
            return new Vector(1.0, 0.0);
        }
        return new Vector(dx / length, dz / length);
    }

    private static double curvature(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b,
            SkyIslandLocalPosition c) {
        double ax = b.x() - a.x();
        double az = b.z() - a.z();
        double bx = c.x() - b.x();
        double bz = c.z() - b.z();
        double la = Math.hypot(ax, az);
        double lb = Math.hypot(bx, bz);
        if (la <= EPSILON || lb <= EPSILON) {
            return 0.0;
        }
        double cosine = Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / (la * lb)));
        double angle = Math.acos(cosine);
        return angle / (0.5 * (la + lb));
    }

    private static SkyIslandLocalPosition offset(
            SkyIslandLocalPosition position,
            Vector normal,
            double distance) {
        return new SkyIslandLocalPosition(
                position.x() + normal.x() * distance,
                position.z() + normal.z() * distance);
    }

    private static double distance(SkyIslandLocalPosition a, SkyIslandLocalPosition b) {
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }

    private record Vector(double x, double z) {}
}
