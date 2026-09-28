package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Transparent functional-group structural-niche evaluation over one exact ecological composition.
 *
 * <p>Structural niche support is not occupancy, abundance, population density, or backend placement
 * authorization.
 */
public record SkyIslandVegetationFunctionalGroupEvaluation(
        SkyIslandVegetationFunctionalGroupProfile profile,
        SkyIslandMultiCommunityRealizationComposition composition,
        OptionalDouble structuralNicheSupport) {

    public SkyIslandVegetationFunctionalGroupEvaluation {
        profile = Objects.requireNonNull(profile, "profile");
        composition = Objects.requireNonNull(composition, "composition");
        structuralNicheSupport =
                Objects.requireNonNull(structuralNicheSupport, "structuralNicheSupport");
        structuralNicheSupport.ifPresent(value -> {
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException(
                        "structuralNicheSupport must be finite and normalized when present");
            }
        });
    }

    public boolean resolved() {
        return structuralNicheSupport.isPresent();
    }

    public SkyIslandLocalPosition position() {
        return composition.position();
    }
}
