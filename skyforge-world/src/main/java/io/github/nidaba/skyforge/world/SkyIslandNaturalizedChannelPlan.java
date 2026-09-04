package io.github.nidaba.skyforge.world;

import io.github.nidaba.skyforge.model.skyisland.SkyIslandDescriptor;
import java.util.List;
import java.util.Objects;

/** Backend-neutral sub-grid centerline geometry for the accepted semantic channel network. */
public record SkyIslandNaturalizedChannelPlan(
        SkyIslandDescriptor descriptor,
        double planningSpacing,
        List<SkyIslandNaturalizedChannelPath> paths) {

    public SkyIslandNaturalizedChannelPlan {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        if (!Double.isFinite(planningSpacing) || planningSpacing <= 0.0) {
            throw new IllegalArgumentException("planningSpacing must be finite and positive");
        }
        paths = List.copyOf(paths);
        paths.forEach(path -> Objects.requireNonNull(path, "path"));
    }

    public double maxChordDeviation() {
        return paths.stream()
                .mapToDouble(SkyIslandNaturalizedChannelPath::maxChordDeviation)
                .max()
                .orElse(0.0);
    }

    public double meanLengthRatio() {
        return paths.stream()
                .mapToDouble(SkyIslandNaturalizedChannelPath::lengthRatio)
                .average()
                .orElse(1.0);
    }

    public double maxLengthRatio() {
        return paths.stream()
                .mapToDouble(SkyIslandNaturalizedChannelPath::lengthRatio)
                .max()
                .orElse(1.0);
    }

    public long count(SkyIslandChannelProfileKind kind) {
        return paths.stream().filter(path -> path.profile().kind() == kind).count();
    }
}
