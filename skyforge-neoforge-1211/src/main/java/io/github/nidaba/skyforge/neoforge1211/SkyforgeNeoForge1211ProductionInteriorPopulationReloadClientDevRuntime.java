package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual ClientLevel half of the SF-IMP-0069 persisted native interior proof. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211ProductionInteriorPopulationReloadClientDevRuntime {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ProductionInteriorPopulationReloadClientDevRuntime.class.getName());
    private static final long CLIENT_TIMEOUT_NANOS = 90_000_000_000L;

    private static boolean proofComplete;
    private static long firstClientTickNanos = Long.MIN_VALUE;

    private SkyforgeNeoForge1211ProductionInteriorPopulationReloadClientDevRuntime() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (proofComplete || !SkyforgeNeoForge1211ProductionInteriorPopulationReloadDevRuntime.enabled()) {
            return;
        }
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = System.nanoTime();
        }
        if (SkyforgeAutomatedAcceptanceHarness.clientMode()
                && System.nanoTime() - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "SF-IMP-0069 reload quick-play client did not reach persisted interior-fluid verification "
                            + "within 90 seconds");
        }

        var expectation = SkyforgeNeoForge1211ProductionInteriorPopulationReloadDevRuntime.clientExpectation();
        if (expectation == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        BlockPos position = BlockPos.of(expectation.position());
        if (!minecraft.level.getChunkSource().hasChunk(
                SectionPos.blockToSectionCoord(position.getX()),
                SectionPos.blockToSectionCoord(position.getZ()))) {
            return;
        }

        String actualState = minecraft.level.getBlockState(position).toString();
        if (!actualState.equals(expectation.expectedStateText())) {
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "SF-IMP-0069 logical client disagreed with persisted server fluid at "
                            + position + ": server=" + expectation.expectedStateText()
                            + ", client=" + actualState);
            return;
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0069 RELOAD CLIENT PASS: position=" + position
                        + ", state=" + actualState
                        + ". Actual ClientLevel received the persisted native interior fluid.");

        SkyforgeAutomatedAcceptanceHarness.completeClientCase(
                java.util.Map.of(
                        "reloadClientPass", true,
                        "clientFluidPos", Long.toString(position.asLong()),
                        "clientFluidState", actualState));

        if (SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            Thread exitFallback = new Thread(() -> {
                try {
                    Thread.sleep(5_000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
                Runtime.getRuntime().halt(0);
            }, "skyforge-sf-imp-0069-client-pass-exit");
            exitFallback.setDaemon(true);
            exitFallback.start();
            minecraft.stop();
        }
    }
}
