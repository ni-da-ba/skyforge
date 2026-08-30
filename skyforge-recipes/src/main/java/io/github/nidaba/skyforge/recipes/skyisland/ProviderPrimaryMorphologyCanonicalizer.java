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
 * <p>The adapter consumes only public structural handles. It does not assume provider-local node
 * names and does not inspect {@link MorphologyFamily}. Exact provider graph bytes remain authoritative
 * when no enrichment is requested; this carrier exists only so generic enrichment can operate on a
 * standalone provider endpoint using the same structural semantics used by provider hybridization.
 */
final class ProviderPrimaryMorphologyCanonicalizer {
    private static final double MAXIMUM_UNDERSIDE_ASYMMETRY = 0.25;
    private static final String SOURCE_PREFIX = "provider-canonical.source.";

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
        ProceduralGraph source = switch (output) {
            case UPPER -> contribution.volume().upperSurfaceGraph();
            case UNDERSIDE -> contribution.volume().undersideSurfaceGraph();
            case DENSITY -> contribution.volume().densityGraph();
        };
        Builder graph = new Builder(source.outputType());
        graph.appendPrefixed(source, SOURCE_PREFIX);

        NodeId one = graph.constant("profile.one", 1.0);
        NodeId remaining = graph.alias(
                "profile.remaining", prefixed(contribution.footprintResidual()), one);
        NodeId radiusSquared = graph.arithmetic(
                "profile.radius-squared", ArithmeticOperator.SUBTRACT, one, remaining);
        NodeId along = graph.alias(
                "profile.along-normalized", prefixed(contribution.alongNormalized()), one);
        graph.alias(
                "profile.across-normalized", prefixed(contribution.acrossNormalized()), one);
        NodeId suspension = graph.constant(
                "descriptor.suspension-elevation", descriptor.suspensionElevation());
        CommonProfile profile = new CommonProfile(remaining, radiusSquared, along, one, suspension);

        return switch (output) {
            case UPPER -> graph.build(addUpper(graph, descriptor, contribution, profile));
            case UNDERSIDE -> graph.build(addUnderside(graph, descriptor, contribution, profile));
            case DENSITY -> {
                NodeId upper = addUpper(graph, descriptor, contribution, profile);
                NodeId underside = addUnderside(graph, descriptor, contribution, profile);
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
            Builder graph,
            SkyIslandVolumeDescriptor descriptor,
            PrimaryMorphologyContribution contribution,
            CommonProfile profile) {
        NodeId factor = graph.alias(
                "provider.upper-factor", prefixed(contribution.upperFactor()), profile.one());
        NodeId crown = graph.arithmetic(
                "provider.crown-profile", ArithmeticOperator.MULTIPLY, profile.remaining(), factor);
        NodeId elevation = graph.constant("descriptor.upper-elevation", descriptor.upperElevation());
        NodeId offset = graph.arithmetic(
                "upper.offset", ArithmeticOperator.MULTIPLY, elevation, crown);
        return graph.arithmetic(
                "upper.surface", ArithmeticOperator.ADD, profile.suspension(), offset);
    }

    private static NodeId addUnderside(
            Builder graph,
            SkyIslandVolumeDescriptor descriptor,
            PrimaryMorphologyContribution contribution,
            CommonProfile profile) {
        NodeId taper = graph.constant("descriptor.underside-taper", descriptor.undersideTaper());
        NodeId taperRadius = graph.arithmetic(
                "underside.taper-radius", ArithmeticOperator.MULTIPLY, taper, profile.radiusSquared());
        NodeId taperDenominator = graph.arithmetic(
                "underside.taper-denominator", ArithmeticOperator.ADD, profile.one(), taperRadius);
        NodeId taperedRemaining = graph.arithmetic(
                "underside.tapered-remaining", ArithmeticOperator.DIVIDE, profile.remaining(), taperDenominator);

        NodeId asymmetryStrength = graph.constant(
                "descriptor.underside-asymmetry",
                MAXIMUM_UNDERSIDE_ASYMMETRY * descriptor.undersideAsymmetry());
        NodeId asymmetryTerm = graph.arithmetic(
                "underside.asymmetry-term", ArithmeticOperator.MULTIPLY, asymmetryStrength, profile.along());
        NodeId asymmetrySquared = graph.arithmetic(
                "underside.asymmetry-squared", ArithmeticOperator.MULTIPLY, asymmetryTerm, asymmetryTerm);
        NodeId onePlusAsymmetry = graph.arithmetic(
                "underside.one-plus-asymmetry", ArithmeticOperator.ADD, profile.one(), asymmetryTerm);
        NodeId asymmetryFactor = graph.arithmetic(
                "underside.asymmetry-factor", ArithmeticOperator.ADD, onePlusAsymmetry, asymmetrySquared);

        NodeId depthFactor = graph.alias(
                "provider.depth-factor", prefixed(contribution.undersideDepthFactor()), profile.one());
        NodeId familyRemaining = graph.arithmetic(
                "underside.provider-remaining",
                ArithmeticOperator.MULTIPLY,
                taperedRemaining,
                depthFactor);
        NodeId shapedDepth = graph.arithmetic(
                "underside.shaped-depth", ArithmeticOperator.MULTIPLY, familyRemaining, asymmetryFactor);
        NodeId depth = graph.constant("descriptor.underside-depth", descriptor.undersideDepth());
        NodeId offset = graph.arithmetic(
                "underside.offset", ArithmeticOperator.MULTIPLY, depth, shapedDepth);
        return graph.arithmetic(
                "underside.surface", ArithmeticOperator.SUBTRACT, profile.suspension(), offset);
    }

    private static Map<String, List<NodeId>> provenance(SkyIslandMorphologyProvider provider) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>();
        result.put("provider-canonical-carrier:" + provider.id(), List.of(
                new NodeId("profile.remaining"),
                new NodeId("profile.along-normalized"),
                new NodeId("profile.across-normalized"),
                new NodeId("provider.upper-factor"),
                new NodeId("provider.depth-factor"),
                new NodeId("upper.offset"),
                new NodeId("underside.offset")));
        return result;
    }

    private static NodeId prefixed(NodeId id) {
        return new NodeId(SOURCE_PREFIX + id.value());
    }

    private enum Output { UPPER, UNDERSIDE, DENSITY }

    private record CommonProfile(
            NodeId remaining,
            NodeId radiusSquared,
            NodeId along,
            NodeId one,
            NodeId suspension) {}

    private static final class Builder {
        private final GraphValueType type;
        private final List<GraphNode> nodes = new ArrayList<>();

        private Builder(GraphValueType type) {
            this.type = type;
        }

        private void appendPrefixed(ProceduralGraph source, String prefix) {
            for (GraphNode node : source.nodes()) {
                NodeId id = new NodeId(prefix + node.id().value());
                if (node instanceof ConstantNode constant) {
                    nodes.add(new ConstantNode(id, constant.outputType(), constant.value()));
                } else if (node instanceof CoordinateNode coordinate) {
                    nodes.add(new CoordinateNode(id, coordinate.outputType(), coordinate.axis()));
                } else if (node instanceof ArithmeticNode arithmetic) {
                    nodes.add(new ArithmeticNode(
                            id,
                            arithmetic.outputType(),
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
                            signal.outputType(),
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
