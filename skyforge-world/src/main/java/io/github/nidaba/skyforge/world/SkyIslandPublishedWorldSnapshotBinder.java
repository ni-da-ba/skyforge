package io.github.nidaba.skyforge.world;

import java.util.Objects;
import java.util.Optional;

/**
 * AUTH-0061 backend-neutral snapshot binding and validation seam.
 *
 * <p>There is deliberately no refresh/rebind operation. Callers bind explicitly, prepare against
 * that immutable snapshot, and validate that exact binding before handing work to a backend commit
 * boundary.
 */
public final class SkyIslandPublishedWorldSnapshotBinder {

    /** Captures the exact snapshot active in the supplied state. Inactive state fails explicitly. */
    public SkyIslandPublishedWorldSnapshotBinding bind(
            SkyIslandPublishedWorldActivationState activationState) {
        Objects.requireNonNull(activationState, "activationState");
        return SkyIslandPublishedWorldSnapshotBinding.of(
                activationState.requireActive());
    }

    /** Validates the binding against the exact supplied immutable activation state. */
    public SkyIslandPublishedWorldBindingValidation validate(
            SkyIslandPublishedWorldSnapshotBinding binding,
            SkyIslandPublishedWorldActivationState activationState) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(activationState, "activationState");

        Optional<SkyIslandPublishedWorldSnapshotId> current =
                activationState.activeSnapshot()
                        .map(SkyIslandPublishedWorldSnapshot::id);
        if (current.isEmpty()) {
            return new SkyIslandPublishedWorldBindingValidation(
                    binding,
                    SkyIslandPublishedWorldBindingStatus.INACTIVE,
                    Optional.empty());
        }
        if (current.orElseThrow().equals(binding.snapshotId())) {
            return new SkyIslandPublishedWorldBindingValidation(
                    binding,
                    SkyIslandPublishedWorldBindingStatus.CURRENT,
                    current);
        }
        return new SkyIslandPublishedWorldBindingValidation(
                binding,
                SkyIslandPublishedWorldBindingStatus.STALE,
                current);
    }
}
