package io.github.nidaba.skyforge.neoforge1211;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Reflection-only adapter for the pinned Aerodynamics4MC gameplay API.
 *
 * <p>No A4MC type appears in a Skyforge signature, so ordinary packaged Skyforge remains loadable
 * when A4MC is absent. This class is a read-through adapter only: it creates no atmosphere field,
 * cache, persistence, or fallback weather model.
 */
final class SkyforgeA4mcAtmosphereBridge implements SkyforgeAtmosphereView {
    private final Method sampleGameplay;
    private final Method trustedForGameplay;
    private final Method meanX;
    private final Method meanY;
    private final Method meanZ;
    private final Method gustX;
    private final Method gustY;
    private final Method gustZ;
    private final Method pressure;
    private final Method turbulenceIntensity;
    private final Method updraftMetersPerSecond;
    private final Method windShearMagnitudePerBlock;
    private final Method confidence;
    private final Method sourceLevel;
    private final Method authority;
    private final Method l1Epoch;
    private final Method worldDeltaEpoch;
    private final Method l2Epoch;

    private SkyforgeA4mcAtmosphereBridge(
            Method sampleGameplay,
            Method trustedForGameplay,
            Method meanX,
            Method meanY,
            Method meanZ,
            Method gustX,
            Method gustY,
            Method gustZ,
            Method pressure,
            Method turbulenceIntensity,
            Method updraftMetersPerSecond,
            Method windShearMagnitudePerBlock,
            Method confidence,
            Method sourceLevel,
            Method authority,
            Method l1Epoch,
            Method worldDeltaEpoch,
            Method l2Epoch) {
        this.sampleGameplay = sampleGameplay;
        this.trustedForGameplay = trustedForGameplay;
        this.meanX = meanX;
        this.meanY = meanY;
        this.meanZ = meanZ;
        this.gustX = gustX;
        this.gustY = gustY;
        this.gustZ = gustZ;
        this.pressure = pressure;
        this.turbulenceIntensity = turbulenceIntensity;
        this.updraftMetersPerSecond = updraftMetersPerSecond;
        this.windShearMagnitudePerBlock = windShearMagnitudePerBlock;
        this.confidence = confidence;
        this.sourceLevel = sourceLevel;
        this.authority = authority;
        this.l1Epoch = l1Epoch;
        this.worldDeltaEpoch = worldDeltaEpoch;
        this.l2Epoch = l2Epoch;
    }

    static SkyforgeA4mcAtmosphereBridge create() throws ReflectiveOperationException {
        Class<?> api = Class.forName("com.aerodynamics4mc.api.minecraft.AeroMinecraftWindApi");
        Class<?> sample = Class.forName("com.aerodynamics4mc.api.GameplayWindSample");

        return new SkyforgeA4mcAtmosphereBridge(
                api.getMethod("sampleGameplay", ServerLevel.class, Vec3.class),
                sample.getMethod("isTrustedForGameplay"),
                sample.getMethod("meanX"),
                sample.getMethod("meanY"),
                sample.getMethod("meanZ"),
                sample.getMethod("gustX"),
                sample.getMethod("gustY"),
                sample.getMethod("gustZ"),
                sample.getMethod("pressure"),
                sample.getMethod("turbulenceIntensity"),
                sample.getMethod("updraftMetersPerSecond"),
                sample.getMethod("windShearMagnitudePerBlock"),
                sample.getMethod("confidence"),
                sample.getMethod("sourceLevel"),
                sample.getMethod("authority"),
                sample.getMethod("l1Epoch"),
                sample.getMethod("worldDeltaEpoch"),
                sample.getMethod("l2Epoch"));
    }

    @Override
    public Sample sample(ServerLevel level, Vec3 position) {
        try {
            Object value = sampleGameplay.invoke(null, level, position);
            if (value == null) {
                return Sample.unavailable();
            }

            Sample sampled = new Sample(
                    (boolean) trustedForGameplay.invoke(value),
                    number(meanX.invoke(value)),
                    number(meanY.invoke(value)),
                    number(meanZ.invoke(value)),
                    number(gustX.invoke(value)),
                    number(gustY.invoke(value)),
                    number(gustZ.invoke(value)),
                    number(pressure.invoke(value)),
                    number(turbulenceIntensity.invoke(value)),
                    number(updraftMetersPerSecond.invoke(value)),
                    number(windShearMagnitudePerBlock.invoke(value)),
                    number(confidence.invoke(value)),
                    String.valueOf(sourceLevel.invoke(value)),
                    String.valueOf(authority.invoke(value)),
                    ((Number) l1Epoch.invoke(value)).longValue(),
                    ((Number) worldDeltaEpoch.invoke(value)).longValue(),
                    ((Number) l2Epoch.invoke(value)).longValue());

            return finite(sampled) ? sampled : Sample.unavailable();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return Sample.unavailable();
        }
    }

    private static double number(Object value) {
        return ((Number) value).doubleValue();
    }

    private static boolean finite(Sample sample) {
        return Double.isFinite(sample.meanX())
                && Double.isFinite(sample.meanY())
                && Double.isFinite(sample.meanZ())
                && Double.isFinite(sample.gustX())
                && Double.isFinite(sample.gustY())
                && Double.isFinite(sample.gustZ())
                && Double.isFinite(sample.pressure())
                && Double.isFinite(sample.turbulenceIntensity())
                && Double.isFinite(sample.updraftMetersPerSecond())
                && Double.isFinite(sample.windShearMagnitudePerBlock())
                && Double.isFinite(sample.confidence());
    }
}
