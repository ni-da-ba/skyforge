package io.github.nidaba.skyforge.world;

import java.util.Locale;
import java.util.Objects;

/** AUTH-0070 identity for one acknowledgement of one exact AUTH-0069 I/O-admission ticket. */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementId(
        int schemaVersion,
        long acknowledgementSequence,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId ticketId) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported checkpoint-consumption acknowledgement schema: " + schemaVersion);
        }
        if (acknowledgementSequence <= 0) {
            throw new IllegalArgumentException("acknowledgementSequence must be positive");
        }
        ticketId = Objects.requireNonNull(ticketId, "ticketId");
    }

    public static SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementId of(
            long acknowledgementSequence,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementId(
                SCHEMA_VERSION,
                acknowledgementSequence,
                ticket.id());
    }

    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfackcpack:v%d:%016x:%s",
                schemaVersion,
                acknowledgementSequence,
                ticketId.canonicalToken());
    }
}
