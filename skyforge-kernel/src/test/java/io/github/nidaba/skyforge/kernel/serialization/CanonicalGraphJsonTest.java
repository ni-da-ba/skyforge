package io.github.nidaba.skyforge.kernel.serialization;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nidaba.skyforge.kernel.coordinate.Coordinate2;
import io.github.nidaba.skyforge.kernel.evaluation.ReferenceEvaluator;
import io.github.nidaba.skyforge.kernel.graph.ArithmeticNode;
import io.github.nidaba.skyforge.kernel.graph.ArithmeticOperator;
import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.CoordinateAxis;
import io.github.nidaba.skyforge.kernel.graph.CoordinateNode;
import io.github.nidaba.skyforge.kernel.graph.GraphNode;
import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.PlanarValueSignalNode;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import io.github.nidaba.skyforge.kernel.signal.PlanarValueSignal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CanonicalGraphJsonTest {
    private static final String CANONICAL_GRAPH = "{\"schemaVersion\":1,\"output\":\"output\","
            + "\"nodes\":["
            + "{\"id\":\"offset\",\"kind\":\"constant\",\"outputType\":\"scalar-field-2\","
            + "\"value\":\"-0x0.0p0\"},"
            + "{\"id\":\"output\",\"kind\":\"arithmetic\",\"outputType\":\"scalar-field-2\","
            + "\"operator\":\"add\",\"inputs\":[\"x\",\"offset\"]},"
            + "{\"id\":\"x\",\"kind\":\"coordinate\",\"outputType\":\"scalar-field-2\","
            + "\"axis\":\"x\"}]}";

    private final CanonicalGraphJson codec = new CanonicalGraphJson();

    @Test
    void emitsTheSpecifiedCanonicalBytesRegardlessOfDeclarationOrder() {
        List<GraphNode> reversed = new ArrayList<>(nodes());
        Collections.reverse(reversed);

        assertAll(
                () -> assertEquals(CANONICAL_GRAPH, codec.writeString(graph(nodes()))),
                () -> assertArrayEquals(codec.write(graph(nodes())), codec.write(graph(reversed))),
                () -> assertArrayEquals(
                        CANONICAL_GRAPH.getBytes(StandardCharsets.UTF_8), codec.write(graph(nodes()))));
    }

    @Test
    void roundTripPreservesNodesRawBitsCanonicalBytesAndEvaluation() {
        ProceduralGraph source = graph(nodes());
        ProceduralGraph restored = codec.read(codec.write(source));
        long sourceBits = Double.doubleToRawLongBits(
                ((ConstantNode) source.requireNode(new NodeId("offset"))).value());
        long restoredBits = Double.doubleToRawLongBits(
                ((ConstantNode) restored.requireNode(new NodeId("offset"))).value());
        ReferenceEvaluator evaluator = new ReferenceEvaluator();
        Coordinate2 coordinate = new Coordinate2(-0.0, 14.0);

        assertAll(
                () -> assertEquals(sourceBits, restoredBits),
                () -> assertEquals(source.requireNode(new NodeId("x")), restored.requireNode(new NodeId("x"))),
                () -> assertEquals(
                        source.requireNode(new NodeId("output")),
                        restored.requireNode(new NodeId("output"))),
                () -> assertArrayEquals(codec.write(source), codec.write(restored)),
                () -> assertEquals(
                        Double.doubleToRawLongBits(evaluator.field2(source).sample(coordinate)),
                        Double.doubleToRawLongBits(evaluator.field2(restored).sample(coordinate))));
    }

    @Test
    void roundTripPreservesRepresentativeFiniteBinary64BitPatterns() {
        double[] values = {
            0.0,
            -0.0,
            Double.MIN_VALUE,
            -Double.MIN_NORMAL,
            Math.PI,
            Double.MAX_VALUE
        };

        for (double value : values) {
            NodeId output = new NodeId("value");
            ProceduralGraph source = new ProceduralGraph(
                    List.of(new ConstantNode(output, GraphValueType.SCALAR_FIELD_2, value)), output);
            ConstantNode restored = (ConstantNode) codec.read(codec.write(source)).requireNode(output);

            assertEquals(
                    Double.doubleToRawLongBits(value),
                    Double.doubleToRawLongBits(restored.value()),
                    () -> "raw bits changed for " + Double.toHexString(value));
        }
    }

    @Test
    void supportsEveryCurrentTypeAxisAndArithmeticOperator() {
        NodeId x = new NodeId("x");
        NodeId y = new NodeId("y");
        NodeId z = new NodeId("z");
        NodeId one = new NodeId("one");
        NodeId sum = new NodeId("sum");
        NodeId difference = new NodeId("difference");
        NodeId product = new NodeId("product");
        NodeId output = new NodeId("output");
        ProceduralGraph graph = new ProceduralGraph(
                List.of(
                        new CoordinateNode(x, GraphValueType.SCALAR_FIELD_3, CoordinateAxis.X),
                        new CoordinateNode(y, GraphValueType.SCALAR_FIELD_3, CoordinateAxis.Y),
                        new CoordinateNode(z, GraphValueType.SCALAR_FIELD_3, CoordinateAxis.Z),
                        new ConstantNode(one, GraphValueType.SCALAR_FIELD_3, 1.0),
                        new ArithmeticNode(sum, GraphValueType.SCALAR_FIELD_3, ArithmeticOperator.ADD, x, y),
                        new ArithmeticNode(
                                difference,
                                GraphValueType.SCALAR_FIELD_3,
                                ArithmeticOperator.SUBTRACT,
                                sum,
                                z),
                        new ArithmeticNode(
                                product,
                                GraphValueType.SCALAR_FIELD_3,
                                ArithmeticOperator.MULTIPLY,
                                difference,
                                one),
                        new ArithmeticNode(
                                output,
                                GraphValueType.SCALAR_FIELD_3,
                                ArithmeticOperator.DIVIDE,
                                product,
                                one)),
                output);

        ProceduralGraph restored = codec.readString(codec.writeString(graph));

        for (GraphNode node : graph.nodes()) {
            assertEquals(node, restored.requireNode(node.id()));
        }
    }

    @Test
    void emitsSchemaTwoAndRoundTripsThePlanarSignalContract() {
        NodeId output = new NodeId("signal");
        ProceduralGraph graph = new ProceduralGraph(
                List.of(new PlanarValueSignalNode(
                        output,
                        GraphValueType.SCALAR_FIELD_2,
                        PlanarValueSignal.VERSION,
                        SeedDerivation.VERSION,
                        Long.MIN_VALUE,
                        "island.height-detail",
                        32.0)),
                output);
        String expected = "{\"schemaVersion\":2,\"output\":\"signal\",\"nodes\":["
                + "{\"id\":\"signal\",\"kind\":\"planar-value-signal\","
                + "\"outputType\":\"scalar-field-2\",\"signalVersion\":1,\"seedVersion\":1,"
                + "\"rootSeed\":\"0x8000000000000000\","
                + "\"namespace\":\"island.height-detail\",\"scale\":\"0x1.0p5\"}]}";

        ProceduralGraph restored = codec.readString(expected);

        assertAll(
                () -> assertEquals(CanonicalGraphJson.LATEST_SCHEMA_VERSION, codec.schemaVersion(graph)),
                () -> assertEquals(expected, codec.writeString(graph)),
                () -> assertEquals(graph.requireNode(output), restored.requireNode(output)),
                () -> assertArrayEquals(codec.write(graph), codec.write(restored)));
    }

    @Test
    void retainsSchemaOneBytesForGraphsWithoutSignals() {
        assertAll(
                () -> assertEquals(CanonicalGraphJson.SCHEMA_VERSION, codec.schemaVersion(graph(nodes()))),
                () -> assertEquals(CANONICAL_GRAPH, codec.writeString(graph(nodes()))));
    }

    @Test
    void escapesAndRestoresJsonAndUnicodeNodeIdentifiers() {
        NodeId id = new NodeId("ridge.\"north\"\\雪\nline");
        ProceduralGraph graph = new ProceduralGraph(
                List.of(new ConstantNode(id, GraphValueType.SCALAR_FIELD_2, 2.5)), id);

        ProceduralGraph restored = codec.read(codec.write(graph));

        assertAll(
                () -> assertEquals(id, restored.output()),
                () -> assertEquals(graph.requireNode(id), restored.requireNode(id)),
                () -> assertArrayEquals(codec.write(graph), codec.write(restored)));
    }

    @Test
    void rejectsUnsupportedVersionsUnknownAndDuplicateMembers() {
        String unsupported = CANONICAL_GRAPH.replace("\"schemaVersion\":1", "\"schemaVersion\":2");
        String unknown = CANONICAL_GRAPH.replace(
                "\"schemaVersion\":1", "\"schemaVersion\":1,\"future\":true");
        String duplicate = CANONICAL_GRAPH.replace(
                "\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1");

        assertAll(
                () -> assertThrows(GraphSerializationException.class, () -> codec.readString(unsupported)),
                () -> assertThrows(GraphSerializationException.class, () -> codec.readString(unknown)),
                () -> assertThrows(GraphSerializationException.class, () -> codec.readString(duplicate)));
    }

    @Test
    void rejectsUnknownEnumsNoncanonicalDoublesAndInvalidGraphReferences() {
        String unknownKind = CANONICAL_GRAPH.replace("\"kind\":\"constant\"", "\"kind\":\"signal\"");
        String unknownType = CANONICAL_GRAPH.replace("scalar-field-2", "density-field");
        String unknownOperator = CANONICAL_GRAPH.replace("\"operator\":\"add\"", "\"operator\":\"power\"");
        String unknownAxis = CANONICAL_GRAPH.replace("\"axis\":\"x\"", "\"axis\":\"north\"");
        String noncanonicalDouble = CANONICAL_GRAPH.replace("-0x0.0p0", "-0.0");
        String missingInput = CANONICAL_GRAPH.replace("[\"x\",\"offset\"]", "[\"missing\",\"offset\"]");

        assertAll(
                () -> assertThrows(GraphSerializationException.class, () -> codec.readString(unknownKind)),
                () -> assertThrows(GraphSerializationException.class, () -> codec.readString(unknownType)),
                () -> assertThrows(GraphSerializationException.class, () -> codec.readString(unknownOperator)),
                () -> assertThrows(GraphSerializationException.class, () -> codec.readString(unknownAxis)),
                () -> assertThrows(GraphSerializationException.class, () -> codec.readString(noncanonicalDouble)),
                () -> assertThrows(GraphSerializationException.class, () -> codec.readString(missingInput)));
    }

    @Test
    void rejectsSignalNodesInSchemaOneAndNoncanonicalSignalFields() {
        String canonical = "{\"schemaVersion\":2,\"output\":\"signal\",\"nodes\":["
                + "{\"id\":\"signal\",\"kind\":\"planar-value-signal\","
                + "\"outputType\":\"scalar-field-2\",\"signalVersion\":1,\"seedVersion\":1,"
                + "\"rootSeed\":\"0x8000000000000000\","
                + "\"namespace\":\"island.height-detail\",\"scale\":\"0x1.0p5\"}]}";

        assertAll(
                () -> assertThrows(
                        GraphSerializationException.class,
                        () -> codec.readString(CANONICAL_GRAPH.replace(
                                "\"schemaVersion\":1", "\"schemaVersion\":2"))),
                () -> assertThrows(
                        GraphSerializationException.class,
                        () -> codec.readString(canonical.replace("\"schemaVersion\":2", "\"schemaVersion\":1"))),
                () -> assertThrows(
                        GraphSerializationException.class,
                        () -> codec.readString(canonical.replace(
                                "0x8000000000000000", "0X8000000000000000"))),
                () -> assertThrows(
                        GraphSerializationException.class,
                        () -> codec.readString(canonical.replace("0x1.0p5", "32.0"))),
                () -> assertThrows(
                        GraphSerializationException.class,
                        () -> codec.readString(canonical.replace(
                                "island.height-detail", "Island.Height-Detail"))));
    }

    @Test
    void rejectsMalformedJsonMalformedUtf8AndUnpairedSurrogates() {
        byte[] malformedUtf8 = {(byte) 0xc3, (byte) 0x28};
        String unpairedSurrogate = "{\"schemaVersion\":1,\"output\":\"\\ud800\",\"nodes\":[]}";

        assertAll(
                () -> assertThrows(GraphSerializationException.class, () -> codec.readString("{")),
                () -> assertThrows(GraphSerializationException.class, () -> codec.read(malformedUtf8)),
                () -> assertThrows(GraphSerializationException.class, () -> codec.readString(unpairedSurrogate)),
                () -> assertThrows(
                        GraphSerializationException.class,
                        () -> codec.writeString(new ProceduralGraph(
                                List.of(new ConstantNode(
                                        new NodeId("bad\ud800"), GraphValueType.SCALAR_FIELD_2, 1.0)),
                                new NodeId("bad\ud800")))));
    }

    @Test
    void rejectsNullBoundaries() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> codec.write(null)),
                () -> assertThrows(NullPointerException.class, () -> codec.read(null)),
                () -> assertThrows(NullPointerException.class, () -> codec.readString(null)));
    }

    private static ProceduralGraph graph(List<GraphNode> graphNodes) {
        return new ProceduralGraph(graphNodes, new NodeId("output"));
    }

    private static List<GraphNode> nodes() {
        NodeId x = new NodeId("x");
        NodeId offset = new NodeId("offset");
        NodeId output = new NodeId("output");
        return List.of(
                new CoordinateNode(x, GraphValueType.SCALAR_FIELD_2, CoordinateAxis.X),
                new ConstantNode(offset, GraphValueType.SCALAR_FIELD_2, -0.0),
                new ArithmeticNode(
                        output,
                        GraphValueType.SCALAR_FIELD_2,
                        ArithmeticOperator.ADD,
                        x,
                        offset));
    }
}
