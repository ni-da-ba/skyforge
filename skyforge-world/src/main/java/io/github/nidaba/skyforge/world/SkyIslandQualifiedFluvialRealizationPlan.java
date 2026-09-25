package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** First terrain-mutating reset plan: qualified fluvial reaches only. */
public record SkyIslandQualifiedFluvialRealizationPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandHydraulicChannelNetworkPlan hydraulicPlan,
        List<SkyIslandGeomorphicReachQualification> acceptedQualifications,
        List<SkyIslandGeomorphicReachQualification> rejectedQualifications,
        SkyIslandQualifiedFluvialTerrainField terrainField) {

    public SkyIslandQualifiedFluvialRealizationPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        hydraulicPlan = Objects.requireNonNull(hydraulicPlan, "hydraulicPlan");
        acceptedQualifications = List.copyOf(acceptedQualifications);
        rejectedQualifications = List.copyOf(rejectedQualifications);
        terrainField = Objects.requireNonNull(terrainField, "terrainField");
        if (!descriptor.equals(hydraulicPlan.descriptor())) {
            throw new IllegalArgumentException("hydraulic plan descriptor must match realization descriptor");
        }
        if (terrainField.acceptedReaches().size() != acceptedQualifications.size()) {
            throw new IllegalArgumentException("terrain field must contain exactly the accepted reaches");
        }
        if (acceptedQualifications.stream().anyMatch(q -> !q.accepted())) {
            throw new IllegalArgumentException("acceptedQualifications contains a rejected reach");
        }
        if (rejectedQualifications.stream().anyMatch(SkyIslandGeomorphicReachQualification::accepted)) {
            throw new IllegalArgumentException("rejectedQualifications contains an accepted reach");
        }
    }
}
