package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/** AUTH-0079 validation of one exact outcome-checkpoint binding against one activation state. */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBinding
                binding,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus
                status,
        Optional<
                        SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId>
                currentCheckpointId) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingValidation {
        binding = Objects.requireNonNull(binding, "binding");
        status = Objects.requireNonNull(status, "status");
        currentCheckpointId = Objects.requireNonNull(currentCheckpointId, "currentCheckpointId");

        switch (status) {
            case CURRENT -> {
                if (currentCheckpointId.isEmpty()
                        || !currentCheckpointId.orElseThrow().equals(binding.checkpointId())) {
                    throw new IllegalArgumentException(
                            "CURRENT outcome-checkpoint validation requires the exact bound checkpoint");
                }
            }
            case STALE -> {
                if (currentCheckpointId.isEmpty()
                        || currentCheckpointId.orElseThrow().equals(binding.checkpointId())) {
                    throw new IllegalArgumentException(
                            "STALE outcome-checkpoint validation requires a different active checkpoint");
                }
            }
            case INACTIVE -> {
                if (currentCheckpointId.isPresent()) {
                    throw new IllegalArgumentException(
                            "INACTIVE outcome-checkpoint validation cannot name a current checkpoint");
                }
            }
        }
    }

    public boolean current() {
        return status
                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointBindingStatus
                        .CURRENT;
    }

    public void requireCurrent() {
        if (!current()) {
            throw new IllegalStateException(
                    "outcome-checkpoint binding is "
                            + status
                            + "; bound="
                            + binding.checkpointId().canonicalToken()
                            + currentCheckpointId
                                    .map(id -> "; current=" + id.canonicalToken())
                                    .orElse("; current=<inactive>"));
        }
    }
}
