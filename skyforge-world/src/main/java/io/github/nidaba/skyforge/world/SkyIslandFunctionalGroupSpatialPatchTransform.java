package io.github.nidaba.skyforge.world;

import java.util.Optional;

/**
 * Backend-neutral deterministic transform from functional-group niche support and ecological
 * patchiness to local spatial-patch support.
 */
@FunctionalInterface
public interface SkyIslandFunctionalGroupSpatialPatchTransform {

    /**
     * Applies one raw bounded kernel signal sample in {@code [-1, 1]}.
     *
     * <p>Unresolved upstream ecology must remain unresolved.
     */
    Optional<SkyIslandFunctionalGroupSpatialPatchState> spatialize(
            SkyIslandVegetationFunctionalGroupEvaluation evaluation,
            double rawSignal);
}
