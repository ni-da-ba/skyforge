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
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provider-neutral signal-free primary morphology hybrid compiler.
 *
 * <p>The compiler depends only on the public provider contract and explicit structural handles. It
 * does not inspect or switch on {@link MorphologyFamily}. Both final surfaces are rebuilt from one
 * blended signed footprint residual, preserving common-rim semantics.
 */
public final class ProviderHybridMorphologySkyIslandVolumeRecipe {
    /** Recipe version for the first provider-neutral hybrid proof. */
    public static final int RECIPE_VERSION = 10;

    private static final double MAXIMUM_UNDERSIDE_ASYMMETRY = 0.25;
    private static final String FIRST_PREFIX = "provider-hybrid.first.";
    private static final String SECOND_PREFIX = "provider-hybrid.second.";

    /** Compiles one signal-free hybrid using providers resolved from the supplied immutable registry. */
    public CompiledSkyIslandVolume compile(
            SkyIslandVolumeDescriptor descriptor,
            MorphologyProviderBlend blend,
            SkyIslandMorphologyProviderRegistry registry) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(blend, "blend");
        Objects.requireNonNull(registry, "registry");
        if (descriptor.signalAmplitude() != 0.0) {
            throw new IllegalArgumentException("provider primary hybrid requires zero detail amplitude");
        }
        if (descriptor.schemaVersion() == SkyIslandVolumeDescriptor.SCHEMA_VERSION_2
                && descriptor.secondaryMorphologyAmplitude() != 0.0) {
            throw new IllegalArgumentException(
                    "provider primary hybrid requires zero secondaryMorphologyAmplitude");
        }

        SkyIslandMorphologyProvider firstProvider = registry.require(blend.first());
        SkyIslandMorphologyProvider secondProvider = registry.require(blend.second());
        PrimaryMorphologyContribution first = firstProvider.compilePrimary(descriptor);
        PrimaryMorphologyContribution second = secondProvider.compilePrimary(descriptor);

        if (blend.secondWeight() == 0.0) {
            return endpoint(descriptor, blend, firstProvider, first.volume());
        }
        if (blend.secondWeight() == 1.0) {
            return endpoint(descriptor, blend, secondProvider, second.volume());
        }

