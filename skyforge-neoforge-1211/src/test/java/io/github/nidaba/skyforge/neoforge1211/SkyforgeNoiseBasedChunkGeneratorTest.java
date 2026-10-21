package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.Test;

final class SkyforgeNoiseBasedChunkGeneratorTest {
    @Test
    void fmlRegistersSkyforgeNoiseOverlayGeneratorCodec() {
        assertTrue(ModList.get().isLoaded(SkyforgeNeoForge1211Mod.MOD_ID));

        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                SkyforgeNeoForge1211Mod.MOD_ID,
                SkyforgeNeoForge1211ChunkGenerators.NOISE_OVERLAY_NAME);
        assertSame(
                SkyforgeNoiseBasedChunkGenerator.CODEC,
                BuiltInRegistries.CHUNK_GENERATOR.get(id));
    }
}
