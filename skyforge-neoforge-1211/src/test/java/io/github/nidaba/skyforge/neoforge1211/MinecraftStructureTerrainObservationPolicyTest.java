package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

final class MinecraftStructureTerrainObservationPolicyTest {
    @Test
    void nativePieceBoundingBoxTranslatesAllThreeAxesWithoutSemanticClassification() {
        BoundingBox box = new BoundingBox(-10, 20, -6, 14, 35, 18);

        var requirements = MinecraftStructureTerrainObservationPolicy.requirements(box);

        assertEquals(-10.0, requirements.bounds().minimumX());
        assertEquals(14.0, requirements.bounds().maximumX());
        assertEquals(20.0, requirements.bounds().minimumY());
        assertEquals(35.0, requirements.bounds().maximumY());
        assertEquals(-6.0, requirements.bounds().minimumZ());
        assertEquals(18.0, requirements.bounds().maximumZ());
        assertEquals(4.0, requirements.sampleSpacing());
    }
}
