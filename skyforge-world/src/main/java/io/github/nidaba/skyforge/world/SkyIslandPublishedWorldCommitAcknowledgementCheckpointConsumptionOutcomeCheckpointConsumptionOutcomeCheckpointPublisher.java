package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0078 backend-neutral outcome-checkpoint publication seam.
 *
 * <p>Publishing creates an immutable handoff capability. It performs no persistence, replication,
 * file/network I/O, or backend mutation.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPublisher {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
            publish(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                            acknowledgementSet,
                    long checkpointRevision) {
        Objects.requireNonNull(acknowledgementSet, "acknowledgementSet");
        return SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                .of(checkpointRevision, acknowledgementSet);
    }
}
