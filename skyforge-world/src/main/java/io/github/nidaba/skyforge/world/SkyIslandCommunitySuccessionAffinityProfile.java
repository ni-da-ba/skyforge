package io.github.nidaba.skyforge.world;

import java.util.OptionalDouble;

/**
 * Profile-specific interpretation of resolved succession state for one community assembly policy.
 *
 * <p>The returned affinity is an assembly-support factor only. It does not assert occupancy,
 * abundance, persistence, or a universal effect of disturbance.
 */
@FunctionalInterface
public interface SkyIslandCommunitySuccessionAffinityProfile {

    /**
     * Returns normalized succession affinity in {@code [0, 1]} when succession evidence resolves.
     *
     * <p>Unresolved succession state must remain {@link OptionalDouble#empty()}.
     */
    OptionalDouble affinity(SkyIslandCommunitySuccessionAssessment assessment);
}
