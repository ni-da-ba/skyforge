package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Objects;

/**
 * Continuous backend-neutral elevation field derived from one coarse hydrologic terrain surface.
 */
public final class SkyIslandContinuousHydrologicTerrainField implements SkyIslandSemanticField {
    private static final double DOMAIN_FADE_THRESHOLD = 0.025;

    private final SkyIslandDescriptor descriptor;
    private final SkyIslandSemanticFieldSet semanticFields;
    private final int gridSize;
    private final double spacing;
    private final double extent;
    private final double[] adjustments;

    private SkyIslandContinuousHydrologicTerrainField(
            SkyIslandDescriptor descriptor,
            SkyIslandHydrologicTerrainSurfacePlan surface) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.semanticFields = SkyIslandSemanticFieldSet.create(descriptor);
        this.gridSize = surface.gridSize();
        this.spacing = surface.spacing();
        this.extent = descriptor.nominalRadius();
        this.adjustments = new double[gridSize * gridSize];

        if (!surface.descriptor().equals(descriptor)) {
            throw new IllegalArgumentException("surface plan descriptor must match field descriptor");
        }
        for (SkyIslandHydrologicTerrainSurfaceCell cell : surface.cells()) {
            int index = cell.watershedCellIndex();
            if (index < 0 || index >= adjustments.length) {
                throw new IllegalArgumentException("surface cell lies outside the declared lattice");
            }
            adjustments[index] = cell.netAdjustment();
        }
    }

    /** Creates the historical/raw AUTH-0016 field from the complete visible-channel diagnostic. */
    public static SkyIslandContinuousHydrologicTerrainField create(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return create(descriptor, SkyIslandHydrologicTerrainSurfacePlanner.plan(descriptor));
    }

    /** Creates a continuous field from one explicit hydrologic terrain surface plan. */
    public static SkyIslandContinuousHydrologicTerrainField create(
            SkyIslandDescriptor descriptor,
            SkyIslandHydrologicTerrainSurfacePlan surface) {
        return new SkyIslandContinuousHydrologicTerrainField(descriptor, surface);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public int gridSize() {
        return gridSize;
    }

    public double spacing() {
        return spacing;
    }

    public double baseElevation(SkyIslandLocalPosition position) {
        return semanticFields.elevationTendency().sample(Objects.requireNonNull(position, "position"));
    }

    public double adjustment(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        if (position.x() < -extent
                || position.x() > extent
                || position.z() < -extent
                || position.z() > extent) {
            return 0.0;
        }

        double gx = (position.x() + extent) / spacing;
        double gz = (position.z() + extent) / spacing;
        if (gx < 0.0 || gz < 0.0 || gx > gridSize - 1.0 || gz > gridSize - 1.0) {
            return 0.0;
        }

        int x0 = Math.min(gridSize - 1, (int) Math.floor(gx));
        int z0 = Math.min(gridSize - 1, (int) Math.floor(gz));
        int x1 = Math.min(gridSize - 1, x0 + 1);
        int z1 = Math.min(gridSize - 1, z0 + 1);
        double tx = smootherstep(gx - x0);
        double tz = smootherstep(gz - z0);

        double d00 = adjustments[index(x0, z0)];
        double d10 = adjustments[index(x1, z0)];
        double d01 = adjustments[index(x0, z1)];
        double d11 = adjustments[index(x1, z1)];
        double lower = lerp(d00, d10, tx);
        double upper = lerp(d01, d11, tx);
        double interpolated = lerp(lower, upper, tz);

        double interiority = semanticFields.interiority().sample(position);
        double domainGate = smoothstep(0.0, DOMAIN_FADE_THRESHOLD, interiority);
        return interpolated * domainGate;
    }

    @Override
    public double sample(SkyIslandLocalPosition position) {
        return clamp01(baseElevation(position) + adjustment(position));
    }

    private int index(int x, int z) {
        return z * gridSize + x;
    }

    private static double smootherstep(double value) {
        double t = clamp01(value);
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = clamp01((value - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double fraction) {
        return a + (b - a) * fraction;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
