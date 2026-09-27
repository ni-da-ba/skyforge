package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * Disturbance evidence bound to one exact community-assembly evidence context.
 *
 * <p>An empty latest-disturbance value means that no explicit disturbance evidence was supplied. It
 * must not be interpreted as pristine habitat, zero disturbance, or infinite recovery time.
 */
public record SkyIslandCommunityDisturbanceEvidence(
        SkyIslandCommunityAssemblyEvidence assemblyEvidence,
        Optional<SkyIslandCommunityDisturbanceEvent> latestDisturbance) {

    public SkyIslandCommunityDisturbanceEvidence {
        assemblyEvidence = Objects.requireNonNull(assemblyEvidence, "assemblyEvidence");
        latestDisturbance = Objects.requireNonNull(latestDisturbance, "latestDisturbance");
    }

    public static SkyIslandCommunityDisturbanceEvidence unresolved(
            SkyIslandCommunityAssemblyEvidence assemblyEvidence) {
        return new SkyIslandCommunityDisturbanceEvidence(
                assemblyEvidence,
                Optional.empty());
    }

    public static SkyIslandCommunityDisturbanceEvidence of(
            SkyIslandCommunityAssemblyEvidence assemblyEvidence,
            SkyIslandCommunityDisturbanceEvent latestDisturbance) {
        return new SkyIslandCommunityDisturbanceEvidence(
                assemblyEvidence,
                Optional.of(Objects.requireNonNull(latestDisturbance, "latestDisturbance")));
    }

    public boolean hasDisturbanceEvidence() {
        return latestDisturbance.isPresent();
    }
}
