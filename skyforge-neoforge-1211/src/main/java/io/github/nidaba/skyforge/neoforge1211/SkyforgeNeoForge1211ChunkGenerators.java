package io.github.nidaba.skyforge.neoforge1211;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NeoForge-owned registration boundary for Skyforge chunk-generator codecs. */
final class SkyforgeNeoForge1211ChunkGenerators {
    static final String NOISE_OVERLAY_NAME = "noise_overlay";

    private static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, SkyforgeNeoForge1211Mod.MOD_ID);

    static final Holder<MapCodec<? extends ChunkGenerator>> NOISE_OVERLAY = CHUNK_GENERATORS.register(
            NOISE_OVERLAY_NAME,
            () -> SkyforgeNoiseBasedChunkGenerator.CODEC);

    private SkyforgeNeoForge1211ChunkGenerators() {}

    static void register(IEventBus modEventBus) {
        CHUNK_GENERATORS.register(modEventBus);
    }
}
