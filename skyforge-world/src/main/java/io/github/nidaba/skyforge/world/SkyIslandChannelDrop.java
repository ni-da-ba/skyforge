package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One discrete semantic drop event selected from routed channel and edge-outflow evidence. */
public record SkyIslandChannelDrop(
        SkyIslandChannelDropKind kind,
        int sourceCellIndex,
        int downstreamCellIndex,
        SkyIslandLocalPosition position,
        double dropPotential,
        double dischargePotential,
        double persistencePotential,
        double plungePoolPotential) {

    public SkyIslandChannelDrop {
        kind = Objects.requireNonNull(kind, "kind");
        position = Objects.requireNonNull(position, "position");
        if (sourceCellIndex < 0 || downstreamCellIndex < -1) {
            throw new IllegalArgumentException("invalid channel-drop identity");
        }
        if (kind == SkyIslandChannelDropKind.EDGE_FALL && downstreamCellIndex != -1) {
            throw new IllegalArgumentException("edge fall must leave the island domain");
        }
        if (kind != SkyIslandChannelDropKind.EDGE_FALL && downstreamCellIndex < 0) {
            throw new IllegalArgumentException("interior drop must reference a downstream channel cell");
        }
        requireNormalized("dropPotential", dropPotential);
        requireNormalized("dischargePotential", dischargePotential);
        requireNormalized("persistencePotential", persistencePotential);
        requireNormalized("plungePoolPotential", plungePoolPotential);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
