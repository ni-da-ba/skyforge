package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One AUTH-0037 planning-cell role assigned to an AUTH-0038 coherence domain. */
public record SkyIslandSemanticPaletteBindingCell(
        int index,
        int xIndex,
        int depthIndex,
        int zIndex,
        int assemblageId,
        SkyIslandLithologicAssemblageKind assemblageKind,
        SkyIslandSemanticMaterialPaletteCandidate candidate) {

    public SkyIslandSemanticPaletteBindingCell {
        if (index < 0
                || xIndex < 0
                || depthIndex < 0
                || zIndex < 0
                || assemblageId < 0) {
            throw new IllegalArgumentException("binding-cell indices must be non-negative");
        }
        assemblageKind = Objects.requireNonNull(assemblageKind, "assemblageKind");
        candidate = Objects.requireNonNull(candidate, "candidate");
    }
}
