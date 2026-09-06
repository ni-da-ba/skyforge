package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual-client half of the persisted current-capability showcase reopen acceptance. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211ShowcaseViewerClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 90_000_000_000L;
    private static boolean proofComplete;
    private static long firstClientTickNanos = Long.MIN_VALUE;

    private SkyforgeNeoForge1211ShowcaseViewerClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (proofComplete
                || !SkyforgeNeoForge1211ShowcaseViewer.enabled()
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            return;
        }
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "showcase quick-play client did not survive persisted-fluid reopen proof within 90 seconds");
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || !SkyforgeNeoForge1211ShowcaseViewer.acceptanceServerProofComplete()) {
            return;
        }

        proofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(
                java.util.Map.of(
                        "viewerClientPass", true,
                        "viewerClientPlayer", minecraft.player.getName().getString(),
                        "viewerAcceptedSample",
                                SkyforgeNeoForge1211ShowcaseViewer.acceptanceSampleDescription()));
        minecraft.stop();
    }
}
