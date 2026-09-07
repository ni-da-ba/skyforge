package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * AUTH-0089 fixed deterministic aggregation of AUTH-0003 ecology over the current authored domain.
 *
 * <p>The raster is a planning quadrature in island-local X/Z. It does not inspect compiled physical
 * terrain, Minecraft blocks, native biomes, entities, or population state.
 */
public final class SkyIslandEcologicalOpportunityProfiler {
    public static final int SAMPLES_PER_AXIS = 128;

    public SkyIslandEcologicalOpportunityProfile profile(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");

        SkyIslandSemanticFieldSet fields = SkyIslandSemanticFieldSet.create(descriptor);
        SkyIslandEcologyField ecology = SkyIslandEcologyField.create(descriptor);
        double radius = descriptor.nominalRadius();
        double cellWidth = (2.0 * radius) / SAMPLES_PER_AXIS;
        double cellArea = cellWidth * cellWidth;

        long owned = 0L;
        double vegetation = 0.0;
        double saturation = 0.0;
        double thermal = 0.0;
        long[] regimeCounts = new long[SkyIslandEcologyRegime.values().length];

        for (int iz = 0; iz < SAMPLES_PER_AXIS; iz++) {
            double z = -radius + (iz + 0.5) * cellWidth;
            for (int ix = 0; ix < SAMPLES_PER_AXIS; ix++) {
                double x = -radius + (ix + 0.5) * cellWidth;
                SkyIslandLocalPosition position = new SkyIslandLocalPosition(x, z);
                if (fields.interiority().sample(position) <= 0.0) {
                    continue;
                }

                SkyIslandEcologySample sample = ecology.sample(position);
                owned++;
                vegetation += sample.vegetationPotential();
                saturation += sample.saturationPotential();
                thermal += sample.thermalSuitability();
                regimeCounts[sample.regime().ordinal()]++;
            }
        }

        if (owned == 0L) {
            throw new IllegalStateException(
                    "AUTH-0089 fixed ecology quadrature found no authored-domain cells");
        }

        Map<SkyIslandEcologyRegime, Double> fractions =
                new EnumMap<>(SkyIslandEcologyRegime.class);
        for (SkyIslandEcologyRegime regime : SkyIslandEcologyRegime.values()) {
            fractions.put(regime, regimeCounts[regime.ordinal()] / (double) owned);
        }

        return new SkyIslandEcologicalOpportunityProfile(
                descriptor,
                SAMPLES_PER_AXIS,
                owned,
                owned * cellArea,
                vegetation / owned,
                saturation / owned,
                thermal / owned,
                fractions);
    }
}
