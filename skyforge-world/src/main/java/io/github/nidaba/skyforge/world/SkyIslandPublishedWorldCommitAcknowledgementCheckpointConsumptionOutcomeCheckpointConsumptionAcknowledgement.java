package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0076 immutable structural binding of one external downstream outcome attestation to one
 * exact AUTH-0075 outcome-checkpoint consumption ticket.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId
                id,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                ticket,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation
                attestation) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement {
        id = Objects.requireNonNull(id, "id");
        ticket = Objects.requireNonNull(ticket, "ticket");
        attestation = Objects.requireNonNull(attestation, "attestation");

        if (!id.ticketId().equals(ticket.id())) {
            throw new IllegalArgumentException(
                    "outcome-checkpoint consumption acknowledgement identity does not bind the exact ticket");
        }
        if (attestation.schemaVersion()
                != SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation
                        .SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported outcome-checkpoint consumption attestation schema: "
                            + attestation.schemaVersion());
        }
        if (!ticket.id().equals(Objects.requireNonNull(attestation.ticketId(), "attestation ticketId"))) {
            throw new IllegalArgumentException(
                    "outcome-checkpoint consumption attestation does not bind the exact ticket");
        }

        Objects.requireNonNull(attestation.outcome(), "attestation outcome");
        String evidenceToken =
                Objects.requireNonNull(attestation.evidenceToken(), "attestation evidenceToken");
        if (evidenceToken.isBlank()) {
            throw new IllegalArgumentException("attestation evidenceToken must not be blank");
        }
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
            outcome() {
        return attestation.outcome();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption
            preparedConsumption() {
        return ticket.preparedConsumption();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
            targetId() {
        return ticket.targetId();
    }
}
