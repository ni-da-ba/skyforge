package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client-side half of the SF-IMP-0062 persisted native cave-decoration proof. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211UndergroundDecorationReloadClientDevRuntime {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211UndergroundDecorationReloadClientDevRuntime.class.getName());
    private static final long CLIENT_TIMEOUT_NANOS = 90_000_000_000L;

    private static boolean proofComplete;
    private static long firstClientTickNanos = Long.MIN_VALUE;

    private SkyforgeNeoForge1211UndergroundDecorationReloadClientDevRuntime() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (proofComplete || !SkyforgeNeoForge1211UndergroundDecorationReloadDevRuntime.enabled()) {
            return;
        }
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = System.nanoTime();
        }
        if (SkyforgeAutomatedAcceptanceHarness.clientMode()
                && System.nanoTime() - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "SF-IMP-0062 reload quick-play client did not reach persisted decoration verification "
                            + "within 90 seconds");
        }

        var expectation = SkyforgeNeoForge1211UndergroundDecorationReloadDevRuntime.clientExpectation();
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
                    "SF-IMP-0062 logical client disagreed with persisted server decoration at "
                            + position + ": server=" + expectation.expectedStateText()
                            + ", client=" + actualState);
            return;
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0062 RELOAD CLIENT PASS: position=" + position
                        + ", state=" + actualState
                        + ". The actual ClientLevel contains the same native cave decoration after reload.");

        SkyforgeAutomatedAcceptanceHarness.completeClientCase(
                java.util.Map.of(
                        "reloadClientPass", true,
                        "clientDecorationPos", position.toShortString(),
                        "clientDecorationState", actualState));
        if (SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            minecraft.stop();
        }
    }
}
