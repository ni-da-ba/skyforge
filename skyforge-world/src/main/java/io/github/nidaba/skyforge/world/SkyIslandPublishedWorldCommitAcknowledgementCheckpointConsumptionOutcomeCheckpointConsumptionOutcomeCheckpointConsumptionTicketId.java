package io.github.nidaba.skyforge.world;

import java.util.Locale;
import java.util.Objects;

/** AUTH-0081 identity for one admission of one exact AUTH-0080 prepared consumption. */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId(
        int schemaVersion,
        long ticketSequence,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                preparedConsumptionId) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported outcome-checkpoint-consumption outcome-checkpoint consumption ticket schema: " + schemaVersion);
        }
        if (ticketSequence <= 0) {
            throw new IllegalArgumentException("ticketSequence must be positive");
        }
        preparedConsumptionId =
                Objects.requireNonNull(preparedConsumptionId, "preparedConsumptionId");
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                    of(
                            long ticketSequence,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumption
                                    preparedConsumption) {
        Objects.requireNonNull(preparedConsumption, "preparedConsumption");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId(
                SCHEMA_VERSION,
                ticketSequence,
                preparedConsumption.id());
    }

    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfackcpoutcpoutticket:v%d:%016x:%s",
                schemaVersion,
                ticketSequence,
                preparedConsumptionId.canonicalToken());
    }
}
