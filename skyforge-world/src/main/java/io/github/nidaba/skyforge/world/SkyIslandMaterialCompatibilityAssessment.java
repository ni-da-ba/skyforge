package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** Auditable AUTH-0040 pass/fail assessment of one capability profile against one request. */
public record SkyIslandMaterialCompatibilityAssessment(
        SkyIslandMaterialCapabilityConstraintSet constraints,
        SkyIslandMaterialCapabilityProfile profile,
        List<SkyIslandMaterialCapabilityEvaluation> evaluations,
        boolean compatible) {

    public SkyIslandMaterialCompatibilityAssessment {
        constraints = Objects.requireNonNull(constraints, "constraints");
        profile = Objects.requireNonNull(profile, "profile");
        evaluations = List.copyOf(evaluations);
        if (evaluations.size() != constraints.requirements().size()) {
            throw new IllegalArgumentException(
                    "compatibility assessment must evaluate every hard requirement");
        }
        for (int i = 0; i < evaluations.size(); i++) {
            SkyIslandMaterialCapabilityEvaluation evaluation =
                    Objects.requireNonNull(evaluations.get(i), "capability evaluation");
            SkyIslandMaterialCapabilityRequirement requirement =
                    constraints.requirements().get(i);
            if (evaluation.capability() != requirement.capability()
                    || Math.abs(evaluation.requiredMinimum() - requirement.minimum())
                            > 1.0e-12
                    || Math.abs(
                                    evaluation.advertised()
                                            - profile.capability(evaluation.capability()))
                            > 1.0e-12) {
                throw new IllegalArgumentException(
                        "compatibility assessment must retain requirement/profile provenance");
            }
        }
        boolean allSatisfied =
                evaluations.stream().allMatch(SkyIslandMaterialCapabilityEvaluation::satisfied);
        if (compatible != allSatisfied) {
            throw new IllegalArgumentException(
                    "compatibility assessment result must equal all hard requirement results");
        }
    }

    public double minimumMargin() {
        return evaluations.stream()
                .mapToDouble(SkyIslandMaterialCapabilityEvaluation::margin)
                .min()
                .orElseThrow();
    }

    public int failedRequirementCount() {
        return (int)
                evaluations.stream()
                        .filter(evaluation -> !evaluation.satisfied())
                        .count();
    }
}
