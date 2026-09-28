package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicit deterministic discrete-realization policy for one vegetation functional-group stream.
 *
 * <p>Candidate geometry and admission policy are supplied by the caller. Functional-group identity
 * does not imply spacing, jitter, density, or a backend feature.
 */
public record SkyIslandVegetationPlacementProfile(
        SkyIslandEcologicalCandidateLatticeProfile candidateProfile,
        SkyIslandVegetationPlacementAdmissionTransform admissionTransform) {

    public SkyIslandVegetationPlacementProfile {
        candidateProfile = Objects.requireNonNull(candidateProfile, "candidateProfile");
        admissionTransform = Objects.requireNonNull(admissionTransform, "admissionTransform");
    }

    /** Generates one candidate from the exact candidate profile retained by this policy. */
    public SkyIslandEcologicalPlacementCandidate candidate(long cellX, long cellZ) {
        return candidateProfile.candidate(cellX, cellZ);
    }

    /** Evaluates one exact candidate against ecology sampled at that exact candidate position. */
    public SkyIslandVegetationPlacementCandidateEvaluation evaluate(
            SkyIslandVegetationSpatialPatchEvaluation spatialEvaluation,
            SkyIslandEcologicalPlacementCandidate candidate) {
        Objects.requireNonNull(spatialEvaluation, "spatialEvaluation");
        Objects.requireNonNull(candidate, "candidate");
        if (!candidateProfile.equals(candidate.latticeProfile())) {
            throw new IllegalArgumentException(
                    "candidate must originate from this placement profile's candidate lattice");
        }
        if (!spatialEvaluation.position().equals(candidate.position())) {
            throw new IllegalArgumentException(
                    "candidate position must exactly match spatial ecological evaluation position");
        }

        Optional<SkyIslandVegetationPlacementAdmissionState> state = Objects.requireNonNull(
                admissionTransform.admit(spatialEvaluation, candidate),
                "candidate admission transform result");
        return new SkyIslandVegetationPlacementCandidateEvaluation(
                spatialEvaluation,
                this,
                candidate,
                state);
    }
}
