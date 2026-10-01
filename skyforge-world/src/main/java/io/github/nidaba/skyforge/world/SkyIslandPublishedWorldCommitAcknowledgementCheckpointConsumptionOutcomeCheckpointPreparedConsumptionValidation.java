package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0074 execution-time currentness validation for one exact prepared outcome-checkpoint
 * consumption.
 *
 * <p>This remains a provenance/currentness gate only; it is not an atomic external storage
 * transaction.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption
                preparedConsumption,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingValidation
                bindingValidation) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation {
        preparedConsumption =
                Objects.requireNonNull(preparedConsumption, "preparedConsumption");
        bindingValidation = Objects.requireNonNull(bindingValidation, "bindingValidation");
        if (!preparedConsumption.binding().equals(bindingValidation.binding())) {
            throw new IllegalArgumentException(
                    "prepared outcome-checkpoint consumption validation belongs to another binding");
        }
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingStatus
            status() {
        return bindingValidation.status();
    }

    public boolean current() {
        return bindingValidation.current();
    }

    public void requireCurrent() {
        bindingValidation.requireCurrent();
    }
}
