package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0064 immutable binding of one downstream outcome attestation to one exact commit ticket.
 *
 * <p>This validates structural provenance only. Authenticating the backend-owned evidence token is
 * outside the backend-neutral authorship layer.
 */
public record SkyIslandPublishedWorldCommitAcknowledgement(
        SkyIslandPublishedWorldCommitAcknowledgementId id,
        SkyIslandPublishedWorldCommitTicket ticket,
        SkyIslandPublishedWorldCommitOutcomeAttestation attestation) {

    public SkyIslandPublishedWorldCommitAcknowledgement {
        id = Objects.requireNonNull(id, "id");
        ticket = Objects.requireNonNull(ticket, "ticket");
        attestation = Objects.requireNonNull(attestation, "attestation");

        if (!id.ticketId().equals(ticket.id())) {
            throw new IllegalArgumentException(
                    "commit acknowledgement identity does not bind the exact ticket");
        }
        if (attestation.schemaVersion()
                != SkyIslandPublishedWorldCommitOutcomeAttestation.SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported commit outcome attestation schema: "
                            + attestation.schemaVersion());
        }
        if (!ticket.id().equals(Objects.requireNonNull(attestation.ticketId(), "attestation ticketId"))) {
            throw new IllegalArgumentException(
                    "commit outcome attestation does not bind the exact ticket");
        }
        Objects.requireNonNull(attestation.outcome(), "attestation outcome");
        String evidenceToken =
                Objects.requireNonNull(attestation.evidenceToken(), "attestation evidenceToken");
        if (evidenceToken.isBlank()) {
            throw new IllegalArgumentException("attestation evidenceToken must not be blank");
        }
    }

    public SkyIslandPublishedWorldCommitOutcome outcome() {
        return attestation.outcome();
    }

    public SkyIslandPublishedWorldPreparedWork preparedWork() {
        return ticket.preparedWork();
    }
}
