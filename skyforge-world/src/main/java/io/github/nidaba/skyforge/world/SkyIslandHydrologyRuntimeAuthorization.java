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

    /**
     * Constructs the runtime token from the exact validated F4H inputs.
     *
     * <p>The association, candidate, and refined field are checked by the quantizer before the
     * token becomes available to a backend runtime binding.
     */
    public static SkyIslandHydrologyRuntimeAuthorization fromF4H(
            SkyIslandAuthoredRealizationAssociation association,
            SkyIslandComponentFluvialTerrainCandidatePlan candidatePlan,
            SkyIslandQualifiedFluvialTerrainField refinedField) {
        Objects.requireNonNull(association, "association");
        Objects.requireNonNull(candidatePlan, "candidatePlan");
        Objects.requireNonNull(refinedField, "refinedField");
        SkyIslandFluvialVoxelQuantizationPlan quantization =
                SkyIslandRefinedFluvialVoxelQuantizationPlanner.plan(
                        association, candidatePlan, refinedField);
        return new SkyIslandHydrologyRuntimeAuthorization(association, quantization);
    }

    public boolean allowsVolume(SkyIslandWorldVolumeId volumeId) {
        return association.realizedVolumeId().equals(Objects.requireNonNull(volumeId, "volumeId"));
    }

    /**
     * Allows a visible-water position only when it remains inside an exact authorized F4H
     * column and the compiled support interval. This is a migration seam; it does not authorize
     * positions discovered from the legacy planner outside the accepted field.
     */
    public boolean allowsHydrologyPosition(int worldX, int y, int worldZ) {
        return quantization.authorizedColumns().stream()
                .anyMatch(column -> column.worldX() == worldX
                        && column.worldZ() == worldZ
                        && y >= column.originalSupport().minimumSolidY()
                        && y <= column.originalSupport().maximumSolidY());
    }

    public boolean allowsRemoval(int worldX, int y, int worldZ) {
        return quantization.authorizedColumns().stream()
                .anyMatch(column -> column.worldX() == worldX
                        && column.worldZ() == worldZ
                        && y > column.targetMaximumSolidY()
                        && y <= column.originalSupport().maximumSolidY());
    }
}
