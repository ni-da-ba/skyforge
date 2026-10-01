package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0069 immutable capability recording admission of one exact CURRENT prepared checkpoint
 * consumption to downstream I/O coordination.
 *
 * <p>The ticket does not assert that any file, replica, database, network peer, or backend accepted
 * or persisted data.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicketId id,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation
                admissionValidation) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTicket {
        id = Objects.requireNonNull(id, "id");
        admissionValidation =
                Objects.requireNonNull(admissionValidation, "admissionValidation");

        admissionValidation.requireCurrent();

        if (!id.preparedConsumptionId()
                .equals(admissionValidation.preparedConsumption().id())) {
            throw new IllegalArgumentException(
                    "checkpoint-consumption ticket identity does not bind the exact admitted preparation");
        }
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption
            preparedConsumption() {
        return admissionValidation.preparedConsumption();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointId checkpointId() {
        return preparedConsumption().checkpointId();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId targetId() {
        return preparedConsumption().targetId();
    }
}
