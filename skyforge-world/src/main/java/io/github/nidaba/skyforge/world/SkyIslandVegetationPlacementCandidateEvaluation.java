package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Transparent deterministic evaluation of one vegetation functional-group placement candidate.
 *
 * <p>The result retains exact spatial ecology, candidate geometry, candidate provenance, and
 * admission policy. Even an admitted candidate remains only a neutral realization proposal.
 */
public record SkyIslandVegetationPlacementCandidateEvaluation(
        SkyIslandVegetationSpatialPatchEvaluation spatialPatchEvaluation,
        SkyIslandVegetationPlacementProfile placementProfile,
        SkyIslandEcologicalPlacementCandidate candidate,
        Optional<SkyIslandVegetationPlacementAdmissionState> admissionState) {

    public SkyIslandVegetationPlacementCandidateEvaluation {
        spatialPatchEvaluation =
                Objects.requireNonNull(spatialPatchEvaluation, "spatialPatchEvaluation");
        placementProfile = Objects.requireNonNull(placementProfile, "placementProfile");
        candidate = Objects.requireNonNull(candidate, "candidate");
        admissionState = Objects.requireNonNull(admissionState, "admissionState");

        if (!placementProfile.candidateProfile().equals(candidate.latticeProfile())) {
            throw new IllegalArgumentException(
                    "candidate provenance must match placement profile candidate lattice");
        }
        if (!spatialPatchEvaluation.position().equals(candidate.position())) {
            throw new IllegalArgumentException(
                    "candidate position must exactly match spatial ecological evaluation position");
        }
        if (admissionState.isPresent()) {
            if (!spatialPatchEvaluation.resolved()) {
                throw new IllegalArgumentException(
                        "resolved candidate admission requires resolved upstream spatial ecology");
            }
            if (admissionState.orElseThrow().admitted()
                    && spatialPatchEvaluation.state().orElseThrow().spatialNicheSupport() == 0.0) {
                throw new IllegalArgumentException(
                        "candidate admission must not create presence from zero spatial support");
            }
        }
    }

    public boolean resolved() {
        return admissionState.isPresent();
    }

    public SkyIslandLocalPosition position() {
        return candidate.position();
    }
}
