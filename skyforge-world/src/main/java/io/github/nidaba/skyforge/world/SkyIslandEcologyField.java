package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import java.util.Objects;

/**
 * Backend-neutral ecological interpretation of island-local semantic geography.
 *
 * <p>This layer deliberately authors broad ecological regimes rather than Minecraft biome IDs.
 * Downstream adapters may later translate these semantics into backend-native biome registrations.
 *
 * <p>AUTH-0003 compatibility is preserved by {@link #create(SkyIslandDescriptor)}. Explicit
 * {@link SkyIslandEcologyInputFieldSet} construction provides the integration seam for later
 * graph-backed environmental producers without coupling ecology to their subsystem APIs.
 */
public final class SkyIslandEcologyField {
    private final SkyIslandDescriptor descriptor;
    private final SkyIslandEcologyInputFieldSet inputFields;

    private SkyIslandEcologyField(SkyIslandEcologyInputFieldSet inputFields) {
        this.inputFields = Objects.requireNonNull(inputFields, "inputFields");
        this.descriptor = inputFields.descriptor();
    }

    /**
     * Creates the exact accepted AUTH-0003 ecology interpretation from current semantic fields.
     */
    public static SkyIslandEcologyField create(SkyIslandDescriptor descriptor) {
        return create(SkyIslandEcologyInputFieldSet.fromCurrentSemantics(descriptor));
    }

    /**
     * Creates ecology from explicit backend-neutral environmental input fields.
     *
     * <p>Callers remain responsible for supplying fields with the normalized semantic meanings
     * documented by {@link SkyIslandEcologyInputFieldSet}. Sampled values fail closed when they are
     * non-finite or outside {@code [0, 1]}.
     */
    public static SkyIslandEcologyField create(SkyIslandEcologyInputFieldSet inputFields) {
        return new SkyIslandEcologyField(inputFields);
    }

    public SkyIslandDescriptor descriptor() {
        return descriptor;
    }

    /** Returns the immutable environmental input bundle consumed by this ecology field. */
    public SkyIslandEcologyInputFieldSet inputFields() {
        return inputFields;
    }

    public SkyIslandEcologySample sample(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        double interiority = sampleNormalized("interiority", inputFields.interiority(), position);
        double temperature = sampleNormalized("temperature", inputFields.temperature(), position);
        double moisture = sampleNormalized("moisture", inputFields.moisture(), position);
        double elevation = sampleNormalized("elevationTendency", inputFields.elevationTendency(), position);
        double exposure = sampleNormalized("exposure", inputFields.exposure(), position);

        double thermalSuitability = clamp01(1.0 - Math.abs(temperature - 0.58) / 0.58);
        double saturationPotential = clamp01(
                moisture * 0.58
                        + inputFields.hydrologicalPotentialPrior() * 0.24
                        + (1.0 - elevation) * interiority * 0.18);
        double vegetationPotential = clamp01(
                inputFields.ecologicalPotentialPrior() * 0.28
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

    private static double sampleNormalized(
            String name,
            SkyIslandSemanticField field,
            SkyIslandLocalPosition position) {
        double value = field.sample(position);
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " field must produce finite values in [0, 1]");
        }
        return value;
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
