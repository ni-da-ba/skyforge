package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** Dimensionless realization potentials for one accepted routed channel segment. */
public record SkyIslandChannelProfile(
        SkyIslandChannelSegment segment,
        SkyIslandChannelProfileKind kind,
        double gradientPotential,
        double streamPowerPotential,
        double bankfullWidthPotential,
        double depthPotential,
        double incisionPotential) {

    public SkyIslandChannelProfile {
        segment = Objects.requireNonNull(segment, "segment");
        kind = Objects.requireNonNull(kind, "kind");
        requireNormalized("gradientPotential", gradientPotential);
        requireNormalized("streamPowerPotential", streamPowerPotential);
        requireNormalized("bankfullWidthPotential", bankfullWidthPotential);
        requireNormalized("depthPotential", depthPotential);
        requireNormalized("incisionPotential", incisionPotential);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
