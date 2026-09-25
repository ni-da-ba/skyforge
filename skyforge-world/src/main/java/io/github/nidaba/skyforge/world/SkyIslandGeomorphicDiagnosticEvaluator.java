package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates cross-section-aware geomorphic diagnostics without applying a terrain modification.
 *
 * <p>Horizontal distances are normalized by island nominal radius when compared with authored
 * elevation potential. These are therefore semantic-space grades, not literal Minecraft block
 * slopes. Backend physical slope limits remain a later projection concern.
 */
public final class SkyIslandGeomorphicDiagnosticEvaluator {
    private static final double EPSILON = 1.0e-12;

    private SkyIslandGeomorphicDiagnosticEvaluator() {}

    public static SkyIslandGeomorphicDiagnostics evaluate(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicChannelNetworkPlan hydraulics,
            SkyIslandSemanticField terrain) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(hydraulics, "hydraulics");
        Objects.requireNonNull(terrain, "terrain");
        if (!descriptor.equals(hydraulics.descriptor())) {
            throw new IllegalArgumentException("hydraulic plan descriptor must match diagnostic descriptor");
        }

        double radius = descriptor.nominalRadius();
        double minimumContainment = Double.POSITIVE_INFINITY;
        double maximumRecoveryGrade = 0.0;
        double maximumCurvatureWidthRatio = 0.0;
        double normalizedCutVolume = 0.0;
        int sampleCount = 0;

        for (SkyIslandHydraulicReachGeometry reach : hydraulics.reaches()) {
            List<SkyIslandHydraulicGeometrySample> samples = reach.samples();
            for (int i = 0; i < samples.size(); i++) {
                SkyIslandHydraulicGeometrySample sample = samples.get(i);
                Vector tangent = tangent(samples, i);
                Vector normal = new Vector(-tangent.z(), tangent.x());
                double halfWidth = sample.bankfullHalfWidth();

                SkyIslandLocalPosition left = new SkyIslandLocalPosition(
                        sample.position().x() + normal.x() * halfWidth,
                        sample.position().z() + normal.z() * halfWidth);
                SkyIslandLocalPosition right = new SkyIslandLocalPosition(
                        sample.position().x() - normal.x() * halfWidth,
                        sample.position().z() - normal.z() * halfWidth);

                double leftTerrain = terrain.sample(left);
                double rightTerrain = terrain.sample(right);
                double lowerBank = Math.min(leftTerrain, rightTerrain);
                minimumContainment = Math.min(
                        minimumContainment,
                        lowerBank - sample.waterSurfacePotential());

                double normalizedHalfWidth = halfWidth / radius;
                if (normalizedHalfWidth > EPSILON) {
                    double leftRecovery = Math.max(
                            0.0,
                            leftTerrain - sample.bedElevationPotential()) / normalizedHalfWidth;
                    double rightRecovery = Math.max(
                            0.0,
                            rightTerrain - sample.bedElevationPotential()) / normalizedHalfWidth;
                    maximumRecoveryGrade = Math.max(
                            maximumRecoveryGrade,
                            Math.max(leftRecovery, rightRecovery));
                }

                if (i > 0) {
                    double ds = distance(samples.get(i - 1).position(), sample.position());
                    double normalizedDs = ds / radius;
                    double normalizedFullWidth = 2.0 * halfWidth / radius;
                    normalizedCutVolume +=
                            0.5
                                    * (samples.get(i - 1).requiredCenterlineLowering()
                                            + sample.requiredCenterlineLowering())
                                    * normalizedFullWidth
                                    * normalizedDs;
                }

                if (i > 0 && i < samples.size() - 1) {
                    double curvature = curvature(
                            samples.get(i - 1).position(),
                            sample.position(),
                            samples.get(i + 1).position());
                    maximumCurvatureWidthRatio = Math.max(
                            maximumCurvatureWidthRatio,
                            curvature * (2.0 * halfWidth));
                }
                sampleCount++;
            }
        }

        if (sampleCount == 0) {
            minimumContainment = 0.0;
        }
        return new SkyIslandGeomorphicDiagnostics(
                minimumContainment,
                maximumRecoveryGrade,
                maximumCurvatureWidthRatio,
                normalizedCutVolume);
    }

    private static Vector tangent(
            List<SkyIslandHydraulicGeometrySample> samples,
            int index) {
        SkyIslandLocalPosition a;
        SkyIslandLocalPosition b;
        if (index == 0) {
            a = samples.get(0).position();
            b = samples.get(1).position();
        } else if (index == samples.size() - 1) {
            a = samples.get(index - 1).position();
            b = samples.get(index).position();
        } else {
            a = samples.get(index - 1).position();
            b = samples.get(index + 1).position();
        }
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
        double ab = distance(a, b);
        double bc = distance(b, c);
        double ca = distance(c, a);
        if (ab <= EPSILON || bc <= EPSILON || ca <= EPSILON) {
            return 0.0;
        }
        double twiceArea = Math.abs(
                (b.x() - a.x()) * (c.z() - a.z())
                        - (b.z() - a.z()) * (c.x() - a.x()));
        if (twiceArea <= EPSILON) {
            return 0.0;
        }
        return 2.0 * twiceArea / (ab * bc * ca);
    }

    private static double distance(
            SkyIslandLocalPosition a,
            SkyIslandLocalPosition b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }

    private record Vector(double x, double z) {}
}
