package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void tectonicReviewCatalogProducesFiveMorphologiesAtOneElevation() {
        String property = SkyforgeNeoForge1211DevRuntime.TECTONIC_REVIEW_ENABLE_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            var adapter = SkyforgeNeoForge1211DevRuntime.adapter();
            assertReviewIsland(adapter, new ChunkPos(-48, 0), -64, 640, 96, 304, "tableland");
            assertReviewIsland(adapter, new ChunkPos(-24, 0), -64, 640, 96, 304, "spine");
            assertReviewIsland(adapter, new ChunkPos(0, 0), -64, 640, 96, 304, "massif");
            assertReviewIsland(adapter, new ChunkPos(24, 0), -64, 640, 96, 304, "basin");
            assertReviewIsland(adapter, new ChunkPos(48, 0), -64, 640, 96, 304, "lobed");
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
    void tectonicBiomeReviewUsesAdjacentSameLevelMassifAndBasin() {
        String combinedProperty = SkyforgeNeoForge1211DevRuntime.TECTONIC_BIOME_SURFACE_REVIEW_PROPERTY;
        String multiIslandProperty = SkyforgeNeoForge1211DevRuntime.TECTONIC_REVIEW_ENABLE_PROPERTY;
        String previousCombined = System.getProperty(combinedProperty);
        String previousMultiIsland = System.getProperty(multiIslandProperty);
        System.setProperty(combinedProperty, "true");
        System.clearProperty(multiIslandProperty);
        try {
            var catalog = SkyforgeNeoForge1211DevRuntime.catalog();
            assertEquals(2, catalog.volumes().size());
            assertTrue(catalog.volumes().get(0).id().path().contains("/sf-imp-0043-dev-massif/"));
            assertTrue(catalog.volumes().get(1).id().path().contains("/sf-imp-0054-review-basin/"));

            var adapter = SkyforgeNeoForge1211DevRuntime.adapter();
            assertTrue(adapter.materialize(new ChunkPos(0, 0), -64, 640).solidBlockCount() > 0,
                    "the accepted Massif review location must remain populated");
            assertTrue(adapter.materialize(new ChunkPos(24, 0), -64, 640).solidBlockCount() > 0,
                    "the neighboring Basin review location must be populated");
            assertEquals(0, adapter.materialize(new ChunkPos(11, 0), -64, 640).solidBlockCount(),
                    "the witness chunk between the two islands must remain empty");
        } finally {
            if (previousCombined == null) System.clearProperty(combinedProperty);
            else System.setProperty(combinedProperty, previousCombined);
            if (previousMultiIsland == null) System.clearProperty(multiIslandProperty);
            else System.setProperty(multiIslandProperty, previousMultiIsland);
        }
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
