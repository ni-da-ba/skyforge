package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0074 backend-neutral outcome-checkpoint consumption preparation/currentness seam.
 *
 * <p>Preparation requires an explicitly CURRENT AUTH-0073 validation. There is no raw-binding
 * shortcut, hidden refresh, retry, connection, persistence, replication, or backend mutation.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer {
    private final
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder
                    binder =
                            new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinder();

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption
            prepare(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingValidation
                            validation,
                    long preparationSequence,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                            targetId) {
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(targetId, "targetId");
        validation.requireCurrent();

        var id =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(preparationSequence, validation.binding(), targetId);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption(
                id,
                validation.binding(),
                validation);
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
            validateForExecution(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption
                            preparedConsumption,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                            activationState) {
        Objects.requireNonNull(preparedConsumption, "preparedConsumption");
        Objects.requireNonNull(activationState, "activationState");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation(
                preparedConsumption,
                binder.validate(preparedConsumption.binding(), activationState));
    }
}
