package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One AUTH-0033 host-material cell interpreted as part of a coherent AUTH-0034 assemblage. */
public record SkyIslandLithologicAssemblageCell(
        int assemblageId,
        SkyIslandLithologicAssemblageKind assemblageKind,
        SkyIslandMaterialFamilyCell familyCharacter) {

    public SkyIslandLithologicAssemblageCell {
        if (assemblageId < 0) {
            throw new IllegalArgumentException("assemblageId must be non-negative");
        }
        assemblageKind = Objects.requireNonNull(assemblageKind, "assemblageKind");
        familyCharacter = Objects.requireNonNull(familyCharacter, "familyCharacter");
    }

    public int index() {
        return familyCharacter.index();
    }

    public int xIndex() {
        return familyCharacter.xIndex();
    }

    public int depthIndex() {
        return familyCharacter.depthIndex();
    }

    public int zIndex() {
        return familyCharacter.zIndex();
    }

    public SkyIslandSubsurfacePosition position() {
        return familyCharacter.position();
    }
}
