package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Objects;

/**
 * AUTH-0030 constructive union of the sealed AUTH-0026 cave field and accepted AUTH-0029 exposure
 * connection corridors.
 *
 * <p>The base AUTH-0026 field is not modified. Islands without accepted exposure geometry therefore
 * retain exactly the same signed-clearance samples.
 */
public final class SkyIslandExteriorConnectedCaveVolumeField {
    private static final double OUTSIDE_DOMAIN = -1.0;

    private final SkyIslandDescriptor descriptor;
    private final SkyIslandCaveVolumeField baseField;
    private final SkyIslandCaveExposureGeometryPlan exposureGeometry;
    private final SkyIslandSemanticFieldSet semantic;

    private SkyIslandExteriorConnectedCaveVolumeField(
            SkyIslandDescriptor descriptor,
            SkyIslandCaveVolumeField baseField,
            SkyIslandCaveExposureGeometryPlan exposureGeometry) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.baseField = Objects.requireNonNull(baseField, "baseField");
        this.exposureGeometry = Objects.requireNonNull(exposureGeometry, "exposureGeometry");
        this.semantic = SkyIslandSemanticFieldSet.create(descriptor);
    }

    public static SkyIslandExteriorConnectedCaveVolumeField create(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return new SkyIslandExteriorConnectedCaveVolumeField(
                descriptor,
                SkyIslandCaveVolumeField.create(descriptor),
                SkyIslandCaveExposureGeometryPlanner.plan(descriptor));
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public SkyIslandCaveVolumeField baseField() {
        return baseField;
    }

    public SkyIslandCaveExposureGeometryPlan exposureGeometry() {
        return exposureGeometry;
    }

    public SkyIslandExteriorConnectedCaveVolumeSample sample(
            SkyIslandSubsurfacePosition position) {
        Objects.requireNonNull(position, "position");
        if (semantic.interiority().sample(position.surfacePosition()) <= 0.0) {
            return SkyIslandExteriorConnectedCaveVolumeSample.outside(OUTSIDE_DOMAIN);
        }

        SkyIslandCaveVolumeSample base = baseField.sample(position);
        SkyIslandExteriorConnectedCaveVolumeSample best =
                SkyIslandExteriorConnectedCaveVolumeSample.fromBase(base);

        for (SkyIslandCaveExposureConnectionGeometry connection : exposureGeometry.connections()) {
            double clearance = connectionClearance(position, connection);
            if (betterExposure(clearance, connection, best)) {
                SkyIslandCaveExposureIntent intent = connection.intent();
                best = new SkyIslandExteriorConnectedCaveVolumeSample(
                        clearance,
                        SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.EXPOSURE_CONNECTION,
                        connection.systemId(),
                        intent.sourcePrimitiveKind(),
                        intent.sourcePrimitiveId(),
                        connection.side());
            }
        }
        return best;
    }

    public double signedClearance(SkyIslandSubsurfacePosition position) {
        return sample(position).signedClearance();
    }

    public boolean contains(SkyIslandSubsurfacePosition position) {
        return sample(position).inside();
    }

    private static double connectionClearance(
            SkyIslandSubsurfacePosition position,
            SkyIslandCaveExposureConnectionGeometry connection) {
        double best = Double.NEGATIVE_INFINITY;
        for (int index = 1; index < connection.points().size(); index++) {
            best = Math.max(
                    best,
                    segmentClearance(
                            position,
                            connection.points().get(index - 1),
                            connection.points().get(index)));
        }
        return best;
    }

    /**
     * Elliptic capsule clearance matching AUTH-0026 passage semantics.
     *
     * <p>Horizontal and semantic-depth scales remain independent. The closest point is solved in a
     * locally scaled coordinate using mean segment radii, then the final local radii are interpolated
     * at that closest-point parameter.
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

    private static boolean betterExposure(
            double clearance,
            SkyIslandCaveExposureConnectionGeometry connection,
            SkyIslandExteriorConnectedCaveVolumeSample current) {
        if (clearance > current.signedClearance() + 1.0e-12) {
            return true;
        }
        if (Math.abs(clearance - current.signedClearance()) > 1.0e-12) {
            return false;
        }

        // Preserve accepted AUTH-0026 provenance on exact ties.
        if (current.sourceKind()
                == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.BASE_CAVE) {
            return false;
        }
        if (current.sourceKind()
                == SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.NONE) {
            return true;
        }
        return connection.systemId() < current.systemId();
    }

    private static double lerp(double first, double second, double t) {
        return first + (second - first) * t;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
