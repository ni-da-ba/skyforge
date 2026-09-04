package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** ClientLevel half of the SF-IMP-0065 authored-cave reload proof. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211AuthoredCaveReloadClientDevRuntime {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211AuthoredCaveReloadClientDevRuntime.class.getName());
    private static final long CLIENT_TIMEOUT_NANOS = 90_000_000_000L;
    private static boolean proofComplete;
    private static long firstClientTickNanos = Long.MIN_VALUE;

    private SkyforgeNeoForge1211AuthoredCaveReloadClientDevRuntime() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (proofComplete || !SkyforgeNeoForge1211AuthoredCaveReloadDevRuntime.enabled()) {
            return;
        }
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = System.nanoTime();
        }
        if (SkyforgeAutomatedAcceptanceHarness.clientMode()
                && System.nanoTime() - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "SF-IMP-0065 reload client did not reach persisted authored-cave verification within 90 seconds");
        }

        var expectation = SkyforgeNeoForge1211AuthoredCaveReloadDevRuntime.clientExpectation();
        if (expectation == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        BlockPos cave = BlockPos.of(expectation.cavePosition());
        BlockPos solid = BlockPos.of(expectation.solidPosition());
        if (!minecraft.level.getChunkSource().hasChunk(
                        SectionPos.blockToSectionCoord(cave.getX()),
                        SectionPos.blockToSectionCoord(cave.getZ()))
                || !minecraft.level.getChunkSource().hasChunk(
                        SectionPos.blockToSectionCoord(solid.getX()),
                        SectionPos.blockToSectionCoord(solid.getZ()))) {
            return;
        }

        var caveState = minecraft.level.getBlockState(cave);
        var solidState = minecraft.level.getBlockState(solid);
        if (!caveState.equals(expectation.caveState())
                || !solidState.equals(expectation.solidState())) {
            throw new IllegalStateException(
                    "SF-IMP-0065 ClientLevel disagreed with persisted authored cave controls: cave="
                            + cave + " server=" + expectation.caveState() + " client=" + caveState
                            + ", solid=" + solid + " server=" + expectation.solidState()
                            + " client=" + solidState);
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0065 RELOAD CLIENT PASS: cave=" + cave
                        + ", caveState=" + caveState
                        + ", solidControl=" + solid
                        + ", solidState=" + solidState + ".");

        SkyforgeAutomatedAcceptanceHarness.completeClientCase(
                java.util.Map.of(
                        "reloadClientPass", true,
                        "clientCavePos", Long.toString(cave.asLong()),
                        "clientCaveState", caveState.toString(),
                        "clientSolidControlState", solidState.toString()));
        if (SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            minecraft.stop();
        }
    }
}
