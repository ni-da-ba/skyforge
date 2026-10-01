package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * AUTH-0096 exact local surface-site evidence for one authored/realized island association.
 *
 * <p>The profile retains the accepted watershed and visible-hydrology source plans so downstream
 * consumers can audit every local measurement back to current Authorship provenance.
 */
public record SkyIslandSurfaceSiteCapabilityProfile(
        SkyIslandAuthoredRealizationAssociation association,
        SkyIslandWatershedPlan watershed,
        SkyIslandVisibleHydrologicRealizationPlan visibleHydrology,
        List<SkyIslandSurfaceSiteCapabilityCell> cells) {

    public SkyIslandSurfaceSiteCapabilityProfile {
        association = Objects.requireNonNull(association, "association");
        watershed = Objects.requireNonNull(watershed, "watershed");
        visibleHydrology = Objects.requireNonNull(visibleHydrology, "visibleHydrology");
        cells = List.copyOf(cells);
        cells.forEach(cell -> Objects.requireNonNull(cell, "surface-site cell"));

        if (!watershed.descriptor().equals(association.authoredDescriptor())
                || !visibleHydrology.descriptor().equals(association.authoredDescriptor())) {
            throw new IllegalArgumentException(
                    "surface-site capability sources must belong to the exact associated authored island");
        }
        if (cells.size() != watershed.cells().size()) {
            throw new IllegalArgumentException(
                    "surface-site capability must cover every accepted watershed cell exactly once");
        }
        for (int ordinal = 0; ordinal < cells.size(); ordinal++) {
            SkyIslandWatershedCell source = watershed.cells().get(ordinal);
            SkyIslandSurfaceSiteCapabilityCell cell = cells.get(ordinal);
            if (source.index() != cell.watershedCellIndex()
                    || !source.position().equals(cell.position())) {
                throw new IllegalArgumentException(
                        "surface-site capability must preserve exact watershed order and identity");
            }
        }
    }

    public int gridSize() {
        return watershed.gridSize();
    }

    public double spacing() {
        return watershed.spacing();
    }

    public int cellCount() {
        return cells.size();
    }

    public long physicalSurfaceCellCount() {
        return cells.stream().filter(SkyIslandSurfaceSiteCapabilityCell::physicalSurfacePresent).count();
    }
}
