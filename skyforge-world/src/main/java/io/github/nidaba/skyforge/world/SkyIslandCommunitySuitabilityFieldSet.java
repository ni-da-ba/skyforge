package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Objects;

/**
 * Deterministic overlapping suitability for broad ecological community structures.
 *
 * <p>This layer refines accepted habitat opportunity into structural community archetypes. It does
 * not perform community assembly: isolation, dispersal, disturbance history, succession, competition,
 * occupancy, and population dynamics remain downstream concerns.
 */
public final class SkyIslandCommunitySuitabilityFieldSet {
    private final SkyIslandHabitatOpportunityFieldSet habitatFields;
    private final SkyIslandSemanticField closedWoodland;
    private final SkyIslandSemanticField openHerbaceous;
    private final SkyIslandSemanticField saturatedWetland;
    private final SkyIslandSemanticField alpineTundra;
    private final SkyIslandSemanticField xericScrub;

    private SkyIslandCommunitySuitabilityFieldSet(
            SkyIslandHabitatOpportunityFieldSet habitatFields) {
        this.habitatFields = Objects.requireNonNull(habitatFields, "habitatFields");
        this.closedWoodland = position -> sample(position).closedWoodland();
        this.openHerbaceous = position -> sample(position).openHerbaceous();
        this.saturatedWetland = position -> sample(position).saturatedWetland();
        this.alpineTundra = position -> sample(position).alpineTundra();
        this.xericScrub = position -> sample(position).xericScrub();
    }

    public static SkyIslandCommunitySuitabilityFieldSet create(SkyIslandDescriptor descriptor) {
        return create(SkyIslandEcologyInputFieldSet.fromCurrentSemantics(descriptor));
    }

    public static SkyIslandCommunitySuitabilityFieldSet create(
            SkyIslandEcologyInputFieldSet inputFields) {
        return new SkyIslandCommunitySuitabilityFieldSet(
                SkyIslandHabitatOpportunityFieldSet.create(inputFields));
    }

    public static SkyIslandCommunitySuitabilityFieldSet create(
            SkyIslandHabitatOpportunityFieldSet habitatFields) {
        return new SkyIslandCommunitySuitabilityFieldSet(habitatFields);
    }

    public SkyIslandDescriptor descriptor() {
        return habitatFields.descriptor();
    }

    public SkyIslandHabitatOpportunityFieldSet habitatFields() {
        return habitatFields;
    }

    public SkyIslandSemanticField closedWoodland() {
        return closedWoodland;
    }

    public SkyIslandSemanticField openHerbaceous() {
        return openHerbaceous;
    }

    public SkyIslandSemanticField saturatedWetland() {
        return saturatedWetland;
    }

    public SkyIslandSemanticField alpineTundra() {
        return alpineTundra;
    }

    public SkyIslandSemanticField xericScrub() {
        return xericScrub;
    }

    /**
     * Samples all accepted structural community-suitability fields.
     *
     * <p>Formulas are smooth refinements of #1196 habitat opportunity and #1194 vegetation response.
     * They intentionally overlap and contain no categorical winner selection.
     */
    public SkyIslandCommunitySuitabilitySample sample(SkyIslandLocalPosition position) {
        Objects.requireNonNull(position, "position");
        double interiority = habitatFields.inputFields().interiority().sample(position);
        if (!Double.isFinite(interiority) || interiority < 0.0 || interiority > 1.0) {
            throw new IllegalArgumentException("interiority field must produce finite values in [0, 1]");
        }
        if (interiority <= 0.0) {
            return SkyIslandCommunitySuitabilitySample.outside();
        }

        SkyIslandHabitatOpportunitySample habitat = habitatFields.sample(position);
        double vegetation =
                habitatFields.responseFields().vegetationPotential().sample(position);

        double closedWoodland = clamp01(
                habitat.woodland()
                        * (0.55 + 0.45 * vegetation)
                        * (1.0 - 0.35 * habitat.openVegetation()));

        double openHerbaceous = clamp01(
                habitat.openVegetation()
                        * (0.50 + 0.50 * vegetation)
                        * (1.0 - 0.45 * habitat.saturatedLowland())
                        * (1.0 - 0.20 * habitat.woodland()));

        double saturatedWetland = clamp01(
                habitat.saturatedLowland()
                        * (0.55 + 0.45 * vegetation)
                        * (1.0 - 0.15 * habitat.xericExposed()));

        double alpineTundra = clamp01(
                habitat.alpineExposed()
                        * (0.45 + 0.55 * vegetation)
                        * (1.0 - 0.25 * habitat.woodland()));

        double xericScrub = clamp01(
                habitat.xericExposed()
                        * (0.45 + 0.55 * vegetation)
                        * (1.0 - 0.20 * habitat.saturatedLowland()));

        return new SkyIslandCommunitySuitabilitySample(
                closedWoodland,
                openHerbaceous,
                saturatedWetland,
                alpineTundra,
                xericScrub);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
