package io.github.nidaba.skyforge.world;

import java.util.Locale;
import java.util.Objects;

/**
 * AUTH-0069 deterministic identity for one explicit downstream I/O-coordination admission.
 *
 * <p>The ticket sequence is an explicit admission axis. It is not a storage attempt result,
 * persistence version, replication generation, or durability marker.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId(
        int schemaVersion,
        long ticketSequence,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId
                preparedConsumptionId) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported checkpoint-consumption ticket identity schema: " + schemaVersion);
        }
        if (ticketSequence <= 0) {
            throw new IllegalArgumentException("ticketSequence must be positive");
        }
        preparedConsumptionId =
                Objects.requireNonNull(preparedConsumptionId, "preparedConsumptionId");
    }

    public static SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId of(
            long ticketSequence,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption
                    preparedConsumption) {
        Objects.requireNonNull(preparedConsumption, "preparedConsumption");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId(
                SCHEMA_VERSION,
                ticketSequence,
                preparedConsumption.id());
    }

    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfackcpticket:v%d:%016x:%s",
                schemaVersion,
                ticketSequence,
                preparedConsumptionId.canonicalToken());
    }
}
