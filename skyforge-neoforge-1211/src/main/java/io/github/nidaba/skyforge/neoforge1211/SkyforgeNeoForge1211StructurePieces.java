package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NeoForge-owned registration boundary for serializable Skyforge structure accommodation pieces. */
final class SkyforgeNeoForge1211StructurePieces {
    static final String FOUNDATION_NAME = "foundation";

    private static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, SkyforgeNeoForge1211Mod.MOD_ID);

    static final DeferredHolder<StructurePieceType, StructurePieceType> FOUNDATION = STRUCTURE_PIECES.register(
            FOUNDATION_NAME,
            SkyforgeNeoForge1211StructurePieces::foundationType);

    private SkyforgeNeoForge1211StructurePieces() {}

    private static StructurePieceType foundationType() {
        return (StructurePieceType.ContextlessType) SkyforgeFoundationPiece::new;
    }

    static void register(IEventBus modEventBus) {
        STRUCTURE_PIECES.register(modEventBus);
    }
}
