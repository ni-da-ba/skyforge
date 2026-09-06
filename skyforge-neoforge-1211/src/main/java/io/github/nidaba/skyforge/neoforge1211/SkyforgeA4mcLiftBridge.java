package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Reflection-only view of the optional Aerodynamics4MC gameplay API.
 *
 * <p>No A4MC type appears in this class signature. Loading ordinary Skyforge without A4MC
 * therefore cannot resolve an optional class accidentally.
 */
final class SkyforgeA4mcLiftBridge {
    record Sample(boolean trusted, double updraftMetersPerSecond) {
        static Sample unavailable() {
            return new Sample(false, 0.0);
        }
    }

    private final Method sampleGameplay;
    private final Method trustedForGameplay;
    private final Method updraftMetersPerSecond;

    private SkyforgeA4mcLiftBridge(
            Method sampleGameplay, Method trustedForGameplay, Method updraftMetersPerSecond) {
        this.sampleGameplay = sampleGameplay;
        this.trustedForGameplay = trustedForGameplay;
        this.updraftMetersPerSecond = updraftMetersPerSecond;
    }

    static SkyforgeA4mcLiftBridge create() throws ReflectiveOperationException {
        Class<?> api = Class.forName("com.aerodynamics4mc.api.minecraft.AeroMinecraftWindApi");
        Class<?> sample = Class.forName("com.aerodynamics4mc.api.GameplayWindSample");

        return new SkyforgeA4mcLiftBridge(
                api.getMethod("sampleGameplay", ServerLevel.class, Vec3.class),
                sample.getMethod("isTrustedForGameplay"),
                sample.getMethod("updraftMetersPerSecond"));
    }

    Sample sample(ServerLevel level, Vec3 position) {
        try {
            Object value = sampleGameplay.invoke(null, level, position);
            if (value == null) {
                return Sample.unavailable();
            }

            boolean trusted = (boolean) trustedForGameplay.invoke(value);
            double updraft = ((Number) updraftMetersPerSecond.invoke(value)).doubleValue();
            return Double.isFinite(updraft) ? new Sample(trusted, updraft) : Sample.unavailable();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return Sample.unavailable();
        }
    }
}
