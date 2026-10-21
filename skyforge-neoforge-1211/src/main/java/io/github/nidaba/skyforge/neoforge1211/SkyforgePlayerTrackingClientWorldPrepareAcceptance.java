package io.github.nidaba.skyforge.neoforge1211;

import java.util.Map;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Creates only the blank quick-play world used by PLATFORM-012 actual-client qualification. */
final class SkyforgePlayerTrackingClientWorldPrepareAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformPlayerTrackingClientWorldPrepare";

    private SkyforgePlayerTrackingClientWorldPrepareAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgePlayerTrackingClientWorldPrepareAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                event.getServer(),
                Map.of(
                        "playerTrackingClientWorldPrepared", true,
                        "preassembledPlatformFixturePersisted", false));
    }
}
