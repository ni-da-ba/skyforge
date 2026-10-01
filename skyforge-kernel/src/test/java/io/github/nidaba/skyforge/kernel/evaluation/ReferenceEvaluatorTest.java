package io.github.nidaba.skyforge.kernel.evaluation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import io.github.nidaba.skyforge.kernel.signal.PlanarValueSignal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ReferenceEvaluatorTest {
    private final ReferenceEvaluator evaluator = new ReferenceEvaluator();

    @Test
    void evaluatesEveryInitialOperatorInAHandCalculatedTwoDimensionalGraph() {
        ProceduralGraph graph = twoDimensionalGraph(orderedTwoDimensionalNodes());
        ScalarField2 field = evaluator.field2(graph);

        assertAll(
                () -> assertEquals(-6.0, field.sample(new Coordinate2(6.0, -3.0))),
                () -> assertEquals(2.0, field.sample(new Coordinate2(-1.0, 8.0))));
    }

    @Test
    void evaluatesAllThreeCoordinateAxesInAHandCalculatedThreeDimensionalGraph() {
        NodeId x = new NodeId("x");
        NodeId y = new NodeId("y");
        NodeId z = new NodeId("z");
        NodeId horizontal = new NodeId("horizontal");
        NodeId output = new NodeId("output");
        ProceduralGraph graph = new ProceduralGraph(
                List.of(
                        new CoordinateNode(x, GraphValueType.SCALAR_FIELD_3, CoordinateAxis.X),
                        new CoordinateNode(y, GraphValueType.SCALAR_FIELD_3, CoordinateAxis.Y),
                        new CoordinateNode(z, GraphValueType.SCALAR_FIELD_3, CoordinateAxis.Z),
                        new ArithmeticNode(
                                horizontal,
                                GraphValueType.SCALAR_FIELD_3,
                                ArithmeticOperator.ADD,
                                x,
                                z),
                        new ArithmeticNode(
                                output,
                                GraphValueType.SCALAR_FIELD_3,
                                ArithmeticOperator.SUBTRACT,
                                horizontal,
                                y)),
                output);
        ScalarField3 field = evaluator.field3(graph);

        assertEquals(-4.75, field.sample(new Coordinate3(1.5, 2.25, -4.0)));
    }

    @Test
    void preservesConstantRawBitsAcrossRepeatedSamples() {
        NodeId output = new NodeId("negative-zero");
        ProceduralGraph graph = new ProceduralGraph(
                List.of(new ConstantNode(output, GraphValueType.SCALAR_FIELD_2, -0.0)), output);
        ScalarField2 field = evaluator.field2(graph);
        Coordinate2 coordinate = new Coordinate2(17.0, -23.0);

        long expected = Double.doubleToRawLongBits(-0.0);
        assertAll(
                () -> assertEquals(expected, Double.doubleToRawLongBits(field.sample(coordinate))),
                () -> assertEquals(expected, Double.doubleToRawLongBits(field.sample(coordinate))));
    }

    @Test
    void evaluatesPlanarSignalsIdenticallyInTwoAndThreeDimensions() {
        NodeId output = new NodeId("signal");
        PlanarValueSignalNode signal2 = new PlanarValueSignalNode(
                output,
                GraphValueType.SCALAR_FIELD_2,
                PlanarValueSignal.VERSION,
                SeedDerivation.VERSION,
                17L,
                "island.height-detail",
                32.0);
        PlanarValueSignalNode signal3 = new PlanarValueSignalNode(
                output,
                GraphValueType.SCALAR_FIELD_3,
                PlanarValueSignal.VERSION,
                SeedDerivation.VERSION,
                17L,
                "island.height-detail",
                32.0);
        ScalarField2 field2 = evaluator.field2(new ProceduralGraph(List.of(signal2), output));
        ScalarField3 field3 = evaluator.field3(new ProceduralGraph(List.of(signal3), output));
        long expected = Double.doubleToRawLongBits(field2.sample(new Coordinate2(12.5, -7.25)));

        assertAll(
                () -> assertEquals(
                        expected,
                        Double.doubleToRawLongBits(field3.sample(new Coordinate3(12.5, -1000.0, -7.25)))),
                () -> assertEquals(
                        expected,
                        Double.doubleToRawLongBits(field3.sample(new Coordinate3(12.5, 1000.0, -7.25)))));
    }

    @Test
    void intersectsPositiveInsideDensityConstraintsWithExactMinimumSemantics() {
        NodeId upper = new NodeId("upper");
        NodeId lower = new NodeId("lower");
        NodeId output = new NodeId("solid");
        ProceduralGraph graph = new ProceduralGraph(
                List.of(
                        new ConstantNode(upper, GraphValueType.SCALAR_FIELD_3, 7.5),
                        new ConstantNode(lower, GraphValueType.SCALAR_FIELD_3, -2.25),
                        new IntersectionNode(output, upper, lower)),
                output);
        ScalarField3 field = evaluator.field3(graph);

        assertAll(
                () -> assertEquals(-2.25, field.sample(new Coordinate3(0.0, 0.0, 0.0))),
                () -> assertEquals(
                        Double.doubleToRawLongBits(-2.25),
                        Double.doubleToRawLongBits(field.sample(new Coordinate3(1.0, 2.0, 3.0)))));
    }

    @Test
    void intersectionPreservesNegativeZeroAndPropagatesNonfiniteArithmetic() {
        NodeId positiveZero = new NodeId("positive-zero");
        NodeId negativeZero = new NodeId("negative-zero");
        NodeId zeroIntersection = new NodeId("zero-intersection");
        ProceduralGraph signedZeroGraph = new ProceduralGraph(
                List.of(
                        new ConstantNode(positiveZero, GraphValueType.SCALAR_FIELD_3, 0.0),
                        new ConstantNode(negativeZero, GraphValueType.SCALAR_FIELD_3, -0.0),
                        new IntersectionNode(zeroIntersection, positiveZero, negativeZero)),
                zeroIntersection);

        NodeId zero = new NodeId("zero");
        NodeId invalid = new NodeId("invalid");
        NodeId one = new NodeId("one");
        NodeId invalidIntersection = new NodeId("invalid-intersection");
        ProceduralGraph nonfiniteGraph = new ProceduralGraph(
                List.of(
                        new ConstantNode(zero, GraphValueType.SCALAR_FIELD_3, 0.0),
                        new ConstantNode(one, GraphValueType.SCALAR_FIELD_3, 1.0),
                        new ArithmeticNode(
                                invalid,
                                GraphValueType.SCALAR_FIELD_3,
                                ArithmeticOperator.DIVIDE,
                                zero,
                                zero),
                        new IntersectionNode(invalidIntersection, one, invalid)),
                invalidIntersection);

        assertAll(
                () -> assertEquals(
                        Double.doubleToRawLongBits(-0.0),
                        Double.doubleToRawLongBits(evaluator.field3(signedZeroGraph)
                                .sample(new Coordinate3(0.0, 0.0, 0.0)))),
                () -> assertTrue(Double.isNaN(evaluator.field3(nonfiniteGraph)
                        .sample(new Coordinate3(0.0, 0.0, 0.0)))));
    }

    @Test
    void resultDoesNotDependOnNodeDeclarationOrSamplingOrder() {
        List<GraphNode> reversed = new ArrayList<>(orderedTwoDimensionalNodes());
        Collections.reverse(reversed);
        ScalarField2 ordered = evaluator.field2(twoDimensionalGraph(orderedTwoDimensionalNodes()));
        ScalarField2 reordered = evaluator.field2(twoDimensionalGraph(reversed));

        List<Coordinate2> coordinates = List.of(
                new Coordinate2(6.0, -3.0),
                new Coordinate2(-1.0, 8.0),
                new Coordinate2(0.25, -0.5));
        List<Coordinate2> reverseSamplingOrder = new ArrayList<>(coordinates);
        Collections.reverse(reverseSamplingOrder);
        Map<Coordinate2, Long> reorderedBits = new HashMap<>();
        for (Coordinate2 coordinate : reverseSamplingOrder) {
            reorderedBits.put(coordinate, Double.doubleToRawLongBits(reordered.sample(coordinate)));
        }

        for (Coordinate2 coordinate : coordinates) {
            assertEquals(
                    Double.doubleToRawLongBits(ordered.sample(coordinate)),
                    reorderedBits.get(coordinate).longValue());
        }
    }

    @Test
    void rejectsTheWrongGraphDomainAndNullInputs() {
        NodeId output2 = new NodeId("output-2");
        ProceduralGraph graph2 = new ProceduralGraph(
                List.of(new ConstantNode(output2, GraphValueType.SCALAR_FIELD_2, 1.0)), output2);
        NodeId output3 = new NodeId("output-3");
        ProceduralGraph graph3 = new ProceduralGraph(
                List.of(new ConstantNode(output3, GraphValueType.SCALAR_FIELD_3, 1.0)), output3);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> evaluator.field2(graph3)),
                () -> assertThrows(IllegalArgumentException.class, () -> evaluator.field3(graph2)),
                () -> assertThrows(NullPointerException.class, () -> evaluator.field2(null)),
                () -> assertThrows(
                        NullPointerException.class,
                        () -> evaluator.field2(graph2).sample(null)));
    }

    private static ProceduralGraph twoDimensionalGraph(List<GraphNode> nodes) {
        return new ProceduralGraph(nodes, new NodeId("output"));
    }

    private static List<GraphNode> orderedTwoDimensionalNodes() {
        NodeId x = new NodeId("x");
        NodeId z = new NodeId("z");
        NodeId two = new NodeId("two");
        NodeId four = new NodeId("four");
        NodeId shifted = new NodeId("shifted");
        NodeId scaled = new NodeId("scaled");
        NodeId output = new NodeId("output");
        return List.of(
                new CoordinateNode(x, GraphValueType.SCALAR_FIELD_2, CoordinateAxis.X),
                new CoordinateNode(z, GraphValueType.SCALAR_FIELD_2, CoordinateAxis.Z),
                new ConstantNode(two, GraphValueType.SCALAR_FIELD_2, 2.0),
                new ConstantNode(four, GraphValueType.SCALAR_FIELD_2, 4.0),
                new ArithmeticNode(
                        shifted,
                        GraphValueType.SCALAR_FIELD_2,
                        ArithmeticOperator.ADD,
                        x,
                        two),
                new ArithmeticNode(
                        scaled,
                        GraphValueType.SCALAR_FIELD_2,
                        ArithmeticOperator.MULTIPLY,
                        shifted,
                        z),
                new ArithmeticNode(
                        output,
                        GraphValueType.SCALAR_FIELD_2,
                        ArithmeticOperator.DIVIDE,
                        scaled,
                        four));
    }
}
