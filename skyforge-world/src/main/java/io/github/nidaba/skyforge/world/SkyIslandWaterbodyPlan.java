package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Deterministic retained-waterbody semantics for one authored island. */
public record SkyIslandWaterbodyPlan(
        SkyIslandDescriptor descriptor,
        List<SkyIslandWaterbodyCandidate> candidates) {

    public SkyIslandWaterbodyPlan {
        Objects.requireNonNull(descriptor, "descriptor");
        candidates = List.copyOf(candidates);
    }

    public long count(SkyIslandWaterbodyKind kind) {
        return candidates.stream().filter(candidate -> candidate.kind() == kind).count();
    }
}
