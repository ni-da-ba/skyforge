package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/**
 * Transparent deterministic evaluation of one vegetation candidate window.
 *
 * <p>The full ordered candidate result is retained. Admitted, rejected, and unresolved projections
 * are deterministic views only and never replace the full provenance-bearing result.
 */
public record SkyIslandVegetationCandidateWindowEvaluation(
        SkyIslandVegetationCandidateWindowProfile profile,
        SkyIslandEcologicalCandidateWindowResult candidateQuery,
        List<SkyIslandVegetationPlacementCandidateEvaluation> evaluations) {

    public SkyIslandVegetationCandidateWindowEvaluation {
        profile = Objects.requireNonNull(profile, "profile");
        candidateQuery = Objects.requireNonNull(candidateQuery, "candidateQuery");
        Objects.requireNonNull(evaluations, "evaluations");
        evaluations = List.copyOf(evaluations);

        if (!profile.placementProfile()\n                .candidateProfile()\n                .equals(candidateQuery.latticeProfile())) {
            throw new IllegalArgumentException(
                    "candidate query lattice must match window placement profile");
        }
        if (evaluations.size() != candidateQuery.candidates().size()) {
            throw new IllegalArgumentException(
                    "candidate-window evaluation must retain exactly one result per queried "
                            + "candidate");
        }

        for (int index = 0; index < evaluations.size(); index++) {
            SkyIslandVegetationPlacementCandidateEvaluation evaluation =
                    Objects.requireNonNull(evaluations.get(index), "evaluation");
            SkyIslandEcologicalPlacementCandidate candidate =
                    candidateQuery.candidates().get(index);
            if (!evaluation.placementProfile().equals(profile.placementProfile())) {
                throw new IllegalArgumentException(
                        "candidate evaluation placement profile must match window profile");
            }
            if (!evaluation.candidate().equals(candidate)) {
                throw new IllegalArgumentException(
                        "candidate evaluations must preserve exact query order and identity");
            }
        }
    }

    /** Returns resolved admitted candidate evaluations in canonical query order. */
    public List<SkyIslandVegetationPlacementCandidateEvaluation> admitted() {
        return evaluations.stream()
                .filter(SkyIslandVegetationPlacementCandidateEvaluation::resolved)
                .filter(evaluation -> evaluation.admissionState().orElseThrow().admitted())
                .toList();
    }

    /** Returns resolved rejected candidate evaluations in canonical query order. */
    public List<SkyIslandVegetationPlacementCandidateEvaluation> rejected() {
        return evaluations.stream()
                .filter(SkyIslandVegetationPlacementCandidateEvaluation::resolved)
                .filter(evaluation -> !evaluation.admissionState().orElseThrow().admitted())
                .toList();
    }

    /** Returns unresolved candidate evaluations in canonical query order. */
    public List<SkyIslandVegetationPlacementCandidateEvaluation> unresolved() {
        return evaluations.stream()
                .filter(evaluation -> !evaluation.resolved())
                .toList();
    }
}
