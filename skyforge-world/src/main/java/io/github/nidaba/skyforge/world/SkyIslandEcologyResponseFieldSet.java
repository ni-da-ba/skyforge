package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Objects;

/**
 * Reusable backend-neutral continuous ecology response fields.
 *
 * <p>This layer owns the accepted AUTH-0003 thermal-suitability, saturation-potential, and
 * vegetation-potential transforms. It deliberately does not classify a categorical ecological
 * regime. Downstream habitat, community, realization, planning, visualization, and backend systems
 * may consume these continuous responses directly.
 */
public final class SkyIslandEcologyResponseFieldSet {
    private final SkyIslandEcologyInputFieldSet inputFields;
    private final SkyIslandSemanticField thermalSuitability;
    private final SkyIslandSemanticField saturationPotential;
    private final SkyIslandSemanticField vegetationPotential;

    private SkyIslandEcologyResponseFieldSet(SkyIslandEcologyInputFieldSet inputFields) {
        this.inputFields = Objects.requireNonNull(inputFields, "inputFields");
        this.thermalSuitability = position -> sample(position).thermalSuitability();
        this.saturationPotential = position -> sample(position).saturationPotential();
        this.vegetationPotential = position -> sample(position).vegetationPotential();
    }

    /** Creates the exact accepted AUTH-0003 response fields from current descriptor semantics. */
    public static SkyIslandEcologyResponseFieldSet create(SkyIslandDescriptor descriptor) {
        return create(SkyIslandEcologyInputFieldSet.fromCurrentSemantics(descriptor));
    }

    /** Creates continuous ecology responses from explicit backend-neutral environmental inputs. */
    public static SkyIslandEcologyResponseFieldSet create(SkyIslandEcologyInputFieldSet inputFields) {
        return new SkyIslandEcologyResponseFieldSet(inputFields);
    }

    public SkyIslandDescriptor descriptor() {
        return inputFields.descriptor();
    }

    public SkyIslandEcologyInputFieldSet inputFields() {
        return inputFields;
    }

    /** Accepted AUTH-0003 thermal favorability in {@code [0, 1]}. */
    public SkyIslandSemanticField thermalSuitability() {
        return thermalSuitability;
    }

    /** Accepted AUTH-0003 saturation opportunity in {@code [0, 1]}. */
    public SkyIslandSemanticField saturationPotential() {
        return saturationPotential;
    }

    /** Accepted AUTH-0003 vegetation opportunity in {@code [0, 1]}. */
    public SkyIslandSemanticField vegetationPotential() {
        return vegetationPotential;
    }

    /** Samples all accepted continuous AUTH-0003 responses with one environmental-field evaluation. */
    public SkyIslandEcologyResponseSample sample(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        double interiority = sampleNormalized("interiority", inputFields.interiority(), position);
        double temperature = sampleNormalized("temperature", inputFields.temperature(), position);
        double moisture = sampleNormalized("moisture", inputFields.moisture(), position);
        double elevation = sampleNormalized("elevationTendency", inputFields.elevationTendency(), position);
        double exposure = sampleNormalized("exposure", inputFields.exposure(), position);
        return evaluateNormalized(interiority, temperature, moisture, elevation, exposure);
    }

    /**
     * Evaluates the accepted response transforms from already validated normalized source values.
     *
     * <p>Package-private so the compatibility classifier can sample environmental inputs once while
     * sharing this single authoritative implementation of the continuous response formulas.
     */
    SkyIslandEcologyResponseSample evaluateNormalized(
            double interiority,
            double temperature,
            double moisture,
            double elevation,
            double exposure) {
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
        return new SkyIslandEcologyResponseSample(
                vegetationPotential,
                saturationPotential,
                thermalSuitability);
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

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
