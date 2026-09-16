package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Shared exact AUTH-0090 area-weighted aggregation math. */
final class SkyIslandEcologicalOpportunityAggregation {
    private SkyIslandEcologicalOpportunityAggregation() {}

    static Result aggregate(List<SkyIslandRegionalEcologicalOpportunityEntry> islands) {
        double area = 0.0;
        double vegetationArea = 0.0;
        double saturationArea = 0.0;
        double thermalArea = 0.0;
        EnumMap<SkyIslandEcologyRegime, Double> regimeAreas =
                new EnumMap<>(SkyIslandEcologyRegime.class);
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            regimeAreas.put(regime, 0.0);
        }

        for (SkyIslandRegionalEcologicalOpportunityEntry entry : islands) {
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
                    "ecological opportunity aggregation requires positive finite authored habitat area");
        }

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
                    "area-weighted AUTH-0003 regime fractions must sum to one");
        }

        return new Result(
                area,
                clampUnit(vegetationArea / area),
                clampUnit(saturationArea / area),
                clampUnit(thermalArea / area),
                Map.copyOf(fractions));
    }

    private static double clampUnit(double value) {
        if (!Double.isFinite(value) || value < -1.0e-12 || value > 1.0 + 1.0e-12) {
            throw new IllegalArgumentException("ecological aggregate escaped [0,1]");
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    record Result(
            double totalHorizontalOwnedAreaEstimate,
            double meanVegetationPotential,
            double meanSaturationPotential,
            double meanThermalSuitability,
            Map<SkyIslandEcologyRegime, Double> regimeFractions) {}
}
