package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Backend-neutral semantic cave geometry for one authored island. */
public record SkyIslandCaveGeometryPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandCaveSystemPlan topology,
        List<SkyIslandCaveSystemGeometry> systems) {

    public SkyIslandCaveGeometryPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        topology = Objects.requireNonNull(topology, "topology");
        systems = List.copyOf(systems);
        systems.forEach(system -> Objects.requireNonNull(system, "cave geometry system"));
    }

    public int chamberCount() {
        return systems.stream().mapToInt(system -> system.chambers().size()).sum();
    }

    public int passageCount() {
        return systems.stream().mapToInt(system -> system.passages().size()).sum();
    }
}