        ProceduralGraph upper = hybridGraph(first, second, descriptor, blend, Output.UPPER);
        ProceduralGraph underside = hybridGraph(first, second, descriptor, blend, Output.UNDERSIDE);
        ProceduralGraph density = hybridGraph(first, second, descriptor, blend, Output.DENSITY);
        return new CompiledSkyIslandVolume(
                descriptor,
                RECIPE_VERSION,
                CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                upper,
                underside,
                density,
                provenance(blend, firstProvider, secondProvider));
    }

    private static CompiledSkyIslandVolume endpoint(
            SkyIslandVolumeDescriptor descriptor,
            MorphologyProviderBlend blend,
            SkyIslandMorphologyProvider provider,
            CompiledSkyIslandVolume parent) {
        LinkedHashMap<String, List<NodeId>> provenance = new LinkedHashMap<>(parent.provenance());
        provenance.put("provider-hybrid-endpoint:" + blend.pairIdentifier(), List.of());
        provenance.put("provider-hybrid-selected:" + provider.id().value(), List.of());
        return new CompiledSkyIslandVolume(
                descriptor,
                RECIPE_VERSION,
                parent.graphSchemaVersion(),
                parent.upperSurfaceGraph(),
                parent.undersideSurfaceGraph(),
                parent.densityGraph(),
                provenance);
    }

    private static ProceduralGraph hybridGraph(
            PrimaryMorphologyContribution first,
            PrimaryMorphologyContribution second,
            SkyIslandVolumeDescriptor descriptor,
            MorphologyProviderBlend blend,
            Output output) {
        ProceduralGraph firstGraph = graph(first.volume(), output);
        ProceduralGraph secondGraph = graph(second.volume(), output);
        if (firstGraph.outputType() != secondGraph.outputType()) {
            throw new IllegalArgumentException("provider hybrid parent graphs must expose the same field type");
        }

        Builder graph = new Builder(firstGraph.outputType());
        graph.appendPrefixed(firstGraph, FIRST_PREFIX);
        graph.appendPrefixed(secondGraph, SECOND_PREFIX);
        BlendNodes weights = graph.weights(blend);

        NodeId remaining = graph.blend(
                "profile.remaining",
                prefixed(FIRST_PREFIX, first.footprintResidual()),
                prefixed(SECOND_PREFIX, second.footprintResidual()),
                weights);
        NodeId one = graph.constant("profile.one", 1.0);
        NodeId radiusSquared = graph.arithmetic(
                "profile.radius-squared", ArithmeticOperator.SUBTRACT, one, remaining);
        NodeId along = graph.blend(
                "profile.along-normalized",
                prefixed(FIRST_PREFIX, first.alongNormalized()),
                prefixed(SECOND_PREFIX, second.alongNormalized()),
                weights);
        NodeId across = graph.blend(
                "profile.across-normalized",
                prefixed(FIRST_PREFIX, first.acrossNormalized()),
                prefixed(SECOND_PREFIX, second.acrossNormalized()),
                weights);
        NodeId suspension = graph.constant(
                "descriptor.suspension-elevation", descriptor.suspensionElevation());
        CommonProfile profile = new CommonProfile(remaining, radiusSquared, along, one, suspension, weights);

        return switch (output) {
            case UPPER -> graph.build(addUpper(graph, descriptor, first, second, profile));
            case UNDERSIDE -> graph.build(addUnderside(graph, descriptor, first, second, profile));
            case DENSITY -> {
                NodeId upper = addUpper(graph, descriptor, first, second, profile);
                NodeId underside = addUnderside(graph, descriptor, first, second, profile);
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

    private static ProceduralGraph graph(CompiledSkyIslandVolume volume, Output output) {
        return switch (output) {
            case UPPER -> volume.upperSurfaceGraph();
            case UNDERSIDE -> volume.undersideSurfaceGraph();
            case DENSITY -> volume.densityGraph();
        };
    }

    private static NodeId addUpper(
            Builder graph,
            SkyIslandVolumeDescriptor descriptor,
            PrimaryMorphologyContribution first,
            PrimaryMorphologyContribution second,
            CommonProfile profile) {
        NodeId familyFactor = graph.blend(
                "provider.upper-factor",
                prefixed(FIRST_PREFIX, first.upperFactor()),
                prefixed(SECOND_PREFIX, second.upperFactor()),
                profile.weights());
        NodeId crown = graph.arithmetic(
                "provider.crown-profile", ArithmeticOperator.MULTIPLY, profile.remaining(), familyFactor);
        NodeId elevation = graph.constant("descriptor.upper-elevation", descriptor.upperElevation());
        NodeId offset = graph.arithmetic(
                "upper.offset", ArithmeticOperator.MULTIPLY, elevation, crown);
        return graph.arithmetic("upper.surface", ArithmeticOperator.ADD, profile.suspension(), offset);
    }

    private static NodeId addUnderside(
            Builder graph,
            SkyIslandVolumeDescriptor descriptor,
            PrimaryMorphologyContribution first,
            PrimaryMorphologyContribution second,
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

        NodeId depthFactor = graph.blend(
                "provider.depth-factor",
                prefixed(FIRST_PREFIX, first.undersideDepthFactor()),
                prefixed(SECOND_PREFIX, second.undersideDepthFactor()),
                profile.weights());
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

    private static Map<String, List<NodeId>> provenance(
            MorphologyProviderBlend blend,
            SkyIslandMorphologyProvider first,
            SkyIslandMorphologyProvider second) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>();
        result.put("provider-hybrid:" + blend.pairIdentifier(), List.of(
                new NodeId("provider-hybrid.first-weight"),
                new NodeId("provider-hybrid.second-weight"),
                new NodeId("profile.remaining"),
                new NodeId("provider.upper-factor"),
                new NodeId("provider.depth-factor")));
        result.put("provider:first:" + first.id().value(), List.of());
        result.put("provider:second:" + second.id().value(), List.of());
        return result;
    }

    private static NodeId prefixed(String prefix, NodeId id) {
        return new NodeId(prefix + id.value());
    }

    private enum Output { UPPER, UNDERSIDE, DENSITY }

    private record BlendNodes(NodeId firstWeight, NodeId secondWeight) {}

    private record CommonProfile(
            NodeId remaining,
            NodeId radiusSquared,
            NodeId along,
            NodeId one,
            NodeId suspension,
            BlendNodes weights) {}

    private static final class Builder {
        private final GraphValueType type;
        private final List<GraphNode> nodes = new ArrayList<>();

        private Builder(GraphValueType type) {
            this.type = type;
        }

        private void appendPrefixed(ProceduralGraph source, String prefix) {
            for (GraphNode node : source.nodes()) {
                NodeId id = prefixed(prefix, node.id());
                if (node instanceof ConstantNode constant) {
                    nodes.add(new ConstantNode(id, constant.outputType(), constant.value()));
                } else if (node instanceof CoordinateNode coordinate) {
                    nodes.add(new CoordinateNode(id, coordinate.outputType(), coordinate.axis()));
                } else if (node instanceof ArithmeticNode arithmetic) {
                    nodes.add(new ArithmeticNode(
                            id,
                            arithmetic.outputType(),
                            arithmetic.operator(),
                            prefixed(prefix, arithmetic.left()),
                            prefixed(prefix, arithmetic.right())));
                } else if (node instanceof IntersectionNode intersection) {
                    nodes.add(new IntersectionNode(
                            id,
                            prefixed(prefix, intersection.left()),
                            prefixed(prefix, intersection.right())));
                } else {
                    throw new IllegalArgumentException(
                            "signal-free provider primary emitted unsupported node kind: " + node.kind());
                }
            }
        }

        private BlendNodes weights(MorphologyProviderBlend blend) {
            return new BlendNodes(
                    constant("provider-hybrid.first-weight", blend.firstWeight()),
                    constant("provider-hybrid.second-weight", blend.secondWeight()));
        }

        private NodeId blend(String id, NodeId first, NodeId second, BlendNodes weights) {
            NodeId firstWeighted = arithmetic(
                    id + ".first-weighted", ArithmeticOperator.MULTIPLY, first, weights.firstWeight());
            NodeId secondWeighted = arithmetic(
                    id + ".second-weighted", ArithmeticOperator.MULTIPLY, second, weights.secondWeight());
            return arithmetic(id, ArithmeticOperator.ADD, firstWeighted, secondWeighted);
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
