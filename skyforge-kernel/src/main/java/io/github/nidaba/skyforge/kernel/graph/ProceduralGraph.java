package io.github.nidaba.skyforge.kernel.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** An immutable, typed, directed acyclic graph with one declared output node. */
public record ProceduralGraph(List<GraphNode> nodes, NodeId output) {
    /**
     * Creates and validates a graph snapshot.
     *
     * @throws NullPointerException if the node collection, a node, or {@code output} is {@code null}
     * @throws GraphValidationException if graph identity, references, types, or acyclicity are invalid
     */
    public ProceduralGraph {
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(output, "output");
        nodes = List.copyOf(nodes);
        validate(nodes, output);
    }

    /** Returns the node identified by {@code id}. */
    public GraphNode requireNode(NodeId id) {
        Objects.requireNonNull(id, "id");
        for (GraphNode node : nodes) {
            if (node.id().equals(id)) {
                return node;
            }
        }
        throw new IllegalArgumentException("unknown node id: " + id);
    }

    /** Returns the field type exposed by the graph output. */
    public GraphValueType outputType() {
        return requireNode(output).outputType();
    }

    private static void validate(List<GraphNode> nodes, NodeId output) {
        Map<NodeId, GraphNode> byId = new HashMap<>();
        for (GraphNode node : nodes) {
            GraphNode previous = byId.put(node.id(), node);
            if (previous != null) {
                throw new GraphValidationException("duplicate node id: " + node.id());
            }
        }

        if (!byId.containsKey(output)) {
            throw new GraphValidationException("output node does not exist: " + output);
        }

        for (GraphNode node : nodes) {
            validateInputs(node, byId);
        }
        validateAcyclic(nodes, byId);
    }

    private static void validateInputs(GraphNode node, Map<NodeId, GraphNode> byId) {
        List<NodeId> inputs = node.inputs();
        List<GraphValueType> inputTypes = node.inputTypes();
        if (inputs.size() != inputTypes.size()) {
            throw new GraphValidationException("node declares inconsistent input metadata: " + node.id());
        }

        for (int index = 0; index < inputs.size(); index++) {
            NodeId inputId = inputs.get(index);
            GraphNode input = byId.get(inputId);
            if (input == null) {
                throw new GraphValidationException(
                        "node " + node.id() + " references missing input " + inputId);
            }
            GraphValueType requiredType = inputTypes.get(index);
            if (input.outputType() != requiredType) {
                throw new GraphValidationException(
                        "node " + node.id() + " requires " + requiredType + " from input " + inputId
                                + " but found " + input.outputType());
            }
        }
    }

    private static void validateAcyclic(List<GraphNode> nodes, Map<NodeId, GraphNode> byId) {
        Map<NodeId, VisitState> states = new HashMap<>();
        for (GraphNode node : nodes) {
            visit(node.id(), byId, states);
        }
    }

    private static void visit(NodeId id, Map<NodeId, GraphNode> byId, Map<NodeId, VisitState> states) {
        VisitState state = states.get(id);
        if (state == VisitState.VISITING) {
            throw new GraphValidationException("cycle detected at node: " + id);
        }
        if (state == VisitState.VISITED) {
            return;
        }

        states.put(id, VisitState.VISITING);
        for (NodeId input : byId.get(id).inputs()) {
            visit(input, byId, states);
        }
        states.put(id, VisitState.VISITED);
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
