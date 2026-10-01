package io.github.nidaba.skyforge.neoforge1211.mixin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preserves packet ordering around Sable 2.0.5 sublevel full-sync.
 *
 * <p>The pinned Sable tracking system sends StartTracking + plot chunks + Finalize over the normal
 * ordered connection, then in the same tick can send the first movement snapshot over UDP. On a
 * real multiplayer client that UDP snapshot can win the race and be discarded because the client
 * has not created the sublevel yet. WBY-INT-0002 reproduced that exact failure with both clients
 * authenticated over Sable UDP.
 *
 * <p>For ten ticks after each full sync, this mixin reports the UDP path unavailable only for that
 * player. Sable then uses its own bundled ordered fallback. No packet contents, poses, tracking
 * range, or steady-state UDP behavior are changed. The injection points are deliberately required:
 * re-verify them before changing the pinned Sable 2.0.5 runtime.
 */
@Pseudo
@Mixin(
        targets = "dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem",
        remap = false)
abstract class SkyforgeSableSubLevelTrackingOrderingMixin {
    @Unique private static volatile Method skyforge$isConnectedToMethod;

    @Inject(
            method = "sendFullSync(Lnet/minecraft/server/level/ServerPlayer;"
                    + "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;"
                    + "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V",
            at = @At("HEAD"),
            require = 1,
            remap = false)
    private void skyforge$recordFreshFullSync(
            ServerPlayer player,
            @Coerce Object ignoredSubLevel,
            CustomPacketPayload ignoredExtraPacket,
            CallbackInfo ci) {
        SkyforgeSableFullSyncOrderingGuard.recordFullSync(
                player.getUUID(),
                player.level().getServer().getTickCount());
    }

    @Redirect(
            method = "sendMovementUpdates",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/network/udp/SableUDPServer;"
                            + "isConnectedTo(Lnet/minecraft/server/level/ServerPlayer;)Z",
                    remap = false),
            require = 1,
            remap = false)
    private boolean skyforge$keepFreshFullSyncSnapshotsOrdered(
            @Coerce Object udpServer,
            ServerPlayer player) {
        long serverTick = player.level().getServer().getTickCount();
        if (SkyforgeSableFullSyncOrderingGuard.needsOrderedSnapshots(player.getUUID(), serverTick)) {
            return false;
        }

        try {
            Method method = skyforge$isConnectedToMethod;
            if (method == null || method.getDeclaringClass() != udpServer.getClass()) {
                method = udpServer.getClass().getMethod("isConnectedTo", ServerPlayer.class);
                skyforge$isConnectedToMethod = method;
            }
            return (Boolean) method.invoke(udpServer, player);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Pinned Sable UDP connection contract changed; re-verify WBY ordering compatibility",
                    exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Sable UDP connection probe failed", cause);
        }
    }
}
