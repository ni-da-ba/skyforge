package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0061 immutable validation result for one captured snapshot binding.
 *
 * <p>Validation is relative to the exact supplied activation state. It is not a promise that a
 * mutable backend activation reference cannot change after validation.
 */
public record SkyIslandPublishedWorldBindingValidation(
        SkyIslandPublishedWorldSnapshotBinding binding,
        SkyIslandPublishedWorldBindingStatus status,
        Optional<SkyIslandPublishedWorldSnapshotId> currentSnapshotId) {

    public SkyIslandPublishedWorldBindingValidation {
        binding = Objects.requireNonNull(binding, "binding");
        status = Objects.requireNonNull(status, "status");
        currentSnapshotId =
                Objects.requireNonNull(currentSnapshotId, "currentSnapshotId");

        switch (status) {
            case CURRENT -> {
                if (currentSnapshotId.isEmpty()
                        || !currentSnapshotId.orElseThrow().equals(binding.snapshotId())) {
                    throw new IllegalArgumentException(
                            "CURRENT binding validation requires the exact bound snapshot identity");
                }
            }
            case STALE -> {
                if (currentSnapshotId.isEmpty()
                        || currentSnapshotId.orElseThrow().equals(binding.snapshotId())) {
                    throw new IllegalArgumentException(
                            "STALE binding validation requires a different active snapshot identity");
                }
            }
            case INACTIVE -> {
                if (currentSnapshotId.isPresent()) {
                    throw new IllegalArgumentException(
                            "INACTIVE binding validation cannot name a current snapshot");
                }
            }
        }
    }

    public boolean current() {
        return status == SkyIslandPublishedWorldBindingStatus.CURRENT;
    }

    public void requireCurrent() {
        if (!current()) {
            throw new IllegalStateException(
                    "snapshot binding is not current: status="
                            + status
                            + ", bound="
                            + binding.snapshotId().canonicalToken()
                            + ", current="
                            + currentSnapshotId
                                    .map(SkyIslandPublishedWorldSnapshotId::canonicalToken)
                                    .orElse("INACTIVE"));
        }
    }
}
