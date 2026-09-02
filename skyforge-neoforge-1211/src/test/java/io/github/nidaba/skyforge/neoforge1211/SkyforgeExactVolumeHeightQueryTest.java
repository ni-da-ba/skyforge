package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.Test;

final class SkyforgeExactVolumeHeightQueryTest {
    @Test
    void exactIslandHeightReturnsOnlyThatOwnerAndEmptyColumnsStayEmpty() throws Exception {
        var catalog = SkyforgeNeoForge1211DevRuntime.catalog();
        var volumeId = catalog.volumes().getFirst().id();
        try (AutoCloseable installation = SkyforgeNeoForge1211SurfaceStage.install(
                SkyforgeNeoForge1211DevRuntime.adapter(),
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            var claim = SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                            volumeId,
                            0,
                            0,
                            Heightmap.Types.WORLD_SURFACE_WG,
                            -64,
                            384)
                    .orElseThrow();
            assertEquals(1, claim.volumeIds().size());
            assertEquals(volumeId, claim.volumeIds().getFirst());
            assertTrue(claim.height() > 96, "development Massif should expose an elevated exact-volume surface");

            assertTrue(
                    SkyforgeNeoForge1211SurfaceStage.queryBaseHeightClaim(
                                    volumeId,
                                    1000,
                                    1000,
                                    Heightmap.Types.WORLD_SURFACE_WG,
                                    -64,
                                    384)
                            .isEmpty(),
                    "an empty island column must not fall through to vanilla terrain or another volume");
        }
    }
}
