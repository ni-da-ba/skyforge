package io.github.nidaba.skyforge.neoforge1211.mixin;

import io.github.nidaba.skyforge.neoforge1211.SkyforgeSableFullSyncOrdering;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Coerce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Orders Sable 2.0.5 movement snapshots behind a newly tracked sub-level's full sync.
 *
 * <p>The pinned Sable {@code SubLevelTrackingSystem} sends StartTracking/chunks/Finalize through
 * the ordered Minecraft connection, then may send the same tick's first MOVE through UDP. On a
 * real multiplayer client the UDP snapshot can arrive first, producing
 * "Received a sub-level movement packet for a non-existent sub-level" and losing the initial
 * remote-craft state. For ten ticks after each full sync, report that player's UDP transport as
 * unavailable so Sable uses its own ordered TCP fallback. Outside that window the original
 * transport decision is preserved unchanged.
 *
 * <p>This mixin is deliberately required at its two injection points so a future Sable bytecode
 * change fails compatibility validation rather than silently reintroducing the ordering race.
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem", remap = false)
abstract class SkyforgeSableFullSyncOrderingMixin {
    @Unique private static Method skyforge$isConnectedToMethod;

    @Inject(method = "sendFullSync", at = @At("HEAD"), require = 1, remap = false)
    private void skyforge$recordFullSync(
            ServerPlayer player,
            @Coerce Object subLevel,
            CustomPacketPayload extraPacket,
            CallbackInfo ci) {
        SkyforgeSableFullSyncOrdering.recordFullSync(
                player.getUUID(),
                player.server.getTickCount());
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
    private boolean skyforge$orderedSnapshotsAfterFullSync(
            @Coerce Object udpServer,
            ServerPlayer player) {
        if (SkyforgeSableFullSyncOrdering.needsOrderedSnapshots(
                player.getUUID(),
                player.server.getTickCount())) {
            return false;
        }
        return skyforge$invokeOriginalIsConnectedTo(udpServer, player);
    }

    @Unique
    private static boolean skyforge$invokeOriginalIsConnectedTo(
            Object udpServer,
            ServerPlayer player) {
        try {
            Method method = skyforge$isConnectedToMethod;
            if (method == null || method.getDeclaringClass() != udpServer.getClass()) {
                method = udpServer.getClass().getMethod("isConnectedTo", ServerPlayer.class);
                skyforge$isConnectedToMethod = method;
            }
            return (Boolean) method.invoke(udpServer, player);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new IllegalStateException(
                    "Sable 2.0.5 UDP compatibility target changed", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Sable UDP transport check failed", cause);
        }
    }
}
