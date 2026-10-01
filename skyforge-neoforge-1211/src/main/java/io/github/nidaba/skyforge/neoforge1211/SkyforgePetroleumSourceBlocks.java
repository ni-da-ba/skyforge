package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Skyforge's sole retained-CDG pumpjack termination block. */
final class SkyforgePetroleumSourceBlocks {
    static final String PETROLEUM_SOURCE_NAME = "petroleum_source";
    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, SkyforgeNeoForge1211Mod.MOD_ID);
    static final DeferredHolder<Block, Block> PETROLEUM_SOURCE = BLOCKS.register(
            PETROLEUM_SOURCE_NAME,
            () -> new Block(BlockBehaviour.Properties.of().strength(-1.0F, 3_600_000.0F)));

    private SkyforgePetroleumSourceBlocks() {}

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
