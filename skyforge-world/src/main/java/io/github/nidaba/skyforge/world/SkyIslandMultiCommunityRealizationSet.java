package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable set of per-community structural realizations sharing one exact ecological context.
 *
 * <p>The set preserves overlapping community realizations rather than selecting a winner. Every
 * member must refer to the same local position and exact disturbance/assembly evidence, and each
 * community archetype may appear at most once.
 */
public record SkyIslandMultiCommunityRealizationSet(
        List<SkyIslandCommunityRealizationEvaluation> evaluations) {

    public SkyIslandMultiCommunityRealizationSet {
        Objects.requireNonNull(evaluations, "evaluations");
        if (evaluations.isEmpty()) {
            throw new IllegalArgumentException("evaluations must not be empty");
        }

        ArrayList<SkyIslandCommunityRealizationEvaluation> copy =
                new ArrayList<>(evaluations.size());
        SkyIslandCommunityRealizationEvaluation first =
                Objects.requireNonNull(evaluations.getFirst(), "evaluation");
        EnumMap<SkyIslandCommunityArchetype, SkyIslandCommunityRealizationEvaluation> seen =
                new EnumMap<>(SkyIslandCommunityArchetype.class);

        for (SkyIslandCommunityRealizationEvaluation evaluation : evaluations) {
            evaluation = Objects.requireNonNull(evaluation, "evaluation");
            if (!evaluation.position().equals(first.position())) {
                throw new IllegalArgumentException(
                        "all community realizations must share the exact local position");
            }
            if (!evaluation.assemblyEvaluation()
                    .disturbanceEvidence()
                    .equals(first.assemblyEvaluation().disturbanceEvidence())) {
                throw new IllegalArgumentException(
                        "all community realizations must share exact disturbance/assembly provenance");
            }
            if (seen.put(evaluation.community(), evaluation) != null) {
                throw new IllegalArgumentException(
                        "duplicate community realization: " + evaluation.community());
            }
            copy.add(evaluation);
        }

        evaluations = List.copyOf(copy);
    }

    public SkyIslandLocalPosition position() {
        return evaluations.getFirst().position();
    }

    public SkyIslandCommunityDisturbanceEvidence disturbanceEvidence() {
        return evaluations.getFirst().assemblyEvaluation().disturbanceEvidence();
    }

    public Optional<SkyIslandCommunityRealizationEvaluation> evaluation(
            SkyIslandCommunityArchetype community) {
        Objects.requireNonNull(community, "community");
        return evaluations.stream()
                .filter(evaluation -> evaluation.community() == community)
                .findFirst();
    }
}
