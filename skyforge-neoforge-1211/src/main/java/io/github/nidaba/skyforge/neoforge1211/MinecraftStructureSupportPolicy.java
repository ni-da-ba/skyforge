package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SurfaceFootprint;
import io.github.nidaba.skyforge.world.SurfaceFootprintRectangle;
import io.github.nidaba.skyforge.world.SurfaceFoundationRequirements;
import io.github.nidaba.skyforge.world.SurfaceSupportRequirements;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Backend-owned translation from native structure piece geometry to neutral support requirements. */
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
        return requirements(List.of(box));
    }

    static SurfaceSupportRequirements requirements(List<BoundingBox> boxes) {
        return supportRequirements(
                boxes,
                SAMPLE_SPACING,
                MINIMUM_COVERAGE,
                MAXIMUM_HEIGHT_SPAN);
    }

    static SurfaceFoundationRequirements foundationRequirements(
            BoundingBox box,
            int resolvedFirstFreeY) {
        return foundationRequirements(List.of(box), box.minY(), resolvedFirstFreeY);
    }

    /**
     * Returns the stricter fill-only accommodation policy beneath one resolved native support plane.
     *
     * <p>The supplied boxes describe only the actual piece-derived X/Z footprint at that plane.
     * Empty gaps between boxes are not terrain requirements. Accommodation still samples every
     * integral Minecraft footprint column, requires complete support and never bridges an island
     * edge. The resolved first-free Skyforge height independently defines the highest existing
     * surface compatible with Minecraft's occupied/free-block convention.
     */
    static SurfaceFoundationRequirements foundationRequirements(
            List<BoundingBox> boxes,
            int structureFloorY,
            int resolvedFirstFreeY) {
        Objects.requireNonNull(boxes, "boxes");
        if (boxes.isEmpty()) {
            throw new IllegalArgumentException("foundation footprint requires at least one box");
        }
        long delta = (long) resolvedFirstFreeY - structureFloorY;
        if (delta < -1L || delta > 1L) {
            throw new IllegalArgumentException(
                    "resolvedFirstFreeY must be within one block of the structure floor Y");
        }
        double foundationTopY = structureFloorY;
        double maximumSurfaceY = Math.max(foundationTopY, resolvedFirstFreeY);
        return new SurfaceFoundationRequirements(
                supportRequirements(
                        boxes,
                        FOUNDATION_SAMPLE_SPACING,
                        FOUNDATION_MINIMUM_COVERAGE,
                        FOUNDATION_MAXIMUM_HEIGHT_SPAN),
                foundationTopY,
                maximumSurfaceY,
                FOUNDATION_MAXIMUM_FILL_DEPTH);
    }

    private static SurfaceSupportRequirements supportRequirements(
            List<BoundingBox> boxes,
            double sampleSpacing,
            double minimumCoverage,
            double maximumHeightSpan) {
        return new SurfaceSupportRequirements(
                footprint(boxes),
                sampleSpacing,
                CLEARANCE,
                minimumCoverage,
                MINIMUM_CLEARANCE_COVERAGE,
                maximumHeightSpan);
    }

    private static SurfaceFootprint footprint(List<BoundingBox> boxes) {
        Objects.requireNonNull(boxes, "boxes");
        if (boxes.isEmpty()) {
            throw new IllegalArgumentException("surface footprint requires at least one box");
        }
        return new SurfaceFootprint(boxes.stream()
                .map(box -> {
                    Objects.requireNonNull(box, "boxes contains null");
                    return new SurfaceFootprintRectangle(
                            box.minX(),
                            box.maxX(),
                            box.minZ(),
                            box.maxZ());
                })
                .toList());
    }
}
