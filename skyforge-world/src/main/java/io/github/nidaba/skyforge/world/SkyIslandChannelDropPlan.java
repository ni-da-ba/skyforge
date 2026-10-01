package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Backend-neutral discrete channel-drop plan for one authored island. */
public record SkyIslandChannelDropPlan(
        SkyIslandDescriptor descriptor,
        List<SkyIslandChannelDrop> drops) {

    public SkyIslandChannelDropPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        drops = List.copyOf(drops);
        drops.forEach(drop -> Objects.requireNonNull(drop, "drop"));
    }

    public long count(SkyIslandChannelDropKind kind) {
        return drops.stream().filter(drop -> drop.kind() == kind).count();
    }

    public double maxDropPotential() {
        return drops.stream().mapToDouble(SkyIslandChannelDrop::dropPotential).max().orElse(0.0);
    }

    public double maxPersistencePotential() {
        return drops.stream().mapToDouble(SkyIslandChannelDrop::persistencePotential).max().orElse(0.0);
    }

    public double maxPlungePoolPotential() {
        return drops.stream().mapToDouble(SkyIslandChannelDrop::plungePoolPotential).max().orElse(0.0);
    }
}
