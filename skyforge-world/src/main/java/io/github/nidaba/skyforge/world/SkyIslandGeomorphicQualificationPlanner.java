package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Hard geomorphic plausibility gate for candidate fluvial geometry.
 *
 * <p>This planner never changes terrain. It converts normalized vertical potentials into authored
 * world units through the island relief budget, evaluates dimensionless geometry ratios, and reports
 * explicit rejection causes. A rejected candidate must be refined/rerouted/reclassified upstream;
 * downstream backends are not allowed to rescue it with deeper excavation.
 */
public final class SkyIslandGeomorphicQualificationPlanner {
    public static final double MAXIMUM_RIDGE_SAMPLE_FRACTION = 0.08;
    public static final double MAXIMUM_LOWERING_RELIEF_FRACTION = 0.18;
    public static final double MAXIMUM_WATER_DEPTH_TO_FULL_WIDTH = 0.35;
    public static final double MAXIMUM_NORMALIZED_EXCAVATION_VOLUME = 0.10;
    public static final double MAXIMUM_RAW_ASCENT_TO_FULL_WIDTH = 0.30;

    private static final double EPSILON = 1.0e-12;

    private SkyIslandGeomorphicQualificationPlanner() {}

    public static SkyIslandGeomorphicQualificationPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandHydraulicChannelNetworkPlan hydraulic =
                SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor);
        SkyIslandSemanticField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);
        SkyIslandSemanticField interiority =
                SkyIslandSemanticFieldSet.create(descriptor).interiority();
        return plan(descriptor, hydraulic, terrain, interiority);
    }

    static SkyIslandGeomorphicQualificationPlan plan(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicChannelNetworkPlan hydraulic,
            SkyIslandSemanticField terrain,
            SkyIslandSemanticField interiority) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(hydraulic, "hydraulic");
        Objects.requireNonNull(terrain, "terrain");
        Objects.requireNonNull(interiority, "interiority");
        if (!descriptor.equals(hydraulic.descriptor())) {
            throw new IllegalArgumentException("hydraulic plan descriptor must match qualification descriptor");
        }

        List<SkyIslandGeomorphicReachQualification> reaches =
                new ArrayList<>(hydraulic.reaches().size());
        for (SkyIslandHydraulicReachGeometry reach : hydraulic.reaches()) {
            SkyIslandContinuousChannelCenterline centerline =
                    SkyIslandContinuousChannelCenterlinePlanner.refine(
                            reach.geomorphicRoute().route(),
                            terrain,
                            interiority,
                            hydraulic.geomorphicNetwork().planningSpacing());
            reaches.add(qualifyReach(descriptor, reach, centerline));
        }
        reaches.sort(Comparator
                .comparingInt((SkyIslandGeomorphicReachQualification qualification) ->
                        qualification.hydraulicReach()
                                .geomorphicRoute()
                                .semanticReach()
                                .startCellIndex())
                .thenComparingInt(qualification ->
                        qualification.hydraulicReach()
                                .geomorphicRoute()
                                .semanticReach()
                                .endCellIndex()));
        return new SkyIslandGeomorphicQualificationPlan(descriptor, hydraulic, reaches);
    }

    static SkyIslandGeomorphicReachQualification qualifyReach(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachGeometry reach,
            SkyIslandContinuousChannelCenterline centerline) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(reach, "reach");
        Objects.requireNonNull(centerline, "centerline");

        double relief = descriptor.reliefBudget();
        double fullWidth = Math.max(EPSILON, 2.0 * reach.maximumBankfullHalfWidth());
        double maximumLoweringWorld = reach.maximumRequiredLowering() * relief;
        double incisionToWidth = maximumLoweringWorld / fullWidth;
        double depthToWidth = reach.maximumWaterDepthPotential() * relief / fullWidth;
        double lateralGrade =
                maximumLoweringWorld / Math.max(EPSILON, reach.maximumBankfullHalfWidth());
        double longitudinalGrade = reach.maximumWaterSurfaceSlope() * relief;
        double curvatureRatio =
                minimumCurvatureRadius(centerline.points()) / fullWidth;
        double excavationVolume =
                estimatedExcavationVolume(reach, relief);
        double normalizationVolume =
                Math.max(
                        EPSILON,
                        reach.pathLength()
                                * fullWidth
                                * relief);
        double normalizedExcavation = excavationVolume / normalizationVolume;
        double rawAscentWorld =
                reach.geomorphicRoute().route().maxUphillStep() * relief;
        double rawAscentToWidth = rawAscentWorld / fullWidth;
        double ridgeFraction =
                reach.geomorphicRoute().route().ridgeSampleFraction();

        Thresholds thresholds = thresholds(reach.geomorphicRoute().semanticReach());
        List<SkyIslandGeomorphicQualificationFailureKind> failures =
                new ArrayList<>();

        if (ridgeFraction > MAXIMUM_RIDGE_SAMPLE_FRACTION + EPSILON) {
            failures.add(SkyIslandGeomorphicQualificationFailureKind.RIDGE_OCCUPANCY);
        }
        if (maximumLoweringWorld
                > descriptor.reliefBudget() * MAXIMUM_LOWERING_RELIEF_FRACTION + EPSILON) {
            failures.add(SkyIslandGeomorphicQualificationFailureKind.EXCESSIVE_CENTERLINE_LOWERING);
        }
        if (incisionToWidth > thresholds.maximumIncisionToFullWidth() + EPSILON) {
            failures.add(SkyIslandGeomorphicQualificationFailureKind.EXCESSIVE_INCISION_TO_WIDTH);
        }
        if (depthToWidth > MAXIMUM_WATER_DEPTH_TO_FULL_WIDTH + EPSILON) {
            failures.add(SkyIslandGeomorphicQualificationFailureKind.EXCESSIVE_WATER_DEPTH_TO_WIDTH);
        }
        if (lateralGrade > thresholds.maximumLateralGrade() + EPSILON) {
            failures.add(SkyIslandGeomorphicQualificationFailureKind.EXCESSIVE_LATERAL_GRADE);
        }
        if (longitudinalGrade > thresholds.maximumLongitudinalGrade() + EPSILON) {
            failures.add(SkyIslandGeomorphicQualificationFailureKind.EXCESSIVE_LONGITUDINAL_GRADE);
        }
        if (curvatureRatio < thresholds.minimumCurvatureRadiusToFullWidth() - EPSILON) {
            failures.add(SkyIslandGeomorphicQualificationFailureKind.EXCESSIVE_CURVATURE);
        }
        if (normalizedExcavation > MAXIMUM_NORMALIZED_EXCAVATION_VOLUME + EPSILON) {
            failures.add(SkyIslandGeomorphicQualificationFailureKind.EXCESSIVE_EXCAVATION_VOLUME);
        }
        if (rawAscentToWidth > MAXIMUM_RAW_ASCENT_TO_FULL_WIDTH + EPSILON) {
            failures.add(SkyIslandGeomorphicQualificationFailureKind.EXCESSIVE_RAW_TERRAIN_ASCENT);
        }

        return new SkyIslandGeomorphicReachQualification(
                reach,
                centerline,
                failures,
                ridgeFraction,
                maximumLoweringWorld,
                incisionToWidth,
                depthToWidth,
                lateralGrade,
                longitudinalGrade,
                Double.isInfinite(curvatureRatio) ? Double.MAX_VALUE : curvatureRatio,
                normalizedExcavation,
                rawAscentWorld);
    }

    private static Thresholds thresholds(
            SkyIslandSemanticChannelReach reach) {
        double maxIncision = Double.POSITIVE_INFINITY;
        double maxLateralGrade = Double.POSITIVE_INFINITY;
        double maxLongitudinalGrade = Double.POSITIVE_INFINITY;
        double minCurvatureRatio = 0.0;

        for (SkyIslandChannelProfile profile : reach.profiles()) {
            Thresholds current = switch (profile.kind()) {
                case ALLUVIAL -> new Thresholds(0.55, 0.85, 0.08, 1.50);
                case INCISED -> new Thresholds(1.10, 1.50, 0.20, 1.00);
                case CASCADE -> new Thresholds(1.60, 2.20, 0.65, 0.70);
            };
            maxIncision = Math.min(maxIncision, current.maximumIncisionToFullWidth());
            maxLateralGrade = Math.min(maxLateralGrade, current.maximumLateralGrade());
            maxLongitudinalGrade =
                    Math.min(maxLongitudinalGrade, current.maximumLongitudinalGrade());
            minCurvatureRatio =
                    Math.max(minCurvatureRatio, current.minimumCurvatureRadiusToFullWidth());
        }
        return new Thresholds(
                maxIncision,
                maxLateralGrade,
                maxLongitudinalGrade,
                minCurvatureRatio);
    }

    private static double estimatedExcavationVolume(
            SkyIslandHydraulicReachGeometry reach,
            double reliefBudget) {
        List<SkyIslandHydraulicGeometrySample> samples = reach.samples();
        double volume = 0.0;
        for (int i = 1; i < samples.size(); i++) {
            SkyIslandHydraulicGeometrySample a = samples.get(i - 1);
            SkyIslandHydraulicGeometrySample b = samples.get(i);
            double ds = Math.hypot(
                    b.position().x() - a.position().x(),
                    b.position().z() - a.position().z());
            double loweringWorld =
                    0.5 * (a.requiredCenterlineLowering() + b.requiredCenterlineLowering())
                            * reliefBudget;
            double halfWidth =
                    0.5 * (a.bankfullHalfWidth() + b.bankfullHalfWidth());
            // Triangular bankfull cut proxy: cross-section area ~= halfWidth * lowering.
            volume += halfWidth * loweringWorld * ds;
        }
        return volume;
    }

    private static double minimumCurvatureRadius(
            List<SkyIslandLocalPosition> points) {
        double minimum = Double.POSITIVE_INFINITY;
        for (int i = 1; i < points.size() - 1; i++) {
            SkyIslandLocalPosition a = points.get(i - 1);
            SkyIslandLocalPosition b = points.get(i);
            SkyIslandLocalPosition c = points.get(i + 1);
            double ab = distance(a, b);
            double bc = distance(b, c);
            double ca = distance(c, a);
            double twiceArea = Math.abs(
                    (b.x() - a.x()) * (c.z() - a.z())
                            - (b.z() - a.z()) * (c.x() - a.x()));
            if (twiceArea <= EPSILON) {
                continue;
            }
            double radius = ab * bc * ca / (2.0 * twiceArea);
            if (Double.isFinite(radius)) {
                minimum = Math.min(minimum, radius);
            }
        }
        return minimum;
    }

    private static double distance(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b) {
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }

    private record Thresholds(
            double maximumIncisionToFullWidth,
            double maximumLateralGrade,
            double maximumLongitudinalGrade,
            double minimumCurvatureRadiusToFullWidth) {}
}
