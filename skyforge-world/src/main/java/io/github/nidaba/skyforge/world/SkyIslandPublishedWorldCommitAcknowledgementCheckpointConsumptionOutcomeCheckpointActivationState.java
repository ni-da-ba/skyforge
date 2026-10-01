package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0073 immutable backend-neutral activation state for one AUTH-0072 outcome checkpoint.
 *
 * <p>Activation selects a checkpoint generation for downstream audit/storage consumers. It does
 * not persist, replicate, or make the checkpoint durable.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState {
    private final Optional<
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint>
            activeCheckpoint;

    private SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState(
            Optional<
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint>
                    activeCheckpoint) {
        this.activeCheckpoint = Objects.requireNonNull(activeCheckpoint, "activeCheckpoint");
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                    inactive() {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState(
                Optional.empty());
    }

    public boolean active() {
        return activeCheckpoint.isPresent();
    }

    public Optional<
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint>
            activeCheckpoint() {
        return activeCheckpoint;
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
            activateInitial(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                            checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        if (active()) {
            throw new IllegalStateException(
                    "AUTH-0073 initial activation requires an inactive outcome-checkpoint state");
        }
        return active(checkpoint);
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
            replace(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId
                            expectedCurrent,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                            replacement) {
        Objects.requireNonNull(expectedCurrent, "expectedCurrent");
        Objects.requireNonNull(replacement, "replacement");

        var current =
                activeCheckpoint.orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "AUTH-0073 replacement requires an active outcome checkpoint"));

        if (!current.id().equals(expectedCurrent)) {
            throw new IllegalStateException(
                    "expected current outcome checkpoint is stale; refusing replacement");
        }
        if (replacement.id().checkpointRevision() <= current.id().checkpointRevision()) {
            throw new IllegalArgumentException(
                    "replacement outcome-checkpoint revision must strictly increase");
        }
        return active(replacement);
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
            requireActive() {
        return activeCheckpoint.orElseThrow(
                () -> new IllegalStateException("no outcome checkpoint is active"));
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState
                    active(
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint
                                    checkpoint) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState(
                Optional.of(Objects.requireNonNull(checkpoint, "checkpoint")));
    }
}
