package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0078 immutable checkpoint binding one explicit outcome-checkpoint identity to one exact
 * validated AUTH-0077 outcome acknowledgement set.
 *
 * <p>The checkpoint is an audit/persistence handoff capability only. It does not claim storage,
 * replication, or durability.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId
                id,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                acknowledgementSet) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint {
        id = Objects.requireNonNull(id, "id");
        acknowledgementSet = Objects.requireNonNull(acknowledgementSet, "acknowledgementSet");

        var expectedIdentity =
                acknowledgementSet.acknowledgements().stream()
                        .map(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                                        ::id)
                        .toList();
        if (!id.acknowledgementIdentity().equals(expectedIdentity)) {
            throw new IllegalArgumentException(
                    "outcome-checkpoint consumption outcome checkpoint identity does not bind the exact acknowledgement set");
        }
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                    of(
                            long checkpointRevision,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                                    acknowledgementSet) {
        Objects.requireNonNull(acknowledgementSet, "acknowledgementSet");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId
                        .of(checkpointRevision, acknowledgementSet),
                acknowledgementSet);
    }

    public int size() {
        return acknowledgementSet.size();
    }

    public boolean isEmpty() {
        return acknowledgementSet.isEmpty();
    }

    public Optional<
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement>
            forTicket(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                            ticketId) {
        return acknowledgementSet.forTicket(ticketId);
    }
}
