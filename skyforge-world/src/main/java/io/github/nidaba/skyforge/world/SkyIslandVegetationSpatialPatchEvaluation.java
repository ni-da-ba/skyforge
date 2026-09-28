package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Transparent deterministic spatial-patch evaluation retaining exact upstream ecological provenance.
 */
public record SkyIslandVegetationSpatialPatchEvaluation(
        SkyIslandVegetationFunctionalGroupEvaluation functionalGroupEvaluation,
        SkyIslandVegetationSpatialPatchProfile spatialProfile,
        double rawSignal,
        Optional<SkyIslandFunctionalGroupSpatialPatchState> state) {

    public SkyIslandVegetationSpatialPatchEvaluation {
        functionalGroupEvaluation =
                Objects.requireNonNull(functionalGroupEvaluation, "functionalGroupEvaluation");
        spatialProfile = Objects.requireNonNull(spatialProfile, "spatialProfile");
        if (!Double.isFinite(rawSignal) || rawSignal < -1.0 || rawSignal > 1.0) {
            throw new IllegalArgumentException("rawSignal must be finite and in [-1, 1]");
        }
        state = Objects.requireNonNull(state, "state");

        if (state.isPresent()) {
            if (functionalGroupEvaluation.structuralNicheSupport().isEmpty()) {
                throw new IllegalArgumentException(
                        "resolved spatial state requires resolved upstream functional-group support");
            }
            if (state.orElseThrow().spatialNicheSupport()
                    > functionalGroupEvaluation.structuralNicheSupport().orElseThrow() + 1.0e-12) {
                throw new IllegalArgumentException(
                        "spatial niche support must not exceed upstream functional-group support");
            }
        }
    }

    public boolean resolved() {
        return state.isPresent();
    }

    public SkyIslandLocalPosition position() {
        return functionalGroupEvaluation.position();
    }
}
