package io.github.nidaba.skyforge.world;

import java.util.OptionalDouble;

/**
 * Explicit composition policy for local community suitability and resolved assembly factors.
 *
 * <p>A combiner produces profile-specific assembly support, not occupancy probability.
 */
@FunctionalInterface
public interface SkyIslandCommunityAssemblyCombiner {

    /**
     * Combines normalized local suitability, optional dispersal accessibility, and optional
     * succession affinity into optional normalized assembly support.
     */
    OptionalDouble support(
            double localSuitability,
            OptionalDouble dispersalAccessibility,
            OptionalDouble successionAffinity);
}
