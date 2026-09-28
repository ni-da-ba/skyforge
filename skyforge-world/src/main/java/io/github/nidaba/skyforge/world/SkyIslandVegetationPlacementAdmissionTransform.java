package io.github.nidaba.skyforge.world;

import java.util.Optional;

/**
 * Backend-neutral deterministic admission policy for one vegetation realization candidate.
 */
@FunctionalInterface
public interface SkyIslandVegetationPlacementAdmissionTransform {

    /**
     * Evaluates one exact candidate against one exact spatial ecological evaluation.
     *
     * <p>Unresolved upstream ecology must remain unresolved.
     */
    Optional<SkyIslandVegetationPlacementAdmissionState> admit(
            SkyIslandVegetationSpatialPatchEvaluation spatialEvaluation,
            SkyIslandEcologicalPlacementCandidate candidate);
}
