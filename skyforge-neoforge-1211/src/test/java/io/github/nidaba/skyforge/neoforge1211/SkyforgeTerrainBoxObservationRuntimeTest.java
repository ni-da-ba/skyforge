package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nidaba.skyforge.world.TerrainBoxObservationRequirements;
import io.github.nidaba.skyforge.world.WorldBounds;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.Test;

final class SkyforgeTerrainBoxObservationRuntimeTest {
    @Test
    void activeRuntimeObservesExactClaimedVolumeWithoutMergingProvenance() throws Exception {
        SkyforgeNeoForge1211ChunkAdapter adapter = SkyforgeNeoForge1211DevRuntime.adapter();
        try (AutoCloseable ignored = SkyforgeNeoForge1211SurfaceStage.install(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            MinecraftSkyforgeHeightClaim claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                            0,
                            0,
                            Heightmap.Types.OCEAN_FLOOR_WG,
                            -64,
                            384)
                    .orElseThrow();
            var volumeId = claim.volumeIds().getFirst();
            double occupiedY = claim.height() - 1.0;
            var requirements = new TerrainBoxObservationRequirements(
                    new WorldBounds(0.0, 0.0, occupiedY, occupiedY, 0.0, 0.0),
                    4.0);

            var observation = SkyforgeNeoForge1211SurfaceStage.observeTerrainBox(volumeId, requirements)
                    .orElseThrow();

            assertEquals(volumeId, observation.observedVolumeId());
            assertEquals(1, observation.sampleCount());
            assertEquals(1, observation.solidSampleCount());
            assertTrue(observation.allSamplesSolid());
        }
    }
}
