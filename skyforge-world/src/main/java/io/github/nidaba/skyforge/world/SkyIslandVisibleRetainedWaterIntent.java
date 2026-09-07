package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Backend-neutral visible standing-water intent for one exact accepted retained-waterbody footprint.
 *
 * <p>The source footprint owns inundation/water-depth potentials while the source margin preserves
 * the accepted dry shoreline transition. This record does not define literal block shorelines or
 * world-space water levels.
 */
public record SkyIslandVisibleRetainedWaterIntent(
        SkyIslandWaterbodyFootprint footprint,
        SkyIslandWaterbodyMargin margin) {

    public SkyIslandVisibleRetainedWaterIntent {
        footprint = Objects.requireNonNull(footprint, "footprint");
        margin = Objects.requireNonNull(margin, "margin");
        if (!margin.footprint().equals(footprint)) {
            throw new IllegalArgumentException(
                    "waterbody margin must reference the exact retained-water footprint");
        }
    }

    public SkyIslandVisibleHydrologicRealizationKind kind() {
        return SkyIslandVisibleHydrologicRealizationKind.RETAINED_WATER;
    }
}
