package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0067 backend-neutral checkpoint binding/currentness seam.
 *
 * <p>There is deliberately no refresh, rebind, latest, or retry operation.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder {

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding bind(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState activationState) {
        Objects.requireNonNull(activationState, "activationState");
        return SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding.of(
                activationState.requireActive());
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation validate(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding binding,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState activationState) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(activationState, "activationState");

        Optional<SkyIslandPublishedWorldCommitAcknowledgementCheckpointId> current =
                activationState.activeCheckpoint()
                        .map(SkyIslandPublishedWorldCommitAcknowledgementCheckpoint::id);

        if (current.isEmpty()) {
            return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation(
                    binding,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.INACTIVE,
                    Optional.empty());
        }
        if (current.orElseThrow().equals(binding.checkpointId())) {
            return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation(
                    binding,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.CURRENT,
                    current);
        }
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation(
                binding,
                SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingStatus.STALE,
                current);
    }
}
