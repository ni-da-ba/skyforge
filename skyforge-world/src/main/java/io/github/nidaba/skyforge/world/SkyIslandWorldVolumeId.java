package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Stable nested identity for one independently compiled island volume in a world catalog. */
public record SkyIslandWorldVolumeId(
        long archipelagoRootSeed,
        String groupIdentifier,
        int groupOrdinal,
        int memberOrdinal,
        long geometrySeed) {

    /** Validates stable hierarchy coordinates. */
    public SkyIslandWorldVolumeId {
        Objects.requireNonNull(groupIdentifier, "groupIdentifier");
        if (groupIdentifier.isBlank()) {
            throw new IllegalArgumentException("groupIdentifier must not be blank");
        }
        if (groupOrdinal < 0) {
            throw new IllegalArgumentException("groupOrdinal must be non-negative");
        }
        if (memberOrdinal < 0) {
            throw new IllegalArgumentException("memberOrdinal must be non-negative");
        }
    }

    /** Stable readable hierarchy path suitable for diagnostics and backend provenance. */
    public String path() {
        return Long.toUnsignedString(archipelagoRootSeed)
                + "/" + groupIdentifier
                + "/" + groupOrdinal
                + "/" + memberOrdinal
                + "/" + Long.toUnsignedString(geometrySeed);
    }
}
