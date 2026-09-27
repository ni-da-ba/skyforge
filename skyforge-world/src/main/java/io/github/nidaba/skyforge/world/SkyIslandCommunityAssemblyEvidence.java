package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandIdentity;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Backend-neutral assembly evidence for one exact authored-realization island association.
 *
 * <p>This record binds local structural community suitability to raw regional isolation evidence.
 * It does not convert distance into dispersal, colonization, occupancy, abundance, persistence, or
 * population pressure.
 */
public record SkyIslandCommunityAssemblyEvidence(
        SkyIslandCommunitySuitabilityFieldSet communitySuitability,
        SkyIslandRegionalIsolationEntry isolationEntry) {

    public SkyIslandCommunityAssemblyEvidence {
        communitySuitability =
                Objects.requireNonNull(communitySuitability, "communitySuitability");
        isolationEntry = Objects.requireNonNull(isolationEntry, "isolationEntry");

        if (!communitySuitability.descriptor()
                .equals(isolationEntry.association().authoredDescriptor())) {
            throw new IllegalArgumentException(
                    "community suitability and isolation evidence must use the exact authored descriptor");
        }
    }

    public SkyIslandAuthoredRealizationAssociation association() {
        return isolationEntry.association();
    }

    public SkyIslandIdentity authoredIdentity() {
        return association().authoredIdentity();
    }

    public SkyIslandWorldVolumeId realizedVolumeId() {
        return association().realizedVolumeId();
    }

    public boolean hasNeighborEvidence() {
        return isolationEntry.hasNeighbor();
    }

    public Optional<SkyIslandRegionalIsolationNeighbor> nearestNeighbor() {
        return isolationEntry.nearestNeighbor();
    }

    public OptionalDouble nearestCenterDistance() {
        return nearestNeighbor().isEmpty()
                ? OptionalDouble.empty()
                : OptionalDouble.of(nearestNeighbor().orElseThrow().centerDistance());
    }

    public OptionalDouble nearestNominalRadialGap() {
        return nearestNeighbor().isEmpty()
                ? OptionalDouble.empty()
                : OptionalDouble.of(nearestNeighbor().orElseThrow().nominalRadialGap());
    }
}
