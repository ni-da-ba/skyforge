package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** NeoForge-owned registration boundary for Skyforge placement-modifier codecs. */
final class SkyforgeNeoForge1211PlacementModifiers {
    static final String ADDITIONAL_SURFACES_NAME = "additional_surfaces";

    private static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, SkyforgeNeoForge1211Mod.MOD_ID);

    static final Holder<PlacementModifierType<?>> ADDITIONAL_SURFACES = PLACEMENT_MODIFIERS.register(
            ADDITIONAL_SURFACES_NAME,
            () -> () -> SkyforgeAdditionalSurfacePlacement.CODEC);

    private SkyforgeNeoForge1211PlacementModifiers() {}

    static void register(IEventBus modEventBus) {
        PLACEMENT_MODIFIERS.register(modEventBus);
    }
}
