package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** F3C local authored-CASCADE head-discontinuity evidence for one island. */
public record SkyIslandCascadeHeadCompatibilityPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandHydraulicTransitionGeometryEvidencePlan transitionGeometry,
        List<SkyIslandCascadeHeadCompatibilityOutcome> outcomes) {

    public SkyIslandCascadeHeadCompatibilityPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        transitionGeometry = Objects.requireNonNull(transitionGeometry, "transitionGeometry");
        outcomes = List.copyOf(outcomes);
        outcomes.forEach(value -> Objects.requireNonNull(value, "cascade outcome"));
        if (!descriptor.equals(transitionGeometry.descriptor())) {
            throw new IllegalArgumentException(
                    "cascade plan descriptor must match transition geometry");
        }
    }
}
