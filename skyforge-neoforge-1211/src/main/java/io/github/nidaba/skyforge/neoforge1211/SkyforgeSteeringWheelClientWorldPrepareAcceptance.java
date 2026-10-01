package io.github.nidaba.skyforge.neoforge1211;

import java.util.Map;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Creates only the blank quick-play world used by PLATFORM-006 actual-client qualification. */
final class SkyforgeSteeringWheelClientWorldPrepareAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.compilerPlatformSteeringWheelClientWorldPrepare";

    private SkyforgeSteeringWheelClientWorldPrepareAcceptance() {}

    static void installFromSystemProperty() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(SkyforgeSteeringWheelClientWorldPrepareAcceptance::onServerStarted);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        SkyforgeAutomatedAcceptanceHarness.completeServerCase(
                event.getServer(),
                Map.of(
                        "steeringWheelClientWorldPrepared", true,
                        "preassembledPlatformFixturePersisted", false));
    }
}
