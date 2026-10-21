package io.github.nidaba.skyforge.neoforge1211;

import io.github.nidaba.skyforge.world.SkyIslandTerrainProfile;
import java.util.Map;

/** Reconstructs the accepted DR-50 semantic terrain as a read-only atmosphere authority. */
final class SkyforgeDr50AtmosphereTerrainDevRuntime {
    static final String ENABLE_PROPERTY = "skyforge.dev.dr50AtmosphereTerrainAuthority";
    private static AutoCloseable binding;

    private SkyforgeDr50AtmosphereTerrainDevRuntime() {}

    static synchronized void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || binding != null) {
            return;
        }
        if (SkyforgeNeoForge1211SurfaceStage.hasActiveBinding()
                || SkyforgePhysicalVolumeAdmissionStage.active()
                || SkyforgeNativeSurfacePopulationStage.hasActiveBinding()
                || SkyforgeComposedCaveStage.active()
                || SkyforgeNativeInteriorPopulationStage.active()) {
            throw new IllegalStateException(
                    "DR-50 atmosphere terrain reconstruction must not coexist with mutation bindings");
        }

        var fixture = SkyforgeNeoForge1211ProductionComposedCaveFixture.single();
        var terrain = new SkyforgeNeoForge1211ChunkAdapter(
                fixture.catalog(),
                SkyIslandTerrainProfile.reference(),
                new SkyforgeMinecraftBlockPalette(),
                Map.of(fixture.volume().id(), fixture.descriptor()));
        var ecology = SkyforgeDr40ProductionEcologyEvidence.resolver(fixture);
        binding = SkyforgeAtmosphereTerrainAuthority.install(
                fixture.catalog(),
                terrain,
                Map.of(fixture.volume().id(), ecology));

        System.getLogger(SkyforgeDr50AtmosphereTerrainDevRuntime.class.getName())
                .log(
                        System.Logger.Level.INFO,
                        "Reconstructed read-only DR-50 semantic terrain for atmosphere; volume="
                                + fixture.volume().id().path());
    }
}
