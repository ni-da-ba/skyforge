package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual-client half of the persisted SF-IMP-0083 single-member seed/scale proof. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211ProductionMorphologySeedScaleViewerClientAcceptance {
    private static final long CLIENT_TIMEOUT_NANOS = 360_000_000_000L;
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionMorphologySeedScaleViewerClientAcceptance.class.getName());

    private static boolean proofComplete;
    private static boolean expectedExperimentalWorldWarningAccepted;
    private static long firstClientTickNanos = Long.MIN_VALUE;
    private static String lastScreenClass = "none";

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
        Minecraft minecraft = Minecraft.getInstance();
        acceptExpectedExperimentalWorldWarning(minecraft);

        if (System.nanoTime() - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "SF-IMP-0083 actual-client single-member reopen did not complete within 360 seconds"
                            + "; lastScreen=" + lastScreenClass);
        }
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
                        "viewerExperimentalWorldWarningAccepted",
                                expectedExperimentalWorldWarningAccepted,
                        "viewerMorphologyEvidence",
                                SkyforgeNeoForge1211ProductionMorphologySeedScaleViewer
                                        .acceptanceEvidence()));
        minecraft.stop();
    }

    private static void acceptExpectedExperimentalWorldWarning(Minecraft minecraft) {
        var screen = minecraft.screen;
        String screenClass = screen == null ? "none" : screen.getClass().getName();
        if (!screenClass.equals(lastScreenClass)) {
            lastScreenClass = screenClass;
            LOGGER.log(
                    System.Logger.Level.INFO,
                    "SF-IMP-0083 actual-client reopen screen: " + lastScreenClass);
        }

        if (expectedExperimentalWorldWarningAccepted || !(screen instanceof BackupConfirmScreen)) {
            return;
        }

        Component skipAndLoad = Component.translatable("selectWorld.backupJoinSkipButton");
        for (var child : screen.children()) {
            if (child instanceof Button button
                    && button.active
                    && button.visible
                    && button.getMessage().equals(skipAndLoad)) {
                expectedExperimentalWorldWarningAccepted = true;
                LOGGER.log(
                        System.Logger.Level.INFO,
                        "SF-IMP-0083 acceptance acknowledged the expected experimental-world load warning.");
                button.onPress();
                return;
            }
        }
    }
}
