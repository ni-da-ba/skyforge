package io.github.nidaba.skyforge.neoforge1211;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Services admitted deferred Skyforge terrain only after Minecraft has promoted target chunks to
 * stable loaded LevelChunks.
 *
 * <p>The service uses {@code ServerChunkCache#getChunkNow}, which never creates a generation ticket.
 * Missing chunks simply remain pending until Minecraft loads them for an independent reason.
 */
@EventBusSubscriber(modid = SkyforgeNeoForge1211Mod.MOD_ID)
final class SkyforgePhysicalVolumeCatchupService {
    private SkyforgePhysicalVolumeCatchupService() {}

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (!SkyforgePhysicalVolumeAdmissionStage.active()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SkyforgeNeoForge1211SurfaceStage.serviceLoadedCatchup(level);
            SkyforgeNeoForge1211PhysicalAdmissionDevRuntime.observeLoaded(level);
        }
    }
}
