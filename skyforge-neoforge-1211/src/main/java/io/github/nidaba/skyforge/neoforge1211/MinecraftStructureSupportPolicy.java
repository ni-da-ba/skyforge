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

    private static final double FOUNDATION_SAMPLE_SPACING = 1.0;
    private static final double FOUNDATION_MINIMUM_COVERAGE = 1.0;
    private static final double FOUNDATION_MAXIMUM_HEIGHT_SPAN = 12.0;
    private static final double FOUNDATION_MAXIMUM_FILL_DEPTH = 8.0;

    private MinecraftStructureSupportPolicy() {}

    static SurfaceSupportRequirements requirements(BoundingBox box) {
        return supportRequirements(
                box,
                SAMPLE_SPACING,
                MINIMUM_COVERAGE,
                MAXIMUM_HEIGHT_SPAN);
    }

    /**
     * Returns the stricter fill-only accommodation policy beneath the native structure floor.
     *
     * <p>Accommodation samples every integral Minecraft X/Z column in the bounding-box footprint,
     * requires complete interior support and never bridges an island edge. The neutral foundation
     * fill plane remains {@code box.minY()}: the serialized foundation may place blocks only below
     * the native structure floor. The resolved first-free Skyforge height independently defines the
     * highest existing surface compatible with the native occupied/free-block convention. A claim
     * one block above the bounding-box minimum therefore authorizes existing terrain in the native
     * floor layer without adding one unit to the measured fill depth.
     */
    static SurfaceFoundationRequirements foundationRequirements(
            BoundingBox box,
            int resolvedFirstFreeY) {
        long delta = (long) resolvedFirstFreeY - box.minY();
        if (delta < -1L || delta > 1L) {
            throw new IllegalArgumentException(
                    "resolvedFirstFreeY must be within one block of the structure minimum Y");
        }
        double foundationTopY = box.minY();
        double maximumSurfaceY = Math.max(foundationTopY, resolvedFirstFreeY);
        return new SurfaceFoundationRequirements(
                supportRequirements(
                        box,
                        FOUNDATION_SAMPLE_SPACING,
                        FOUNDATION_MINIMUM_COVERAGE,
                        FOUNDATION_MAXIMUM_HEIGHT_SPAN),
                foundationTopY,
                maximumSurfaceY,
                FOUNDATION_MAXIMUM_FILL_DEPTH);
    }

    private static SurfaceSupportRequirements supportRequirements(
            BoundingBox box,
            double sampleSpacing,
            double minimumCoverage,
            double maximumHeightSpan) {
        return new SurfaceSupportRequirements(
                box.minX(),
                box.maxX(),
                box.minZ(),
                box.maxZ(),
                sampleSpacing,
                CLEARANCE,
                minimumCoverage,
                MINIMUM_CLEARANCE_COVERAGE,
                maximumHeightSpan);
    }
}
