package io.github.nidaba.skyforge.neoforge1211;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Stateless caller-facing view of the authoritative gameplay atmosphere.
 *
 * <p>This interface owns no weather simulation, cache, persistence, or per-consumer state. A caller
 * supplies the exact server world and world-space query position; the installed atmosphere authority
 * supplies one immutable sample.
 */
@FunctionalInterface
interface SkyforgeAtmosphereView {
    Sample sample(ServerLevel level, Vec3 position);

    /**
     * Backend-facing copy of the exact A4MC gameplay quantities Skyforge is authorized to consume.
     *
     * <p>Wind components and updraft are metres per second. Pressure remains the upstream A4MC
     * pressure anomaly/local-solver proxy. Turbulence and shear are passed through without Skyforge
     * remapping. Source level, authority, confidence, and epochs preserve upstream provenance.
     */
    record Sample(
            boolean trustedForGameplay,
            double meanX,
            double meanY,
            double meanZ,
            double gustX,
            double gustY,
            double gustZ,
            double pressure,
            double turbulenceIntensity,
            double updraftMetersPerSecond,
            double windShearMagnitudePerBlock,
            double confidence,
            String sourceLevel,
            String authority,
            long l1Epoch,
            long worldDeltaEpoch,
            long l2Epoch) {
        public Sample {
            Objects.requireNonNull(sourceLevel, "sourceLevel");
            Objects.requireNonNull(authority, "authority");
        }

        static Sample unavailable() {
            return new Sample(
                    false,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "NONE",
                    "NONE",
                    -1L,
                    -1L,
                    -1L);
        }

        double effectiveX() {
            return meanX + gustX;
        }

        double effectiveY() {
            return meanY + gustY;
        }

        double effectiveZ() {
            return meanZ + gustZ;
        }
    }
}
