package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0060 immutable backend-neutral activation state for an admitted publication-view snapshot.
 *
 * <p>Activation and replacement return new state objects. No backend mutation, persistence, or
 * global registry is performed here.
 */
public final class SkyIslandPublishedWorldActivationState {
    private final Optional<SkyIslandPublishedWorldSnapshot> activeSnapshot;

    private SkyIslandPublishedWorldActivationState(
            Optional<SkyIslandPublishedWorldSnapshot> activeSnapshot) {
        this.activeSnapshot =
                Objects.requireNonNull(activeSnapshot, "activeSnapshot");
    }

    public static SkyIslandPublishedWorldActivationState inactive() {
        return new SkyIslandPublishedWorldActivationState(Optional.empty());
    }

    public boolean active() {
        return activeSnapshot.isPresent();
    }

    public Optional<SkyIslandPublishedWorldSnapshot> activeSnapshot() {
        return activeSnapshot;
    }

    /**
     * Activates the first snapshot explicitly.
     *
     * <p>Re-activating an already active state is rejected; callers must use compare-and-replace.
     */
    public SkyIslandPublishedWorldActivationState activateInitial(
            SkyIslandPublishedWorldView view,
            long snapshotRevision) {
        Objects.requireNonNull(view, "view");
        if (active()) {
            throw new IllegalStateException(
                    "AUTH-0060 initial activation requires an inactive state");
        }
        return active(SkyIslandPublishedWorldSnapshot.of(snapshotRevision, view));
    }

    /**
     * Explicit compare-and-replace activation.
     *
     * <p>The exact expected snapshot identity must still be active and the activation revision must
     * strictly increase. The replacement view is already required to be a valid AUTH-0059 view.
     */
    public SkyIslandPublishedWorldActivationState replace(
            SkyIslandPublishedWorldSnapshotId expectedCurrent,
            SkyIslandPublishedWorldView replacementView,
            long replacementSnapshotRevision) {
        Objects.requireNonNull(expectedCurrent, "expectedCurrent");
        Objects.requireNonNull(replacementView, "replacementView");
        SkyIslandPublishedWorldSnapshot current =
                activeSnapshot.orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "AUTH-0060 replacement requires an active snapshot"));
        if (!current.id().equals(expectedCurrent)) {
            throw new IllegalStateException(
                    "expected current snapshot is stale; refusing activation replacement");
        }
        if (replacementSnapshotRevision <= current.id().snapshotRevision()) {
            throw new IllegalArgumentException(
                    "replacement snapshot revision must strictly increase");
        }
        return active(
                SkyIslandPublishedWorldSnapshot.of(
                        replacementSnapshotRevision,
                        replacementView));
    }

    /** Query through the exact active snapshot; inactive state fails explicitly. */
    public List<SkyIslandPublishedWorldEntry> query(WorldBounds region) {
        Objects.requireNonNull(region, "region");
        return requireActive().query(region);
    }

    public SkyIslandPublishedWorldSnapshot requireActive() {
        return activeSnapshot.orElseThrow(
                () -> new IllegalStateException("no published-world snapshot is active"));
    }

    private static SkyIslandPublishedWorldActivationState active(
            SkyIslandPublishedWorldSnapshot snapshot) {
        return new SkyIslandPublishedWorldActivationState(
                Optional.of(Objects.requireNonNull(snapshot, "snapshot")));
    }
}
