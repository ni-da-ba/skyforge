package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** F4E fail-closed qualification of direct world-space water projection for all F4C components. */
public record SkyIslandWorldWaterProjectionQualificationPlan(
        SkyIslandFluvialVoxelQuantizationPlan terrainVoxelPlan,
        List<SkyIslandWorldWaterComponentQualification> components) {

    public SkyIslandWorldWaterProjectionQualificationPlan {
        terrainVoxelPlan = Objects.requireNonNull(terrainVoxelPlan, "terrainVoxelPlan");
        components = List.copyOf(components);
        components.forEach(value -> Objects.requireNonNull(value, "component"));
        if (components.size() != terrainVoxelPlan.components().size()) {
            throw new IllegalArgumentException(
                    "every F4C component must receive exactly one F4E water outcome");
        }
    }

    public List<SkyIslandWorldWaterComponentQualification> qualifiedComponents() {
        return components.stream()
                .filter(component ->
                        component.status() == SkyIslandWorldWaterComponentStatus.QUALIFIED)
                .toList();
    }

    public List<SkyIslandWorldWaterComponentQualification> refinementRequiredComponents() {
        return components.stream()
                .filter(component ->
                        component.status()
                                == SkyIslandWorldWaterComponentStatus.HEAD_REFINEMENT_REQUIRED)
                .toList();
    }
}
