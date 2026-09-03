package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One coarse dry riparian transition cell associated with an accepted channel segment. */
public record SkyIslandRiparianCell(
        int watershedCellIndex,
        SkyIslandLocalPosition position,
        SkyIslandRiparianKind kind,
        int channelSourceCellIndex,
        int channelDownstreamCellIndex,
        SkyIslandChannelRole channelRole,
        int streamOrder,
        int channelDistance,
        double channelInfluence,
        double saturationPotential,
        double retentionPotential,
        double riparianPotential) {

    public SkyIslandRiparianCell {
        if (watershedCellIndex < 0 || channelSourceCellIndex < 0 || channelDownstreamCellIndex < 0) {
            throw new IllegalArgumentException("invalid watershed/channel cell identity");
        }
        if (channelSourceCellIndex == channelDownstreamCellIndex) {
            throw new IllegalArgumentException("channel endpoints must differ");
        }
        position = Objects.requireNonNull(position, "position");
        kind = Objects.requireNonNull(kind, "kind");
        channelRole = Objects.requireNonNull(channelRole, "channelRole");
        if (streamOrder < 1) {
            throw new IllegalArgumentException("streamOrder must be positive");
        }
        if (channelDistance < 1 || channelDistance > 2) {
            throw new IllegalArgumentException("channelDistance must be one or two coarse cells");
        }
        requireNormalized("channelInfluence", channelInfluence);
        requireNormalized("saturationPotential", saturationPotential);
        requireNormalized("retentionPotential", retentionPotential);
        requireNormalized("riparianPotential", riparianPotential);
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
