package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * Explicit human-gated runtime authorization for one validated hydrology realization.
 *
 * <p>This token is association-specific and carries no discovery or expansion behavior. Callers must
 * present the exact validated F4H quantization plan; rejected components and unplanned coordinates
 * are never authorized.
 */
public record SkyIslandHydrologyRuntimeAuthorization(
        SkyIslandAuthoredRealizationAssociation association,
        SkyIslandFluvialVoxelQuantizationPlan quantization) {

    public SkyIslandHydrologyRuntimeAuthorization {
        association = Objects.requireNonNull(association, "association");
        quantization = Objects.requireNonNull(quantization, "quantization");
        if (!association.equals(quantization.association())) {
            throw new IllegalArgumentException(
                    "runtime authorization must use the exact F4H association");
        }
        if (!quantization.rejectedComponents().isEmpty()) {
            throw new IllegalArgumentException(
                    "runtime authorization cannot include rejected hydrology components");
        }
        if (quantization.authorizedColumns().isEmpty()) {
            throw new IllegalArgumentException(
                    "runtime authorization requires at least one validated hydrology column");
        }
    }

    public boolean allowsVolume(SkyIslandWorldVolumeId volumeId) {
        return association.realizedVolumeId().equals(Objects.requireNonNull(volumeId, "volumeId"));
    }

    public boolean allowsRemoval(int worldX, int y, int worldZ) {
        return quantization.authorizedColumns().stream()
                .anyMatch(column -> column.worldX() == worldX
                        && column.worldZ() == worldZ
                        && y > column.targetMaximumSolidY()
                        && y <= column.originalSupport().maximumSolidY());
    }
}
