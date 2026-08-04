package io.github.nidaba.skyforge.reference.evidence;

import io.github.nidaba.skyforge.recipes.island.CompiledIsland;
import io.github.nidaba.skyforge.reference.sampling.ScalarGrid;
import java.util.Objects;

/** Complete in-memory numerical evidence for one sampled compiled island. */
public record IslandEvidence(
        CompiledIsland compiledIsland,
        ScalarGrid height,
        ScalarGrid landMask,
        ScalarGrid slope,
        CrossSection eastWest,
        CrossSection northSouth,
        GridStatistics heightStatistics,
        GridStatistics slopeStatistics,
        IslandMetrics metrics) {
    /** Validates a coherent evidence set over one common grid. */
    public IslandEvidence {
        Objects.requireNonNull(compiledIsland, "compiledIsland");
        Objects.requireNonNull(height, "height");
        Objects.requireNonNull(landMask, "landMask");
        Objects.requireNonNull(slope, "slope");
        Objects.requireNonNull(eastWest, "eastWest");
        Objects.requireNonNull(northSouth, "northSouth");
        Objects.requireNonNull(heightStatistics, "heightStatistics");
        Objects.requireNonNull(slopeStatistics, "slopeStatistics");
        Objects.requireNonNull(metrics, "metrics");
        if (!height.specification().equals(landMask.specification())
                || !height.specification().equals(slope.specification())) {
            throw new IllegalArgumentException("evidence grids must share one specification");
        }
        if (eastWest.axis() != CrossSection.Axis.EAST_WEST
                || northSouth.axis() != CrossSection.Axis.NORTH_SOUTH) {
            throw new IllegalArgumentException("cross-section orientations are invalid");
        }
    }
}
