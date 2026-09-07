package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual-client half of the persisted SF-IMP-0082 morphology-atlas proof. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211ProductionMorphologyAtlasViewerClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 240_000_000_000L;
    private static boolean proofComplete;
    private static long firstClientTickNanos = Long.MIN_VALUE;

    private SkyforgeNeoForge1211ProductionMorphologyAtlasViewerClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (proofComplete
                || !SkyforgeNeoForge1211ProductionMorphologyAtlasViewer.enabled()
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            return;
        }
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "SF-IMP-0082 actual-client atlas reopen did not complete within 240 seconds");
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || !SkyforgeNeoForge1211ProductionMorphologyAtlasViewer
                        .acceptanceServerProofComplete()) {
            return;
        }

        proofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(
                java.util.Map.of(
                        "viewerClientPass", true,
                        "viewerClientPlayer", minecraft.player.getName().getString(),
                        "viewerMorphologyEvidence",
                                SkyforgeNeoForge1211ProductionMorphologyAtlasViewer
                                        .acceptanceEvidence()));
        minecraft.stop();
    }
}
