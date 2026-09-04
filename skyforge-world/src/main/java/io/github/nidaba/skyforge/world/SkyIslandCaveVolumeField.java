package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Objects;

/**
 * Continuous backend-neutral cave-volume field compiled from AUTH-0025 geometry.
 *
 * <p>The field uses a positive-inside sign convention because it represents authored cave void
 * occupancy, not Skyforge solid density. Values are normalized clearances against local chamber or
 * passage thickness and therefore must not be interpreted as block distances.
 */
public final class SkyIslandCaveVolumeField {
    private static final double OUTSIDE_DOMAIN = -1.0;

    private final SkyIslandDescriptor descriptor;
    private final SkyIslandCaveGeometryPlan geometry;
    private final SkyIslandSemanticFieldSet semantic;

    private SkyIslandCaveVolumeField(
            SkyIslandDescriptor descriptor,
            SkyIslandCaveGeometryPlan geometry) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.semantic = SkyIslandSemanticFieldSet.create(descriptor);
    }

    public static SkyIslandCaveVolumeField create(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return new SkyIslandCaveVolumeField(
                descriptor,
                SkyIslandCaveGeometryPlanner.plan(descriptor));
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public SkyIslandCaveGeometryPlan geometry() {
        return geometry;
    }

    public SkyIslandCaveVolumeSample sample(SkyIslandSubsurfacePosition position) {
        Objects.requireNonNull(position, "position");
        if (semantic.interiority().sample(position.surfacePosition()) <= 0.0) {
            return SkyIslandCaveVolumeSample.outside(OUTSIDE_DOMAIN);
        }

        double best = Double.NEGATIVE_INFINITY;
        int bestSystem = -1;
        SkyIslandCaveVolumeSample.PrimitiveKind bestKind =
                SkyIslandCaveVolumeSample.PrimitiveKind.NONE;
        int bestPrimitive = -1;

        for (SkyIslandCaveSystemGeometry system : geometry.systems()) {
            for (SkyIslandCaveChamberGeometry chamber : system.chambers()) {
                double value = chamberClearance(position, chamber);
                if (better(value, system.systemId(), 0, chamber.nodeId(),
                        best, bestSystem, bestKind, bestPrimitive)) {
                    best = value;
                    bestSystem = system.systemId();
                    bestKind = SkyIslandCaveVolumeSample.PrimitiveKind.CHAMBER;
                    bestPrimitive = chamber.nodeId();
                }
            }
            for (SkyIslandCavePassageGeometry passage : system.passages()) {
                double value = passageClearance(position, passage);
                if (better(value, system.systemId(), 1, passage.linkId(),
                        best, bestSystem, bestKind, bestPrimitive)) {
                    best = value;
                    bestSystem = system.systemId();
                    bestKind = SkyIslandCaveVolumeSample.PrimitiveKind.PASSAGE;
                    bestPrimitive = passage.linkId();
                }
            }
        }

        if (bestKind == SkyIslandCaveVolumeSample.PrimitiveKind.NONE) {
            return SkyIslandCaveVolumeSample.outside(OUTSIDE_DOMAIN);
        }
        if (best <= 0.0) {
            return new SkyIslandCaveVolumeSample(best, bestSystem, bestKind, bestPrimitive);
        }
        return new SkyIslandCaveVolumeSample(best, bestSystem, bestKind, bestPrimitive);
    }

    public double signedClearance(SkyIslandSubsurfacePosition position) {
        return sample(position).signedClearance();
    }

    public boolean contains(SkyIslandSubsurfacePosition position) {
        return sample(position).inside();
    }

    private static double chamberClearance(
            SkyIslandSubsurfacePosition position,
            SkyIslandCaveChamberGeometry chamber) {
        double dx = position.x() - chamber.center().x();
        double dz = position.z() - chamber.center().z();
        double dd = position.depthFraction() - chamber.center().depthFraction();

        double horizontal = Math.hypot(dx, dz) / chamber.horizontalRadius();
        double vertical = dd / chamber.depthRadius();
        double metric = Math.sqrt(horizontal * horizontal + vertical * vertical);
        return 1.0 - metric;
    }

    private static double passageClearance(
            SkyIslandSubsurfacePosition position,
            SkyIslandCavePassageGeometry passage) {
        double best = Double.NEGATIVE_INFINITY;
        for (int index = 1; index < passage.points().size(); index++) {
            SkyIslandCavePassagePoint first = passage.points().get(index - 1);
            SkyIslandCavePassagePoint second = passage.points().get(index);
            best = Math.max(best, segmentClearance(position, first, second));
        }
        return best;
    }

    /**
     * Elliptic capsule clearance for one sampled passage segment.
     *
     * <p>The closest point is solved in a locally scaled x/z/depth coordinate using mean segment
     * radii. Radius interpolation at that point then supplies the final normalized clearance.
     */
    private static double segmentClearance(
            SkyIslandSubsurfacePosition position,
            SkyIslandCavePassagePoint first,
            SkyIslandCavePassagePoint second) {
        double meanHorizontal = 0.5 * (first.horizontalRadius() + second.horizontalRadius());
        double meanDepth = 0.5 * (first.depthRadius() + second.depthRadius());

        double ax = first.position().x() / meanHorizontal;
        double az = first.position().z() / meanHorizontal;
        double ad = first.position().depthFraction() / meanDepth;
        double bx = second.position().x() / meanHorizontal;
        double bz = second.position().z() / meanHorizontal;
        double bd = second.position().depthFraction() / meanDepth;
        double px = position.x() / meanHorizontal;
        double pz = position.z() / meanHorizontal;
        double pd = position.depthFraction() / meanDepth;

        double vx = bx - ax;
        double vz = bz - az;
        double vd = bd - ad;
        double denominator = vx * vx + vz * vz + vd * vd;
        double t = denominator <= 1.0e-18
                ? 0.0
                : ((px - ax) * vx + (pz - az) * vz + (pd - ad) * vd) / denominator;
        t = clamp01(t);

        double cx = lerp(first.position().x(), second.position().x(), t);
        double cz = lerp(first.position().z(), second.position().z(), t);
        double cd = lerp(first.position().depthFraction(), second.position().depthFraction(), t);
        double horizontalRadius = lerp(first.horizontalRadius(), second.horizontalRadius(), t);
        double depthRadius = lerp(first.depthRadius(), second.depthRadius(), t);

        double horizontal = Math.hypot(position.x() - cx, position.z() - cz) / horizontalRadius;
        double vertical = (position.depthFraction() - cd) / depthRadius;
        return 1.0 - Math.sqrt(horizontal * horizontal + vertical * vertical);
    }

    private static boolean better(
            double value,
            int systemId,
            int kindOrder,
            int primitiveId,
            double best,
            int bestSystem,
            SkyIslandCaveVolumeSample.PrimitiveKind bestKind,
            int bestPrimitive) {
        if (value > best + 1.0e-12) {
            return true;
        }
        if (Math.abs(value - best) > 1.0e-12) {
            return false;
        }
        if (bestSystem < 0 || systemId < bestSystem) {
            return true;
        }
        if (systemId > bestSystem) {
            return false;
        }
        int bestKindOrder = switch (bestKind) {
            case NONE -> Integer.MAX_VALUE;
            case CHAMBER -> 0;
            case PASSAGE -> 1;
        };
        return kindOrder < bestKindOrder
                || (kindOrder == bestKindOrder && primitiveId < bestPrimitive);
    }

    private static double lerp(double first, double second, double t) {
        return first + (second - first) * t;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
