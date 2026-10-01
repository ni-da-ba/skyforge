package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0075 immutable capability recording admission of one exact CURRENT AUTH-0074 prepared
 * outcome-checkpoint consumption to downstream audit/storage coordination.
 *
 * <p>The ticket records coordination admission only. It does not assert that any target accepted,
 * persisted, replicated, fsynced, or durably stored the checkpoint.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicketId
                id,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
                admissionValidation) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTicket {
        id = Objects.requireNonNull(id, "id");
        admissionValidation =
                Objects.requireNonNull(admissionValidation, "admissionValidation");

        admissionValidation.requireCurrent();

        if (!id.preparedConsumptionId()
                .equals(admissionValidation.preparedConsumption().id())) {
            throw new IllegalArgumentException(
                    "outcome-checkpoint consumption ticket identity does not bind the exact admitted preparation");
        }
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption
            preparedConsumption() {
        return admissionValidation.preparedConsumption();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId
            checkpointId() {
        return preparedConsumption().checkpointId();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
            targetId() {
        return preparedConsumption().targetId();
    }
}
