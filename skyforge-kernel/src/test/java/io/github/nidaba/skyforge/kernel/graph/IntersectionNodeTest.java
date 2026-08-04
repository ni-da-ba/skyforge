package io.github.nidaba.skyforge.kernel.graph;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class IntersectionNodeTest {
    @Test
    void fixesTheOperationToTwoThreeDimensionalDensityInputs() {
        NodeId left = new NodeId("upper-constraint");
        NodeId right = new NodeId("lower-constraint");
        IntersectionNode node = new IntersectionNode(new NodeId("solid"), left, right);

        assertAll(
                () -> assertEquals(NodeKind.INTERSECTION, node.kind()),
                () -> assertEquals(GraphValueType.SCALAR_FIELD_3, node.outputType()),
                () -> assertEquals(List.of(left, right), node.inputs()),
                () -> assertEquals(
                        List.of(GraphValueType.SCALAR_FIELD_3, GraphValueType.SCALAR_FIELD_3),
                        node.inputTypes()));
    }

    @Test
    void graphValidationRejectsTwoDimensionalIntersectionInputs() {
        NodeId left = new NodeId("left");
        NodeId right = new NodeId("right");
        NodeId output = new NodeId("output");

        assertThrows(
                GraphValidationException.class,
                () -> new ProceduralGraph(
                        List.of(
                                new ConstantNode(left, GraphValueType.SCALAR_FIELD_2, 1.0),
                                new ConstantNode(right, GraphValueType.SCALAR_FIELD_3, 2.0),
                                new IntersectionNode(output, left, right)),
                        output));
    }

    @Test
    void rejectsNullIdentityAndDependencies() {
        NodeId id = new NodeId("node");

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new IntersectionNode(null, id, id)),
                () -> assertThrows(NullPointerException.class, () -> new IntersectionNode(id, null, id)),
                () -> assertThrows(NullPointerException.class, () -> new IntersectionNode(id, id, null)));
    }
}
