package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.Test;

final class MinecraftStructurePieceUndersideSeparationRuntimeTest {
    @Test
    void activeRuntimeProvesEntireIntegerPieceBelowExactDevVolumeUnderside() throws Exception {
        var catalog = SkyforgeNeoForge1211DevRuntime.catalog();
        var volumeId = catalog.volumes().getFirst().id();
        var adapter = SkyforgeNeoForge1211DevRuntime.adapter();
        try (AutoCloseable installation = SkyforgeNeoForge1211SurfaceStage.install(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(installation);

            BoundingBox box = new BoundingBox(-1, 0, -1, 1, 2, 1);
            var evidence = MinecraftStructurePieceUndersideSeparationProbe.probe(box, volumeId)
                    .orElseThrow();

            assertEquals(volumeId, evidence.supportingVolumeId());
            assertEquals(27, evidence.observation().sampleCount());
            assertTrue(evidence.observation().allSamplesAtOrBelowUndersideSurface());
        }
    }
}
