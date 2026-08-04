package io.github.nidaba.skyforge.kernel.graph;

import java.util.List;
import java.util.Objects;

/** Pointwise intersection of two positive-inside three-dimensional signed-density fields. */
public record IntersectionNode(NodeId id, NodeId left, NodeId right) implements GraphNode {
    /** Creates an intersection with two ordered three-dimensional dependencies. */
    public IntersectionNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }

    @Override
    public NodeKind kind() {
        return NodeKind.INTERSECTION;
    }

    @Override
    public GraphValueType outputType() {
        return GraphValueType.SCALAR_FIELD_3;
    }

    @Override
    public List<NodeId> inputs() {
        return List.of(left, right);
    }

    @Override
    public List<GraphValueType> inputTypes() {
        return List.of(GraphValueType.SCALAR_FIELD_3, GraphValueType.SCALAR_FIELD_3);
    }
}
