package io.github.nidaba.skyforge.neoforge1211;

import net.neoforged.fml.common.Mod;

/** Minimal production NeoForge entrypoint for the Minecraft 1.21.1 adapter. */
@Mod(SkyforgeNeoForge1211Mod.MOD_ID)
public final class SkyforgeNeoForge1211Mod {
    public static final String MOD_ID = "skyforge";

    public SkyforgeNeoForge1211Mod() {
        // Normal packaged Skyforge remains inert until a runtime binding is configured. The
        // isolated ModDevGradle client used by SF-IMP-0034 opts into one finite development
        // specimen through a JVM property.
        SkyforgeNeoForge1211DevRuntime.installFromSystemProperty();
    }
}
