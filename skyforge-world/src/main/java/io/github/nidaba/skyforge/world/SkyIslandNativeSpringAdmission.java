package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Explainable backend-neutral decision for one candidate native subsurface spring.
 *
 * <p>The decision carries exact authored cave and aquifer provenance when available. Negative IDs
 * mean that no corresponding authored provenance exists.
 */
public record SkyIslandNativeSpringAdmission(
        SkyIslandSubsurfacePosition position,
        SkyIslandNativeSpringFluidKind fluidKind,
        SkyIslandNativeSpringAdmissionStatus status,
        SkyIslandExteriorConnectedCaveVolumeSample.SourceKind caveSourceKind,
        int caveSystemId,
        int aquiferRegionId,
        int aquiferCellIndex,
        double aquiferMembership) {

    public SkyIslandNativeSpringAdmission {
        position = Objects.requireNonNull(position, "position");
        fluidKind = Objects.requireNonNull(fluidKind, "fluidKind");
        status = Objects.requireNonNull(status, "status");
        caveSourceKind = Objects.requireNonNull(caveSourceKind, "caveSourceKind");
        if (caveSystemId < -1 || aquiferRegionId < -1 || aquiferCellIndex < -1) {
            throw new IllegalArgumentException("native spring provenance IDs must be >= -1");
        }
        if (!Double.isFinite(aquiferMembership)
                || aquiferMembership < 0.0
                || aquiferMembership > 1.0) {
            throw new IllegalArgumentException("aquiferMembership must be finite and in [0, 1]");
        }

        boolean cavePresent = caveSourceKind != SkyIslandExteriorConnectedCaveVolumeSample.SourceKind.NONE;
        if (cavePresent != (caveSystemId >= 0)) {
            throw new IllegalArgumentException("cave source kind and caveSystemId disagree");
        }

        boolean aquiferPresent = aquiferRegionId >= 0 || aquiferCellIndex >= 0 || aquiferMembership > 0.0;
        if (aquiferPresent
                != (aquiferRegionId >= 0 && aquiferCellIndex >= 0 && aquiferMembership > 0.0)) {
            throw new IllegalArgumentException("aquifer provenance must be complete or absent");
        }

        if (status.admitted()) {
            if (fluidKind != SkyIslandNativeSpringFluidKind.WATER
                    || !cavePresent
                    || !aquiferPresent) {
                throw new IllegalArgumentException(
                        "admitted native spring must be aquifer-supported cave water");
            }
        }
        if (status == SkyIslandNativeSpringAdmissionStatus.OUTSIDE_AUTHORED_ISLAND
                && (cavePresent || aquiferPresent)) {
            throw new IllegalArgumentException(
                    "outside-island admission must not claim cave or aquifer provenance");
        }
        if (status == SkyIslandNativeSpringAdmissionStatus.NOT_AUTHORED_CAVE_INTERIOR
                && cavePresent) {
            throw new IllegalArgumentException(
                    "non-cave admission must not claim cave provenance");
        }
        if (status == SkyIslandNativeSpringAdmissionStatus.NO_AQUIFER_SUPPORT
                && aquiferPresent) {
            throw new IllegalArgumentException(
                    "no-aquifer admission must not claim aquifer provenance");
        }
    }

    /** Convenience mirror of the status admission predicate. */
    public boolean admitted() {
        return status.admitted();
    }
}
