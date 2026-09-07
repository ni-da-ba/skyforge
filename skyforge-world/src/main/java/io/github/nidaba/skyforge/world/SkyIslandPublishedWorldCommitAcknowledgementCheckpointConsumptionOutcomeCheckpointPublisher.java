package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0072 backend-neutral outcome-checkpoint publication seam.
 *
 * <p>Publishing creates an immutable handoff capability. It performs no persistence, replication,
 * file/network I/O, or backend mutation.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPublisher {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
            publish(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                            acknowledgementSet,
                    long checkpointRevision) {
        Objects.requireNonNull(acknowledgementSet, "acknowledgementSet");
        return SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                .of(checkpointRevision, acknowledgementSet);
    }
}
