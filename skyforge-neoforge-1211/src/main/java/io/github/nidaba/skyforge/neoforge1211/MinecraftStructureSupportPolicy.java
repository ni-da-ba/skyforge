package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SurfaceFoundationRequirements;
import io.github.nidaba.skyforge.world.SurfaceSupportRequirements;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Backend-owned translation from a native structure footprint to neutral support requirements. */
final class MinecraftStructureSupportPolicy {
    private static final double SAMPLE_SPACING = 4.0;
    private static final double CLEARANCE = 2.0;
    private static final double MINIMUM_COVERAGE = 0.90;
    private static final double MINIMUM_CLEARANCE_COVERAGE = 0.50;
    private static final double MAXIMUM_HEIGHT_SPAN = 4.0;

    private static final double FOUNDATION_MINIMUM_COVERAGE = 1.0;
    private static final double FOUNDATION_MAXIMUM_HEIGHT_SPAN = 12.0;
    private static final double FOUNDATION_MAXIMUM_FILL_DEPTH = 8.0;

    private MinecraftStructureSupportPolicy() {}

    static SurfaceSupportRequirements requirements(BoundingBox box) {
        return supportRequirements(
                box,
                MINIMUM_COVERAGE,
                MAXIMUM_HEIGHT_SPAN);
    }

    /**
     * Returns the stricter fill-only accommodation policy beneath the native structure floor.
     *
     * <p>Accommodation requires complete interior support; unlike ordinary admission, it never
     * bridges an island edge. The relaxed height span only permits bounded downward foundation
     * fill and does not authorize excavation.
     */
    static SurfaceFoundationRequirements foundationRequirements(BoundingBox box) {
        return new SurfaceFoundationRequirements(
                supportRequirements(
                        box,
                        FOUNDATION_MINIMUM_COVERAGE,
                        FOUNDATION_MAXIMUM_HEIGHT_SPAN),
                Math.subtractExact(box.minY(), 1),
                FOUNDATION_MAXIMUM_FILL_DEPTH);
    }

    private static SurfaceSupportRequirements supportRequirements(
            BoundingBox box,
            double minimumCoverage,
            double maximumHeightSpan) {
        return new SurfaceSupportRequirements(
                box.minX(),
                box.maxX(),
                box.minZ(),
                box.maxZ(),
                SAMPLE_SPACING,
                CLEARANCE,
                minimumCoverage,
                MINIMUM_CLEARANCE_COVERAGE,
                maximumHeightSpan);
    }
}
