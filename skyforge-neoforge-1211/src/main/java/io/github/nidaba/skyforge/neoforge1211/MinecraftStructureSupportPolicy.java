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
     * top is a continuous boundary plane, so a structure whose first occupied floor block is at
     * {@code box.minY()} uses {@code box.minY()} as the target boundary. The serialized Minecraft
     * foundation piece still fills only through block {@code box.minY() - 1}. This keeps continuous
     * Skyforge surface coordinates and discrete Minecraft block coordinates consistent.
     */
    static SurfaceFoundationRequirements foundationRequirements(BoundingBox box) {
        return new SurfaceFoundationRequirements(
                supportRequirements(
                        box,
                        FOUNDATION_SAMPLE_SPACING,
                        FOUNDATION_MINIMUM_COVERAGE,
                        FOUNDATION_MAXIMUM_HEIGHT_SPAN),
                box.minY(),
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
