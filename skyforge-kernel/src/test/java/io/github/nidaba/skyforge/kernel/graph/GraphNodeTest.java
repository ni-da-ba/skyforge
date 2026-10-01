package io.github.nidaba.skyforge.kernel.graph;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class GraphNodeTest {
    @Test
    void nodeIdsAreHumanReadableAndNeverSilentlyNormalized() {
        assertAll(
                () -> assertEquals("ridge.base", new NodeId("ridge.base").value()),
                () -> assertThrows(NullPointerException.class, () -> new NodeId(null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new NodeId("")),
                () -> assertThrows(IllegalArgumentException.class, () -> new NodeId("  ")),
                () -> assertThrows(IllegalArgumentException.class, () -> new NodeId(" ridge")));
    }

    @Test
    void constantNodesRetainFiniteValuesAndHaveNoInputs() {
        ConstantNode node = new ConstantNode(
                new NodeId("sea-level"), GraphValueType.SCALAR_FIELD_3, -0.0);

        assertAll(
                () -> assertEquals(NodeKind.CONSTANT, node.kind()),
                () -> assertEquals(
                        Double.doubleToRawLongBits(-0.0),
                        Double.doubleToRawLongBits(node.value())),
                () -> assertEquals(List.of(), node.inputs()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new ConstantNode(
                                new NodeId("invalid"), GraphValueType.SCALAR_FIELD_2, Double.NaN)));
    }

    @Test
    void coordinateNodesRespectTheirDomains() {
        CoordinateNode horizontalZ = new CoordinateNode(
                new NodeId("z"), GraphValueType.SCALAR_FIELD_2, CoordinateAxis.Z);

        assertAll(
                () -> assertEquals(NodeKind.COORDINATE, horizontalZ.kind()),
                () -> assertEquals(CoordinateAxis.Z, horizontalZ.axis()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new CoordinateNode(
                                new NodeId("y"), GraphValueType.SCALAR_FIELD_2, CoordinateAxis.Y)));
    }

    @Test
    void arithmeticNodesExposeOrderedInputsAndRequirements() {
        NodeId left = new NodeId("left");
        NodeId right = new NodeId("right");
        ArithmeticNode node = new ArithmeticNode(
                new NodeId("sum"),
                GraphValueType.SCALAR_FIELD_2,
                ArithmeticOperator.ADD,
                left,
                right);

        assertAll(
                () -> assertEquals(NodeKind.ARITHMETIC, node.kind()),
                () -> assertEquals(List.of(left, right), node.inputs()),
                () -> assertEquals(
                        List.of(GraphValueType.SCALAR_FIELD_2, GraphValueType.SCALAR_FIELD_2),
                        node.inputTypes()));
    }

    @Test
    void planarSignalsExposeVersionedSeedIdentityWithoutGraphInputs() {
        PlanarValueSignalNode node = new PlanarValueSignalNode(
                new NodeId("detail"),
                GraphValueType.SCALAR_FIELD_3,
                1,
                1,
                Long.MIN_VALUE,
                "island.height-detail",
                32.0);

        assertAll(
                () -> assertEquals(NodeKind.PLANAR_VALUE_SIGNAL, node.kind()),
                () -> assertEquals(List.of(), node.inputs()),
                () -> assertEquals(List.of(), node.inputTypes()),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new PlanarValueSignalNode(
                                node.id(), node.outputType(), 2, 1, 0L, node.namespace(), node.scale())),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new PlanarValueSignalNode(
                                node.id(), node.outputType(), 1, 1, 0L, node.namespace(), 0.0)));
    }
}
