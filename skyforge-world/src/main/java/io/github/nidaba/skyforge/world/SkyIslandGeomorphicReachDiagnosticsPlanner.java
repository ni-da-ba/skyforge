package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Measures geomorphic plausibility of candidate hydrology without authoring or mutating terrain.
 *
 * <p>This class intentionally contains no acceptance thresholds. D1 exists to characterize the
 * distributions needed to calibrate hard rejection policy. Curvature and cross-section measurements
 * use accepted continuous geometry, never the raw search lattice.
 */
public final class SkyIslandGeomorphicReachDiagnosticsPlanner {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandGeomorphicReachDiagnosticsPlanner() {}

    public static List<SkyIslandGeomorphicReachDiagnostics> measure(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicChannelNetworkPlan hydraulic,
            SkyIslandSemanticField terrain) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(hydraulic, "hydraulic");
        Objects.requireNonNull(terrain, "terrain");
        if (!descriptor.equals(hydraulic.descriptor())) {
            throw new IllegalArgumentException("hydraulic descriptor must match diagnostics descriptor");
        }

        double planningSpacing =
                SkyIslandSemanticChannelReachPlanner.plan(descriptor).planningSpacing();
        List<SkyIslandGeomorphicReachDiagnostics> result = new ArrayList<>(hydraulic.reaches().size());
        for (SkyIslandHydraulicReachGeometry reach : hydraulic.reaches()) {
            result.add(measureReach(descriptor, reach, terrain, planningSpacing));
        }
        return List.copyOf(result);
    }

    public static List<SkyIslandGeomorphicReachDiagnostics> measure(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return measure(
                descriptor,
                SkyIslandHydraulicChannelNetworkPlanner.plan(descriptor),
                SkyIslandPreHydrologicTerrainField.create(descriptor));
    }

    static SkyIslandGeomorphicReachDiagnostics measureReach(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachGeometry reach,
            SkyIslandSemanticField terrain) {
        return measureReach(
                descriptor,
                reach,
                terrain,
                SkyIslandSemanticChannelReachPlanner.plan(descriptor).planningSpacing());
    }

    static SkyIslandGeomorphicReachDiagnostics measureReach(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachGeometry reach,
            SkyIslandSemanticField terrain,
            double planningSpacing) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(reach, "reach");
        Objects.requireNonNull(terrain, "terrain");

        SkyIslandGeomorphicMeasurements measurements =
                measureGeometry(
                        descriptor,
                        reach.geomorphicRoute().semanticReach(),
                        reach.centerline().points(),
                        reach.samples(),
                        terrain,
                        planningSpacing);
        return new SkyIslandGeomorphicReachDiagnostics(
                reach,
                measurements.maximumCenterlineLoweringPotential(),
                measurements.maximumCenterlineLoweringWorldUnits(),
                measurements.maximumLateralRecoveryGrade(),
                measurements.maximumBankContainmentDeficitWorldUnits(),
                measurements.maximumDepthToBankfullWidthRatio(),
                measurements.maximumReliefToValleyWidthRatio(),
                measurements.normalizedExcavationBurden(),
                measurements.excavationVolumeProxyWorldUnitsCubed(),
                measurements.maximumCurvatureWidthRatio(),
                measurements.ridgeLengthFraction(),
                measurements.maximumLongitudinalGrade());
    }

    static SkyIslandGeomorphicMeasurements measureGeometry(
            SkyIslandDescriptor descriptor,
            SkyIslandSemanticChannelReach semantic,
            List<SkyIslandLocalPosition> points,
            List<SkyIslandHydraulicGeometrySample> samples,
            SkyIslandSemanticField terrain,
            double planningSpacing) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(semantic, "semantic");
        points = List.copyOf(points);
        samples = List.copyOf(samples);
        Objects.requireNonNull(terrain, "terrain");
        if (!Double.isFinite(planningSpacing) || planningSpacing <= 0.0) {
            throw new IllegalArgumentException("planningSpacing must be finite and positive");
        }
        if (samples.size() != points.size() || samples.size() < 2) {
            throw new IllegalArgumentException(
                    "hydraulic samples and continuous points must align with at least two samples");
        }
        points.forEach(point -> Objects.requireNonNull(point, "point"));
        samples.forEach(sample -> Objects.requireNonNull(sample, "sample"));

        List<SkyIslandChannelProfile> profiles = semantic.profiles();
        double reliefBudget = descriptor.reliefBudget();
        double maximumLoweringPotential = 0.0;
        double maximumLoweringWorld = 0.0;
        double maximumLateralGrade = 0.0;
        double maximumContainmentDeficit = 0.0;
        double maximumDepthToWidth = 0.0;
        double maximumReliefToValleyWidth = 0.0;
        double maximumCurvatureWidthRatio = 0.0;
        double maximumLongitudinalGrade = 0.0;
        double excavationVolumeProxy = 0.0;
        double weightedBurdenNumerator = 0.0;
        double weightedBurdenDenominator = 0.0;

        for (int i = 0; i < samples.size(); i++) {
            SkyIslandHydraulicGeometrySample sample = samples.get(i);
            SkyIslandChannelProfileKind kind = profileKind(profiles, sample.stationFraction());
            double loweringPotential = sample.requiredCenterlineLowering();
            double loweringWorld = loweringPotential * reliefBudget;
            maximumLoweringPotential = Math.max(maximumLoweringPotential, loweringPotential);
            maximumLoweringWorld = Math.max(maximumLoweringWorld, loweringWorld);

            Vector tangent = tangent(points, i);
            Vector normal = new Vector(-tangent.z(), tangent.x());
            double valleyHalfWidth = sample.bankfullHalfWidth() * valleyMultiplier(kind);
            double bankProbe = sample.bankfullHalfWidth();

            double leftValleyTerrain =
                    terrain.sample(offset(sample.position(), normal, valleyHalfWidth));
            double rightValleyTerrain =
                    terrain.sample(offset(sample.position(), normal, -valleyHalfWidth));
            double leftValleyRise =
                    Math.max(0.0, leftValleyTerrain - sample.bedElevationPotential()) * reliefBudget;
            double rightValleyRise =
                    Math.max(0.0, rightValleyTerrain - sample.bedElevationPotential()) * reliefBudget;
            double lateralGrade =
                    Math.max(leftValleyRise, rightValleyRise) / Math.max(valleyHalfWidth, EPSILON);
            maximumLateralGrade = Math.max(maximumLateralGrade, lateralGrade);

            double leftBankTerrain =
                    terrain.sample(offset(sample.position(), normal, bankProbe));
            double rightBankTerrain =
                    terrain.sample(offset(sample.position(), normal, -bankProbe));
            double waterSurface = sample.waterSurfacePotential();
            double lowerBank = Math.min(leftBankTerrain, rightBankTerrain);
            double containmentDeficit =
                    Math.max(0.0, waterSurface - lowerBank) * reliefBudget;
            maximumContainmentDeficit =
                    Math.max(maximumContainmentDeficit, containmentDeficit);

            double fullBankfullWidth = 2.0 * sample.bankfullHalfWidth();
            double depthWorld = sample.waterDepthPotential() * reliefBudget;
            double depthToWidth = depthWorld / Math.max(fullBankfullWidth, EPSILON);
            maximumDepthToWidth = Math.max(maximumDepthToWidth, depthToWidth);

            double localReliefWorld = Math.max(leftValleyRise, rightValleyRise);
            double fullValleyWidth = 2.0 * valleyHalfWidth;
            double reliefToValleyWidth =
                    localReliefWorld / Math.max(fullValleyWidth, EPSILON);
            maximumReliefToValleyWidth =
                    Math.max(maximumReliefToValleyWidth, reliefToValleyWidth);

            if (i > 0) {
                double ds = distance(points.get(i - 1), points.get(i));
                double previousLowering =
                        samples.get(i - 1).requiredCenterlineLowering() * reliefBudget;
                double previousFullWidth =
                        2.0 * samples.get(i - 1).bankfullHalfWidth();
                double currentArea = loweringWorld * fullBankfullWidth;
                double previousArea = previousLowering * previousFullWidth;
                excavationVolumeProxy +=
                        0.5 * (previousArea + currentArea) * ds;

                double meanWidth = 0.5 * (previousFullWidth + fullBankfullWidth);
                weightedBurdenNumerator +=
                        0.5
                                * (samples.get(i - 1).requiredCenterlineLowering()
                                        + loweringPotential)
                                * meanWidth
                                * ds;
                weightedBurdenDenominator += meanWidth * ds;

                if (ds > EPSILON) {
                    double longitudinalGrade =
                            Math.abs(
                                            sample.waterSurfacePotential()
                                                    - samples.get(i - 1).waterSurfacePotential())
                                    * reliefBudget
                                    / ds;
                    maximumLongitudinalGrade =
                            Math.max(maximumLongitudinalGrade, longitudinalGrade);
                }
            }

            if (i > 0 && i + 1 < points.size()) {
                double curvature =
                        curvature(points.get(i - 1), points.get(i), points.get(i + 1));
                maximumCurvatureWidthRatio =
                        Math.max(
                                maximumCurvatureWidthRatio,
                                curvature * fullBankfullWidth);
            }
        }

        double burden =
                weightedBurdenDenominator <= EPSILON
                        ? 0.0
                        : weightedBurdenNumerator / weightedBurdenDenominator;
        double ridgeLengthFraction =
                SkyIslandRouteFunctionalDiagnosticsPlanner.ridgeLengthFraction(
                        points, terrain, planningSpacing);

        return new SkyIslandGeomorphicMeasurements(
                maximumLoweringPotential,
                maximumLoweringWorld,
                maximumLateralGrade,
                maximumContainmentDeficit,
                maximumDepthToWidth,
                maximumReliefToValleyWidth,
                burden,
                excavationVolumeProxy,
                maximumCurvatureWidthRatio,
                ridgeLengthFraction,
                maximumLongitudinalGrade);
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
            case CASCADE -> 1.8;
        };
    }

    private static Vector tangent(List<SkyIslandLocalPosition> points, int index) {
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
        double cosine =
                Math.max(-1.0, Math.min(1.0, (ax * bx + az * bz) / (la * lb)));
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

    private static double distance(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b) {
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }

    private record Vector(double x, double z) {}
}
