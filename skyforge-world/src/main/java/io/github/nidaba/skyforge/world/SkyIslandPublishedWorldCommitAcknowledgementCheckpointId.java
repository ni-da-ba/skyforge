package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * AUTH-0066 identity for one explicit checkpoint of an exact validated acknowledgement set.
 *
 * <p>The checkpoint revision versions the whole set checkpoint. It is distinct from acknowledgement
 * sequence, ticket sequence, snapshot revision, and any backend storage revision.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointId(
        int schemaVersion,
        long checkpointRevision,
        List<SkyIslandPublishedWorldCommitAcknowledgementId> acknowledgementIdentity) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported published-world acknowledgement checkpoint identity schema: "
                            + schemaVersion);
        }
        if (checkpointRevision <= 0) {
            throw new IllegalArgumentException("checkpointRevision must be positive");
        }

        acknowledgementIdentity =
                List.copyOf(
                        Objects.requireNonNull(
                                acknowledgementIdentity,
                                "acknowledgementIdentity"));
        if (acknowledgementIdentity.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("acknowledgementIdentity contains null");
        }

        long previousSequence = 0L;
        ArrayList<SkyIslandPublishedWorldCommitTicketId> seenTickets = new ArrayList<>();
        for (SkyIslandPublishedWorldCommitAcknowledgementId acknowledgementId :
                acknowledgementIdentity) {
            long sequence = acknowledgementId.acknowledgementSequence();
            if (sequence <= previousSequence) {
                throw new IllegalArgumentException(
                        "acknowledgement checkpoint identity must be in strict canonical sequence order");
            }
            if (seenTickets.contains(acknowledgementId.ticketId())) {
                throw new IllegalArgumentException(
                        "acknowledgement checkpoint identity contains duplicate ticket");
            }
            previousSequence = sequence;
            seenTickets.add(acknowledgementId.ticketId());
        }
    }

    public static SkyIslandPublishedWorldCommitAcknowledgementCheckpointId of(
            long checkpointRevision,
            SkyIslandPublishedWorldCommitAcknowledgementSet acknowledgementSet) {
        Objects.requireNonNull(acknowledgementSet, "acknowledgementSet");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointId(
                SCHEMA_VERSION,
                checkpointRevision,
                acknowledgementSet.acknowledgements().stream()
                        .map(SkyIslandPublishedWorldCommitAcknowledgement::id)
                        .toList());
    }

    public String canonicalToken() {
        StringBuilder token =
                new StringBuilder(
                        String.format(
                                Locale.ROOT,
                                "sfackcp:v%d:%016x:%d",
                                schemaVersion,
                                checkpointRevision,
                                acknowledgementIdentity.size()));
        for (SkyIslandPublishedWorldCommitAcknowledgementId acknowledgementId :
                acknowledgementIdentity) {
            token.append(':').append(acknowledgementId.canonicalToken());
        }
        return token.toString();
    }
}
