package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One AUTH-0037 palette candidate assigned to an AUTH-0038 stable semantic binding key. */
public record SkyIslandSemanticPaletteBindingCandidate(
        SkyIslandSemanticMaterialPaletteCandidate candidate,
        SkyIslandSemanticPaletteBindingKey bindingKey) {

    public SkyIslandSemanticPaletteBindingCandidate {
        candidate = Objects.requireNonNull(candidate, "candidate");
        bindingKey = Objects.requireNonNull(bindingKey, "bindingKey");
        if (candidate.role() != bindingKey.role()
                || candidate.sourceChannel() != bindingKey.sourceChannel()) {
            throw new IllegalArgumentException(
                    "binding candidate must retain role/source provenance");
        }
    }
}
