package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Explicit backend-neutral composition of candidate-window query, spatial ecology sampling, and
 * vegetation candidate admission.
 *
 * <p>This profile adds no ecological formula. It only composes the accepted #1225 candidate query,
 * #1221 spatial-patch evaluation, and #1223 placement-admission contracts.
 */
public record SkyIslandVegetationCandidateWindowProfile(
        SkyIslandVegetationPlacementProfile placementProfile,
        SkyIslandVegetationSpatialPatchSampler spatialSampler) {

    public SkyIslandVegetationCandidateWindowProfile {
        placementProfile = Objects.requireNonNull(placementProfile, "placementProfile");
        spatialSampler = Objects.requireNonNull(spatialSampler, "spatialSampler");
    }

    /** Evaluates every canonical placement candidate inside one exact half-open query window. */
    public SkyIslandVegetationCandidateWindowEvaluation evaluate(
            SkyIslandEcologicalCandidateQueryWindow window) {
        Objects.requireNonNull(window, "window");
        SkyIslandEcologicalCandidateWindowResult candidateQuery =
                placementProfile.candidateProfile().query(window);
        ArrayList<SkyIslandVegetationPlacementCandidateEvaluation> evaluations =
                new ArrayList<>(candidateQuery.candidates().size());

        for (SkyIslandEcologicalPlacementCandidate candidate : candidateQuery.candidates()) {
            SkyIslandVegetationSpatialPatchEvaluation spatialEvaluation =
                    Objects.requireNonNull(
                            spatialSampler.sample(candidate.position()),
                            "spatial sampler returned null");
            if (!spatialEvaluation.position().equals(candidate.position())) {
                throw new IllegalArgumentException(
                        "spatial sampler result must exactly match candidate position");
            }
            evaluations.add(placementProfile.evaluate(spatialEvaluation, candidate));
        }

        return new SkyIslandVegetationCandidateWindowEvaluation(
                this,
                candidateQuery,
                evaluations);
    }
}
