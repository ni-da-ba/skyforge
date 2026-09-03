package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import java.util.Objects;

/**
 * Backend-neutral ecological interpretation of island-local semantic geography.
 *
 * <p>This layer deliberately authors broad ecological regimes rather than Minecraft biome IDs.
 * Downstream adapters may later translate these semantics into backend-native biome registrations.
 */
public final class SkyIslandEcologyField {
    private final SkyIslandDescriptor descriptor;
    private final SkyIslandSemanticFieldSet fields;

    private SkyIslandEcologyField(SkyIslandDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.fields = SkyIslandSemanticFieldSet.create(descriptor);
    }

    public static SkyIslandEcologyField create(SkyIslandDescriptor descriptor) {
        return new SkyIslandEcologyField(descriptor);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    public SkyIslandEcologySample sample(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        double interiority = fields.interiority().sample(position);
        double temperature = fields.temperature().sample(position);
        double moisture = fields.moisture().sample(position);
        double elevation = fields.elevationTendency().sample(position);
        double exposure = fields.exposure().sample(position);

        double thermalSuitability = clamp01(1.0 - Math.abs(temperature - 0.58) / 0.58);
        double saturationPotential = clamp01(
                moisture * 0.58
                        + descriptor.hydrologicalPotential() * 0.24
                        + (1.0 - elevation) * interiority * 0.18);
        double vegetationPotential = clamp01(
                descriptor.ecologicalPotential() * 0.28
                        + thermalSuitability * 0.24
                        + moisture * 0.28
                        + interiority * 0.12
                        + (1.0 - exposure) * 0.08);

        SkyIslandEcologyRegime regime = classify(
                interiority,
                temperature,
                moisture,
                elevation,
                exposure,
                vegetationPotential,
                saturationPotential);
        return new SkyIslandEcologySample(regime, vegetationPotential, saturationPotential, thermalSuitability);
    }

    private static SkyIslandEcologyRegime classify(
            double interiority,
            double temperature,
            double moisture,
            double elevation,
            double exposure,
            double vegetation,
            double saturation) {
        if (interiority <= 0.0 || vegetation < 0.22) {
            return SkyIslandEcologyRegime.COLD_BARREN;
        }
        if (elevation > 0.72 && (temperature < 0.43 || exposure > 0.62)) {
            return SkyIslandEcologyRegime.ALPINE;
        }
        if (saturation > 0.68 && elevation < 0.46 && interiority > 0.30) {
            return SkyIslandEcologyRegime.WETLAND;
        }
        if (temperature < 0.34) {
            return vegetation > 0.42
                    ? SkyIslandEcologyRegime.BOREAL_WOODLAND
                    : SkyIslandEcologyRegime.COLD_BARREN;
        }
        if (moisture < 0.26 || exposure > 0.76) {
            return SkyIslandEcologyRegime.DRY_SCRUB;
        }
        if (vegetation < 0.50 || moisture < 0.43) {
            return SkyIslandEcologyRegime.OPEN_GRASSLAND;
        }
        if (moisture > 0.67 && temperature > 0.48) {
            return SkyIslandEcologyRegime.HUMID_WOODLAND;
        }
        return SkyIslandEcologyRegime.TEMPERATE_WOODLAND;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
