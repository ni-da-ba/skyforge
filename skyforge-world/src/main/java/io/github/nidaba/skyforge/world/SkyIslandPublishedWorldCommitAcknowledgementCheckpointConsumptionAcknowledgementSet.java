package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0071 immutable admission set for exact AUTH-0070 checkpoint-consumption outcome
 * acknowledgements.
 *
 * <p>The set rejects acknowledgement replay, duplicate/contradictory outcomes for one exact
 * AUTH-0069 I/O ticket, and acknowledgement-sequence reuse. It never replaces an admitted outcome
 * and never selects a newest winner.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet {
    public static final int SCHEMA_VERSION = 1;

    private static final Comparator<
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement>
            CANONICAL_ORDER =
                    Comparator.comparingLong(
                            acknowledgement -> acknowledgement.id().acknowledgementSequence());

    private final int schemaVersion;
    private final List<
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement>
            acknowledgements;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet(
            int schemaVersion,
            List<
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement>
                    acknowledgements) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported checkpoint-consumption acknowledgement-set schema: "
                            + schemaVersion);
        }
        Objects.requireNonNull(acknowledgements, "acknowledgements");
        if (acknowledgements.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("acknowledgements contains null");
        }

        ArrayList<
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement>
                canonical = new ArrayList<>(acknowledgements);
        canonical.sort(CANONICAL_ORDER);

        for (int i = 0; i < canonical.size(); i++) {
            var current = canonical.get(i);
            for (int j = 0; j < i; j++) {
                var previous = canonical.get(j);

                if (previous.id().acknowledgementSequence()
                        == current.id().acknowledgementSequence()) {
                    throw new IllegalArgumentException(
                            "checkpoint-consumption acknowledgement sequence reuse is not admitted: "
                                    + current.id().acknowledgementSequence());
                }
                if (previous.ticket().id().equals(current.ticket().id())) {
                    throw new IllegalArgumentException(
                            "checkpoint-consumption I/O ticket already has an admitted outcome acknowledgement: "
                                    + current.ticket().id().canonicalToken());
                }
            }
        }

        this.schemaVersion = schemaVersion;
        this.acknowledgements = List.copyOf(canonical);
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
                    empty() {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet(
                SCHEMA_VERSION,
                List.of());
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet of(
                    List<
                                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement>
                            acknowledgements) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet(
                SCHEMA_VERSION,
                acknowledgements);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public List<SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement>
            acknowledgements() {
        return acknowledgements;
    }

    public int size() {
        return acknowledgements.size();
    }

    public boolean isEmpty() {
        return acknowledgements.isEmpty();
    }

    public Optional<
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement>
            forTicket(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId
                            ticketId) {
        Objects.requireNonNull(ticketId, "ticketId");
        return acknowledgements.stream()
                .filter(acknowledgement -> acknowledgement.ticket().id().equals(ticketId))
                .findFirst();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet
            admit(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement
                            acknowledgement) {
        Objects.requireNonNull(acknowledgement, "acknowledgement");
        ArrayList<
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement>
                revised = new ArrayList<>(acknowledgements);
        revised.add(acknowledgement);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementSet(
                schemaVersion,
                revised);
    }
}
