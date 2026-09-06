package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0081 immutable capability recording admission of one exact CURRENT AUTH-0080 prepared
 * outcome-checkpoint-consumption outcome-checkpoint consumption to downstream audit/storage coordination.
 *
 * <p>The ticket records coordination admission only. It does not assert that any target accepted,
 * persisted, replicated, fsynced, or durably stored the checkpoint.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicket(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                id,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                admissionValidation) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTicket {
        id = Objects.requireNonNull(id, "id");
        admissionValidation =
                Objects.requireNonNull(admissionValidation, "admissionValidation");

        admissionValidation.requireCurrent();

        if (!id.preparedConsumptionId()
                .equals(admissionValidation.preparedConsumption().id())) {
            throw new IllegalArgumentException(
                    "outcome-checkpoint-consumption outcome-checkpoint consumption ticket identity does not bind the exact admitted preparation");
        }
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumption
            preparedConsumption() {
        return admissionValidation.preparedConsumption();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId
            checkpointId() {
        return preparedConsumption().checkpointId();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
            targetId() {
        return preparedConsumption().targetId();
    }
}
