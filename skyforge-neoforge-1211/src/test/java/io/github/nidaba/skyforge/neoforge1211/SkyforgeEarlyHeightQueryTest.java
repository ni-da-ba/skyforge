package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.Test;

final class SkyforgeEarlyHeightQueryTest {
    private static final int MINIMUM_Y = -64;
    private static final int HEIGHT = 384;

    @Test
    void inactiveBindingContributesNoEarlyHeightAnswer() {
        assertTrue(SkyforgeNeoForge1211SurfaceStage.queryBaseHeight(
                        0,
                        0,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        MINIMUM_Y,
                        HEIGHT)
                .isEmpty());
    }

    @Test
    void activeBindingExposesHighestSkyforgeSolidBeforeChunkMutation() throws Exception {
        SkyforgeNeoForge1211ChunkAdapter adapter = SkyforgeNeoForge1211DevRuntime.adapter();
        MinecraftChunkMaterialization materialization = adapter.materialize(
                new ChunkPos(0, 0),
                MINIMUM_Y,
                HEIGHT);
        MaterializedPosition highestSolid = highestSolid(materialization);
        int worldX = materialization.chunkPos().getMinBlockX() + highestSolid.localX();
        int worldZ = materialization.chunkPos().getMinBlockZ() + highestSolid.localZ();

        try (AutoCloseable activeBinding = SkyforgeNeoForge1211SurfaceStage.install(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(activeBinding);
            int worldSurfaceHeight = SkyforgeNeoForge1211SurfaceStage.queryBaseHeight(
                            worldX,
                            worldZ,
                            Heightmap.Types.WORLD_SURFACE_WG,
                            MINIMUM_Y,
                            HEIGHT)
                    .orElseThrow();
            int oceanFloorHeight = SkyforgeNeoForge1211SurfaceStage.queryBaseHeight(
                            worldX,
                            worldZ,
                            Heightmap.Types.OCEAN_FLOOR_WG,
                            MINIMUM_Y,
                            HEIGHT)
                    .orElseThrow();

            assertEquals(highestSolid.worldY() + 1, worldSurfaceHeight);
            assertEquals(worldSurfaceHeight, oceanFloorHeight);
            assertTrue(SkyforgeNeoForge1211SurfaceStage.queryBaseHeight(
                            1000,
                            1000,
                            Heightmap.Types.WORLD_SURFACE_WG,
                            MINIMUM_Y,
                            HEIGHT)
                    .isEmpty());
        }

        assertTrue(SkyforgeNeoForge1211SurfaceStage.queryBaseHeight(
                        worldX,
                        worldZ,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        MINIMUM_Y,
                        HEIGHT)
                .isEmpty());
    }

    @Test
    void developmentStructureFootprintSamplesElevatedSkyforgeTerrain() throws Exception {
        SkyforgeNeoForge1211ChunkAdapter adapter = SkyforgeNeoForge1211DevRuntime.adapter();
        int[][] footprintSamples = {
            {0, 0},
            {20, 0},
            {0, 20},
            {20, 20},
            {10, 10}
        };

        try (AutoCloseable activeBinding = SkyforgeNeoForge1211SurfaceStage.install(
                adapter,
                new SkyforgeNeoForge1211ChunkWriter(new MinecraftBlockStateResolver()))) {
            assertNotNull(activeBinding);
            for (int[] sample : footprintSamples) {
                int height = SkyforgeNeoForge1211SurfaceStage.queryBaseHeight(
                                sample[0],
                                sample[1],
                                Heightmap.Types.OCEAN_FLOOR_WG,
                                MINIMUM_Y,
                                HEIGHT)
                        .orElseThrow();
                assertTrue(
                        height > 160,
                        "the SF-IMP-0043 native structure proof footprint must sample elevated Skyforge terrain");
            }
        }
    }

    private static MaterializedPosition highestSolid(MinecraftChunkMaterialization materialization) {
        for (int worldY = materialization.minimumY() + materialization.height() - 1;
                worldY >= materialization.minimumY();
                worldY--) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    ResourceLocation key = materialization.blockKeyAt(localX, worldY, localZ);
                    if (!SkyforgeMinecraftBlockPalette.AIR.equals(key)) {
                        return new MaterializedPosition(localX, worldY, localZ);
                    }
                }
            }
        }
        throw new AssertionError("expected the development Massif to contain a solid sample");
    }

    private record MaterializedPosition(int localX, int worldY, int localZ) {}
}
