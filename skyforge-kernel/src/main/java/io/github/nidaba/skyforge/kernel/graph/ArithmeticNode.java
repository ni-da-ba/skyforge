package io.github.nidaba.skyforge.kernel.graph;

import java.util.List;
import java.util.Objects;

/** An ordered binary arithmetic operation over two equally typed scalar fields. */
public record ArithmeticNode(
        NodeId id,
        GraphValueType outputType,
        ArithmeticOperator operator,
        NodeId left,
        NodeId right)
        implements GraphNode {
    /** Creates a binary arithmetic node with explicit ordered dependencies. */
    public ArithmeticNode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(outputType, "outputType");
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }

    @Override
    public NodeKind kind() {
        return NodeKind.ARITHMETIC;
    }

    @Override
    public List<NodeId> inputs() {
        return List.of(left, right);
    }

    @Override
    public List<GraphValueType> inputTypes() {
        return List.of(outputType, outputType);
    }
}
