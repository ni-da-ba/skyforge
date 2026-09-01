package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.TerrainBoxObservationRequirements;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Objects;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Backend-owned translation from native piece geometry to neutral sampled 3-D observation. */
final class MinecraftStructureTerrainObservationPolicy {
    private static final double SAMPLE_SPACING = 4.0;

    private MinecraftStructureTerrainObservationPolicy() {}

    static TerrainBoxObservationRequirements requirements(BoundingBox box) {
        Objects.requireNonNull(box, "box");
        return new TerrainBoxObservationRequirements(
                new WorldBounds(
                        box.minX(),
                        box.maxX(),
                        box.minY(),
                        box.maxY(),
                        box.minZ(),
                        box.maxZ()),
                SAMPLE_SPACING);
    }
}
