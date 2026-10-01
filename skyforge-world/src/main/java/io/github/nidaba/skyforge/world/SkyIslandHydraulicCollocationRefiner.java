package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Diagnostic midpoint refinement of hydraulic collocation on the unchanged accepted C2 polyline. */
public final class SkyIslandHydraulicCollocationRefiner {
    private SkyIslandHydraulicCollocationRefiner() {}

    public static SkyIslandHydraulicReachSkeleton refineMidpoints(
            SkyIslandDescriptor descriptor,
            SkyIslandHydraulicReachSkeleton skeleton) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(skeleton, "skeleton");
        SkyIslandSemanticField terrain =
                SkyIslandPreHydrologicTerrainField.create(descriptor);

        List<SkyIslandHydraulicGeometrySkeletonSample> source = skeleton.samples();
        List<SkyIslandHydraulicGeometrySkeletonSample> refined =
                new ArrayList<>(2 * source.size() - 1);
        List<SkyIslandLocalPosition> points =
                new ArrayList<>(2 * source.size() - 1);
        refined.add(source.getFirst());
        points.add(source.getFirst().position());

        double maximumWidth = source.getFirst().bankfullHalfWidth();
        double maximumDepth = source.getFirst().waterDepthPotential();

        for (int i = 0; i + 1 < source.size(); i++) {
            SkyIslandHydraulicGeometrySkeletonSample a = source.get(i);
            SkyIslandHydraulicGeometrySkeletonSample b = source.get(i + 1);
            double arc = 0.5 * (a.arcLength() + b.arcLength());
            double station = arc / skeleton.pathLength();
            SkyIslandLocalPosition position = new SkyIslandLocalPosition(
                    0.5 * (a.position().x() + b.position().x()),
                    0.5 * (a.position().z() + b.position().z()));
            double discharge = 0.5 * (a.relativeDischarge() + b.relativeDischarge());
            double width =
                    SkyIslandHydraulicGeometryCalibration.bankfullHalfWidth(
                            descriptor.nominalRadius(), discharge);
            double depth =
                    SkyIslandHydraulicGeometryCalibration.waterDepthPotential(discharge);
            SkyIslandHydraulicGeometrySkeletonSample midpoint =
                    new SkyIslandHydraulicGeometrySkeletonSample(
                            position,
                            arc,
                            station,
                            discharge,
                            width,
                            depth,
                            clamp01(terrain.sample(position)));
            refined.add(midpoint);
            refined.add(b);
            points.add(position);
            points.add(b.position());
            maximumWidth = Math.max(maximumWidth, Math.max(width, b.bankfullHalfWidth()));
            maximumDepth = Math.max(maximumDepth, Math.max(depth, b.waterDepthPotential()));
        }

        SkyIslandContinuousChannelCenterline original = skeleton.centerline();
        SkyIslandContinuousChannelCenterline refinedCenterline =
                new SkyIslandContinuousChannelCenterline(
                        original.searchRoute(),
                        points,
                        original.pathLength(),
                        original.maximumSearchPathDeviation(),
                        original.maximumTurnAngleRadians());

        return new SkyIslandHydraulicReachSkeleton(
                skeleton.geomorphicRoute(),
                refinedCenterline,
                refined,
                skeleton.pathLength(),
                maximumWidth,
                maximumDepth);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
