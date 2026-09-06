package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0079 immutable backend-neutral activation state for one AUTH-0078 outcome checkpoint.
 *
 * <p>Activation selects a checkpoint generation for downstream audit/storage consumers. It does
 * not persist, replicate, or make the checkpoint durable.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState {
    private final Optional<
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint>
            activeCheckpoint;

    private SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState(
            Optional<
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint>
                    activeCheckpoint) {
        this.activeCheckpoint = Objects.requireNonNull(activeCheckpoint, "activeCheckpoint");
    }

    public static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                    inactive() {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState(
                Optional.empty());
    }

    public boolean active() {
        return activeCheckpoint.isPresent();
    }

    public Optional<
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint>
            activeCheckpoint() {
        return activeCheckpoint;
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
            activateInitial(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                            checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        if (active()) {
            throw new IllegalStateException(
                    "AUTH-0079 initial activation requires an inactive outcome-checkpoint state");
        }
        return active(checkpoint);
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
            replace(
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId
                            expectedCurrent,
                    SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                            replacement) {
        Objects.requireNonNull(expectedCurrent, "expectedCurrent");
        Objects.requireNonNull(replacement, "replacement");

        var current =
                activeCheckpoint.orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "AUTH-0079 replacement requires an active outcome checkpoint"));

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

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
            requireActive() {
        return activeCheckpoint.orElseThrow(
                () -> new IllegalStateException("no outcome checkpoint is active"));
    }

    private static
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState
                    active(
                            SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint
                                    checkpoint) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointActivationState(
                Optional.of(Objects.requireNonNull(checkpoint, "checkpoint")));
    }
}
