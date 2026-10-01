package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import java.util.Objects;

/** One deterministic ecological interpretation of authored semantic fields at an island-local position. */
public record SkyIslandEcologySample(
        SkyIslandEcologyRegime regime,
        double vegetationPotential,
        double saturationPotential,
        double thermalSuitability) {
    public SkyIslandEcologySample {
        regime = Objects.requireNonNull(regime, "regime");
        requireNormalized("vegetationPotential", vegetationPotential);
        requireNormalized("saturationPotential", saturationPotential);
        requireNormalized("thermalSuitability", thermalSuitability);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
