package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Projects accepted authored hydrology into exact backend-neutral visible-water realization intent.
 *
 * <p>This planner introduces no new hydrologic threshold, route, retained basin, or drop event.
 * It only binds already accepted source semantics into a downstream-consumable contract. A channel
 * whose two graph endpoints are owned by the same retained basin remains part of submerged fluvial
 * geomorphology but is omitted as separate visible channel water because the standing basin owns
 * that water surface.
 */
public final class SkyIslandVisibleHydrologicRealizationPlanner {
    private SkyIslandVisibleHydrologicRealizationPlanner() {}

    public static SkyIslandVisibleHydrologicRealizationPlan plan(SkyIslandDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");

        SkyIslandCoherentHydrologicRealizationPlan coherent =
                SkyIslandCoherentHydrologicRealizationPlanner.plan(descriptor);
        SkyIslandWaterbodyFootprintPlan waterbodies =
                SkyIslandWaterbodyFootprintPlanner.plan(descriptor);
        SkyIslandWaterbodyMarginPlan waterbodyMargins =
                SkyIslandWaterbodyMarginPlanner.plan(descriptor);

        List<SkyIslandVisibleChannelWaterIntent> channels = new ArrayList<>();
        for (SkyIslandNaturalizedChannelPath path :
                SkyIslandVisibleHydrologicRealizationPlan.visibleChannelPaths(
                        coherent, waterbodies)) {
            SkyIslandChannelSegment segment = path.profile().segment();
            List<SkyIslandRiparianCell> riparian = coherent.riparian().cells().stream()
                    .filter(cell -> cell.channelSourceCellIndex() == segment.sourceCellIndex()
                            && cell.channelDownstreamCellIndex() == segment.downstreamCellIndex())
                    .toList();
            channels.add(new SkyIslandVisibleChannelWaterIntent(path, riparian));
        }

        if (waterbodies.footprints().size() != waterbodyMargins.margins().size()) {
            throw new IllegalStateException(
                    "accepted waterbody footprint/margin plans lost one-to-one correspondence");
        }
        List<SkyIslandVisibleRetainedWaterIntent> retainedWater = new ArrayList<>();
        for (int i = 0; i < waterbodies.footprints().size(); i++) {
            retainedWater.add(new SkyIslandVisibleRetainedWaterIntent(
                    waterbodies.footprints().get(i),
                    waterbodyMargins.margins().get(i)));
        }

        List<SkyIslandVisibleDropWaterIntent> drops =
                coherent.drops().drops().stream()
                        .map(SkyIslandVisibleDropWaterIntent::new)
                        .toList();

        return new SkyIslandVisibleHydrologicRealizationPlan(
                descriptor,
                coherent,
                waterbodies,
                waterbodyMargins,
                channels,
                retainedWater,
                drops);
    }
}
