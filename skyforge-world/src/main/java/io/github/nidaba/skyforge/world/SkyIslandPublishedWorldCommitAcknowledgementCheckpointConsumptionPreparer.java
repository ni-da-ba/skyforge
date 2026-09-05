package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0068 backend-neutral checkpoint-consumption preparation/currentness seam.
 *
 * <p>Preparation requires an explicitly CURRENT AUTH-0067 validation. There is no raw-binding
 * shortcut, hidden refresh, retry, connection, persistence, replication, or backend mutation.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer {
    private final SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder binder =
            new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder();

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption prepare(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation validation,
            long preparationSequence,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId targetId) {
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(targetId, "targetId");
        validation.requireCurrent();

        var id =
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId.of(
                        preparationSequence,
                        validation.binding(),
                        targetId);
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption(
                id,
                validation.binding(),
                validation);
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation
            validateForExecution(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption
                            preparedConsumption,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState
                            activationState) {
        Objects.requireNonNull(preparedConsumption, "preparedConsumption");
        Objects.requireNonNull(activationState, "activationState");
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionValidation(
                preparedConsumption,
                binder.validate(preparedConsumption.binding(), activationState));
    }
}
