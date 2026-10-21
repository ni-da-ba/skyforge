package io.github.nidaba.skyforge.neoforge1211;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

final class MinecraftBlockStateResolverTest {

    @Test
    void resolvesRegisteredVanillaKeysToLiveDefaultStates() {
        MinecraftTestChunkFactory.protoChunk(new net.minecraft.world.level.ChunkPos(0, 0));
        MinecraftBlockStateResolver resolver = new MinecraftBlockStateResolver();

        assertEquals(Blocks.AIR.defaultBlockState(), resolver.resolve(SkyforgeMinecraftBlockPalette.AIR));
        assertEquals(Blocks.DIRT.defaultBlockState(), resolver.resolve(SkyforgeMinecraftBlockPalette.DIRT));
        assertEquals(Blocks.STONE.defaultBlockState(), resolver.resolve(SkyforgeMinecraftBlockPalette.STONE));
        assertEquals(Blocks.DEEPSLATE.defaultBlockState(), resolver.resolve(SkyforgeMinecraftBlockPalette.DEEPSLATE));
    }

    @Test
    void missingRegistryKeyFailsInsteadOfFallingThroughDefaultRegistry() {
        MinecraftTestChunkFactory.protoChunk(new net.minecraft.world.level.ChunkPos(0, 0));
        MinecraftBlockStateResolver resolver = new MinecraftBlockStateResolver();
        ResourceLocation missing = ResourceLocation.fromNamespaceAndPath("skyforge", "definitely_missing_block");

        assertEquals(false, BuiltInRegistries.BLOCK.containsKey(missing));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(missing));
    }
}
