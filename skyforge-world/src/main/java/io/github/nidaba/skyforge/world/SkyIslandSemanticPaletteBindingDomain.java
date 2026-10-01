package io.github.nidaba.skyforge.world;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One connected AUTH-0038 semantic palette-binding coherence domain. */
public record SkyIslandSemanticPaletteBindingDomain(
        SkyIslandSemanticPaletteBindingKey key,
        List<SkyIslandSemanticPaletteBindingCell> cells) {

    public SkyIslandSemanticPaletteBindingDomain {
        key = Objects.requireNonNull(key, "key");
        cells = List.copyOf(cells);
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("palette-binding domain requires at least one cell");
        }
        if (key.domainKind() == SkyIslandSemanticPaletteBindingDomainKind.CONTACT_TRANSITION) {
            throw new IllegalArgumentException(
                    "contact-transition keys are continuous fallback keys, not planning-cell domains");
        }

        Set<Integer> assemblages = new HashSet<>();
        int minimumIndex = Integer.MAX_VALUE;
        for (SkyIslandSemanticPaletteBindingCell cell : cells) {
            Objects.requireNonNull(cell, "binding cell");
            if (cell.candidate().role() != key.role()
                    || cell.candidate().sourceChannel() != key.sourceChannel()) {
                throw new IllegalArgumentException(
                        "binding-domain cells must retain key role/source provenance");
            }
            assemblages.add(cell.assemblageId());
            minimumIndex = Math.min(minimumIndex, cell.index());
        }
        if (minimumIndex != key.anchorId()) {
            throw new IllegalArgumentException(
                    "planning-domain binding key must anchor at minimum member cell index");
        }

        boolean matrixRole =
                key.role() == SkyIslandSemanticMaterialPaletteRole.PRIMARY_MATRIX
                        || key.role() == SkyIslandSemanticMaterialPaletteRole.SECONDARY_MATRIX;
        if (matrixRole
                && key.domainKind()
                        != SkyIslandSemanticPaletteBindingDomainKind.ASSEMBLAGE_REGION) {
            throw new IllegalArgumentException(
                    "matrix roles require ASSEMBLAGE_REGION coherence domains");
        }
        if (matrixRole && assemblages.size() != 1) {
            throw new IllegalArgumentException(
                    "matrix binding domains cannot cross AUTH-0034 assemblage boundaries");
        }
        if (!matrixRole
                && key.domainKind()
                        != SkyIslandSemanticPaletteBindingDomainKind.CONDITIONED_REGION) {
            throw new IllegalArgumentException(
                    "conditioned roles require CONDITIONED_REGION coherence domains");
        }
    }

    public int cellCount() {
        return cells.size();
    }

    public int assemblageCount() {
        return (int) cells.stream()
                .map(SkyIslandSemanticPaletteBindingCell::assemblageId)
                .distinct()
                .count();
    }
}
