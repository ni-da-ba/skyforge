package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SurfaceSupportRequirements;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Backend-owned translation from a native structure footprint to neutral support requirements. */
final class MinecraftStructureSupportPolicy {
    private static final double SAMPLE_SPACING = 4.0;
    private static final double CLEARANCE = 2.0;
    private static final double MINIMUM_COVERAGE = 0.90;
    private static final double MINIMUM_CLEARANCE_COVERAGE = 0.50;
    private static final double MAXIMUM_HEIGHT_SPAN = 4.0;

    private MinecraftStructureSupportPolicy() {}

    static SurfaceSupportRequirements requirements(BoundingBox box) {
        return new SurfaceSupportRequirements(
                box.minX(),
                box.maxX(),
                box.minZ(),
                box.maxZ(),
                SAMPLE_SPACING,
                CLEARANCE,
                MINIMUM_COVERAGE,
                MINIMUM_CLEARANCE_COVERAGE,
                MAXIMUM_HEIGHT_SPAN);
    }
}
