package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Backend-neutral transition-zone plan around retained waterbodies on one authored island. */
public record SkyIslandWaterbodyMarginPlan(
        SkyIslandDescriptor descriptor,
        List<SkyIslandWaterbodyMargin> margins) {

    public SkyIslandWaterbodyMarginPlan {
        Objects.requireNonNull(descriptor, "descriptor");
        margins = List.copyOf(margins);
        margins.forEach(margin -> Objects.requireNonNull(margin, "margin"));
    }

    public int marginCellCount() {
        return margins.stream().mapToInt(margin -> margin.cells().size()).sum();
    }

    public long count(SkyIslandWaterbodyMarginKind kind) {
        return margins.stream().mapToLong(margin -> margin.count(kind)).sum();
    }
}
