package io.github.nidaba.skyforge.world;

import java.util.Locale;
import java.util.Objects;

/** AUTH-0076 identity for one acknowledgement of one exact AUTH-0075 coordination ticket. */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId(
        int schemaVersion,
        long acknowledgementSequence,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                ticketId) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported outcome-checkpoint consumption acknowledgement schema: "
                            + schemaVersion);
        }
        if (acknowledgementSequence <= 0) {
            throw new IllegalArgumentException("acknowledgementSequence must be positive");
        }
        ticketId = Objects.requireNonNull(ticketId, "ticketId");
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId
                    of(
                            long acknowledgementSequence,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                                    ticket) {
        Objects.requireNonNull(ticket, "ticket");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId(
                SCHEMA_VERSION,
                acknowledgementSequence,
                ticket.id());
    }

    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfackcpoutack:v%d:%016x:%s",
                schemaVersion,
                acknowledgementSequence,
                ticketId.canonicalToken());
    }
}
