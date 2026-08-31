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

        // Normal packaged Skyforge remains inert until a runtime binding is configured. The
        // isolated ModDevGradle client opts into one finite development specimen through a JVM
        // property.
        SkyforgeNeoForge1211DevRuntime.installFromSystemProperty();
    }
}
