package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual-client half of the persisted SF-IMP-0080 ecology showcase reopen proof. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211ShowcaseEcologyViewerClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 180_000_000_000L;
    private static boolean proofComplete;
    private static long firstClientTickNanos = Long.MIN_VALUE;

    private SkyforgeNeoForge1211ShowcaseEcologyViewerClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (proofComplete
                || !SkyforgeNeoForge1211ShowcaseEcologyViewer.enabled()
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            return;
        }
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "SF-IMP-0080 actual-client ecology reopen did not complete within 180 seconds");
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || !SkyforgeNeoForge1211ShowcaseEcologyViewer.acceptanceServerProofComplete()) {
            return;
        }

        proofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(
                java.util.Map.of(
                        "viewerClientPass", true,
                        "viewerClientPlayer", minecraft.player.getName().getString(),
                        "viewerEcologyEvidence",
                                SkyforgeNeoForge1211ShowcaseEcologyViewer.acceptanceEvidence()));
        minecraft.stop();
    }
}
