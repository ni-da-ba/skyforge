package io.github.nidaba.skyforge.kernel.graph;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ProceduralGraphTest {
    private static final NodeId X = new NodeId("x");
    private static final NodeId OFFSET = new NodeId("offset");
    private static final NodeId OUTPUT = new NodeId("output");

    @Test
    void acceptsAValidTypedDagAndSnapshotsItsNodeOrder() {
        List<GraphNode> source = new ArrayList<>(validNodes());
        ProceduralGraph graph = new ProceduralGraph(source, OUTPUT);
        source.clear();

        assertAll(
                () -> assertEquals(3, graph.nodes().size()),
                () -> assertEquals(GraphValueType.SCALAR_FIELD_2, graph.outputType()),
                () -> assertEquals(NodeKind.ARITHMETIC, graph.requireNode(OUTPUT).kind()),
                () -> assertThrows(UnsupportedOperationException.class, () -> graph.nodes().clear()),
                () -> assertThrows(IllegalArgumentException.class, () -> graph.requireNode(new NodeId("missing"))));
    }

    @Test
    void rejectsDuplicateAndMissingNodeIdentities() {
        ConstantNode duplicate = new ConstantNode(X, GraphValueType.SCALAR_FIELD_2, 4.0);

        assertAll(
                () -> assertThrows(
                        GraphValidationException.class,
                        () -> new ProceduralGraph(List.of(validNodes().getFirst(), duplicate), X)),
                () -> assertThrows(
                        GraphValidationException.class,
                        () -> new ProceduralGraph(validNodes(), new NodeId("missing-output"))),
                () -> assertThrows(
                        GraphValidationException.class,
                        () -> new ProceduralGraph(
                                List.of(new ArithmeticNode(
                                        OUTPUT,
                                        GraphValueType.SCALAR_FIELD_2,
                                        ArithmeticOperator.ADD,
                                        X,
                                        new NodeId("missing-input"))),
                                OUTPUT)));
    }

    @Test
    void rejectsTypeMismatches() {
        ConstantNode spatial = new ConstantNode(OFFSET, GraphValueType.SCALAR_FIELD_3, 2.0);
        ArithmeticNode horizontal = new ArithmeticNode(
                OUTPUT,
                GraphValueType.SCALAR_FIELD_2,
                ArithmeticOperator.ADD,
                X,
                OFFSET);

        assertThrows(
                GraphValidationException.class,
                () -> new ProceduralGraph(List.of(validNodes().getFirst(), spatial, horizontal), OUTPUT));
    }

    @Test
    void rejectsCyclesEvenWhenAllReferencesAndTypesExist() {
        NodeId firstId = new NodeId("first");
        NodeId secondId = new NodeId("second");
        ConstantNode base = new ConstantNode(OFFSET, GraphValueType.SCALAR_FIELD_2, 1.0);
        ArithmeticNode first = new ArithmeticNode(
                firstId,
                GraphValueType.SCALAR_FIELD_2,
                ArithmeticOperator.ADD,
                secondId,
                OFFSET);
        ArithmeticNode second = new ArithmeticNode(
                secondId,
                GraphValueType.SCALAR_FIELD_2,
                ArithmeticOperator.SUBTRACT,
                firstId,
                OFFSET);

        assertThrows(
                GraphValidationException.class,
                () -> new ProceduralGraph(List.of(base, first, second), firstId));
    }

    private static List<GraphNode> validNodes() {
        return List.of(
                new CoordinateNode(X, GraphValueType.SCALAR_FIELD_2, CoordinateAxis.X),
                new ConstantNode(OFFSET, GraphValueType.SCALAR_FIELD_2, 2.0),
                new ArithmeticNode(
                        OUTPUT,
                        GraphValueType.SCALAR_FIELD_2,
                        ArithmeticOperator.ADD,
                        X,
                        OFFSET));
    }
}
