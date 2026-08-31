package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import org.junit.jupiter.api.Test;

final class SkyforgeNeoForge1211FeatureStageTest {
    @Test
    void additionalSurfacePlacementTypeIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                SkyforgeNeoForge1211Mod.MOD_ID,
                SkyforgeNeoForge1211PlacementModifiers.ADDITIONAL_SURFACES_NAME);
        PlacementModifierType<?> type = BuiltInRegistries.PLACEMENT_MODIFIER_TYPE.get(id);

        assertNotNull(type);
        assertSame(SkyforgeAdditionalSurfacePlacement.CODEC, type.codec());
    }

    @Test
    void scopeWithoutSkyforgeBindingIsEmptyAndAlwaysCleared() {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());
        assertFalse(SkyforgeNeoForge1211FeatureStage.hasActiveScope());

        try (SkyforgeNeoForge1211FeatureStage.Scope scope = SkyforgeNeoForge1211FeatureStage.open(chunk)) {
            scope.requireActive();
            assertTrue(SkyforgeNeoForge1211FeatureStage.hasActiveScope());
            assertTrue(SkyforgeNeoForge1211FeatureStage.additionalPositions(0, 0).isEmpty());
        }

        assertFalse(SkyforgeNeoForge1211FeatureStage.hasActiveScope());
    }

    @Test
    void activeScopeExposesPreservedGroundBelowDevelopmentMassif() throws Exception {
        ProtoChunk chunk = MinecraftTestChunkFactory.protoChunk(new ChunkPos(0, 0));
        chunk.setBlockState(new BlockPos(0, 64, 0), Blocks.GRASS_BLOCK.defaultBlockState(), false);

        try (AutoCloseable binding = SkyforgeNeoForge1211DevRuntime.installSpecimen()) {
            assertNotNull(binding);
            assertTrue(SkyforgeNeoForge1211SurfaceStage.realize(chunk).isPresent());
            Heightmap.primeHeightmaps(chunk, ChunkStatus.FINAL_HEIGHTMAPS);

            try (SkyforgeNeoForge1211FeatureStage.Scope scope =
                    SkyforgeNeoForge1211FeatureStage.open(chunk)) {
                scope.requireActive();
                assertTrue(SkyforgeNeoForge1211FeatureStage.additionalPositions(0, 0)
                        .contains(new BlockPos(0, 65, 0)));
            }
        }

        assertFalse(SkyforgeNeoForge1211FeatureStage.hasActiveScope());
        assertFalse(SkyforgeNeoForge1211SurfaceStage.hasActiveBinding());
    }
}
