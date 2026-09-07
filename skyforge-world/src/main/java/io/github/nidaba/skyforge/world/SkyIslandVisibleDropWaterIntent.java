package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Backend-neutral visible-water intent for one exact accepted channel-drop event. */
public record SkyIslandVisibleDropWaterIntent(SkyIslandChannelDrop drop) {

    public SkyIslandVisibleDropWaterIntent {
        drop = Objects.requireNonNull(drop, "drop");
    }

    public SkyIslandVisibleHydrologicRealizationKind kind() {
        return switch (drop.kind()) {
            case CASCADE_STEP -> SkyIslandVisibleHydrologicRealizationKind.CASCADE;
            case WATERFALL -> SkyIslandVisibleHydrologicRealizationKind.WATERFALL;
            case EDGE_FALL -> SkyIslandVisibleHydrologicRealizationKind.EDGE_DISCHARGE;
        };
    }
}
