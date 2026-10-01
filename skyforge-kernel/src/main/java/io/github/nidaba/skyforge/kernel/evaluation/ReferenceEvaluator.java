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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The normative, deliberately simple evaluator for validated procedural graphs.
 *
 * <p>Each exposed field compiles graph-local node ids to stable array indexes once. Every sample
 * then uses thread-local scratch arrays with generation stamps, so evaluation remains independent
 * of declaration order, prior samples, and shared mutable state without allocating a map or
 * linearly scanning the graph for every node lookup.
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
        EvaluationPlan plan = EvaluationPlan.compile(graph);
        return coordinate -> plan.sample2(Objects.requireNonNull(coordinate, "coordinate"));
    }

    /**
     * Exposes a three-dimensional graph as a scalar field.
     *
     * @throws NullPointerException if {@code graph} is {@code null}
     * @throws IllegalArgumentException if the graph does not produce a three-dimensional field
     */
    public ScalarField3 field3(ProceduralGraph graph) {
        requireOutputType(graph, GraphValueType.SCALAR_FIELD_3);
        EvaluationPlan plan = EvaluationPlan.compile(graph);
        return coordinate -> plan.sample3(Objects.requireNonNull(coordinate, "coordinate"));
    }

    private static void requireOutputType(ProceduralGraph graph, GraphValueType expected) {
        Objects.requireNonNull(graph, "graph");
        if (graph.outputType() != expected) {
            throw new IllegalArgumentException(
                    "expected graph output " + expected + " but found " + graph.outputType());
        }
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

    private static final class EvaluationPlan {
        private final GraphNode[] nodes;
        private final int[][] inputs;
        private final int output;
        private final ThreadLocal<Scratch> scratch;

        private EvaluationPlan(GraphNode[] nodes, int[][] inputs, int output) {
            this.nodes = nodes;
            this.inputs = inputs;
            this.output = output;
            this.scratch = ThreadLocal.withInitial(() -> new Scratch(nodes.length));
        }

        private static EvaluationPlan compile(ProceduralGraph graph) {
            List<GraphNode> source = graph.nodes();
            GraphNode[] nodes = source.toArray(GraphNode[]::new);
            Map<NodeId, Integer> indexes = new HashMap<>(nodes.length * 2);
            for (int index = 0; index < nodes.length; index++) {
                indexes.put(nodes[index].id(), index);
            }

            int[][] inputs = new int[nodes.length][];
            for (int index = 0; index < nodes.length; index++) {
                List<NodeId> nodeInputs = nodes[index].inputs();
                int[] resolved = new int[nodeInputs.size()];
                for (int input = 0; input < nodeInputs.size(); input++) {
                    Integer resolvedIndex = indexes.get(nodeInputs.get(input));
                    if (resolvedIndex == null) {
                        throw new IllegalStateException(
                                "validated graph contains unresolved input: " + nodeInputs.get(input));
                    }
                    resolved[input] = resolvedIndex;
                }
                inputs[index] = resolved;
            }

            Integer output = indexes.get(graph.output());
            if (output == null) {
                throw new IllegalStateException("validated graph output is unresolved: " + graph.output());
            }
            return new EvaluationPlan(nodes, inputs, output);
        }

        private double sample2(Coordinate2 coordinate) {
            Scratch local = scratch.get();
            int generation = local.beginSample();
            return evaluate2(output, coordinate, local, generation);
        }

        private double sample3(Coordinate3 coordinate) {
            Scratch local = scratch.get();
            int generation = local.beginSample();
            return evaluate3(output, coordinate, local, generation);
        }

        private double evaluate2(
                int index,
                Coordinate2 coordinate,
                Scratch local,
                int generation) {
            if (local.stamps[index] == generation) {
                return local.values[index];
            }

            GraphNode node = nodes[index];
            double value;
            if (node instanceof ConstantNode constant) {
                value = constant.value();
            } else if (node instanceof CoordinateNode coordinateNode) {
                value = coordinate2Component(coordinateNode.axis(), coordinate);
            } else if (node instanceof ArithmeticNode arithmetic) {
                value = apply(
                        arithmetic.operator(),
                        evaluate2(inputs[index][0], coordinate, local, generation),
                        evaluate2(inputs[index][1], coordinate, local, generation));
            } else if (node instanceof IntersectionNode) {
                throw new IllegalStateException("intersection nodes require a three-dimensional graph");
            } else if (node instanceof PlanarValueSignalNode signal) {
                value = PlanarValueSignal.sample(signal, coordinate.x(), coordinate.z());
            } else {
                throw new IllegalStateException("unsupported node kind: " + node.kind());
            }
            local.values[index] = value;
            local.stamps[index] = generation;
            return value;
        }

        private double evaluate3(
                int index,
                Coordinate3 coordinate,
                Scratch local,
                int generation) {
            if (local.stamps[index] == generation) {
                return local.values[index];
            }

            GraphNode node = nodes[index];
            double value;
            if (node instanceof ConstantNode constant) {
                value = constant.value();
            } else if (node instanceof CoordinateNode coordinateNode) {
                value = coordinate3Component(coordinateNode.axis(), coordinate);
            } else if (node instanceof ArithmeticNode arithmetic) {
                value = apply(
                        arithmetic.operator(),
                        evaluate3(inputs[index][0], coordinate, local, generation),
                        evaluate3(inputs[index][1], coordinate, local, generation));
            } else if (node instanceof IntersectionNode) {
                value = Math.min(
                        evaluate3(inputs[index][0], coordinate, local, generation),
                        evaluate3(inputs[index][1], coordinate, local, generation));
            } else if (node instanceof PlanarValueSignalNode signal) {
                value = PlanarValueSignal.sample(signal, coordinate.x(), coordinate.z());
            } else {
                throw new IllegalStateException("unsupported node kind: " + node.kind());
            }
            local.values[index] = value;
            local.stamps[index] = generation;
            return value;
        }
    }

    private static final class Scratch {
        private final double[] values;
        private final int[] stamps;
        private int generation;

        private Scratch(int size) {
            values = new double[size];
            stamps = new int[size];
        }

        private int beginSample() {
            if (generation == Integer.MAX_VALUE) {
                Arrays.fill(stamps, 0);
                generation = 1;
            } else {
                generation++;
            }
            return generation;
        }
    }
}
