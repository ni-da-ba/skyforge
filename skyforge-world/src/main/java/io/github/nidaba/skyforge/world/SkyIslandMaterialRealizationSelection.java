package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/** AUTH-0044 one-point backend-neutral semantic material realization. */
public record SkyIslandMaterialRealizationSelection(
        SkyIslandMaterialExpressionSample expressionSample,
        SkyIslandMaterialExpressionAllocation structuralWinner,
        SkyIslandMaterialExpressionAllocation winner,
        int activeConditionedClaims) {

    public SkyIslandMaterialRealizationSelection {
        expressionSample = Objects.requireNonNull(expressionSample, "expressionSample");
        if (activeConditionedClaims < 0) {
            throw new IllegalArgumentException(
                    "active conditioned-claim count cannot be negative");
        }

        if (!expressionSample.source().materialPresent()) {
            if (structuralWinner != null || winner != null || activeConditionedClaims != 0) {
                throw new IllegalArgumentException(
                        "non-material realization cannot contain a semantic winner");
            }
        } else {
            structuralWinner =
                    Objects.requireNonNull(structuralWinner, "structuralWinner");
            winner = Objects.requireNonNull(winner, "winner");
            if (!expressionSample.allocations().contains(structuralWinner)
                    || !expressionSample.allocations().contains(winner)) {
                throw new IllegalArgumentException(
                        "material realization winners must come from the AUTH-0043 sample");
            }
            if (structuralWinner.mode()
                    != SkyIslandMaterialExpressionMode.STRUCTURAL_MATRIX_SHARE) {
                throw new IllegalArgumentException(
                        "structural winner must be a structural matrix allocation");
            }
            boolean conditionedWinner =
                    winner.mode()
                            == SkyIslandMaterialExpressionMode.CONDITIONED_EXPRESSION_CLAIM;
            if (conditionedWinner != (activeConditionedClaims > 0)) {
                throw new IllegalArgumentException(
                        "conditioned winner state must match active conditioned claims");
            }
            if (!conditionedWinner && !winner.equals(structuralWinner)) {
                throw new IllegalArgumentException(
                        "without an active conditioned claim, final winner must equal structural winner");
            }
        }
    }

    public boolean materialPresent() {
        return winner != null;
    }

    public boolean conditionedWinner() {
        return winner != null
                && winner.mode()
                        == SkyIslandMaterialExpressionMode.CONDITIONED_EXPRESSION_CLAIM;
    }

    public Optional<SkyIslandSemanticMaterialPaletteRole> winnerRole() {
        return winner == null ? Optional.empty() : Optional.of(winner.role());
    }

    public Optional<SkyIslandSemanticPaletteBindingKey> winnerBindingKey() {
        return winner == null ? Optional.empty() : Optional.of(winner.bindingKey());
    }
}
