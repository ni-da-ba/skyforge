package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0080 immutable preparation unit for downstream audit/storage consumption of one exact
 * outcome-checkpoint-consumption outcome checkpoint.
 *
 * <p>The object captures identity and provenance only. It performs no I/O and does not claim
 * persistence, replication, durability, or remote acknowledgement.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumption(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                id,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinding
                binding,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation
                preparationValidation) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumption {
        id = Objects.requireNonNull(id, "id");
        binding = Objects.requireNonNull(binding, "binding");
        preparationValidation =
                Objects.requireNonNull(preparationValidation, "preparationValidation");

        preparationValidation.requireCurrent();

        if (!preparationValidation.binding().equals(binding)) {
            throw new IllegalArgumentException(
                    "prepared outcome-checkpoint-consumption outcome-checkpoint consumption validation does not bind the exact captured binding");
        }
        if (!id.checkpointId().equals(binding.checkpointId())) {
            throw new IllegalArgumentException(
                    "prepared outcome-checkpoint-consumption outcome-checkpoint consumption identity does not bind the exact checkpoint");
        }
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
            targetId() {
        return id.targetId();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId
            checkpointId() {
        return id.checkpointId();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
            checkpoint() {
        return binding.checkpoint();
    }
}
