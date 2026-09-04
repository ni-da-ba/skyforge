package io.github.nidaba.skyforge.world;

import java.util.Objects;

/** One semantic topological connection between two authored cave nodes. */
public record SkyIslandCaveLink(
        int linkId,
        int firstNodeId,
        int secondNodeId,
        SkyIslandCaveConnectionKind kind,
        double normalizedLength,
        double fractureSupport,
        double aquiferSupport) {

    public SkyIslandCaveLink {
        if (linkId < 0 || firstNodeId < 0 || secondNodeId < 0 || firstNodeId == secondNodeId) {
            throw new IllegalArgumentException("cave link identifiers must be valid and distinct");
        }
        kind = Objects.requireNonNull(kind, "kind");
        if (!Double.isFinite(normalizedLength) || normalizedLength <= 0.0) {
            throw new IllegalArgumentException("normalizedLength must be positive and finite");
        }
        requireNormalized("fractureSupport", fractureSupport);
        requireNormalized("aquiferSupport", aquiferSupport);
    }

    public boolean touches(int nodeId) {
        return firstNodeId == nodeId || secondNodeId == nodeId;
    }

    public int other(int nodeId) {
        if (firstNodeId == nodeId) {
            return secondNodeId;
        }
        if (secondNodeId == nodeId) {
            return firstNodeId;
        }
        throw new IllegalArgumentException("node is not incident to this cave link");
    }

    private static void requireNormalized(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
    }
}
