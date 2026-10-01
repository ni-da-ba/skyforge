package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0077 immutable admission set for exact AUTH-0076 outcome-checkpoint consumption outcome
 * acknowledgements.
 *
 * <p>The set rejects acknowledgement replay, duplicate/contradictory outcomes for one exact
 * AUTH-0075 coordination ticket, and acknowledgement-sequence reuse. It never replaces an admitted
 * outcome and never selects a newest winner.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet {
    public static final int SCHEMA_VERSION = 1;

    private static final Comparator<
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement>
            CANONICAL_ORDER =
                    Comparator.comparingLong(
                            acknowledgement -> acknowledgement.id().acknowledgementSequence());

    private final int schemaVersion;
    private final List<
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement>
            acknowledgements;

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet(
            int schemaVersion,
            List<
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement>
                    acknowledgements) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported outcome-checkpoint consumption acknowledgement-set schema: "
                            + schemaVersion);
        }
        Objects.requireNonNull(acknowledgements, "acknowledgements");
        if (acknowledgements.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("acknowledgements contains null");
        }

        ArrayList<
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement>
                canonical = new ArrayList<>(acknowledgements);
        canonical.sort(CANONICAL_ORDER);

        for (int i = 0; i < canonical.size(); i++) {
            var current = canonical.get(i);
            for (int j = 0; j < i; j++) {
                var previous = canonical.get(j);

                if (previous.id().acknowledgementSequence()
                        == current.id().acknowledgementSequence()) {
                    throw new IllegalArgumentException(
                            "outcome-checkpoint consumption acknowledgement sequence reuse is not admitted: "
                                    + current.id().acknowledgementSequence());
                }
                if (previous.ticket().id().equals(current.ticket().id())) {
                    throw new IllegalArgumentException(
                            "outcome-checkpoint consumption ticket already has an admitted outcome acknowledgement: "
                                    + current.ticket().id().canonicalToken());
                }
            }
        }

        this.schemaVersion = schemaVersion;
        this.acknowledgements = List.copyOf(canonical);
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                    empty() {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet(
                SCHEMA_VERSION,
                List.of());
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
                    of(
                            List<
                                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement>
                                    acknowledgements) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet(
                SCHEMA_VERSION,
                acknowledgements);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public List<
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement>
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
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement>
            forTicket(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                            ticketId) {
        Objects.requireNonNull(ticketId, "ticketId");
        return acknowledgements.stream()
                .filter(acknowledgement -> acknowledgement.ticket().id().equals(ticketId))
                .findFirst();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet
            admit(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement
                            acknowledgement) {
        Objects.requireNonNull(acknowledgement, "acknowledgement");
        ArrayList<
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement>
                revised = new ArrayList<>(acknowledgements);
        revised.add(acknowledgement);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementSet(
                schemaVersion,
                revised);
    }
}
