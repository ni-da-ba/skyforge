package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyBiomeCatalog;
import io.github.nidaba.skyforge.model.skyisland.SkyBiomeZoneAssignment;
import java.util.Objects;

/** One closed spatial region assigned to a stable SkyBiome zone. */
public record SkyBiomeSpatialZone(WorldBounds bounds, SkyBiomeZoneAssignment assignment) {
    public SkyBiomeSpatialZone {
        bounds = Objects.requireNonNull(bounds, "bounds");
        assignment = Objects.requireNonNull(assignment, "assignment");
        if (SkyBiomeCatalog.find(assignment.skyBiomeId()).isEmpty()) {
            throw new IllegalArgumentException(
                    "unknown alpha SkyBiome ID: " + assignment.skyBiomeId().value());
        }
    }

    /** Higher-priority habitat regions take precedence where authored bounds overlap. */
    int priority() {
        if (assignment.skyBiomeId().equals(SkyBiomeCatalog.ISLAND_LEE_ID)) {
            return 2;
        }
        if (assignment.skyBiomeId().equals(SkyBiomeCatalog.CLOUD_SHELF_ID)) {
            return 1;
        }
        return 0;
    }
}
