package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0072 immutable checkpoint binding one explicit outcome-checkpoint identity to one exact
 * validated AUTH-0071 outcome acknowledgement set.
 *
 * <p>The checkpoint is an audit/persistence handoff capability only. It does not claim storage,
 * replication, or durability.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId id,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                acknowledgementSet) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint {
        id = Objects.requireNonNull(id, "id");
        acknowledgementSet = Objects.requireNonNull(acknowledgementSet, "acknowledgementSet");

        var expectedIdentity =
                acknowledgementSet.acknowledgements().stream()
                        .map(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                                        ::id)
                        .toList();
        if (!id.acknowledgementIdentity().equals(expectedIdentity)) {
            throw new IllegalArgumentException(
                    "outcome checkpoint identity does not bind the exact outcome acknowledgement set");
        }
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint of(
                    long checkpointRevision,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                            acknowledgementSet) {
        Objects.requireNonNull(acknowledgementSet, "acknowledgementSet");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId
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
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement>
            forTicket(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId
                            ticketId) {
        return acknowledgementSet.forTicket(ticketId);
    }
}
