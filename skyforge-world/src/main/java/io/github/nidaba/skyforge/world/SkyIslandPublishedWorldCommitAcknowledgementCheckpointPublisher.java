package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0066 backend-neutral checkpoint publication seam.
 *
 * <p>Publishing creates an immutable checkpoint capability. It performs no persistence, replication,
 * I/O, or backend mutation.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointPublisher {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpoint publish(
            SkyIslandPublishedWorldCommitAcknowledgementSet acknowledgementSet,
            long checkpointRevision) {
        Objects.requireNonNull(acknowledgementSet, "acknowledgementSet");
        return SkyIslandPublishedWorldCommitAcknowledgementCheckpoint.of(
                checkpointRevision,
                acknowledgementSet);
    }
}
