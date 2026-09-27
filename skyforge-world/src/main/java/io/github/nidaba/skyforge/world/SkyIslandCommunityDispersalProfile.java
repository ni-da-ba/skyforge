package io.github.nidaba.skyforge.world;

import java.util.OptionalDouble;

/**
 * Backend-neutral dispersal response over raw community-assembly isolation evidence.
 *
 * <p>A profile interprets geometry for one explicitly chosen dispersal strategy. It does not assert
 * occupancy, colonization, persistence, population size, or species identity.
 */
@FunctionalInterface
public interface SkyIslandCommunityDispersalProfile {

    /**
     * Returns normalized assembly accessibility in {@code [0, 1]} when neighbor evidence exists.
     *
     * <p>Missing nearest-neighbor evidence must remain {@link OptionalDouble#empty()} rather than
     * being reinterpreted as infinite isolation or complete accessibility.
     */
    OptionalDouble accessibility(SkyIslandCommunityAssemblyEvidence evidence);
}
