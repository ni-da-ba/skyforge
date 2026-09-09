package io.github.nidaba.skyforge.kernel.serialization;

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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Versioned, dependency-free canonical JSON serialization for procedural graphs. */
public final class CanonicalGraphJson {
    /** Base graph schema retained for signal-free canonical compatibility. */
    public static final int SCHEMA_VERSION = 1;

    /** Graph schema adding the planar value signal node. */
    public static final int PLANAR_SIGNAL_SCHEMA_VERSION = 2;

    /** Graph schema adding positive-inside three-dimensional intersections. */
    public static final int INTERSECTION_SCHEMA_VERSION = 3;

    /** Latest graph schema. */
    public static final int LATEST_SCHEMA_VERSION = INTERSECTION_SCHEMA_VERSION;

    /** Serializes a graph to its canonical UTF-8 byte representation. */
    public byte[] write(ProceduralGraph graph) {
        return writeString(graph).getBytes(StandardCharsets.UTF_8);
    }

    /** Serializes a graph to its canonical JSON text representation. */
    public String writeString(ProceduralGraph graph) {
        Objects.requireNonNull(graph, "graph");
        StringBuilder result = new StringBuilder();
        result.append("{\"schemaVersion\":").append(schemaVersion(graph));
        result.append(",\"output\":");
        appendString(result, graph.output().value());
        result.append(",\"nodes\":[");

        List<GraphNode> nodes = graph.nodes().stream()
                .sorted(Comparator.comparing(node -> node.id().value()))
                .toList();
        for (int index = 0; index < nodes.size(); index++) {
            if (index != 0) {
                result.append(',');
            }
            appendNode(result, nodes.get(index));
        }
        return result.append("]}").toString();
    }

    /** Returns the minimum canonical schema required to encode {@code graph}. */
    public int schemaVersion(ProceduralGraph graph) {
        Objects.requireNonNull(graph, "graph");
        if (graph.nodes().stream().anyMatch(IntersectionNode.class::isInstance)) {
            return INTERSECTION_SCHEMA_VERSION;
        }
        if (graph.nodes().stream().anyMatch(PlanarValueSignalNode.class::isInstance)) {
            return PLANAR_SIGNAL_SCHEMA_VERSION;
        }
        return SCHEMA_VERSION;
    }

