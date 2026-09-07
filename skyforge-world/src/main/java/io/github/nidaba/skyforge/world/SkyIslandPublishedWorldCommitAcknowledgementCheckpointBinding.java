package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0067 immutable consumer binding to one exact active AUTH-0066 acknowledgement checkpoint.
 *
 * <p>The binding retains the checkpoint capability and never refreshes itself to a later activation.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding(
        int schemaVersion,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpoint checkpoint) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported acknowledgement checkpoint binding schema: " + schemaVersion);
        }
        checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
    }

    public static SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding of(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpoint checkpoint) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding(
                SCHEMA_VERSION,
                checkpoint);
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointId checkpointId() {
        return checkpoint.id();
    }

    public String canonicalToken() {
        return "sfackcpbinding:v" + schemaVersion + ":" + checkpointId().canonicalToken();
    }

    public Optional<SkyIslandPublishedWorldCommitAcknowledgement> forTicket(
            SkyIslandPublishedWorldCommitTicketId ticketId) {
        return checkpoint.forTicket(ticketId);
    }
}
