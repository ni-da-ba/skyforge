package io.github.nidaba.skyforge.world;

/**
 * Backend-neutral exact-position sampler for one vegetation functional-group spatial-patch stream.
 *
 * <p>The returned evaluation must describe the exact position requested by the caller. The
 * candidate-window composition seam validates that invariant before admission.
 */
@FunctionalInterface
public interface SkyIslandVegetationSpatialPatchSampler {

    /** Samples one exact island-local position. */
    SkyIslandVegetationSpatialPatchEvaluation sample(SkyIslandLocalPosition position);
}
