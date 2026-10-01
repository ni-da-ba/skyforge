package io.github.nidaba.skyforge.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0065 immutable admission set for exact AUTH-0064 commit acknowledgements.
 *
 * <p>The set rejects acknowledgement replay, duplicate/contradictory outcomes for one ticket, and
 * acknowledgement-sequence reuse. It never replaces an admitted acknowledgement and never selects
 * a newest winner.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementSet {
    public static final int SCHEMA_VERSION = 1;

    private static final Comparator<SkyIslandPublishedWorldCommitAcknowledgement> CANONICAL_ORDER =
            Comparator.comparingLong(
                    acknowledgement -> acknowledgement.id().acknowledgementSequence());

    private final int schemaVersion;
    private final List<SkyIslandPublishedWorldCommitAcknowledgement> acknowledgements;

    public SkyIslandPublishedWorldCommitAcknowledgementSet(
            int schemaVersion,
            List<SkyIslandPublishedWorldCommitAcknowledgement> acknowledgements) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported published-world commit acknowledgement-set schema: "
                            + schemaVersion);
        }
        Objects.requireNonNull(acknowledgements, "acknowledgements");
        if (acknowledgements.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("acknowledgements contains null");
        }

        ArrayList<SkyIslandPublishedWorldCommitAcknowledgement> canonical =
                new ArrayList<>(acknowledgements);
        canonical.sort(CANONICAL_ORDER);

        for (int i = 0; i < canonical.size(); i++) {
            SkyIslandPublishedWorldCommitAcknowledgement current = canonical.get(i);

            for (int j = 0; j < i; j++) {
                SkyIslandPublishedWorldCommitAcknowledgement previous = canonical.get(j);

                if (previous.id().acknowledgementSequence()
                        == current.id().acknowledgementSequence()) {
                    throw new IllegalArgumentException(
                            "commit acknowledgement sequence reuse is not admitted: "
                                    + current.id().acknowledgementSequence());
                }

                if (previous.ticket().id().equals(current.ticket().id())) {
                    throw new IllegalArgumentException(
                            "commit ticket already has an admitted acknowledgement: "
                                    + current.ticket().id().canonicalToken());
                }
            }
        }

        this.schemaVersion = schemaVersion;
        this.acknowledgements = List.copyOf(canonical);
    }

    public static SkyIslandPublishedWorldCommitAcknowledgementSet empty() {
        return new SkyIslandPublishedWorldCommitAcknowledgementSet(
                SCHEMA_VERSION,
                List.of());
    }

    public static SkyIslandPublishedWorldCommitAcknowledgementSet of(
            List<SkyIslandPublishedWorldCommitAcknowledgement> acknowledgements) {
        return new SkyIslandPublishedWorldCommitAcknowledgementSet(
                SCHEMA_VERSION,
                acknowledgements);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public List<SkyIslandPublishedWorldCommitAcknowledgement> acknowledgements() {
        return acknowledgements;
    }

    public int size() {
        return acknowledgements.size();
    }

    public boolean isEmpty() {
        return acknowledgements.isEmpty();
    }

    public Optional<SkyIslandPublishedWorldCommitAcknowledgement> forTicket(
            SkyIslandPublishedWorldCommitTicketId ticketId) {
        Objects.requireNonNull(ticketId, "ticketId");
        return acknowledgements.stream()
                .filter(acknowledgement -> acknowledgement.ticket().id().equals(ticketId))
                .findFirst();
    }

    /**
     * Admits one new acknowledgement and returns a new immutable set.
     *
     * <p>Replay, duplicate ticket outcome, contradictory ticket outcome, and sequence reuse fail
     * closed through normal set reconstruction.
     */
    public SkyIslandPublishedWorldCommitAcknowledgementSet admit(
            SkyIslandPublishedWorldCommitAcknowledgement acknowledgement) {
        Objects.requireNonNull(acknowledgement, "acknowledgement");
        ArrayList<SkyIslandPublishedWorldCommitAcknowledgement> revised =
                new ArrayList<>(acknowledgements);
        revised.add(acknowledgement);
        return new SkyIslandPublishedWorldCommitAcknowledgementSet(
                schemaVersion,
                revised);
    }
}
