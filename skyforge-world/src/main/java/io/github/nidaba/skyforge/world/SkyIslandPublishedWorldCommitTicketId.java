package io.github.nidaba.skyforge.world;

import java.util.Locale;
import java.util.Objects;

/**
 * AUTH-0063 deterministic identity for one explicit downstream commit-coordination admission.
 *
 * <p>The ticket sequence is an explicit attempt/admission axis. It is not inferred and is not a
 * backend-success sequence.
 */
public record SkyIslandPublishedWorldCommitTicketId(
        int schemaVersion,
        long ticketSequence,
        SkyIslandPublishedWorldPreparedWorkId preparedWorkId) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitTicketId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported published-world commit-ticket identity schema: " + schemaVersion);
        }
        if (ticketSequence <= 0) {
            throw new IllegalArgumentException("ticketSequence must be positive");
        }
        preparedWorkId = Objects.requireNonNull(preparedWorkId, "preparedWorkId");
    }

    public static SkyIslandPublishedWorldCommitTicketId of(
            long ticketSequence,
            SkyIslandPublishedWorldPreparedWork preparedWork) {
        Objects.requireNonNull(preparedWork, "preparedWork");
        return new SkyIslandPublishedWorldCommitTicketId(
                SCHEMA_VERSION,
                ticketSequence,
                preparedWork.id());
    }

    public String canonicalToken() {
        return String.format(
                Locale.ROOT,
                "sfticket:v%d:%016x:%s",
                schemaVersion,
                ticketSequence,
                preparedWorkId.canonicalToken());
    }
}
