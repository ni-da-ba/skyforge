package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Stable AUTH-0039 lithologic context carried by one backend-neutral material-binding request. */
public record SkyIslandMaterialBindingAssemblageContext(
        int assemblageId,
        SkyIslandLithologicAssemblageKind assemblageKind) {

    public SkyIslandMaterialBindingAssemblageContext {
        if (assemblageId < 0) {
            throw new IllegalArgumentException("assemblageId must be non-negative");
        }
        assemblageKind = Objects.requireNonNull(assemblageKind, "assemblageKind");
    }
}
