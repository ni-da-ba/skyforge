package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic support-threshold candidate admission.
 *
 * <p>For resolved local spatial support {@code s} and candidate admission scalar {@code a},
 * admission is exactly {@code a < s}. Candidate hashing therefore only thins already-established
 * ecological support; it cannot create support.
 */
public enum SkyIslandSupportThresholdVegetationPlacementAdmissionTransform
        implements SkyIslandVegetationPlacementAdmissionTransform {
    INSTANCE;

    @Override
    public Optional<SkyIslandVegetationPlacementAdmissionState> admit(
            SkyIslandVegetationSpatialPatchEvaluation spatialEvaluation,
            SkyIslandEcologicalPlacementCandidate candidate) {
        Objects.requireNonNull(spatialEvaluation, "spatialEvaluation");
        Objects.requireNonNull(candidate, "candidate");
        if (!spatialEvaluation.position().equals(candidate.position())) {
            throw new IllegalArgumentException(
                    "candidate position must exactly match spatial ecological evaluation position");
        }
        if (spatialEvaluation.state().isEmpty()) {
            return Optional.empty();
        }

        double support = spatialEvaluation.state().orElseThrow().spatialNicheSupport();
        return Optional.of(new SkyIslandVegetationPlacementAdmissionState(
                candidate.admissionValue() < support));
    }
}
