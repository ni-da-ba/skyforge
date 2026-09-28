package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Explicit backend-neutral structural-niche policy for one vegetation functional group.
 *
 * <p>The group identity does not imply any particular affinity profile. Both affinity and transform
 * are supplied explicitly.
 */
public record SkyIslandVegetationFunctionalGroupProfile(
        SkyIslandVegetationFunctionalGroup functionalGroup,
        SkyIslandFunctionalGroupStructuralAffinity structuralAffinity,
        SkyIslandFunctionalGroupNicheTransform transform) {

    public SkyIslandVegetationFunctionalGroupProfile {
        functionalGroup = Objects.requireNonNull(functionalGroup, "functionalGroup");
        structuralAffinity = Objects.requireNonNull(structuralAffinity, "structuralAffinity");
        transform = Objects.requireNonNull(transform, "transform");
    }

    /** Evaluates this profile against one exact multi-community realization composition. */
    public SkyIslandVegetationFunctionalGroupEvaluation evaluate(
            SkyIslandMultiCommunityRealizationComposition composition) {
        Objects.requireNonNull(composition, "composition");
        OptionalDouble support = Objects.requireNonNull(
                transform.support(composition.aggregateRealization(), structuralAffinity),
                "functional-group transform result");
        support.ifPresent(value -> {
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException(
                        "functional-group structural niche support must be finite and normalized");
            }
        });
        return new SkyIslandVegetationFunctionalGroupEvaluation(
                this,
                composition,
                support);
    }
}
