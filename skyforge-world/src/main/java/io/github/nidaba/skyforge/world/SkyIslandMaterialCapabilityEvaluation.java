package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One audited AUTH-0040 capability comparison inside a compatibility assessment. */
public record SkyIslandMaterialCapabilityEvaluation(
        SkyIslandMaterialCapability capability,
        double requiredMinimum,
        double advertised,
        double margin,
        boolean satisfied) {

    public SkyIslandMaterialCapabilityEvaluation {
        capability = Objects.requireNonNull(capability, "capability");
        requireNormalized("requiredMinimum", requiredMinimum);
        requireNormalized("advertised", advertised);
        if (requiredMinimum <= 0.0) {
            throw new IllegalArgumentException(
                    "capability evaluation requires a positive minimum");
        }
        if (!Double.isFinite(margin)
                || Math.abs(margin - (advertised - requiredMinimum)) > 1.0e-12) {
            throw new IllegalArgumentException(
                    "capability evaluation margin must equal advertised - requiredMinimum");
        }
        if (satisfied != (margin >= -1.0e-12)) {
            throw new IllegalArgumentException(
                    "capability evaluation satisfied state must match its margin");
        }
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
