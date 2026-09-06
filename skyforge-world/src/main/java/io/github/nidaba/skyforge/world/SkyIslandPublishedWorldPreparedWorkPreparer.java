package io.github.nidaba.skyforge.world;

import java.util.Objects;

/**
 * AUTH-0062 backend-neutral prepared-work construction and commit-validation seam.
 *
 * <p>No backend mutation occurs here. Preparation is pinned to the supplied immutable AUTH-0061
 * binding; commit validation reuses exact binding currentness without refresh or retry.
 */
public final class SkyIslandPublishedWorldPreparedWorkPreparer {
    private final SkyIslandPublishedWorldSnapshotBinder binder =
            new SkyIslandPublishedWorldSnapshotBinder();

    public SkyIslandPublishedWorldPreparedWork prepare(
            SkyIslandPublishedWorldSnapshotBinding binding,
            long workSequence,
            WorldBounds region) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(region, "region");
        SkyIslandPublishedWorldPreparedWorkId id =
                SkyIslandPublishedWorldPreparedWorkId.of(
                        workSequence,
                        binding,
                        region);
        return SkyIslandPublishedWorldPreparedWork.of(id, binding);
    }

    public SkyIslandPublishedWorldPreparedWorkValidation validateForCommit(
            SkyIslandPublishedWorldPreparedWork preparedWork,
            SkyIslandPublishedWorldActivationState activationState) {
        Objects.requireNonNull(preparedWork, "preparedWork");
        Objects.requireNonNull(activationState, "activationState");
        return new SkyIslandPublishedWorldPreparedWorkValidation(
                preparedWork,
                binder.validate(preparedWork.binding(), activationState));
    }
}
