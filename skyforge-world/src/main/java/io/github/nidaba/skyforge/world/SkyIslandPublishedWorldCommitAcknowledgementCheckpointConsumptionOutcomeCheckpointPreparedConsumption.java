package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0074 immutable preparation unit for downstream audit/storage consumption of one exact
 * outcome checkpoint.
 *
 * <p>The object captures identity and provenance only. It performs no I/O and does not claim
 * persistence, replication, durability, or remote acknowledgement.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId
                id,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinding
                binding,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBindingValidation
                preparationValidation) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumption {
        id = Objects.requireNonNull(id, "id");
        binding = Objects.requireNonNull(binding, "binding");
        preparationValidation =
                Objects.requireNonNull(preparationValidation, "preparationValidation");

        preparationValidation.requireCurrent();

        if (!preparationValidation.binding().equals(binding)) {
            throw new IllegalArgumentException(
                    "prepared outcome-checkpoint consumption validation does not bind the exact captured binding");
        }
        if (!id.checkpointId().equals(binding.checkpointId())) {
            throw new IllegalArgumentException(
                    "prepared outcome-checkpoint consumption identity does not bind the exact checkpoint");
        }
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId
            targetId() {
        return id.targetId();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId
            checkpointId() {
        return id.checkpointId();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
            checkpoint() {
        return binding.checkpoint();
    }
}
