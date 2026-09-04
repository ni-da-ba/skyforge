package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Deterministic semantic cave-system topology for one authored island. */
public record SkyIslandCaveSystemPlan(
        SkyIslandDescriptor descriptor,
        SkyIslandGeologicRegionPlan geology,
        List<SkyIslandCaveSystem> systems) {

    public SkyIslandCaveSystemPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        geology = Objects.requireNonNull(geology, "geology");
        systems = List.copyOf(systems);
        systems.forEach(system -> Objects.requireNonNull(system, "cave system"));
    }

    public int nodeCount() {
        return systems.stream().mapToInt(system -> system.nodes().size()).sum();
    }

    public int linkCount() {
        return systems.stream().mapToInt(system -> system.links().size()).sum();
    }

    public long waterInfluencedSystemCount() {
        return systems.stream().filter(SkyIslandCaveSystem::waterInfluenced).count();
    }

    public int crossRegionLinkCount() {
        return (int) systems.stream()
                .flatMap(system -> system.links().stream())
                .filter(link -> link.kind() != SkyIslandCaveConnectionKind.VOID_CONTINUITY)
                .count();
    }
}
