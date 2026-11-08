package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

final class SkyforgeNeoForge1211DevRuntimeTest {
    @Test
    void ordinaryUnitTestLaunchDoesNotEnableDevelopmentSpecimen() {
        assertFalse(Boolean.getBoolean(SkyforgeNeoForge1211DevRuntime.ENABLE_PROPERTY));
        assertFalse(SkyforgeNeoForge1211ChunkLifecycle.hasActiveBinding());
        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());
        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasNativeSurfaceAdaptation());
    }

    @Test
    void developmentSpecimenUsesNativeSurfacePostSurfaceBindingRatherThanLateLifecycle() throws Exception {
        assertFalse(SkyforgeNeoForge1211ChunkLifecycle.hasActiveBinding());
        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());

        try (AutoCloseable activeBinding = SkyforgeNeoForge1211DevRuntime.installSpecimen()) {
            assertNotNull(activeBinding);
            assertTrue(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());
            assertTrue(
                    SkyforgeNeoForge1211SurfaceStage.hasNativeSurfaceAdaptation(),
                    "the 0037 development proof must exercise Minecraft-native surface adaptation");
            assertFalse(
                    SkyforgeNeoForge1211ChunkLifecycle.hasActiveBinding(),
                    "the 0037 development proof must not silently fall back to ChunkEvent.Load");
        }

        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());
        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasNativeSurfaceAdaptation());
        assertFalse(SkyforgeNeoForge1211ChunkLifecycle.hasActiveBinding());
    }

    @Test
    void tectonicReviewCatalogProducesThreeMassifsAtSeparatedHeights() {
        String property = SkyforgeNeoForge1211DevRuntime.TECTONIC_REVIEW_ENABLE_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            var adapter = SkyforgeNeoForge1211DevRuntime.adapter();
            assertReviewIsland(adapter, new ChunkPos(-24, 0), -64, 640, -32, 176, "lower");
            assertReviewIsland(adapter, new ChunkPos(0, 0), -64, 640, 96, 304, "middle");
            assertReviewIsland(adapter, new ChunkPos(24, 0), -64, 640, 224, 432, "higher");
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    private static void assertReviewIsland(
            SkyforgeNeoForge1211ChunkAdapter adapter,
            ChunkPos chunkPos,
            int minimumY,
            int height,
            int expectedMinimumY,
            int expectedMaximumY,
            String label) {
        MinecraftChunkMaterialization materialization =
                adapter.materialize(chunkPos, minimumY, height);
        assertTrue(materialization.candidateVolumeReferences() > 0, label + " island is absent from its review chunk");
        assertTrue(materialization.solidBlockCount() > 0, label + " island materialized no solid blocks");

        boolean surfaceWithinBand = false;
        for (int worldY = expectedMinimumY; worldY <= expectedMaximumY && !surfaceWithinBand; worldY++) {
            for (int localZ = 0; localZ < 16 && !surfaceWithinBand; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    if (!SkyforgeMinecraftBlockPalette.AIR.equals(
                            materialization.blockKeyAt(localX, worldY, localZ))) {
                        surfaceWithinBand = true;
                        break;
                    }
                }
            }
        }
        assertTrue(surfaceWithinBand, label + " island missed its expected vertical review band");
    }

    @Test
    void developmentSpecimenProducesSolidTerrainAtDocumentedInspectionRegion() {
        MinecraftChunkMaterialization materialization = SkyforgeNeoForge1211DevRuntime.adapter()
                .materialize(new ChunkPos(0, 0), -64, 384);

        assertTrue(materialization.candidateVolumeReferences() > 0);
        assertTrue(materialization.solidBlockCount() > 0);

        boolean solidNearInspectionHeight = false;
        for (int worldY = 160; worldY <= 280 && !solidNearInspectionHeight; worldY++) {
            for (int localZ = 0; localZ < 16 && !solidNearInspectionHeight; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    if (!SkyforgeMinecraftBlockPalette.AIR.equals(
                            materialization.blockKeyAt(localX, worldY, localZ))) {
                        solidNearInspectionHeight = true;
                        break;
                    }
                }
            }
        }
        assertTrue(
                solidNearInspectionHeight,
                "the development Massif should be visible in the documented in-game inspection band");
    }
}
