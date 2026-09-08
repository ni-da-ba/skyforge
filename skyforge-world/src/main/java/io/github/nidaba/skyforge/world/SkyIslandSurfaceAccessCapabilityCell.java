package io.github.nidaba.skyforge.world;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** AUTH-0097 directional access evidence attached to one exact AUTH-0096 surface anchor. */
public record SkyIslandSurfaceAccessCapabilityCell(
        SkyIslandSurfaceSiteCapabilityCell sourceCell,
        List<SkyIslandSurfaceAccessRay> rays) {

    public SkyIslandSurfaceAccessCapabilityCell {
        sourceCell = Objects.requireNonNull(sourceCell, "sourceCell");
        rays = List.copyOf(rays);
        rays.forEach(ray -> Objects.requireNonNull(ray, "surface-access ray"));

        if (!sourceCell.physicalSurfacePresent()) {
            if (!rays.isEmpty()) {
                throw new IllegalArgumentException(
                        "surface anchor without exact physical support cannot carry access rays");
            }
        } else {
            List<SkyIslandSurfaceAccessDirection> expected =
                    Arrays.asList(SkyIslandSurfaceAccessDirection.values());
            List<SkyIslandSurfaceAccessDirection> actual =
                    rays.stream().map(SkyIslandSurfaceAccessRay::direction).toList();
            if (!actual.equals(expected)) {
                throw new IllegalArgumentException(
                        "physically supported surface anchor requires all directions in canonical order");
            }
        }
    }

    public int watershedCellIndex() {
        return sourceCell.watershedCellIndex();
    }

    public SkyIslandLocalPosition position() {
        return sourceCell.position();
    }
}
