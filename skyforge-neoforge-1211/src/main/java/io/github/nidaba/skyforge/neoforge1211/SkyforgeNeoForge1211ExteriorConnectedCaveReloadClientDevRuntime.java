package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** ClientLevel half of the SF-IMP-0066 persisted exterior-cave proof. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211ExteriorConnectedCaveReloadClientDevRuntime {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211ExteriorConnectedCaveReloadClientDevRuntime.class.getName());
    private static final long CLIENT_TIMEOUT_NANOS = 90_000_000_000L;
    private static boolean proofComplete;
    private static long firstClientTickNanos = Long.MIN_VALUE;

    private SkyforgeNeoForge1211ExteriorConnectedCaveReloadClientDevRuntime() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (proofComplete || !SkyforgeNeoForge1211ExteriorConnectedCaveReloadDevRuntime.enabled()) {
            return;
        }
        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = System.nanoTime();
        }
        if (SkyforgeAutomatedAcceptanceHarness.clientMode()
                && System.nanoTime() - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "SF-IMP-0066 reload client did not reach persisted exterior-cave verification "
                            + "within 90 seconds");
        }

        var expectation =
                SkyforgeNeoForge1211ExteriorConnectedCaveReloadDevRuntime.clientExpectation();
        if (expectation == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        BlockPos mouth = BlockPos.of(expectation.mouthPosition());
        BlockPos outward = BlockPos.of(expectation.outwardPosition());
        BlockPos base = BlockPos.of(expectation.basePosition());
        for (BlockPos position : java.util.List.of(mouth, outward, base)) {
            if (!minecraft.level.getChunkSource().hasChunk(
                    SectionPos.blockToSectionCoord(position.getX()),
                    SectionPos.blockToSectionCoord(position.getZ()))) {
                return;
            }
        }

        var mouthState = minecraft.level.getBlockState(mouth);
        var outwardState = minecraft.level.getBlockState(outward);
        var baseState = minecraft.level.getBlockState(base);
        if (!mouthState.equals(expectation.mouthState())
                || !outwardState.equals(expectation.outwardState())
                || !baseState.equals(expectation.baseState())) {
            throw new IllegalStateException(
                    "SF-IMP-0066 ClientLevel disagreed with persisted exterior cave: mouth="
                            + mouthState + ", outward=" + outwardState + ", base=" + baseState);
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0066 RELOAD CLIENT PASS: mouth=" + mouth
                        + ", outwardExterior=" + outward
                        + ", baseCave=" + base + ".");

        SkyforgeAutomatedAcceptanceHarness.completeClientCase(
                java.util.Map.of(
                        "reloadClientPass", true,
                        "clientMouthPos", Long.toString(mouth.asLong()),
                        "clientMouthState", mouthState.toString(),
                        "clientOutwardState", outwardState.toString(),
                        "clientBaseCaveState", baseState.toString()));
        if (SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            minecraft.stop();
        }
    }
}
