package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Produces deterministic terrain-aware channel centerlines while preserving watershed topology.
 *
 * <p>Hydrology-overhaul H2b promotes only headwaters, confluences and terminals to hard geometric
 * controls. Degree-two coarse watershed cells remain semantic profile boundaries but no longer
 * force literal visible-river vertices.
 */
public final class SkyIslandNaturalizedChannelPlanner {
    public static final int SUBDIVISIONS =
            SkyIslandTerrainAwareMacroReachRouter.STATIONS_PER_COARSE_REACH;
    public static final double MAX_CHORD_DEVIATION_SPACING_FRACTION = 0.70;

    private SkyIslandNaturalizedChannelPlanner() {}

    /** Historical/raw AUTH-0017 geometry for the complete visible-channel diagnostic. */
    public static SkyIslandNaturalizedChannelPlan plan(SkyIslandDescriptor descriptor) {
        return plan(descriptor, SkyIslandChannelProfilePlanner.plan(descriptor).profiles());
    }

    /** Naturalizes one explicit channel-profile subset without changing accepted graph topology. */
    public static SkyIslandNaturalizedChannelPlan plan(
            SkyIslandDescriptor descriptor,
            List<SkyIslandChannelProfile> profiles) {
        Objects.requireNonNull(descriptor, "descriptor");
        profiles = List.copyOf(profiles);

        SkyIslandWatershedPlan watershed = SkyIslandWatershedPlanner.plan(descriptor);
        SkyIslandSemanticFieldSet semanticFields = SkyIslandSemanticFieldSet.create(descriptor);
        SkyIslandSemanticField terrain = semanticFields.elevationTendency();
        SkyIslandSemanticField interiority = semanticFields.interiority();
        double spacing = watershed.spacing();

        List<SkyIslandNaturalizedChannelPath> paths = new ArrayList<>(profiles.size());
        for (SkyIslandChannelMacroReach macro : SkyIslandChannelMacroReachPlanner.plan(profiles)) {
            paths.addAll(SkyIslandTerrainAwareMacroReachRouter.route(
                    macro,
                    terrain,
                    interiority,
                    spacing,
                    MAX_CHORD_DEVIATION_SPACING_FRACTION));
        }
        paths.sort(Comparator.comparingInt(
                path -> path.profile().segment().sourceCellIndex()));
        return new SkyIslandNaturalizedChannelPlan(descriptor, spacing, paths);
    }
}
