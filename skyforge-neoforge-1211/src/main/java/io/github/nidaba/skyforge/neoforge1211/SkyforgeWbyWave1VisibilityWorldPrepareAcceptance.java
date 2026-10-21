package io.github.nidaba.skyforge.neoforge1211;

import java.util.Map;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Creates the blank quick-play world used by the WBY Wave 1 actual-client visibility gate. */
final class SkyforgeWbyWave1VisibilityWorldPrepareAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.wbyWave1VisibilityWorldPrepare";

    private SkyforgeWbyWave1VisibilityWorldPrepareAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeWbyWave1VisibilityWorldPrepareAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                event.getServer(),
                Map.of(
                        "wbyWave1VisibilityWorldPrepared", true,
                        "preassembledDistantSableBodyPersisted", false));
    }
}
