package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Composes AUTH-0018 coherent visible channels through all downstream river-dependent semantics. */
public final class SkyIslandCoherentHydrologicRealizationPlanner {
    private SkyIslandCoherentHydrologicRealizationPlanner() {}

    public static SkyIslandCoherentHydrologicRealizationPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        SkyIslandCoherentChannelPlan channels = SkyIslandCoherentChannelPlanner.plan(descriptor);
        List<SkyIslandChannelProfile> profiles = channels.profiles();

        SkyIslandRiparianCorridorPlan riparian =
                SkyIslandRiparianCorridorPlanner.plan(descriptor, profiles);
        SkyIslandChannelDropPlan drops =
                SkyIslandChannelDropPlanner.plan(descriptor, profiles);
        SkyIslandHydrologicTerrainInfluencePlan influence =
                SkyIslandHydrologicTerrainInfluencePlanner.plan(
                        descriptor, profiles, riparian, drops);
        SkyIslandHydrologicTerrainSurfacePlan surface =
                SkyIslandHydrologicTerrainSurfacePlanner.plan(
                        descriptor, influence, riparian);
        SkyIslandNaturalizedChannelPlan naturalized =
                SkyIslandNaturalizedChannelPlanner.plan(descriptor, profiles);

        return new SkyIslandCoherentHydrologicRealizationPlan(
                descriptor,
                channels,
                riparian,
                drops,
                influence,
                surface,
                naturalized);
    }
}
