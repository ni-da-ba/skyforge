package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0070 immutable structural binding of one downstream outcome attestation to one exact
 * AUTH-0069 checkpoint-consumption ticket.
 *
 * <p>This validates provenance only. Authenticating the backend-owned evidence token remains a
 * downstream responsibility.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementId id,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket ticket,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeAttestation
                attestation) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement {
        id = Objects.requireNonNull(id, "id");
        ticket = Objects.requireNonNull(ticket, "ticket");
        attestation = Objects.requireNonNull(attestation, "attestation");

        if (!id.ticketId().equals(ticket.id())) {
            throw new IllegalArgumentException(
                    "checkpoint-consumption acknowledgement identity does not bind the exact ticket");
        }
        if (attestation.schemaVersion()
                != SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeAttestation
                        .SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported checkpoint-consumption outcome attestation schema: "
                            + attestation.schemaVersion());
        }
        if (!ticket.id().equals(Objects.requireNonNull(attestation.ticketId(), "attestation ticketId"))) {
            throw new IllegalArgumentException(
                    "checkpoint-consumption outcome attestation does not bind the exact ticket");
        }

        Objects.requireNonNull(attestation.outcome(), "attestation outcome");
        String evidenceToken =
                Objects.requireNonNull(attestation.evidenceToken(), "attestation evidenceToken");
        if (evidenceToken.isBlank()) {
            throw new IllegalArgumentException("attestation evidenceToken must not be blank");
        }
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome outcome() {
        return attestation.outcome();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption
            preparedConsumption() {
        return ticket.preparedConsumption();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId targetId() {
        return ticket.targetId();
    }
}
