package io.github.nidaba.skyforge.recipes.skyisland;

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
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Re-expresses one provider primary contribution under Skyforge's canonical enrichment carrier IDs.
 *
 * <p>The provider's authored upper and underside surfaces remain authoritative. The adapter derives
 * canonical offsets from those surfaces and consumes public structural handles only for the shared
 * footprint/directional vocabulary needed by enrichment. It therefore does not assume that an
 * external provider uses Skyforge's built-in upper or underside construction formulas.
 *
 * <p>Exact provider graph bytes remain authoritative when no enrichment is requested; this carrier
 * exists only so generic enrichment can operate on a standalone provider endpoint using canonical
 * structural IDs.
 */
final class ProviderPrimaryMorphologyCanonicalizer {
    private static final String SOURCE_PREFIX = "provider-canonical.source.";
    private static final String UNDERSIDE_SOURCE_PREFIX = "provider-canonical.underside-source.";

    private ProviderPrimaryMorphologyCanonicalizer() {}

    static CompiledSkyIslandVolume canonicalize(
            SkyIslandVolumeDescriptor descriptor,
            SkyIslandMorphologyProvider provider,
            int recipeVersion) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(provider, "provider");
        if (recipeVersion <= 0) {
            throw new IllegalArgumentException("recipeVersion must be positive");
        }
        PrimaryMorphologyContribution contribution = provider.compilePrimary(descriptor);
        ProceduralGraph upper = graph(contribution, descriptor, Output.UPPER);
        ProceduralGraph underside = graph(contribution, descriptor, Output.UNDERSIDE);
        ProceduralGraph density = graph(contribution, descriptor, Output.DENSITY);
        return new CompiledSkyIslandVolume(
                descriptor,
                recipeVersion,
                CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                upper,
                underside,
                density,
                provenance(provider));
    }

    private static ProceduralGraph graph(
            PrimaryMorphologyContribution contribution,
            SkyIslandVolumeDescriptor descriptor,
            Output output) {
        ProceduralGraph upperSource = contribution.volume().upperSurfaceGraph();
        ProceduralGraph undersideSource = contribution.volume().undersideSurfaceGraph();
        GraphValueType targetType = output == Output.DENSITY
                ? GraphValueType.SCALAR_FIELD_3
                : GraphValueType.SCALAR_FIELD_2;
        Builder graph = new Builder(targetType);

        String structuralPrefix;
        String depthPrefix = null;
        NodeId providerUpperSurface = null;
        NodeId providerUndersideSurface = null;
        switch (output) {
            case UPPER -> {
                graph.appendPrefixed(upperSource, SOURCE_PREFIX);
                structuralPrefix = SOURCE_PREFIX;
                providerUpperSurface = prefixed(SOURCE_PREFIX, upperSource.output());
            }
            case UNDERSIDE -> {
                graph.appendPrefixed(undersideSource, SOURCE_PREFIX);
                structuralPrefix = SOURCE_PREFIX;
                depthPrefix = SOURCE_PREFIX;
                providerUndersideSurface = prefixed(SOURCE_PREFIX, undersideSource.output());
            }
            case DENSITY -> {
                // Promote the two provider-authored 2-D surface graphs independently into the 3-D
                // carrier. This preserves their exact x/z functions without requiring the provider's
                // density graph to expose any particular local surface node names.
                graph.appendPrefixed(upperSource, SOURCE_PREFIX);
                graph.appendPrefixed(undersideSource, UNDERSIDE_SOURCE_PREFIX);
                structuralPrefix = SOURCE_PREFIX;
                depthPrefix = UNDERSIDE_SOURCE_PREFIX;
                providerUpperSurface = prefixed(SOURCE_PREFIX, upperSource.output());
                providerUndersideSurface = prefixed(UNDERSIDE_SOURCE_PREFIX, undersideSource.output());
            }
            default -> throw new IllegalStateException("unknown canonical carrier output: " + output);
        }

        NodeId one = graph.constant("profile.one", 1.0);
        NodeId remaining = graph.alias(
                "profile.remaining",
                prefixed(structuralPrefix, contribution.footprintResidual()),
                one);
        graph.arithmetic(
                "profile.radius-squared", ArithmeticOperator.SUBTRACT, one, remaining);
        graph.alias(
                "profile.along-normalized",
                prefixed(structuralPrefix, contribution.alongNormalized()),
                one);
        graph.alias(
                "profile.across-normalized",
                prefixed(structuralPrefix, contribution.acrossNormalized()),
                one);

        // PrimaryMorphologyContribution guarantees upperFactor only in upper/density graphs and
        // undersideDepthFactor only in underside/density graphs. Do not require a provider to emit
        // either handle into an unrelated surface graph merely for inspectability.
        if (output != Output.UNDERSIDE) {
            graph.alias(
                    "provider.upper-factor",
                    prefixed(structuralPrefix, contribution.upperFactor()),
                    one);
        }
        if (output != Output.UPPER) {
            graph.alias(
                    "provider.depth-factor",
                    prefixed(Objects.requireNonNull(depthPrefix), contribution.undersideDepthFactor()),
                    one);
        }

        NodeId suspension = graph.constant(
                "descriptor.suspension-elevation", descriptor.suspensionElevation());

        return switch (output) {
            case UPPER -> graph.build(addUpper(graph, providerUpperSurface, suspension));
            case UNDERSIDE -> graph.build(addUnderside(graph, providerUndersideSurface, suspension));
            case DENSITY -> {
                NodeId upper = addUpper(graph, providerUpperSurface, suspension);
                NodeId underside = addUnderside(graph, providerUndersideSurface, suspension);
                NodeId y = graph.coordinate("position.y", CoordinateAxis.Y);
                NodeId upperConstraint = graph.arithmetic(
                        "density.upper-constraint", ArithmeticOperator.SUBTRACT, upper, y);
                NodeId lowerConstraint = graph.arithmetic(
                        "density.lower-constraint", ArithmeticOperator.SUBTRACT, y, underside);
                yield graph.build(graph.intersection(
                        "density.solid-intersection", upperConstraint, lowerConstraint));
            }
        };
    }

    private static NodeId addUpper(
            Builder graph, NodeId providerSurface, NodeId suspension) {
        Objects.requireNonNull(providerSurface, "providerSurface");
        NodeId offset = graph.arithmetic(
                "upper.offset", ArithmeticOperator.SUBTRACT, providerSurface, suspension);
        return graph.arithmetic(
                "upper.surface", ArithmeticOperator.ADD, suspension, offset);
    }

    private static NodeId addUnderside(
            Builder graph, NodeId providerSurface, NodeId suspension) {
        Objects.requireNonNull(providerSurface, "providerSurface");
        NodeId offset = graph.arithmetic(
                "underside.offset", ArithmeticOperator.SUBTRACT, suspension, providerSurface);
        return graph.arithmetic(
                "underside.surface", ArithmeticOperator.SUBTRACT, suspension, offset);
    }

    private static Map<String, List<NodeId>> provenance(SkyIslandMorphologyProvider provider) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>();
        result.put("provider-canonical-carrier:" + provider.id(), List.of(
                new NodeId("profile.remaining"),
                new NodeId("profile.along-normalized"),
                new NodeId("profile.across-normalized"),
                new NodeId("upper.offset"),
                new NodeId("underside.offset")));
        return result;
    }

    private static NodeId prefixed(String prefix, NodeId id) {
        return new NodeId(prefix + id.value());
    }

    private enum Output { UPPER, UNDERSIDE, DENSITY }

    private static final class Builder {
        private final GraphValueType type;
        private final List<GraphNode> nodes = new ArrayList<>();

        private Builder(GraphValueType type) {
            this.type = type;
        }

        /** Copies a provider graph into this carrier, promoting 2-D nodes to 3-D when required. */
        private void appendPrefixed(ProceduralGraph source, String prefix) {
            for (GraphNode node : source.nodes()) {
                NodeId id = new NodeId(prefix + node.id().value());
                if (node instanceof ConstantNode constant) {
                    nodes.add(new ConstantNode(id, type, constant.value()));
                } else if (node instanceof CoordinateNode coordinate) {
                    nodes.add(new CoordinateNode(id, type, coordinate.axis()));
                } else if (node instanceof ArithmeticNode arithmetic) {
                    nodes.add(new ArithmeticNode(
                            id,
                            type,
                            arithmetic.operator(),
                            new NodeId(prefix + arithmetic.left().value()),
                            new NodeId(prefix + arithmetic.right().value())));
                } else if (node instanceof IntersectionNode intersection) {
                    nodes.add(new IntersectionNode(
                            id,
                            new NodeId(prefix + intersection.left().value()),
                            new NodeId(prefix + intersection.right().value())));
                } else if (node instanceof PlanarValueSignalNode signal) {
                    nodes.add(new PlanarValueSignalNode(
                            id,
                            type,
                            signal.signalVersion(),
                            signal.seedVersion(),
                            signal.rootSeed(),
                            signal.namespace(),
                            signal.scale()));
                } else {
                    throw new IllegalArgumentException(
                            "unsupported provider primary node kind: " + node.kind());
                }
            }
        }

        private NodeId alias(String id, NodeId source, NodeId one) {
            return arithmetic(id, ArithmeticOperator.MULTIPLY, source, one);
        }

        private NodeId coordinate(String id, CoordinateAxis axis) {
            NodeId result = new NodeId(id);
            nodes.add(new CoordinateNode(result, type, axis));
            return result;
        }

        private NodeId constant(String id, double value) {
            NodeId result = new NodeId(id);
            nodes.add(new ConstantNode(result, type, value));
            return result;
        }

        private NodeId arithmetic(String id, ArithmeticOperator operator, NodeId left, NodeId right) {
            NodeId result = new NodeId(id);
            nodes.add(new ArithmeticNode(result, type, operator, left, right));
            return result;
        }

        private NodeId intersection(String id, NodeId left, NodeId right) {
            NodeId result = new NodeId(id);
            nodes.add(new IntersectionNode(result, left, right));
            return result;
        }

        private ProceduralGraph build(NodeId output) {
            return new ProceduralGraph(nodes, output);
        }
    }
}
