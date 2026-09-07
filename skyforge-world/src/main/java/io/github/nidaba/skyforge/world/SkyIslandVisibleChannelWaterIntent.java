package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * Backend-neutral visible-water intent for one exact accepted naturalized channel path.
 *
 * <p>The intent preserves the source channel geometry/profile and its exact dry riparian
 * relationship. It does not choose blocks, physical Y coordinates, or fluid update behavior.
 */
public record SkyIslandVisibleChannelWaterIntent(
        SkyIslandNaturalizedChannelPath path,
        List<SkyIslandRiparianCell> riparianCells) {

    public SkyIslandVisibleChannelWaterIntent {
        path = Objects.requireNonNull(path, "path");
        riparianCells = List.copyOf(riparianCells);
        SkyIslandChannelSegment segment = path.profile().segment();
        for (SkyIslandRiparianCell cell : riparianCells) {
            Objects.requireNonNull(cell, "riparian cell");
            if (cell.channelSourceCellIndex() != segment.sourceCellIndex()
                    || cell.channelDownstreamCellIndex() != segment.downstreamCellIndex()) {
                throw new IllegalArgumentException(
                        "riparian cell does not belong to the exact source channel path");
            }
        }
    }

    public SkyIslandVisibleHydrologicRealizationKind kind() {
        return SkyIslandVisibleHydrologicRealizationKind.CHANNEL_WATER;
    }
}
