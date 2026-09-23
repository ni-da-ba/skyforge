package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Produces deterministic sub-grid channel centerlines while preserving accepted watershed topology.
 *
 * <p>AUTH-0017 originally naturalized each coarse graph edge with decorative seeded Bezier
 * curvature. Hydrology-overhaul H2 retains the accepted graph nodes but delegates the physical
 * centerline to a fine terrain-aware corridor search. Catchment topology therefore stays authored
 * by the watershed while the visible river is no longer forced to inherit the coarse lattice line.
 */
public final class SkyIslandNaturalizedChannelPlanner {
    public static final int SUBDIVISIONS = SkyIslandTerrainAwareChannelCorridorPlanner.STATIONS;
    public static final double MAX_CHORD_DEVIATION_SPACING_FRACTION = 0.42;

    private SkyIslandNaturalizedChannelPlanner() {}

    /** Historical/raw AUTH-0017 geometry for the complete visible-channel diagnostic. */
    public static SkyIslandNaturalizedChannelPlan plan(SkyIslandDescriptor descriptor) {
        return plan(descriptor, SkyIslandChannelProfilePlanner.plan(descriptor).profiles());
    }

    /** Naturalizes one explicit channel-profile subset without changing any graph node. */
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
        for (SkyIslandChannelProfile profile : profiles) {
            paths.add(SkyIslandTerrainAwareChannelCorridorPlanner.route(
                    profile,
                    terrain,
                    interiority,
                    spacing,
                    MAX_CHORD_DEVIATION_SPACING_FRACTION));
        }
        return new SkyIslandNaturalizedChannelPlan(descriptor, spacing, paths);
    }
}
