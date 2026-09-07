package io.github.nidaba.skyforge.world;

import java.util.Locale;
import java.util.Objects;

/** AUTH-0075 identity for one admission of one exact AUTH-0074 prepared consumption. */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId(
        int schemaVersion,
        long ticketSequence,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                preparedConsumptionId) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported outcome-checkpoint consumption ticket schema: " + schemaVersion);
        }
        if (ticketSequence <= 0) {
            throw new IllegalArgumentException("ticketSequence must be positive");
        }
        preparedConsumptionId =
                Objects.requireNonNull(preparedConsumptionId, "preparedConsumptionId");
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                    of(
                            long ticketSequence,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption
                                    preparedConsumption) {
        Objects.requireNonNull(preparedConsumption, "preparedConsumption");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId(
                SCHEMA_VERSION,
                ticketSequence,
                preparedConsumption.id());
    }

    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfackcpoutticket:v%d:%016x:%s",
                schemaVersion,
                ticketSequence,
                preparedConsumptionId.canonicalToken());
    }
}
