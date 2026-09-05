package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0068 execution-time currentness validation for one exact prepared checkpoint consumption.
 *
 * <p>This remains a provenance/currentness gate only; it is not an atomic storage transaction.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption preparedConsumption,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation bindingValidation) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation {
        preparedConsumption =
                Objects.requireNonNull(preparedConsumption, "preparedConsumption");
        bindingValidation = Objects.requireNonNull(bindingValidation, "bindingValidation");
        if (!preparedConsumption.binding().equals(bindingValidation.binding())) {
            throw new IllegalArgumentException(
                    "prepared checkpoint-consumption validation belongs to another binding");
        }
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus status() {
        return bindingValidation.status();
    }

    public boolean current() {
        return bindingValidation.current();
    }

    public void requireCurrent() {
        bindingValidation.requireCurrent();
    }
}
