package io.github.nidaba.skyforge.neoforge1211;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Minimal production NeoForge entrypoint for the Minecraft 1.21.1 adapter. */
@Mod(SkyforgeNeoForge1211Mod.MOD_ID)
public final class SkyforgeNeoForge1211Mod {
    public static final String MOD_ID = "skyforge";

    public SkyforgeNeoForge1211Mod(IEventBus modEventBus) {
        // Register supported worldgen codecs without changing any ordinary world. A world/datapack
        // must select the Skyforge generator or placement behavior explicitly, and both remain inert
        // without the appropriate compiled runtime scope.
        SkyforgeNeoForge1211ChunkGenerators.register(modEventBus);
        SkyforgeNeoForge1211PlacementModifiers.register(modEventBus);
        SkyforgeNeoForge1211StructurePieces.register(modEventBus);

        // Normal packaged Skyforge remains inert until a runtime binding is configured. Isolated
        // ModDevGradle runs opt into exactly one finite development specimen through JVM properties.
        SkyforgeNeoForge1211DevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211AccommodationDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211UndersideContradictionDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211IsolationDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211PopulationDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211BiomePopulationDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211SurfacePopulationDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211PhysicalAdmissionDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211LocalModificationsDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211UndergroundStackedDevRuntime.installFromSystemProperty();
    }
}
