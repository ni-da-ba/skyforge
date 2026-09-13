package io.github.nidaba.skyforge.neoforge1211;

import java.util.Map;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Creates only the reusable blank quick-play world required by the v0.20 actual-client run. */
final class SkyforgeAircraftCompilerPilotWorldPrepareAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.aircraftCompilerPilotWorldPrepare";

    private SkyforgeAircraftCompilerPilotWorldPrepareAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeAircraftCompilerPilotWorldPrepareAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                event.getServer(),
                Map.of(
                        "pilotClientWorldPrepared", true,
                        "aircraftAssemblyPersistedIntoPreparedWorld", false));
    }
}
