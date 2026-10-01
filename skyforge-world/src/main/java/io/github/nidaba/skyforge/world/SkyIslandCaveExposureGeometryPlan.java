package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Backend-neutral boundary-connection geometry for accepted cave exposure intents. */
public record SkyIslandCaveExposureGeometryPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandCaveExposurePlan exposurePlan,
        List<SkyIslandCaveExposureConnectionGeometry> connections) {

    public SkyIslandCaveExposureGeometryPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        exposurePlan = Objects.requireNonNull(exposurePlan, "exposurePlan");
        connections = List.copyOf(connections);
        connections.forEach(connection -> Objects.requireNonNull(connection, "exposure connection"));

        if (connections.size() != exposurePlan.intents().size()) {
            throw new IllegalArgumentException("every accepted exposure intent must have exactly one connection geometry");
        }
        long distinctSystems = connections.stream()
                .map(SkyIslandCaveExposureConnectionGeometry::systemId)
                .distinct()
                .count();
        if (distinctSystems != connections.size()) {
            throw new IllegalArgumentException("first-generation exposure geometry allows one connection per system");
        }
    }

    public int connectionCount() {
        return connections.size();
    }

    public long upperSurfaceCount() {
        return connections.stream()
                .filter(connection -> connection.side() == SkyIslandCaveExposureSide.UPPER_SURFACE)
                .count();
    }

    public long undersideCount() {
        return connections.stream()
                .filter(connection -> connection.side() == SkyIslandCaveExposureSide.UNDERSIDE)
                .count();
    }
}
