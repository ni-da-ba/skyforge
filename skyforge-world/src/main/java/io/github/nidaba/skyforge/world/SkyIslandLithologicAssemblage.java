package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** One connected authored lithologic assemblage retaining the complete AUTH-0033 family state. */
public record SkyIslandLithologicAssemblage(
        int assemblageId,
        SkyIslandLithologicAssemblageKind kind,
        List<SkyIslandLithologicAssemblageCell> cells) {

    public SkyIslandLithologicAssemblage {
        if (assemblageId < 0) {
            throw new IllegalArgumentException("assemblageId must be non-negative");
        }
        kind = Objects.requireNonNull(kind, "kind");
        cells = List.copyOf(cells);
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("lithologic assemblage must contain at least one cell");
        }
        for (SkyIslandLithologicAssemblageCell cell : cells) {
            Objects.requireNonNull(cell, "assemblage cell");
            if (cell.assemblageId() != assemblageId || cell.assemblageKind() != kind) {
                throw new IllegalArgumentException(
                        "assemblage cells must retain their parent assemblage identity");
            }
        }
    }

    public int cellCount() {
        return cells.size();
    }

    public double meanFamilyMembership(SkyIslandMaterialFamilyKind family) {
        Objects.requireNonNull(family, "family");
        return cells.stream()
                .mapToDouble(cell -> cell.familyCharacter().membership(family))
                .average()
                .orElse(0.0);
    }

    public double peakFamilyMembership(SkyIslandMaterialFamilyKind family) {
        Objects.requireNonNull(family, "family");
        return cells.stream()
                .mapToDouble(cell -> cell.familyCharacter().membership(family))
                .max()
                .orElse(0.0);
    }
}
