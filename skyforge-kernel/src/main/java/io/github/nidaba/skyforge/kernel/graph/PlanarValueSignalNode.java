package io.github.nidaba.skyforge.kernel.graph;

import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import io.github.nidaba.skyforge.kernel.signal.PlanarValueSignal;
import java.util.List;
import java.util.Objects;

/** A versioned seeded signal sampled in the horizontal x-z plane. */
public record PlanarValueSignalNode(
        NodeId id,
        GraphValueType outputType,
        int signalVersion,
        int seedVersion,
        long rootSeed,
        String namespace,
        double scale) implements GraphNode {
    /** Validates the signal contract and its semantic seed identity. */
    public PlanarValueSignalNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(outputType, "outputType");
        if (signalVersion != PlanarValueSignal.VERSION) {
            throw new IllegalArgumentException("unsupported planar value signal version: " + signalVersion);
        }
        if (seedVersion != SeedDerivation.VERSION) {
            throw new IllegalArgumentException("unsupported seed derivation version: " + seedVersion);
        }
        SeedDerivation.requireNamespace(namespace);
        if (!Double.isFinite(scale) || scale <= 0.0) {
            throw new IllegalArgumentException("scale must be finite and greater than zero");
        }
    }

    @Override
    public NodeKind kind() {
        return NodeKind.PLANAR_VALUE_SIGNAL;
    }

    @Override
    public List<NodeId> inputs() {
        return List.of();
    }

    @Override
    public List<GraphValueType> inputTypes() {
        return List.of();
    }
}
