package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0090 deterministic regional aggregation of exact AUTH-0089 island opportunity profiles.
 *
 * <p>The regional boundary is the accepted AUTH-0058 publication carried by the AUTH-0087 binding.
 * Values remain authored ecological planning evidence rather than backend population/resource policy.
 */
public final class SkyIslandRegionalEcologicalOpportunityProfile {
    private final SkyIslandPublishedAuthoredRealizationBinding binding;
    private final List<SkyIslandRegionalEcologicalOpportunityEntry> islands;
    private final double totalHorizontalOwnedAreaEstimate;
    private final double meanVegetationPotential;
    private final double meanSaturationPotential;
    private final double meanThermalSuitability;
    private final Map<SkyIslandEcologyRegime, Double> regimeFractions;

    SkyIslandRegionalEcologicalOpportunityProfile(
            SkyIslandPublishedAuthoredRealizationBinding binding,
            List<SkyIslandRegionalEcologicalOpportunityEntry> islands) {
        this.binding = Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(islands, "islands");
        this.islands = List.copyOf(islands);

        List<SkyIslandAuthoredRealizationAssociation> expected =
                binding.associationCatalog().associations();
        if (this.islands.size() != expected.size()) {
            throw new IllegalArgumentException(
                    "regional ecology profile requires exact AUTH-0087 association coverage");
        }
        for (int index = 0; index < expected.size(); index++) {
            SkyIslandRegionalEcologicalOpportunityEntry entry = this.islands.get(index);
            if (!entry.association().equals(expected.get(index))) {
                throw new IllegalArgumentException(
                        "regional ecology entries must preserve canonical AUTH-0087 association order");
            }
        }

        double area = 0.0;
        double vegetationArea = 0.0;
        double saturationArea = 0.0;
        double thermalArea = 0.0;
        EnumMap<SkyIslandEcologyRegime, Double> regimeAreas =
                new EnumMap<>(SkyIslandEcologyRegime.class);
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            regimeAreas.put(regime, 0.0);
        }

        for (SkyIslandRegionalEcologicalOpportunityEntry entry : this.islands) {
            SkyIslandEcologicalOpportunityProfile profile = entry.islandProfile();
            double islandArea = profile.horizontalOwnedAreaEstimate();
            area += islandArea;
            vegetationArea += islandArea * profile.meanVegetationPotential();
            saturationArea += islandArea * profile.meanSaturationPotential();
            thermalArea += islandArea * profile.meanThermalSuitability();
            for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
                regimeAreas.put(
                        regime,
                        regimeAreas.get(regime)
                                + islandArea * profile.regimeFraction(regime));
            }
        }

        if (!Double.isFinite(area) || area <= 0.0) {
            throw new IllegalArgumentException(
                    "regional ecology profile requires positive finite authored habitat area");
        }

        this.totalHorizontalOwnedAreaEstimate = area;
        this.meanVegetationPotential = clampUnit(vegetationArea / area);
        this.meanSaturationPotential = clampUnit(saturationArea / area);
        this.meanThermalSuitability = clampUnit(thermalArea / area);

        EnumMap<SkyIslandEcologyRegime, Double> fractions =
                new EnumMap<>(SkyIslandEcologyRegime.class);
        double fractionSum = 0.0;
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            double fraction = clampUnit(regimeAreas.get(regime) / area);
            fractions.put(regime, fraction);
            fractionSum += fraction;
        }
        if (Math.abs(fractionSum - 1.0) > 1.0e-12) {
            throw new IllegalStateException(
                    "area-weighted regional AUTH-0003 regime fractions must sum to one");
        }
        this.regimeFractions = Map.copyOf(fractions);
    }

    public SkyIslandPublishedAuthoredRealizationBinding binding() {
        return binding;
    }

    public SkyIslandCompiledWorldPublicationId publicationId() {
        return binding.publication().id();
    }

    public long authoredWorldSeed() {
        return binding.authoredWorldSeed();
    }

    public int islandCount() {
        return islands.size();
    }

    /** Canonical AUTH-0087 association order. */
    public List<SkyIslandRegionalEcologicalOpportunityEntry> islands() {
        return islands;
    }

    public double totalHorizontalOwnedAreaEstimate() {
        return totalHorizontalOwnedAreaEstimate;
    }

    public double meanVegetationPotential() {
        return meanVegetationPotential;
    }

    public double meanSaturationPotential() {
        return meanSaturationPotential;
    }

    public double meanThermalSuitability() {
        return meanThermalSuitability;
    }

    public Map<SkyIslandEcologyRegime, Double> regimeFractions() {
        return regimeFractions;
    }

    public double regimeFraction(SkyIslandEcologyRegime regime) {
        Objects.requireNonNull(regime, "regime");
        return regimeFractions.get(regime);
    }

    private static double clampUnit(double value) {
        if (!Double.isFinite(value) || value < -1.0e-12 || value > 1.0 + 1.0e-12) {
            throw new IllegalArgumentException("regional ecological aggregate escaped [0,1]");
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
