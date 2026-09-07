package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/** AUTH-0067 validation of one exact checkpoint binding against one supplied activation state. */
public record SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation(
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding binding,
        SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus status,
        Optional<SkyIslandPublishedWorldCommitAcknowledgementCheckpointId> currentCheckpointId) {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation {
        binding = Objects.requireNonNull(binding, "binding");
        status = Objects.requireNonNull(status, "status");
        currentCheckpointId =
                Objects.requireNonNull(currentCheckpointId, "currentCheckpointId");

        switch (status) {
            case CURRENT -> {
                if (currentCheckpointId.isEmpty()
                        || !currentCheckpointId.orElseThrow().equals(binding.checkpointId())) {
                    throw new IllegalArgumentException(
                            "CURRENT checkpoint binding validation requires the exact bound checkpoint");
                }
            }
            case STALE -> {
                if (currentCheckpointId.isEmpty()
                        || currentCheckpointId.orElseThrow().equals(binding.checkpointId())) {
                    throw new IllegalArgumentException(
                            "STALE checkpoint binding validation requires a different active checkpoint");
                }
            }
            case INACTIVE -> {
                if (currentCheckpointId.isPresent()) {
                    throw new IllegalArgumentException(
                            "INACTIVE checkpoint binding validation cannot name a current checkpoint");
                }
            }
        }
    }

    public boolean current() {
        return status
                == SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.CURRENT;
    }

    public void requireCurrent() {
        if (!current()) {
            throw new IllegalStateException(
                    "acknowledgement checkpoint binding is "
                            + status
                            + "; bound="
                            + binding.checkpointId().canonicalToken()
                            + currentCheckpointId
                                    .map(
                                            id ->
                                                    "; current="
                                                            + id.canonicalToken())
                                    .orElse("; current=<inactive>"));
        }
    }
}
