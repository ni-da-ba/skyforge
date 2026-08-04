package io.github.nidaba.skyforge.kernel.graph;

import java.util.List;

/** An immutable, typed operation in a procedural graph. */
public sealed interface GraphNode permits ArithmeticNode, ConstantNode, CoordinateNode {
    /** Returns this node's graph-local identifier. */
    NodeId id();

    /** Returns the stable family of operation represented by this node. */
    NodeKind kind();

    /** Returns the field type produced by this node. */
    GraphValueType outputType();

    /** Returns ordered references to this node's dependencies. */
    List<NodeId> inputs();

    /** Returns the required type of each ordered input. */
    List<GraphValueType> inputTypes();
}
