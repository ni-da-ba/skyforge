package io.github.nidaba.skyforge.neoforge1211;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Bounded actual-client join proof for the modded-biome dedicated-server specimen. */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID, value = Dist.CLIENT)
final class SkyforgeNeoForge1211BiomePopulationJoinClientAcceptance {
    static final String ENABLE_PROPERTY = "skyforge.dev.biomePopulationJoinClient";
    // Loading the full modded client and its resource registries on a hosted runner can exceed
    // the usual three-minute startup budget. Keep the join proof bounded while allowing six
    // minutes, consistent with the workflow's longer client task timeout.
    private static final long CLIENT_TIMEOUT_NANOS = 360_000_000_000L;

    private static long firstClientTickNanos = Long.MIN_VALUE;
    private static boolean connectionStarted;
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
                    "modded-biome client did not join the dedicated server within 360 seconds"
                            + "; screen="
                            + (Minecraft.getInstance().screen == null
                                    ? "none"
                                    : Minecraft.getInstance().screen.getClass().getName())
                            + "; connectionPresent="
                            + (Minecraft.getInstance().getConnection() != null));
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!connectionStarted
                && minecraft.level == null
                && minecraft.getConnection() == null
                && minecraft.screen != null) {
            connectionStarted = true;
            System.getLogger(SkyforgeNeoForge1211BiomePopulationJoinClientAcceptance.class.getName())
                    .log(
                            System.Logger.Level.INFO,
                            "Starting explicit modded-biome acceptance connection; screen={0}",
                            minecraft.screen.getClass().getName());
            ServerData server = new ServerData(
                    "Skyforge Acceptance",
                    "127.0.0.1:25565",
                    ServerData.Type.OTHER);
            ConnectScreen.startConnecting(
                    minecraft.screen,
                    minecraft,
                    ServerAddress.parseString(server.ip),
                    server,
                    false,
                    new TransferState(Map.of()));
        }

        if (minecraft.level == null
                || minecraft.player == null
                || minecraft.getSingleplayerServer() != null
                || !minecraft.level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        BlockPos feet = minecraft.player.blockPosition();
        BlockPos supportPosition = feet.below();
        BlockState feetState = minecraft.level.getBlockState(feet);
        BlockState supportState = minecraft.level.getBlockState(supportPosition);
        if (!feetState.isAir()
                || supportState.isAir()
                || supportState.getCollisionShape(minecraft.level, supportPosition).isEmpty()) {
            return;
        }

        proofComplete = true;
        SkyforgeAutomatedAcceptanceHarness.completeClientCase(
                Map.of(
                        "remoteServerJoin", true,
                        "player", minecraft.player.getName().getString(),
                        "dimension", minecraft.level.dimension().location(),
                        "spawnX", minecraft.player.getBlockX(),
                        "spawnY", minecraft.player.getBlockY(),
                        "spawnZ", minecraft.player.getBlockZ(),
                        "spawnSupportBlock", supportState.getBlock().toString(),
                        "spawnSupported", true,
                        "singleplayerServer", false));
        minecraft.stop();
    }
}
