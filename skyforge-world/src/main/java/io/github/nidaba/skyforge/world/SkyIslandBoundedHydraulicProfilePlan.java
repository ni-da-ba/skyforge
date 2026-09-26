package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Island-level F2C bounded-profile results; still pre-terrain-authority. */
public record SkyIslandBoundedHydraulicProfilePlan(
        SkyIslandDescriptor descriptor,
        SkyIslandHydraulicGeometrySkeletonPlan skeleton,
        List<SkyIslandBoundedHydraulicReachOutcome> outcomes) {

    public SkyIslandBoundedHydraulicProfilePlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        skeleton = Objects.requireNonNull(skeleton, "skeleton");
        outcomes = List.copyOf(outcomes);
        outcomes.forEach(outcome -> Objects.requireNonNull(outcome, "outcome"));
        if (!descriptor.equals(skeleton.descriptor())) {
            throw new IllegalArgumentException("profile-plan descriptor must match skeleton");
        }
        if (outcomes.size() != skeleton.reaches().size()) {
            throw new IllegalArgumentException("every skeleton reach must produce one F2C outcome");
        }
    }
}
