package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Backend-neutral structural community archetypes accepted by #1198.
 *
 * <p>These values identify reusable ecological community structures, not Minecraft biome IDs,
 * concrete species lists, occupancy states, or backend registry identity.
 */
public enum SkyIslandCommunityArchetype {
    CLOSED_WOODLAND,
    OPEN_HERBACEOUS,
    SATURATED_WETLAND,
    ALPINE_TUNDRA,
    XERIC_SCRUB;

    /** Selects this archetype's exact accepted #1198 suitability value from one sample. */
    public double suitability(SkyIslandCommunitySuitabilitySample sample) {
        Objects.requireNonNull(sample, "sample");
        return switch (this) {
            case CLOSED_WOODLAND -> sample.closedWoodland();
            case OPEN_HERBACEOUS -> sample.openHerbaceous();
            case SATURATED_WETLAND -> sample.saturatedWetland();
            case ALPINE_TUNDRA -> sample.alpineTundra();
            case XERIC_SCRUB -> sample.xericScrub();
        };
    }

    /** Selects this archetype's exact accepted #1198 local suitability field. */
    public SkyIslandSemanticField field(SkyIslandCommunitySuitabilityFieldSet fields) {
        Objects.requireNonNull(fields, "fields");
        return switch (this) {
            case CLOSED_WOODLAND -> fields.closedWoodland();
            case OPEN_HERBACEOUS -> fields.openHerbaceous();
            case SATURATED_WETLAND -> fields.saturatedWetland();
            case ALPINE_TUNDRA -> fields.alpineTundra();
            case XERIC_SCRUB -> fields.xericScrub();
        };
    }
}
