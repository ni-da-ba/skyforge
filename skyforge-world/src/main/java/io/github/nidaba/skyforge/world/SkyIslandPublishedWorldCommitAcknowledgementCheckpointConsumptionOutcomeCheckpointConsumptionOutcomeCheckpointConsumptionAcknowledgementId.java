package io.github.nidaba.skyforge.world;

import java.util.Locale;
import java.util.Objects;

/** AUTH-0082 identity for one acknowledgement of one exact AUTH-0081 coordination ticket. */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId(
        int schemaVersion,
        long acknowledgementSequence,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                ticketId) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId {
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
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId
                    of(
                            long acknowledgementSequence,
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                                    ticket) {
        Objects.requireNonNull(ticket, "ticket");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId(
                SCHEMA_VERSION,
                acknowledgementSequence,
                ticket.id());
    }

    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfackcpoutcpoutack:v%d:%016x:%s",
                schemaVersion,
                acknowledgementSequence,
                ticketId.canonicalToken());
    }
}