    /** Reads a graph from strict UTF-8 JSON and validates the reconstructed DAG. */
    public ProceduralGraph read(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
            return readString(text);
        } catch (CharacterCodingException exception) {
            throw new GraphSerializationException("graph JSON is not valid UTF-8", exception);
        }
    }

    /** Reads graph JSON, rejecting unsupported versions, fields, and node encodings. */
    public ProceduralGraph readString(String encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            Object parsed = new JsonParser(encoded).parse();
            Map<String, Object> root = requireObject(parsed, "graph");
            requireFields(root, Set.of("schemaVersion", "output", "nodes"), "graph");
            int schemaVersion = requireInteger(root.get("schemaVersion"), "schemaVersion");
            if (schemaVersion < SCHEMA_VERSION || schemaVersion > LATEST_SCHEMA_VERSION) {
                throw new GraphSerializationException("unsupported graph schema version: " + schemaVersion);
            }

            NodeId output = nodeId(root.get("output"), "output");
            List<Object> encodedNodes = requireArray(root.get("nodes"), "nodes");
            List<GraphNode> nodes = new ArrayList<>(encodedNodes.size());
            for (int index = 0; index < encodedNodes.size(); index++) {
                nodes.add(readNode(
                        requireObject(encodedNodes.get(index), "nodes[" + index + "]"),
                        schemaVersion));
            }
            ProceduralGraph graph = new ProceduralGraph(nodes, output);
            if (schemaVersion(graph) != schemaVersion) {
                throw new GraphSerializationException(
                        "graph schema version is not the minimum required by its node set");
            }
            return graph;
        } catch (GraphSerializationException exception) {
            throw exception;
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new GraphSerializationException("invalid procedural graph: " + exception.getMessage(), exception);
        }
    }

    private static void appendNode(StringBuilder result, GraphNode node) {
        result.append("{\"id\":");
        appendString(result, node.id().value());
        result.append(",\"kind\":");
        appendString(result, node.kind().identifier());
        result.append(",\"outputType\":");
        appendString(result, externalType(node.outputType()));

        if (node instanceof ConstantNode constant) {
            result.append(",\"value\":");
            appendString(result, Double.toHexString(constant.value()));
        } else if (node instanceof CoordinateNode coordinate) {
            result.append(",\"axis\":");
            appendString(result, coordinate.axis().name().toLowerCase(java.util.Locale.ROOT));
        } else if (node instanceof ArithmeticNode arithmetic) {
            result.append(",\"operator\":");
            appendString(result, arithmetic.operator().name().toLowerCase(java.util.Locale.ROOT));
            result.append(",\"inputs\":[");
            appendString(result, arithmetic.left().value());
            result.append(',');
            appendString(result, arithmetic.right().value());
            result.append(']');
        } else if (node instanceof IntersectionNode intersection) {
            result.append(",\"inputs\":[");
            appendString(result, intersection.left().value());
            result.append(',');
            appendString(result, intersection.right().value());
            result.append(']');
        } else if (node instanceof PlanarValueSignalNode signal) {
            result.append(",\"signalVersion\":").append(signal.signalVersion());
            result.append(",\"seedVersion\":").append(signal.seedVersion());
            result.append(",\"rootSeed\":");
            appendString(result, "0x" + HexFormat.of().toHexDigits(signal.rootSeed()));
            result.append(",\"namespace\":");
            appendString(result, signal.namespace());
            result.append(",\"scale\":");
            appendString(result, Double.toHexString(signal.scale()));
        } else {
            throw new GraphSerializationException("unsupported node kind: " + node.kind());
        }
        result.append('}');
    }

    private static GraphNode readNode(Map<String, Object> encoded, int schemaVersion) {
        String kind = requireString(encoded.get("kind"), "node.kind");
        return switch (kind) {
            case "constant" -> readConstant(encoded);
            case "coordinate" -> readCoordinate(encoded);
            case "arithmetic" -> readArithmetic(encoded);
            case "intersection" -> {
                if (schemaVersion < INTERSECTION_SCHEMA_VERSION) {
                    throw new GraphSerializationException(
                            "intersection requires graph schema version 3");
                }
                yield readIntersection(encoded);
            }
            case "planar-value-signal" -> {
                if (schemaVersion < PLANAR_SIGNAL_SCHEMA_VERSION) {
                    throw new GraphSerializationException(
                            "planar-value-signal requires graph schema version 2");
                }
                yield readPlanarValueSignal(encoded);
            }
            default -> throw new GraphSerializationException("unknown node kind: " + kind);
        };
    }

    private static IntersectionNode readIntersection(Map<String, Object> encoded) {
        requireFields(encoded, Set.of("id", "kind", "outputType", "inputs"), "intersection node");
        if (valueType(encoded.get("outputType")) != GraphValueType.SCALAR_FIELD_3) {
            throw new GraphSerializationException("intersection outputType must be scalar-field-3");
        }
        List<Object> inputs = requireArray(encoded.get("inputs"), "intersection.inputs");
        if (inputs.size() != 2) {
            throw new GraphSerializationException(
                    "intersection.inputs must contain exactly two node ids");
        }
        return new IntersectionNode(
                nodeId(encoded.get("id"), "intersection.id"),
                nodeId(inputs.get(0), "intersection.inputs[0]"),
                nodeId(inputs.get(1), "intersection.inputs[1]"));
    }

    private static PlanarValueSignalNode readPlanarValueSignal(Map<String, Object> encoded) {
        requireFields(
                encoded,
                Set.of(
                        "id",
                        "kind",
                        "outputType",
                        "signalVersion",
                        "seedVersion",
                        "rootSeed",
                        "namespace",
                        "scale"),
                "planar value signal node");
        String rootSeed = requireString(encoded.get("rootSeed"), "planarValueSignal.rootSeed");
        if (!rootSeed.matches("0x[0-9a-f]{16}")) {
            throw new GraphSerializationException(
                    "planarValueSignal.rootSeed must be 16 lowercase hexadecimal digits");
        }
        long parsedSeed;
        try {
            parsedSeed = Long.parseUnsignedLong(rootSeed.substring(2), 16);
        } catch (NumberFormatException exception) {
            throw new GraphSerializationException("planarValueSignal.rootSeed is invalid", exception);
        }
        String scaleText = requireString(encoded.get("scale"), "planarValueSignal.scale");
        double scale;
        try {
            scale = Double.parseDouble(scaleText);
        } catch (NumberFormatException exception) {
            throw new GraphSerializationException(
                    "planarValueSignal.scale is not binary64 hexadecimal", exception);
        }
        if (!Double.isFinite(scale) || !Double.toHexString(scale).equals(scaleText)) {
            throw new GraphSerializationException(
                    "planarValueSignal.scale is not canonical finite binary64 hexadecimal");
        }
        return new PlanarValueSignalNode(
                nodeId(encoded.get("id"), "planarValueSignal.id"),
                valueType(encoded.get("outputType")),
                requireInteger(encoded.get("signalVersion"), "planarValueSignal.signalVersion"),
                requireInteger(encoded.get("seedVersion"), "planarValueSignal.seedVersion"),
                parsedSeed,
                requireString(encoded.get("namespace"), "planarValueSignal.namespace"),
                scale);
    }

    private static ConstantNode readConstant(Map<String, Object> encoded) {
        requireFields(encoded, Set.of("id", "kind", "outputType", "value"), "constant node");
        String valueText = requireString(encoded.get("value"), "constant.value");
        double value;
        try {
            value = Double.parseDouble(valueText);
        } catch (NumberFormatException exception) {
            throw new GraphSerializationException("constant.value is not binary64 hexadecimal", exception);
        }
        if (!Double.isFinite(value) || !Double.toHexString(value).equals(valueText)) {
            throw new GraphSerializationException("constant.value is not canonical finite binary64 hexadecimal");
        }
        return new ConstantNode(
                nodeId(encoded.get("id"), "constant.id"),
                valueType(encoded.get("outputType")),
                value);
    }

    private static CoordinateNode readCoordinate(Map<String, Object> encoded) {
        requireFields(encoded, Set.of("id", "kind", "outputType", "axis"), "coordinate node");
        String axis = requireString(encoded.get("axis"), "coordinate.axis");
        CoordinateAxis parsedAxis = switch (axis) {
            case "x" -> CoordinateAxis.X;
            case "y" -> CoordinateAxis.Y;
            case "z" -> CoordinateAxis.Z;
            default -> throw new GraphSerializationException("unknown coordinate axis: " + axis);
        };
        return new CoordinateNode(
                nodeId(encoded.get("id"), "coordinate.id"),
                valueType(encoded.get("outputType")),
                parsedAxis);
    }

    private static ArithmeticNode readArithmetic(Map<String, Object> encoded) {
        requireFields(
                encoded,
                Set.of("id", "kind", "outputType", "operator", "inputs"),
                "arithmetic node");
        String operator = requireString(encoded.get("operator"), "arithmetic.operator");
        ArithmeticOperator parsedOperator = switch (operator) {
            case "add" -> ArithmeticOperator.ADD;
            case "subtract" -> ArithmeticOperator.SUBTRACT;
            case "multiply" -> ArithmeticOperator.MULTIPLY;
            case "divide" -> ArithmeticOperator.DIVIDE;
            default -> throw new GraphSerializationException("unknown arithmetic operator: " + operator);
        };
        List<Object> inputs = requireArray(encoded.get("inputs"), "arithmetic.inputs");
        if (inputs.size() != 2) {
            throw new GraphSerializationException("arithmetic.inputs must contain exactly two node ids");
        }
        return new ArithmeticNode(
                nodeId(encoded.get("id"), "arithmetic.id"),
                valueType(encoded.get("outputType")),
                parsedOperator,
                nodeId(inputs.get(0), "arithmetic.inputs[0]"),
                nodeId(inputs.get(1), "arithmetic.inputs[1]"));
    }

    private static GraphValueType valueType(Object encoded) {
        String type = requireString(encoded, "node.outputType");
        return switch (type) {
            case "scalar-field-2" -> GraphValueType.SCALAR_FIELD_2;
            case "scalar-field-3" -> GraphValueType.SCALAR_FIELD_3;
            default -> throw new GraphSerializationException("unknown graph value type: " + type);
        };
    }

    private static String externalType(GraphValueType type) {
        return switch (type) {
            case SCALAR_FIELD_2 -> "scalar-field-2";
            case SCALAR_FIELD_3 -> "scalar-field-3";
        };
    }

    private static NodeId nodeId(Object encoded, String location) {
        return new NodeId(requireString(encoded, location));
    }

    private static Map<String, Object> requireObject(Object value, String location) {
        if (!(value instanceof Map<?, ?> raw)) {
            throw new GraphSerializationException(location + " must be a JSON object");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new GraphSerializationException(location + " contains a non-string member name");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static List<Object> requireArray(Object value, String location) {
        if (!(value instanceof List<?> raw)) {
            throw new GraphSerializationException(location + " must be a JSON array");
        }
        return new ArrayList<>(raw);
    }

    private static String requireString(Object value, String location) {
        if (!(value instanceof String text)) {
            throw new GraphSerializationException(location + " must be a JSON string");
        }
        return text;
    }

    private static int requireInteger(Object value, String location) {
        if (!(value instanceof JsonNumber number) || !number.lexeme().matches("0|[1-9][0-9]*")) {
            throw new GraphSerializationException(location + " must be a non-negative JSON integer");
        }
        try {
            return Integer.parseInt(number.lexeme());
        } catch (NumberFormatException exception) {
            throw new GraphSerializationException(location + " is outside the supported integer range", exception);
        }
    }

    private static void requireFields(Map<String, Object> object, Set<String> expected, String location) {
        if (!object.keySet().equals(expected)) {
            Set<String> missing = new java.util.TreeSet<>(expected);
            missing.removeAll(object.keySet());
            Set<String> unknown = new java.util.TreeSet<>(object.keySet());
            unknown.removeAll(expected);
            throw new GraphSerializationException(
                    location + " has invalid members; missing=" + missing + ", unknown=" + unknown);
        }
    }

    private static void appendString(StringBuilder result, String value) {
        requireValidUnicode(value);
        result.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (character < 0x20) {
                        result.append("\\u00");
                        result.append(Character.forDigit((character >>> 4) & 0xf, 16));
                        result.append(Character.forDigit(character & 0xf, 16));
                    } else {
                        result.append(character);
                    }
                }
            }
        }
        result.append('"');
    }

    private static void requireValidUnicode(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (++index >= value.length() || !Character.isLowSurrogate(value.charAt(index))) {
                    throw new GraphSerializationException("string contains an unpaired UTF-16 surrogate");
                }
            } else if (Character.isLowSurrogate(character)) {
                throw new GraphSerializationException("string contains an unpaired UTF-16 surrogate");
            }
        }
    }

    private record JsonNumber(String lexeme) {}

    private static final class JsonParser {
        private static final int MAXIMUM_DEPTH = 64;
        private final String source;
        private int offset;

        private JsonParser(String source) {
            this.source = source;
        }

        private Object parse() {
            skipWhitespace();
            Object value = parseValue(0);
            skipWhitespace();
            if (offset != source.length()) {
                fail("unexpected trailing content");
            }
            return value;
        }

        private Object parseValue(int depth) {
            if (depth > MAXIMUM_DEPTH) {
                fail("maximum JSON nesting depth exceeded");
            }
            if (offset >= source.length()) {
                fail("unexpected end of JSON");
            }
            return switch (source.charAt(offset)) {
                case '{' -> parseObject(depth + 1);
                case '[' -> parseArray(depth + 1);
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject(int depth) {
            offset++;
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (consume('}')) {
                return result;
            }
            while (true) {
                if (offset >= source.length() || source.charAt(offset) != '"') {
                    fail("object member name must be a string");
                }
                String name = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                Object value = parseValue(depth);
                if (result.containsKey(name)) {
                    fail("duplicate object member: " + name);
                }
                result.put(name, value);
                skipWhitespace();
                if (consume('}')) {
                    return result;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private List<Object> parseArray(int depth) {
            offset++;
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (consume(']')) {
                return result;
            }
            while (true) {
                result.add(parseValue(depth));
                skipWhitespace();
                if (consume(']')) {
                    return result;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (offset < source.length()) {
                char character = source.charAt(offset++);
                if (character == '"') {
                    String value = result.toString();
                    requireValidUnicode(value);
                    return value;
                }
                if (character == '\\') {
                    result.append(parseEscape());
                } else if (character < 0x20) {
                    fail("unescaped control character in string");
                } else {
                    result.append(character);
                }
            }
            fail("unterminated string");
            return null;
        }

        private char parseEscape() {
            if (offset >= source.length()) {
                fail("unterminated string escape");
            }
            return switch (source.charAt(offset++)) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> parseUnicodeEscape();
                default -> {
                    fail("unknown string escape");
                    yield 0;
                }
            };
        }

        private char parseUnicodeEscape() {
            if (offset + 4 > source.length()) {
                fail("incomplete Unicode escape");
            }
            int value = 0;
            for (int count = 0; count < 4; count++) {
                int digit = Character.digit(source.charAt(offset++), 16);
                if (digit < 0) {
                    fail("invalid Unicode escape");
                }
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private Object parseLiteral(String literal, Object value) {
            if (!source.startsWith(literal, offset)) {
                fail("invalid JSON value");
            }
            offset += literal.length();
            return value;
        }

        private JsonNumber parseNumber() {
            int start = offset;
            if (consume('-') && offset >= source.length()) {
                fail("incomplete JSON number");
            }
            if (consume('0')) {
                if (offset < source.length() && Character.isDigit(source.charAt(offset))) {
                    fail("leading zero in JSON number");
                }
            } else {
                requireDigits();
            }
            if (consume('.')) {
                requireDigits();
            }
            if (offset < source.length() && (source.charAt(offset) == 'e' || source.charAt(offset) == 'E')) {
                offset++;
                if (offset < source.length() && (source.charAt(offset) == '+' || source.charAt(offset) == '-')) {
                    offset++;
                }
                requireDigits();
            }
            return new JsonNumber(source.substring(start, offset));
        }

        private void requireDigits() {
            int start = offset;
            while (offset < source.length() && Character.isDigit(source.charAt(offset))) {
                offset++;
            }
            if (start == offset) {
                fail("expected digit in JSON number");
            }
        }

        private void skipWhitespace() {
            while (offset < source.length()) {
                char character = source.charAt(offset);
                if (character != ' ' && character != '\n' && character != '\r' && character != '\t') {
                    return;
                }
                offset++;
            }
        }

        private boolean consume(char expected) {
            if (offset < source.length() && source.charAt(offset) == expected) {
                offset++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!consume(expected)) {
                fail("expected '" + expected + "'");
            }
        }

        private void fail(String message) {
            throw new GraphSerializationException(message + " at character " + offset);
        }
    }
}
