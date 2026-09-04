package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Sparse exterior-exposure intent for the cave systems of one authored island. */
public record SkyIslandCaveExposurePlan(
        SkyIslandDescriptor descriptor,
        SkyIslandCaveGeometryPlan geometry,
        List<SkyIslandCaveExposureIntent> intents) {

    public SkyIslandCaveExposurePlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        geometry = Objects.requireNonNull(geometry, "geometry");
        intents = List.copyOf(intents);
        intents.forEach(intent -> Objects.requireNonNull(intent, "exposure intent"));
        if (intents.size() > geometry.systems().size()) {
            throw new IllegalArgumentException("first-generation exposure plan allows at most one intent per cave system");
        }
        long distinctSystems = intents.stream().map(SkyIslandCaveExposureIntent::systemId).distinct().count();
        if (distinctSystems != intents.size()) {
            throw new IllegalArgumentException("first-generation exposure plan contains duplicate system intents");
        }
    }

    public int exposedSystemCount() {
        return intents.size();
    }

    public int sealedSystemCount() {
        return geometry.systems().size() - exposedSystemCount();
    }

    public long upperSurfaceCount() {
        return intents.stream().filter(intent -> intent.side() == SkyIslandCaveExposureSide.UPPER_SURFACE).count();
    }

    public long undersideCount() {
        return intents.stream().filter(intent -> intent.side() == SkyIslandCaveExposureSide.UNDERSIDE).count();
    }
}
