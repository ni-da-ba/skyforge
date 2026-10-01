package io.github.nidaba.skyforge.recipes.skyisland.group;

/**
 * Provider-neutral morphology intent attached to one planned group member.
 *
 * <p>The group planner treats morphology as opaque semantic intent. It never inspects built-in
 * morphology enums and therefore remains compatible with external providers and future generalized
 * provider mixtures.
 */
public sealed interface SkyIslandMorphologySpec
        permits ProviderMorphologySpec, ProviderBlendMorphologySpec {
    /** Independent accepted bounded-detail amplitude in [0, 1]. */
    double detailAmplitude();

    /** Independent organized secondary-morphology amplitude in [0, 1]. */
    double secondaryMorphologyAmplitude();

    /** Stable human-readable morphology identity for evidence and provenance. */
    String stableIdentifier();
}
