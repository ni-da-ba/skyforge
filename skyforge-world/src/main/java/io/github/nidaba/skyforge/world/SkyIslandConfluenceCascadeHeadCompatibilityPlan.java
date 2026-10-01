package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** F3H deterministic joint confluence/authored-CASCADE head evidence for one island. */
public record SkyIslandConfluenceCascadeHeadCompatibilityPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandHydraulicTransitionGeometryEvidencePlan transitionGeometry,
        List<SkyIslandConfluenceCascadeHeadCompatibilityOutcome> outcomes) {

    public SkyIslandConfluenceCascadeHeadCompatibilityPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        transitionGeometry = Objects.requireNonNull(transitionGeometry, "transitionGeometry");
        outcomes = List.copyOf(outcomes);
        outcomes.forEach(value -> Objects.requireNonNull(value, "joint transition outcome"));
        if (!descriptor.equals(transitionGeometry.descriptor())) {
            throw new IllegalArgumentException(
                    "joint transition plan descriptor must match transition geometry");
        }
    }

    public long solvedCount() {
        return outcomes.stream()
                .filter(value -> value.status()
                        == SkyIslandConfluenceCascadeHeadCompatibilityStatus.SOLVED)
                .count();
    }
}
