package io.github.nidaba.skyforge.kernel.graph;

import java.util.List;
import java.util.Objects;

/** A scalar field with one finite value at every coordinate. */
public record ConstantNode(NodeId id, GraphValueType outputType, double value) implements GraphNode {
    /**
     * Creates a constant graph node.
     *
     * @throws NullPointerException if {@code id} or {@code outputType} is {@code null}
     * @throws IllegalArgumentException if {@code value} is not finite
     */
    public ConstantNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(outputType, "outputType");
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
    }

    @Override
    public NodeKind kind() {
        return NodeKind.CONSTANT;
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
