package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.Objects;

/** Current coherent downstream hydrologic realization for one authored island. */
public record SkyIslandCoherentHydrologicRealizationPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandCoherentChannelPlan channels,
        SkyIslandRiparianCorridorPlan riparian,
        SkyIslandChannelDropPlan drops,
        SkyIslandHydrologicTerrainInfluencePlan terrainInfluence,
        SkyIslandHydrologicTerrainSurfacePlan terrainSurface,
        SkyIslandNaturalizedChannelPlan naturalizedChannels) {

    public SkyIslandCoherentHydrologicRealizationPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        channels = Objects.requireNonNull(channels, "channels");
        riparian = Objects.requireNonNull(riparian, "riparian");
        drops = Objects.requireNonNull(drops, "drops");
        terrainInfluence = Objects.requireNonNull(terrainInfluence, "terrainInfluence");
        terrainSurface = Objects.requireNonNull(terrainSurface, "terrainSurface");
        naturalizedChannels = Objects.requireNonNull(naturalizedChannels, "naturalizedChannels");
    }

    public SkyIslandContinuousHydrologicTerrainField continuousTerrain() {
        return SkyIslandContinuousHydrologicTerrainField.create(descriptor, terrainSurface);
    }
}
