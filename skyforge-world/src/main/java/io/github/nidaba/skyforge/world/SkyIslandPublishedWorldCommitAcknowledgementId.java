package io.github.nidaba.skyforge.world;

import java.util.Locale;
import java.util.Objects;

/** AUTH-0064 identity for one acknowledgement of one exact AUTH-0063 commit ticket. */
public record SkyIslandPublishedWorldCommitAcknowledgementId(
        int schemaVersion,
        long acknowledgementSequence,
        SkyIslandPublishedWorldCommitTicketId ticketId) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported published-world commit acknowledgement schema: " + schemaVersion);
        }
        if (acknowledgementSequence <= 0) {
            throw new IllegalArgumentException("acknowledgementSequence must be positive");
        }
        ticketId = Objects.requireNonNull(ticketId, "ticketId");
    }

    public static SkyIslandPublishedWorldCommitAcknowledgementId of(
            long acknowledgementSequence,
            SkyIslandPublishedWorldCommitTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        return new SkyIslandPublishedWorldCommitAcknowledgementId(
                SCHEMA_VERSION,
                acknowledgementSequence,
                ticket.id());
    }

    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfack:v%d:%016x:%s",
                schemaVersion,
                acknowledgementSequence,
                ticketId.canonicalToken());
    }
}
