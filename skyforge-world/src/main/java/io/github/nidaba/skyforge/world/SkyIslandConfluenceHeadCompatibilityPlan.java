package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** F3B local confluence head-compatibility evidence for one island. */
public record SkyIslandConfluenceHeadCompatibilityPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandHydraulicTransitionGeometryEvidencePlan transitionGeometry,
        List<SkyIslandConfluenceHeadCompatibilityOutcome> outcomes) {

    public SkyIslandConfluenceHeadCompatibilityPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        transitionGeometry = Objects.requireNonNull(transitionGeometry, "transitionGeometry");
        outcomes = List.copyOf(outcomes);
        outcomes.forEach(value -> Objects.requireNonNull(value, "confluence outcome"));
        if (!descriptor.equals(transitionGeometry.descriptor())) {
            throw new IllegalArgumentException(
                    "confluence plan descriptor must match transition geometry");
        }
    }
}
