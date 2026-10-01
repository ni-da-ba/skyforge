package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0068 immutable preparation unit for downstream persistence/replication consumption.
 *
 * <p>The object captures identity and provenance only. It performs no I/O and does not claim
 * durability, persistence, replication, or remote acknowledgement.
 */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId id,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding binding,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation preparationValidation) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption {
        id = Objects.requireNonNull(id, "id");
        binding = Objects.requireNonNull(binding, "binding");
        preparationValidation =
                Objects.requireNonNull(preparationValidation, "preparationValidation");

        preparationValidation.requireCurrent();

        if (!preparationValidation.binding().equals(binding)) {
            throw new IllegalArgumentException(
                    "prepared checkpoint consumption validation does not bind the exact captured binding");
        }
        if (!id.checkpointId().equals(binding.checkpointId())) {
            throw new IllegalArgumentException(
                    "prepared checkpoint consumption identity does not bind the exact checkpoint");
        }
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId targetId() {
        return id.targetId();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointId checkpointId() {
        return id.checkpointId();
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpoint checkpoint() {
        return binding.checkpoint();
    }
}
