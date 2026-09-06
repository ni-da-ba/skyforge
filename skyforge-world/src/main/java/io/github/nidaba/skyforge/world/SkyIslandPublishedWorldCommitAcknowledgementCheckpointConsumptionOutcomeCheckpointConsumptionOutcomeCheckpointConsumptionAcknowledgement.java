package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0082 immutable structural binding of one external downstream outcome attestation to one
 * exact AUTH-0081 outcome-checkpoint consumption ticket.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgementId
                id,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicket
                ticket,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation
                attestation) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionAcknowledgement {
        id = Objects.requireNonNull(id, "id");
        ticket = Objects.requireNonNull(ticket, "ticket");
        attestation = Objects.requireNonNull(attestation, "attestation");

        if (!id.ticketId().equals(ticket.id())) {
            throw new IllegalArgumentException(
                    "outcome-checkpoint consumption acknowledgement identity does not bind the exact ticket");
        }
        if (attestation.schemaVersion()
                != SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeAttestation
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

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionOutcome
            outcome() {
        return attestation.outcome();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumption
            preparedConsumption() {
        return ticket.preparedConsumption();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
            targetId() {
        return ticket.targetId();
    }
}
