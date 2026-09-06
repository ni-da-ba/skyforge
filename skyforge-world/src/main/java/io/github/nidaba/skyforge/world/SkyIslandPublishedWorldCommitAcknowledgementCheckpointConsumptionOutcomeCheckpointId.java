package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * AUTH-0072 identity for one explicit checkpoint of an exact validated AUTH-0071
 * checkpoint-consumption outcome set.
 *
 * <p>The checkpoint revision versions the whole outcome-set checkpoint. It is distinct from
 * per-outcome acknowledgement sequence and from any backend storage revision.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId(
        int schemaVersion,
        long checkpointRevision,
        List<
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementId>
                acknowledgementIdentity) {

    public static final int SCHEMA_VERSION = 1;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported checkpoint-consumption outcome checkpoint identity schema: "
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
        ArrayList<
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId>
                seenTickets = new ArrayList<>();
        for (var acknowledgementId : acknowledgementIdentity) {
            long sequence = acknowledgementId.acknowledgementSequence();
            if (sequence <= previousSequence) {
                throw new IllegalArgumentException(
                        "outcome checkpoint identity must be in strict canonical acknowledgement-sequence order");
            }
            if (seenTickets.contains(acknowledgementId.ticketId())) {
                throw new IllegalArgumentException(
                        "outcome checkpoint identity contains duplicate I/O ticket");
            }
            previousSequence = sequence;
            seenTickets.add(acknowledgementId.ticketId());
        }
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId of(
                    long checkpointRevision,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                            acknowledgementSet) {
        Objects.requireNonNull(acknowledgementSet, "acknowledgementSet");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId(
                SCHEMA_VERSION,
                checkpointRevision,
                acknowledgementSet.acknowledgements().stream()
                        .map(
                                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                                        ::id)
                        .toList());
    }

    public String canonicalToken() {
        StringBuilder token =
                new StringBuilder(
                        String.format(
                                Locale.ROOT,
                                "sfackcpoutcp:v%d:%016x:%d",
                                schemaVersion,
                                checkpointRevision,
                                acknowledgementIdentity.size()));
        for (var acknowledgementId : acknowledgementIdentity) {
            token.append(':').append(acknowledgementId.canonicalToken());
        }
        return token.toString();
    }
}
