package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.TerrainBoxObservationRequirements;
import io.github.nidaba.skyforge.world.WorldBounds;
import java.util.Objects;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Backend-owned translation from native piece geometry to neutral sampled 3-D observation. */
final class MinecraftStructureTerrainObservationPolicy {
    private static final double OBSERVATION_SAMPLE_SPACING = 4.0;
    private static final double PROOF_SAMPLE_SPACING = 1.0;

    private MinecraftStructureTerrainObservationPolicy() {}

    /** Sparse read-only observation used when collecting descriptive native-piece evidence. */
    static TerrainBoxObservationRequirements requirements(BoundingBox box) {
        return requirements(box, OBSERVATION_SAMPLE_SPACING);
    }

    /**
     * Minecraft-lattice observation used when a later policy needs proof about every possible block
     * coordinate represented by an integer native piece bounding box.
     */
    static TerrainBoxObservationRequirements proofRequirements(BoundingBox box) {
        return requirements(box, PROOF_SAMPLE_SPACING);
    }

    private static TerrainBoxObservationRequirements requirements(BoundingBox box, double sampleSpacing) {
        Objects.requireNonNull(box, "box");
        return new TerrainBoxObservationRequirements(
                new WorldBounds(
                        box.minX(),
                        box.maxX(),
                        box.minY(),
                        box.maxY(),
                        box.minZ(),
                        box.maxZ()),
                sampleSpacing);
    }
}
