package io.github.nidaba.skyforge.world;

import java.util.Optional;

/**
 * Explicit backend-neutral policy for composing overlapping per-community structural realizations.
 */
@FunctionalInterface
public interface SkyIslandMultiCommunityRealizationCompositor {

    /**
     * Produces optional aggregate neutral structure from exact per-community realizations and
     * explicit coexistence weights.
     */
    Optional<SkyIslandCommunityStructureRealization> compose(
            SkyIslandMultiCommunityRealizationSet realizationSet,
            SkyIslandCommunityCoexistenceWeights coexistenceWeights);
}
