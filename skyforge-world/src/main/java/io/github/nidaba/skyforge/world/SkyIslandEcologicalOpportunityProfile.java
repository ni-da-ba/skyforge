package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0089 deterministic island-scale summary of accepted AUTH-0003 surface ecology.
 *
 * <p>The area term is a horizontal authored-domain planning estimate, not physical sloped terrain
 * area and not backend block coverage.
 */
public record SkyIslandEcologicalOpportunityProfile(
        SkyIslandDescriptor descriptor,
        int samplesPerAxis,
        long ownedCellCount,
        double horizontalOwnedAreaEstimate,
        double meanVegetationPotential,
        double meanSaturationPotential,
        double meanThermalSuitability,
        Map<SkyIslandEcologyRegime, Double> regimeFractions) {

    public SkyIslandEcologicalOpportunityProfile {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (samplesPerAxis <= 0) {
            throw new IllegalArgumentException("samplesPerAxis must be positive");
        }
        if (ownedCellCount <= 0L) {
            throw new IllegalArgumentException("ownedCellCount must be positive");
        }
        if (!Double.isFinite(horizontalOwnedAreaEstimate) || horizontalOwnedAreaEstimate <= 0.0) {
            throw new IllegalArgumentException(
                    "horizontalOwnedAreaEstimate must be finite and positive");
        }
        requireNormalized("meanVegetationPotential", meanVegetationPotential);
        requireNormalized("meanSaturationPotential", meanSaturationPotential);
        requireNormalized("meanThermalSuitability", meanThermalSuitability);

        Objects.requireNonNull(regimeFractions, "regimeFractions");
        if (regimeFractions.size() != SkyIslandEcologyRegime.values().length) {
            throw new IllegalArgumentException(
                    "regimeFractions must cover every AUTH-0003 ecology regime exactly");
        }
        double sum = 0.0;
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            Double fraction = regimeFractions.get(regime);
            if (fraction == null) {
                throw new IllegalArgumentException("missing ecology regime fraction: " + regime);
            }
            requireNormalized("regime fraction " + regime, fraction);
            sum += fraction;
        }
        if (Math.abs(sum - 1.0) > 1.0e-12) {
            throw new IllegalArgumentException("ecology regime fractions must sum to one");
        }
        regimeFractions = Map.copyOf(regimeFractions);
    }

    public double regimeFraction(SkyIslandEcologyRegime regime) {
        Objects.requireNonNull(regime, "regime");
        return regimeFractions.get(regime);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
