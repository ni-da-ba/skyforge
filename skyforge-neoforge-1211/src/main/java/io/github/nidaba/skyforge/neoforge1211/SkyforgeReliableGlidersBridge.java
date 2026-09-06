package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Method;
import net.minecraft.world.entity.player.Player;

/** Reflection-only view of Reliable Gliders' public gliding-state API. */
final class SkyforgeReliableGlidersBridge {
    private final Method isGliding;

    private SkyforgeReliableGlidersBridge(Method isGliding) {
        this.isGliding = isGliding;
    }

    static SkyforgeReliableGlidersBridge create() throws ReflectiveOperationException {
        Class<?> glidingState =
                Class.forName("com.evandev.reliable_gliders.api.GlidingState");
        return new SkyforgeReliableGlidersBridge(
                glidingState.getMethod("isGliding", Player.class));
    }

    boolean isGliding(Player player) throws ReflectiveOperationException {
        return (boolean) isGliding.invoke(null, player);
    }
}
