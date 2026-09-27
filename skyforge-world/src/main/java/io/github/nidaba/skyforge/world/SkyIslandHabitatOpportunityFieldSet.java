package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Objects;

/**
 * Deterministic overlapping ecological habitat-opportunity fields.
 *
 * <p>This layer is intentionally broader than a final community or biome model. It translates the
 * accepted AUTH-0003 continuous ecological responses plus accepted local semantic environment into
 * several reusable habitat envelopes. Multiple opportunities may be strong at the same position.
 *
 * <p>Issue #1196 does not claim occupancy, species identity, carrying capacity, succession, or
 * physical climate/hydrology authority.
 */
public final class SkyIslandHabitatOpportunityFieldSet {
    private final SkyIslandEcologyInputFieldSet inputFields;
    private final SkyIslandEcologyResponseFieldSet responseFields;
    private final SkyIslandSemanticField woodland;
    private final SkyIslandSemanticField openVegetation;
    private final SkyIslandSemanticField saturatedLowland;
    private final SkyIslandSemanticField alpineExposed;
    private final SkyIslandSemanticField xericExposed;

    private SkyIslandHabitatOpportunityFieldSet(SkyIslandEcologyInputFieldSet inputFields) {
        this.inputFields = Objects.requireNonNull(inputFields, "inputFields");
        this.responseFields = SkyIslandEcologyResponseFieldSet.create(inputFields);
        this.woodland = position -> sample(position).woodland();
        this.openVegetation = position -> sample(position).openVegetation();
        this.saturatedLowland = position -> sample(position).saturatedLowland();
        this.alpineExposed = position -> sample(position).alpineExposed();
        this.xericExposed = position -> sample(position).xericExposed();
    }

    /** Creates habitat opportunity from the accepted current authored-island semantics. */
    public static SkyIslandHabitatOpportunityFieldSet create(SkyIslandDescriptor descriptor) {
        return create(SkyIslandEcologyInputFieldSet.fromCurrentSemantics(descriptor));
    }

    /** Creates habitat opportunity from explicit backend-neutral environmental inputs. */
    public static SkyIslandHabitatOpportunityFieldSet create(SkyIslandEcologyInputFieldSet inputFields) {
        return new SkyIslandHabitatOpportunityFieldSet(inputFields);
    }

    public SkyIslandDescriptor descriptor() {
        return inputFields.descriptor();
    }

    public SkyIslandEcologyInputFieldSet inputFields() {
        return inputFields;
    }

    public SkyIslandEcologyResponseFieldSet responseFields() {
        return responseFields;
    }

    public SkyIslandSemanticField woodland() {
        return woodland;
    }

    public SkyIslandSemanticField openVegetation() {
        return openVegetation;
    }

    public SkyIslandSemanticField saturatedLowland() {
        return saturatedLowland;
    }

    public SkyIslandSemanticField alpineExposed() {
        return alpineExposed;
    }

    public SkyIslandSemanticField xericExposed() {
        return xericExposed;
    }

    /**
     * Samples all currently accepted habitat opportunities.
     *
     * <p>The formulas are smooth compositions of accepted normalized semantics. Interiority is an
     * ownership mask: positions outside the authored island domain expose no habitat opportunity.
     */
    public SkyIslandHabitatOpportunitySample sample(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");

        double interiority = sampleNormalized("interiority", inputFields.interiority(), position);
        if (interiority <= 0.0) {
            return SkyIslandHabitatOpportunitySample.outside();
        }

        double moisture = sampleNormalized("moisture", inputFields.moisture(), position);
        double elevation = sampleNormalized("elevationTendency", inputFields.elevationTendency(), position);
        double exposure = sampleNormalized("exposure", inputFields.exposure(), position);
        SkyIslandEcologyResponseSample response = responseFields.sample(position);

        double vegetation = response.vegetationPotential();
        double saturation = response.saturationPotential();
        double thermal = response.thermalSuitability();
        double shelter = 1.0 - exposure;
        double dryness = 1.0 - moisture;
        double coolStress = 1.0 - thermal;

        double woodlandSupport =
                0.40 * thermal
                        + 0.35 * moisture
                        + 0.25 * shelter;
        double woodland = clamp01(interiority * vegetation * woodlandSupport);

        double opennessSupport =
                0.45
                        + 0.30 * dryness
                        + 0.25 * exposure;
        double openVegetation = clamp01(interiority * vegetation * opennessSupport);

        double saturatedLowland = clamp01(
                interiority
                        * saturation
                        * (1.0 - elevation)
                        * (0.65 + 0.35 * vegetation));

        double alpineExposed = clamp01(
                interiority
                        * elevation
                        * (0.55 * coolStress + 0.45 * exposure));

        double xericExposed = clamp01(
                interiority
                        * (0.55 * dryness + 0.45 * exposure)
                        * (0.60 + 0.40 * thermal)
                        * (1.0 - 0.50 * saturation));

        return new SkyIslandHabitatOpportunitySample(
                woodland,
                openVegetation,
                saturatedLowland,
                alpineExposed,
                xericExposed);
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
