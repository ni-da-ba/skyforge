package io.github.nidaba.skyforge.world;

import java.util.Optional;

/**
 * Explicit backend-neutral transform from community assembly support to neutral visible structure.
 *
 * <p>A transform may only emit structure when its required assembly evidence resolves. It does not
 * choose a community winner or map structure to concrete backend content.
 */
@FunctionalInterface
public interface SkyIslandCommunityRealizationTransform {

    /**
     * Produces optional structural realization from one exact assembly evaluation and capacity
     * profile.
     */
    Optional<SkyIslandCommunityStructureRealization> realize(
            SkyIslandCommunityAssemblyEvaluation assemblyEvaluation,
            SkyIslandCommunityStructureCapacity capacity);
}
