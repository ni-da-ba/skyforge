package io.github.nidaba.skyforge.neoforge1211;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Bounded actual-client join proof for the modded-biome dedicated-server specimen. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211BiomePopulationJoinClientAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.biomePopulationJoinClient";
    private static final long CLIENT_TIMEOUT_NANOS = 180_000_000_000L;

    private static long firstClientTickNanos = Long.MIN_VALUE;
    private static boolean proofComplete;

    private SkyforgeNeoForge1211BiomePopulationJoinClientAcceptance() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)
                || proofComplete
                || !SkyforgeAutomatedAcceptanceHarness.clientMode()) {
            return;
        }

        if (firstClientTickNanos == Long.MIN_VALUE) {
            firstClientTickNanos = System.nanoTime();
        }
        if (System.nanoTime() - firstClientTickNanos > CLIENT_TIMEOUT_NANOS) {
            SkyforgeAutomatedAcceptanceHarness.failClientCase(
                    "modded-biome client did not join the dedicated server within 180 seconds");
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null
                || minecraft.player == null
                || minecraft.getSingleplayerServer() != null
                || !minecraft.level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        proofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(
                Map.of(
                        "remoteServerJoin", true,
                        "player", minecraft.player.getName().getString(),
                        "dimension", minecraft.level.dimension().location(),
                        "singleplayerServer", false));
        minecraft.stop();
    }
}
