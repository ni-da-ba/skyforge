package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0080 backend-neutral outcome-checkpoint-consumption outcome-checkpoint consumption preparation/currentness seam.
 *
 * <p>Preparation requires an explicitly CURRENT AUTH-0079 validation. There is no raw-binding
 * shortcut, hidden refresh, retry, connection, persistence, replication, or backend mutation.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparer {
    private final
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder
                    binder =
                            new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinder();

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumption
            prepare(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation
                            validation,
                    long preparationSequence,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
                            targetId) {
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(targetId, "targetId");
        validation.requireCurrent();

        var id =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                        .of(preparationSequence, validation.binding(), targetId);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumption(
                id,
                validation.binding(),
                validation);
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation
            validateForExecution(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumption
                            preparedConsumption,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                            activationState) {
        Objects.requireNonNull(preparedConsumption, "preparedConsumption");
        Objects.requireNonNull(activationState, "activationState");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionValidation(
                preparedConsumption,
                binder.validate(preparedConsumption.binding(), activationState));
    }
}
