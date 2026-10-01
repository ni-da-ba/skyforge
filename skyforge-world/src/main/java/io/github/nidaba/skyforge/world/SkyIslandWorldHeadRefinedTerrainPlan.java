package io.github.nidaba.skyforge.world;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * F4G association-specific continuous terrain evidence after extending solved F4F corrections
 * laterally through the accepted F4A cross-section field.
 */
public record SkyIslandWorldHeadRefinedTerrainPlan(
        SkyIslandWorldWaterHeadRefinementPlan headRefinement,
        List<SkyIslandHydraulicReachGeometry> reaches,
        Set<Long> refinedReachIdentities,
        List<SkyIslandGeomorphicReachQualification> postRefinementQualifications,
        SkyIslandQualifiedFluvialTerrainField terrainField) {

    public SkyIslandWorldHeadRefinedTerrainPlan {
        headRefinement = Objects.requireNonNull(headRefinement, "headRefinement");
        reaches = List.copyOf(reaches);
        refinedReachIdentities = Set.copyOf(refinedReachIdentities);
        postRefinementQualifications = List.copyOf(postRefinementQualifications);
        terrainField = Objects.requireNonNull(terrainField, "terrainField");
        reaches.forEach(value -> Objects.requireNonNull(value, "reach"));
        postRefinementQualifications.forEach(value -> Objects.requireNonNull(value, "qualification"));

        if (reaches.size() != terrainField.acceptedReaches().size()
                || reaches.size() != postRefinementQualifications.size()) {
            throw new IllegalArgumentException(
                    "F4G reach, field, and post-D2 qualification cardinalities must match");
        }
        if (postRefinementQualifications.stream().anyMatch(q -> !q.accepted())) {
            throw new IllegalArgumentException("F4G publishes only post-refinement D2-accepted reaches");
        }
    }

    public int refinedReachCount() {
        return refinedReachIdentities.size();
    }
}
