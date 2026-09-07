package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual-client half of the persisted SF-IMP-0083 four-member family seed/scale proof. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211ProductionMorphologySeedScaleViewerClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 360_000_000_000L;
    private static boolean proofComplete;
    private static long firstClientTickNanos = Long.MIN_VALUE;

    private SkyforgeNeoForge1211ProductionMorphologySeedScaleViewerClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (proofComplete
                || !SkyforgeNeoForge1211ProductionMorphologySeedScaleViewer.enabled()
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            return;
        }
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "SF-IMP-0083 actual-client family-atlas reopen did not complete within 360 seconds");
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || !SkyforgeNeoForge1211ProductionMorphologySeedScaleViewer
                        .acceptanceServerProofComplete()) {
            return;
        }

        proofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(
                java.util.Map.of(
                        "viewerClientPass", true,
                        "viewerClientPlayer", minecraft.player.getName().getString(),
                        "viewerMorphologyEvidence",
                                SkyforgeNeoForge1211ProductionMorphologySeedScaleViewer
                                        .acceptanceEvidence()));
        minecraft.stop();
    }
}
