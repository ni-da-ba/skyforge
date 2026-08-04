package io.github.nidaba.skyforge.kernel.evaluation;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.coordinate.Coordinate3;
import io.github.nidaba.skyforge.kernel.field.ScalarField2;
import io.github.nidaba.skyforge.kernel.field.ScalarField3;
import io.github.nidaba.skyforge.kernel.graph.ArithmeticNode;
import io.github.nidaba.skyforge.kernel.graph.ArithmeticOperator;
import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.CoordinateAxis;
import io.github.nidaba.skyforge.kernel.graph.CoordinateNode;
import io.github.nidaba.skyforge.kernel.graph.GraphNode;
import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.IntersectionNode;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.PlanarValueSignalNode;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.signal.PlanarValueSignal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The normative, deliberately simple evaluator for validated procedural graphs.
 *
 * <p>Each sample is evaluated from graph identity with fresh local memoization. Evaluation
 * therefore does not depend on graph declaration order, prior samples, or shared mutable state.
 */
public final class ReferenceEvaluator {
    /**
     * Exposes a two-dimensional graph as a scalar field.
     *
     * @throws NullPointerException if {@code graph} is {@code null}
     * @throws IllegalArgumentException if the graph does not produce a two-dimensional field
     */
    public ScalarField2 field2(ProceduralGraph graph) {
        requireOutputType(graph, GraphValueType.SCALAR_FIELD_2);
        return coordinate -> sample2(graph, Objects.requireNonNull(coordinate, "coordinate"));
    }

    /**
     * Exposes a three-dimensional graph as a scalar field.
     *
     * @throws NullPointerException if {@code graph} is {@code null}
     * @throws IllegalArgumentException if the graph does not produce a three-dimensional field
     */
    public ScalarField3 field3(ProceduralGraph graph) {
        requireOutputType(graph, GraphValueType.SCALAR_FIELD_3);
        return coordinate -> sample3(graph, Objects.requireNonNull(coordinate, "coordinate"));
    }

    private static void requireOutputType(ProceduralGraph graph, GraphValueType expected) {
        Objects.requireNonNull(graph, "graph");
        if (graph.outputType() != expected) {
            throw new IllegalArgumentException(
                    "expected graph output " + expected + " but found " + graph.outputType());
        }
    }

    private static double sample2(ProceduralGraph graph, Coordinate2 coordinate) {
        return evaluateNode2(graph, graph.output(), coordinate, new HashMap<>());
    }

    private static double sample3(ProceduralGraph graph, Coordinate3 coordinate) {
        return evaluateNode3(graph, graph.output(), coordinate, new HashMap<>());
    }

    private static double evaluateNode2(
            ProceduralGraph graph,
            NodeId id,
            Coordinate2 coordinate,
            Map<NodeId, Double> values) {
        Double cached = values.get(id);
        if (cached != null) {
            return cached;
        }

        GraphNode node = graph.requireNode(id);
        double value;
        if (node instanceof ConstantNode constant) {
            value = constant.value();
        } else if (node instanceof CoordinateNode coordinateNode) {
            value = coordinate2Component(coordinateNode.axis(), coordinate);
        } else if (node instanceof ArithmeticNode arithmetic) {
            value = apply(
                    arithmetic.operator(),
                    evaluateNode2(graph, arithmetic.left(), coordinate, values),
                    evaluateNode2(graph, arithmetic.right(), coordinate, values));
        } else if (node instanceof IntersectionNode) {
            throw new IllegalStateException("intersection nodes require a three-dimensional graph");
        } else if (node instanceof PlanarValueSignalNode signal) {
            value = PlanarValueSignal.sample(signal, coordinate.x(), coordinate.z());
        } else {
            throw new IllegalStateException("unsupported node kind: " + node.kind());
        }
        values.put(id, value);
        return value;
    }

    private static double evaluateNode3(
            ProceduralGraph graph,
            NodeId id,
            Coordinate3 coordinate,
            Map<NodeId, Double> values) {
        Double cached = values.get(id);
        if (cached != null) {
            return cached;
        }

        GraphNode node = graph.requireNode(id);
        double value;
        if (node instanceof ConstantNode constant) {
            value = constant.value();
        } else if (node instanceof CoordinateNode coordinateNode) {
            value = coordinate3Component(coordinateNode.axis(), coordinate);
        } else if (node instanceof ArithmeticNode arithmetic) {
            value = apply(
                    arithmetic.operator(),
                    evaluateNode3(graph, arithmetic.left(), coordinate, values),
                    evaluateNode3(graph, arithmetic.right(), coordinate, values));
        } else if (node instanceof IntersectionNode intersection) {
            value = Math.min(
                    evaluateNode3(graph, intersection.left(), coordinate, values),
                    evaluateNode3(graph, intersection.right(), coordinate, values));
        } else if (node instanceof PlanarValueSignalNode signal) {
            value = PlanarValueSignal.sample(signal, coordinate.x(), coordinate.z());
        } else {
            throw new IllegalStateException("unsupported node kind: " + node.kind());
        }
        values.put(id, value);
        return value;
    }

    private static double coordinate2Component(CoordinateAxis axis, Coordinate2 coordinate) {
        return switch (axis) {
            case X -> coordinate.x();
            case Z -> coordinate.z();
            case Y -> throw new IllegalStateException("two-dimensional graph contains a y-coordinate node");
        };
    }

    private static double coordinate3Component(CoordinateAxis axis, Coordinate3 coordinate) {
        return switch (axis) {
            case X -> coordinate.x();
            case Y -> coordinate.y();
            case Z -> coordinate.z();
        };
    }

    private static double apply(ArithmeticOperator operator, double left, double right) {
        return switch (operator) {
            case ADD -> left + right;
            case SUBTRACT -> left - right;
            case MULTIPLY -> left * right;
            case DIVIDE -> left / right;
        };
    }
}
