package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgePhysicalVolumeAdmissionStageTest {
    @Test
    void inactiveStagePreservesHistoricalPopulationBehavior() {
        var volumeId = SkyforgeNeoForge1211PopulationDevRuntime.catalog().volumes().getFirst().id();
        assertFalse(SkyforgePhysicalVolumeAdmissionStage.active());
        assertTrue(SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId));
    }

    @Test
    void deferredChunkSchedulingIsCanonicalAcrossHashIterationOrder() {
        var keys = new HashSet<Long>();
        keys.add(new ChunkPos(3, -2).toLong());
        keys.add(new ChunkPos(-4, 9).toLong());
        keys.add(new ChunkPos(3, -7).toLong());
        keys.add(new ChunkPos(-4, -1).toLong());

        assertEquals(
                List.of(
                        new ChunkPos(-4, -1).toLong(),
                        new ChunkPos(-4, 9).toLong(),
                        new ChunkPos(3, -7).toLong(),
                        new ChunkPos(3, -2).toLong()),
                List.copyOf(SkyforgePhysicalVolumeAdmissionStage.orderedChunkKeys(keys)));
    }

    @Test
    void plannedVolumeCannotPopulateBeforeWholeVolumeAdmission() throws Exception {
        var catalog = SkyforgeNeoForge1211PopulationDevRuntime.catalog();
        var volumeId = catalog.volumes().getFirst().id();

        try (AutoCloseable binding = SkyforgePhysicalVolumeAdmissionStage.install(catalog)) {
            assertNotNull(binding);
            assertTrue(SkyforgePhysicalVolumeAdmissionStage.active());
            assertFalse(SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId));
        }

        assertFalse(SkyforgePhysicalVolumeAdmissionStage.active());
        assertTrue(SkyforgePhysicalVolumeAdmissionStage.allowsPopulation(volumeId));
    }
}
