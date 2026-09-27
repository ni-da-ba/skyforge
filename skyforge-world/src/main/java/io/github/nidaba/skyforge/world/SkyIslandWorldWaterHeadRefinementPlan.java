package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;

/** F4F centerline-only joint head/bed refinement evidence. */
public record SkyIslandWorldWaterHeadRefinementPlan(
        SkyIslandWorldWaterProjectionQualificationPlan directQualification,
        List<SkyIslandWorldWaterHeadRefinementComponent> components) {

    public SkyIslandWorldWaterHeadRefinementPlan {
        directQualification = Objects.requireNonNull(directQualification, "directQualification");
        components = List.copyOf(components);
        components.forEach(value -> Objects.requireNonNull(value, "component"));
    }

    public List<SkyIslandWorldWaterHeadRefinementComponent> solvedComponents() {
        return components.stream()
                .filter(value -> value.status() == SkyIslandWorldWaterHeadRefinementStatus.SOLVED)
                .toList();
    }
}
