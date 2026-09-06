package io.github.nidaba.skyforge.neoforge1211;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Minimal production NeoForge entrypoint for the Minecraft 1.21.1 adapter. */
@Mod(SkyforgeNeoForge1211Mod.MOD_ID)
public final class SkyforgeNeoForge1211Mod {
    public static final String MOD_ID = "skyforge";

    public SkyforgeNeoForge1211Mod(IEventBus modEventBus) {
        SkyforgeRuntimePerformanceMetrics.initialize();
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
        SkyforgeNeoForge1211CarverDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211UndergroundDecorationDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211FluidSpringsDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211NativeLakesDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211AuthoredCaveDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211ExteriorConnectedCaveDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211ComposedCaveDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211ProductionComposedCaveDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211ProductionInteriorPopulationDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211ProductionInteriorPopulationReloadDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211ProductionInteriorPopulationStackedDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211ProductionComposedCaveStackedDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211ComposedCaveStackedDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211ExteriorConnectedCaveStackedDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211AuthoredCaveStackedDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211NativeLakesStackedDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211NativeLakesReloadDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211FluidSpringsStackedDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211FluidSpringsReloadDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211UndergroundDecorationStackedDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211UndergroundDecorationReloadDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211CarverReloadDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211CarverStackedDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211LocalModificationsStackedDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211UndergroundStackedDevRuntime.installFromSystemProperty();
        SkyforgeNeoForge1211ShowcaseViewer.installFromSystemProperty();
        SkyforgeWaveC6SoaringFaunaDevRuntime.installFromSystemProperty();
        SkyforgeWaveC7GliderLiftDevRuntime.installFromSystemProperty();
    }
}
