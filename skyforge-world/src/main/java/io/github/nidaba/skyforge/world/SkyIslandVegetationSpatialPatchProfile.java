package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicit deterministic spatial-realization policy for one functional-group niche evaluation.
 *
 * <p>The signal profile and transform are supplied explicitly. No functional-group identity implies
 * a seed, namespace, scale, or patch response.
 */
public record SkyIslandVegetationSpatialPatchProfile(
        SkyIslandEcologicalPatchSignalProfile signalProfile,
        SkyIslandFunctionalGroupSpatialPatchTransform transform) {

    public SkyIslandVegetationSpatialPatchProfile {
        signalProfile = Objects.requireNonNull(signalProfile, "signalProfile");
        transform = Objects.requireNonNull(transform, "transform");
    }

    /** Evaluates this spatial policy at the exact local position retained by upstream ecology. */
    public SkyIslandVegetationSpatialPatchEvaluation evaluate(
            SkyIslandVegetationFunctionalGroupEvaluation functionalGroupEvaluation) {
        Objects.requireNonNull(functionalGroupEvaluation, "functionalGroupEvaluation");
        double rawSignal = signalProfile.sample(functionalGroupEvaluation.position());
        Optional<SkyIslandFunctionalGroupSpatialPatchState> state =
                Objects.requireNonNull(
                        transform.spatialize(functionalGroupEvaluation, rawSignal),
                        "spatial patch transform result");
        return new SkyIslandVegetationSpatialPatchEvaluation(
                functionalGroupEvaluation,
                this,
                rawSignal,
                state);
    }
}
