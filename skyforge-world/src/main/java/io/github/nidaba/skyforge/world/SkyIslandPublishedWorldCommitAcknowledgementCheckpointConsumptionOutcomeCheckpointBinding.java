package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0073 immutable consumer binding to one exact active AUTH-0072 outcome checkpoint.
 *
 * <p>The binding retains the checkpoint capability and never refreshes itself to a later
 * activation.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinding(
        int schemaVersion,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                checkpoint) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinding {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported outcome-checkpoint binding schema: " + schemaVersion);
        }
        checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinding
                    of(
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                                    checkpoint) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinding(
                SCHEMA_VERSION,
                checkpoint);
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId
            checkpointId() {
        return checkpoint.id();
    }

    public String canonicalToken() {
        return "sfackcpoutbinding:v" + schemaVersion + ":" + checkpointId().canonicalToken();
    }

    public Optional<
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement>
            forTicket(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId
                            ticketId) {
        return checkpoint.forTicket(ticketId);
    }
}
