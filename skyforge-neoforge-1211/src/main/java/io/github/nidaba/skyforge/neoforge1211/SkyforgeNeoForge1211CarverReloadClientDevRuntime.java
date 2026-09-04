package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Client-side half of the SF-IMP-0061 save/reload persistence proof. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211CarverReloadClientDevRuntime {
    private static final System.Logger LOGGER =
            System.getLogger(SkyforgeNeoForge1211CarverReloadClientDevRuntime.class.getName());
    private static boolean proofComplete;

    private SkyforgeNeoForge1211CarverReloadClientDevRuntime() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (proofComplete || !SkyforgeNeoForge1211CarverReloadDevRuntime.enabled()) {
            return;
        }
        var expectation = SkyforgeNeoForge1211CarverReloadDevRuntime.clientExpectation();
        if (expectation == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        BlockPos position = BlockPos.of(expectation.position());
        if (!minecraft.level.hasChunkAt(position)) {
            return;
        }
        var actual = minecraft.level.getBlockState(position);
        if (!actual.equals(expectation.expectedState())) {
            throw new IllegalStateException(
                    "SF-IMP-0061 reload client disagreed with persisted server cave state at "
                            + position + ": server=" + expectation.expectedState() + ", client=" + actual);
        }

        proofComplete = true;
        LOGGER.log(
                System.Logger.Level.INFO,
                "SF-IMP-0061 RELOAD CLIENT PASS: position=" + position
                        + ", state=" + actual
                        + ". The tracking client's ClientLevel contains the same persisted cave block "
                        + "after a full save/stop/reload.");
    }
}
