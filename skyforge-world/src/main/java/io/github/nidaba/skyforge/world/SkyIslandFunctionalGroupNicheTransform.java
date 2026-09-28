package io.github.nidaba.skyforge.world;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Backend-neutral transform from optional aggregate ecological structure to functional-group
 * structural niche support.
 */
@FunctionalInterface
public interface SkyIslandFunctionalGroupNicheTransform {

    /**
     * Returns normalized structural niche support in {@code [0, 1]} when aggregate structure
     * resolves. Unresolved aggregate realization must remain unresolved.
     */
    OptionalDouble support(
            Optional<SkyIslandCommunityStructureRealization> aggregateRealization,
            SkyIslandFunctionalGroupStructuralAffinity affinity);
}
