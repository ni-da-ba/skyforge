package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Development-only acceptance for the standalone 1:1 Nether coordinate-scale datapack.
 *
 * <p>The assertion reads the final live dimension types after datapack loading. It does not modify
 * portal behavior or dimension state.
 */
final class SkyforgeWaveC10NetherScaleAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.waveC10NetherScaleAcceptance";
    private static final double EPSILON = 1.0e-12;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeWaveC10NetherScaleAcceptance.class.getName());

    private SkyforgeWaveC10NetherScaleAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWaveC10NetherScaleAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ServerLevel overworld = event.getServer().overworld();
        ServerLevel nether = event.getServer().getLevel(Level.NETHER);
        if (nether == null) {
            fail("Nether ServerLevel was not present after server start");
            return;
        }

        double overworldScale = overworld.dimensionType().coordinateScale();
        double netherScale = nether.dimensionType().coordinateScale();

        if (Math.abs(overworldScale - 1.0) > EPSILON) {
            fail("Overworld coordinate scale changed unexpectedly: " + overworldScale);
            return;
        }
        if (Math.abs(netherScale - 1.0) > EPSILON) {
            fail("Nether datapack did not produce coordinate_scale=1.0; live value=" + netherScale);
            return;
        }

        LOGGER.log(
                System.Logger.Level.INFO,
                "WAVE_C10_ACCEPTANCE PASS overworldScale="
                        + overworldScale
                        + " netherScale="
                        + netherScale);
    }

    private static void fail(String reason) {
        LOGGER.log(System.Logger.Level.ERROR, "WAVE_C10_ACCEPTANCE FAIL " + reason);
        throw new IllegalStateException("Wave C10 Nether-scale acceptance failed: " + reason);
    }
}
