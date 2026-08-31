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
