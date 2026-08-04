package io.github.nidaba.skyforge.kernel.graph;

import java.util.List;
import java.util.Objects;

/** A scalar field equal to one component of its sample coordinate. */
public record CoordinateNode(NodeId id, GraphValueType outputType, CoordinateAxis axis) implements GraphNode {
    /**
     * Creates a coordinate-component node.
     *
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the vertical axis is requested in two dimensions
     */
    public CoordinateNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(outputType, "outputType");
        Objects.requireNonNull(axis, "axis");
        if (outputType == GraphValueType.SCALAR_FIELD_2 && axis == CoordinateAxis.Y) {
            throw new IllegalArgumentException("SCALAR_FIELD_2 has no y coordinate");
        }
    }

    @Override
    public NodeKind kind() {
        return NodeKind.COORDINATE;
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
