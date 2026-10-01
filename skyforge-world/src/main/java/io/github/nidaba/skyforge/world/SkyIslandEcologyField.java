package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandEcologyRegime;
import java.util.Objects;

/**
 * Backend-neutral categorical compatibility interpretation of island-local ecology.
 *
 * <p>This layer deliberately authors broad ecological regimes rather than Minecraft biome IDs.
 * Downstream adapters may later translate these semantics into backend-native biome registrations.
 *
 * <p>AUTH-0003 compatibility is preserved by {@link #create(SkyIslandDescriptor)}. Continuous
 * ecological responses are owned by {@link SkyIslandEcologyResponseFieldSet} and are independently
 * consumable without requiring this categorical projection.
 */
public final class SkyIslandEcologyField {
    private final SkyIslandDescriptor descriptor;
    private final SkyIslandEcologyInputFieldSet inputFields;
    private final SkyIslandEcologyResponseFieldSet responseFields;

    private SkyIslandEcologyField(SkyIslandEcologyInputFieldSet inputFields) {
        this.inputFields = Objects.requireNonNull(inputFields, "inputFields");
        this.descriptor = inputFields.descriptor();
        this.responseFields = SkyIslandEcologyResponseFieldSet.create(inputFields);
    }

    /** Creates the exact accepted AUTH-0003 ecology interpretation from current semantic fields. */
    public static SkyIslandEcologyField create(SkyIslandDescriptor descriptor) {
        return create(SkyIslandEcologyInputFieldSet.fromCurrentSemantics(descriptor));
    }

    /**
     * Creates categorical compatibility ecology from explicit backend-neutral environmental inputs.
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

    /** Returns the reusable continuous response layer shared by this categorical projection. */
    public SkyIslandEcologyResponseFieldSet responseFields() {
        return responseFields;
    }

    public SkyIslandEcologySample sample(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        double interiority = sampleNormalized("interiority", inputFields.interiority(), position);
        double temperature = sampleNormalized("temperature", inputFields.temperature(), position);
        double moisture = sampleNormalized("moisture", inputFields.moisture(), position);
        double elevation = sampleNormalized("elevationTendency", inputFields.elevationTendency(), position);
        double exposure = sampleNormalized("exposure", inputFields.exposure(), position);

        SkyIslandEcologyResponseSample responses =
                responseFields.evaluateNormalized(interiority, temperature, moisture, elevation, exposure);

        SkyIslandEcologyRegime regime = classify(
                interiority,
                temperature,
                moisture,
                elevation,
                exposure,
                responses.vegetationPotential(),
                responses.saturationPotential());
        return new SkyIslandEcologySample(
                regime,
                responses.vegetationPotential(),
                responses.saturationPotential(),
                responses.thermalSuitability());
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
}
