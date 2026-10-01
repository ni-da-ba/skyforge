package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0066 immutable checkpoint binding one explicit checkpoint identity to one exact validated
 * acknowledgement set.
 *
 * <p>A checkpoint is a persistence/replication handoff capability only. It does not claim that any
 * backend stored, replicated, or durably committed the set.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpoint(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointId id,
        SkyIslandPublishedWorldCommitAcknowledgementSet acknowledgementSet) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpoint {
        id = Objects.requireNonNull(id, "id");
        acknowledgementSet = Objects.requireNonNull(acknowledgementSet, "acknowledgementSet");

        var expectedIdentity =
                acknowledgementSet.acknowledgements().stream()
                        .map(SkyIslandPublishedWorldCommitAcknowledgement::id)
                        .toList();
        if (!id.acknowledgementIdentity().equals(expectedIdentity)) {
            throw new IllegalArgumentException(
                    "acknowledgement checkpoint identity does not bind the exact acknowledgement set");
        }
    }

    public static SkyIslandPublishedWorldCommitAcknowledgementCheckpoint of(
            long checkpointRevision,
            SkyIslandPublishedWorldCommitAcknowledgementSet acknowledgementSet) {
        Objects.requireNonNull(acknowledgementSet, "acknowledgementSet");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpoint(
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointId.of(
                        checkpointRevision,
                        acknowledgementSet),
                acknowledgementSet);
    }

    public int size() {
        return acknowledgementSet.size();
    }

    public boolean isEmpty() {
        return acknowledgementSet.isEmpty();
    }

    public Optional<SkyIslandPublishedWorldCommitAcknowledgement> forTicket(
            SkyIslandPublishedWorldCommitTicketId ticketId) {
        return acknowledgementSet.forTicket(ticketId);
    }
}
