package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0067 immutable backend-neutral activation state for one AUTH-0066 acknowledgement checkpoint.
 *
 * <p>Activation selects a checkpoint generation for downstream consumers. It does not persist,
 * replicate, or make the checkpoint durable.
 */
public final class SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState {
    private final Optional<SkyIslandPublishedWorldCommitAcknowledgementCheckpoint> activeCheckpoint;

    private SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState(
            Optional<SkyIslandPublishedWorldCommitAcknowledgementCheckpoint> activeCheckpoint) {
        this.activeCheckpoint = Objects.requireNonNull(activeCheckpoint, "activeCheckpoint");
    }

    public static SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState inactive() {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState(
                Optional.empty());
    }

    public boolean active() {
        return activeCheckpoint.isPresent();
    }

    public Optional<SkyIslandPublishedWorldCommitAcknowledgementCheckpoint> activeCheckpoint() {
        return activeCheckpoint;
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState activateInitial(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        if (active()) {
            throw new IllegalStateException(
                    "AUTH-0067 initial activation requires an inactive checkpoint state");
        }
        return active(checkpoint);
    }

    /**
     * Explicit compare-and-replace activation.
     *
     * <p>The exact expected checkpoint identity must still be active and the replacement checkpoint
     * revision must strictly increase. There is no latest/newest selection.
     */
    public SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState replace(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpointId expectedCurrent,
            SkyIslandPublishedWorldCommitAcknowledgementCheckpoint replacement) {
        Objects.requireNonNull(expectedCurrent, "expectedCurrent");
        Objects.requireNonNull(replacement, "replacement");

        SkyIslandPublishedWorldCommitAcknowledgementCheckpoint current =
                activeCheckpoint.orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "AUTH-0067 replacement requires an active checkpoint"));

        if (!current.id().equals(expectedCurrent)) {
            throw new IllegalStateException(
                    "expected current acknowledgement checkpoint is stale; refusing replacement");
        }
        if (replacement.id().checkpointRevision()
                <= current.id().checkpointRevision()) {
            throw new IllegalArgumentException(
                    "replacement checkpoint revision must strictly increase");
        }
        return active(replacement);
    }

    public SkyIslandPublishedWorldCommitAcknowledgementCheckpoint requireActive() {
        return activeCheckpoint.orElseThrow(
                () ->
                        new IllegalStateException(
                                "no acknowledgement checkpoint is active"));
    }

    private static SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState active(
            SkyIslandPublishedWorldCommitAcknowledgementCheckpoint checkpoint) {
        return new SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState(
                Optional.of(Objects.requireNonNull(checkpoint, "checkpoint")));
    }
}
