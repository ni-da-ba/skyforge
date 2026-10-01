package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic AUTH-0040 hard compatibility evaluator. */
public final class SkyIslandMaterialCompatibilityEvaluator {
    private SkyIslandMaterialCompatibilityEvaluator() {}

    public static SkyIslandMaterialCompatibilityAssessment evaluate(
            SkyIslandMaterialBindingRequest request,
            SkyIslandMaterialCapabilityProfile profile) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(profile, "profile");

        SkyIslandMaterialCapabilityConstraintSet constraints =
                SkyIslandMaterialCapabilityPolicy.constraints(request);
        List<SkyIslandMaterialCapabilityEvaluation> evaluations =
                new ArrayList<>(constraints.requirements().size());

        for (SkyIslandMaterialCapabilityRequirement requirement :
                constraints.requirements()) {
            double advertised = profile.capability(requirement.capability());
            double margin = advertised - requirement.minimum();
            evaluations.add(
                    new SkyIslandMaterialCapabilityEvaluation(
                            requirement.capability(),
                            requirement.minimum(),
                            advertised,
                            margin,
                            margin >= -1.0e-12));
        }

        boolean compatible =
                evaluations.stream()
                        .allMatch(SkyIslandMaterialCapabilityEvaluation::satisfied);
        return new SkyIslandMaterialCompatibilityAssessment(
                constraints, profile, evaluations, compatible);
    }
}
