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
 * Signal-free recipe-layer hybrid of two accepted primary morphology families.
 *
 * <p>Each parent is compiled by the accepted built-in primary provider. Parent graphs are namespaced
 * and their signed footprint residuals and positive family factors are interpolated. The hybrid then
 * rebuilds upper and underside from one shared blended residual, preserving exact common-rim
 * semantics rather than averaging two finished surfaces.
 */
public final class HybridMorphologySkyIslandVolumeRecipe {
    /** Recipe version for the first primary morphology-hybridization proof. */
    public static final int RECIPE_VERSION = 8;

    private static final double MAXIMUM_UNDERSIDE_ASYMMETRY = 0.25;
    private static final String FIRST_PREFIX = "hybrid.first.";
    private static final String SECOND_PREFIX = "hybrid.second.";

    /**
     * Compiles one signal-free hybrid primary volume.
     *
     * @throws NullPointerException if either argument is null
     * @throws IllegalArgumentException if detail or semantic secondary morphology is requested
     */
    public CompiledSkyIslandVolume compile(
            SkyIslandVolumeDescriptor descriptor, MorphologyBlend blend) {
        Objects.requireNonNull(descriptor, "descriptor");
        Objects.requireNonNull(blend, "blend");
        if (descriptor.signalAmplitude() != 0.0) {
            throw new IllegalArgumentException("hybrid primary proof requires zero detail amplitude");
        }
        if (descriptor.schemaVersion() == SkyIslandVolumeDescriptor.SCHEMA_VERSION_2
                && descriptor.secondaryMorphologyAmplitude() != 0.0) {
            throw new IllegalArgumentException(
                    "hybrid primary proof requires zero secondaryMorphologyAmplitude");
        }

        SkyIslandPrimaryMorphologyProvider firstProvider =
                SkyIslandPrimaryMorphologyProvider.builtIn(blend.first());
        SkyIslandPrimaryMorphologyProvider secondProvider =
                SkyIslandPrimaryMorphologyProvider.builtIn(blend.second());
        CompiledSkyIslandVolume first = firstProvider.compilePrimary(descriptor);
        CompiledSkyIslandVolume second = secondProvider.compilePrimary(descriptor);

        if (blend.secondWeight() == 0.0) {
            return endpoint(descriptor, blend, first);
        }
        if (blend.secondWeight() == 1.0) {
            return endpoint(descriptor, blend, second);
        }

        ProceduralGraph upper = hybridGraph(
                first.upperSurfaceGraph(), second.upperSurfaceGraph(), descriptor, blend, Output.UPPER);
        ProceduralGraph underside = hybridGraph(
                first.undersideSurfaceGraph(), second.undersideSurfaceGraph(), descriptor, blend, Output.UNDERSIDE);
        ProceduralGraph density = hybridGraph(
                first.densityGraph(), second.densityGraph(), descriptor, blend, Output.DENSITY);

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
            MorphologyBlend blend,
            CompiledSkyIslandVolume parent) {
        LinkedHashMap<String, List<NodeId>> provenance = new LinkedHashMap<>(parent.provenance());
        provenance.put("morphology-hybrid-endpoint:" + blend.pairIdentifier(), List.of(
                new NodeId("profile.remaining"),
                new NodeId("family.upper-factor"),
                new NodeId("family.depth-factor")));
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
            ProceduralGraph first,
            ProceduralGraph second,
            SkyIslandVolumeDescriptor descriptor,
            MorphologyBlend blend,
            Output output) {
        if (first.outputType() != second.outputType()) {
            throw new IllegalArgumentException("hybrid parent graphs must have the same output type");
        }
        Builder graph = new Builder(first.outputType());
        graph.appendPrefixed(first, FIRST_PREFIX);
        graph.appendPrefixed(second, SECOND_PREFIX);

        BlendNodes weights = graph.weights(blend);
        NodeId remaining = graph.blend(
                "profile.remaining",
                prefixed(FIRST_PREFIX, "profile.remaining"),
                prefixed(SECOND_PREFIX, "profile.remaining"),
                weights);
        NodeId one = graph.constant("profile.one", 1.0);
        NodeId radiusSquared = graph.arithmetic(
                "profile.radius-squared", ArithmeticOperator.SUBTRACT, one, remaining);
        NodeId along = graph.blend(
                "profile.along-normalized",
                prefixed(FIRST_PREFIX, "profile.along-normalized"),
                prefixed(SECOND_PREFIX, "profile.along-normalized"),
                weights);
        NodeId across = graph.blend(
                "profile.across-normalized",
                prefixed(FIRST_PREFIX, "profile.across-normalized"),
                prefixed(SECOND_PREFIX, "profile.across-normalized"),
                weights);
        NodeId alongSquared = graph.arithmetic(
                "profile.along-squared", ArithmeticOperator.MULTIPLY, along, along);
        NodeId acrossSquared = graph.arithmetic(
                "profile.across-squared", ArithmeticOperator.MULTIPLY, across, across);
        NodeId lobeDirectional = graph.blend(
                "family.lobe-directional",
                prefixed(FIRST_PREFIX, "family.lobe-directional"),
                prefixed(SECOND_PREFIX, "family.lobe-directional"),
                weights);
        NodeId suspension = graph.constant(
                "descriptor.suspension-elevation", descriptor.suspensionElevation());

        CommonProfile profile = new CommonProfile(
                remaining,
                radiusSquared,
                along,
                across,
                alongSquared,
                acrossSquared,
                lobeDirectional,
                one,
                suspension,
                weights);

        return switch (output) {
            case UPPER -> graph.build(addUpper(graph, descriptor, profile));
            case UNDERSIDE -> graph.build(addUnderside(graph, descriptor, profile));
            case DENSITY -> {
                NodeId upper = addUpper(graph, descriptor, profile);
                NodeId underside = addUnderside(graph, descriptor, profile);
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
            CommonProfile profile) {
        NodeId familyFactor = graph.blend(
                "family.upper-factor",
                prefixed(FIRST_PREFIX, "family.upper-factor"),
                prefixed(SECOND_PREFIX, "family.upper-factor"),
                profile.weights());
        NodeId crown = graph.arithmetic(
                "family.crown-profile",
                ArithmeticOperator.MULTIPLY,
                profile.remaining(),
                familyFactor);
        NodeId elevation = graph.constant("descriptor.upper-elevation", descriptor.upperElevation());
        NodeId offset = graph.arithmetic(
                "upper.offset", ArithmeticOperator.MULTIPLY, elevation, crown);
        return graph.arithmetic(
                "upper.surface", ArithmeticOperator.ADD, profile.suspension(), offset);
    }

    private static NodeId addUnderside(
            Builder graph,
            SkyIslandVolumeDescriptor descriptor,
            CommonProfile profile) {
        NodeId taper = graph.constant("descriptor.underside-taper", descriptor.undersideTaper());
        NodeId taperRadius = graph.arithmetic(
                "underside.taper-radius",
                ArithmeticOperator.MULTIPLY,
                taper,
                profile.radiusSquared());
        NodeId taperDenominator = graph.arithmetic(
                "underside.taper-denominator",
                ArithmeticOperator.ADD,
                profile.one(),
                taperRadius);
        NodeId taperedRemaining = graph.arithmetic(
                "underside.tapered-remaining",
                ArithmeticOperator.DIVIDE,
                profile.remaining(),
                taperDenominator);

        NodeId asymmetryStrength = graph.constant(
                "descriptor.underside-asymmetry",
                MAXIMUM_UNDERSIDE_ASYMMETRY * descriptor.undersideAsymmetry());
        NodeId asymmetryTerm = graph.arithmetic(
                "underside.asymmetry-term",
                ArithmeticOperator.MULTIPLY,
                asymmetryStrength,
                profile.alongNormalized());
        NodeId asymmetrySquared = graph.arithmetic(
                "underside.asymmetry-squared",
                ArithmeticOperator.MULTIPLY,
                asymmetryTerm,
                asymmetryTerm);
        NodeId onePlusAsymmetry = graph.arithmetic(
                "underside.one-plus-asymmetry",
                ArithmeticOperator.ADD,
                profile.one(),
                asymmetryTerm);
        NodeId asymmetryFactor = graph.arithmetic(
                "underside.asymmetry-factor",
                ArithmeticOperator.ADD,
                onePlusAsymmetry,
                asymmetrySquared);

        NodeId depthFactor = graph.blend(
                "family.depth-factor",
                prefixed(FIRST_PREFIX, "family.depth-factor"),
                prefixed(SECOND_PREFIX, "family.depth-factor"),
                profile.weights());
        NodeId familyRemaining = graph.arithmetic(
                "underside.family-remaining",
                ArithmeticOperator.MULTIPLY,
                taperedRemaining,
                depthFactor);
        NodeId shapedDepth = graph.arithmetic(
                "underside.shaped-depth",
                ArithmeticOperator.MULTIPLY,
                familyRemaining,
                asymmetryFactor);
        NodeId depth = graph.constant("descriptor.underside-depth", descriptor.undersideDepth());
        NodeId offset = graph.arithmetic(
                "underside.offset", ArithmeticOperator.MULTIPLY, depth, shapedDepth);
        return graph.arithmetic(
                "underside.surface", ArithmeticOperator.SUBTRACT, profile.suspension(), offset);
    }

    private static Map<String, List<NodeId>> provenance(
            MorphologyBlend blend,
            SkyIslandPrimaryMorphologyProvider first,
            SkyIslandPrimaryMorphologyProvider second) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>();
        result.put("morphology-hybrid:" + blend.pairIdentifier(), List.of(
                new NodeId("hybrid.first-weight"),
                new NodeId("hybrid.second-weight"),
                new NodeId("profile.remaining"),
                new NodeId("family.upper-factor"),
                new NodeId("family.depth-factor")));
        result.put("morphology-provider:first:" + first.identifier(), List.of(
                prefixed(FIRST_PREFIX, "profile.remaining")));
        result.put("morphology-provider:second:" + second.identifier(), List.of(
                prefixed(SECOND_PREFIX, "profile.remaining")));
        result.put("horizontal-center", List.of(
                prefixed(FIRST_PREFIX, "descriptor.center-x"),
                prefixed(FIRST_PREFIX, "descriptor.center-z")));
        result.put("suspension-elevation", List.of(new NodeId("descriptor.suspension-elevation")));
        result.put("upper-elevation", List.of(
                new NodeId("descriptor.upper-elevation"), new NodeId("upper.surface")));
        result.put("underside-depth", List.of(
                new NodeId("descriptor.underside-depth"), new NodeId("underside.surface")));
        result.put("primary-orientation", List.of(
                new NodeId("profile.along-normalized"), new NodeId("profile.across-normalized")));
        result.put("signal-controls", List.of());
        return result;
    }

    private static NodeId prefixed(String prefix, String id) {
        return new NodeId(prefix + id);
    }

    private enum Output {
        UPPER,
        UNDERSIDE,
        DENSITY
    }

    private record BlendNodes(NodeId firstWeight, NodeId secondWeight) {}

    private record CommonProfile(
            NodeId remaining,
            NodeId radiusSquared,
            NodeId alongNormalized,
            NodeId acrossNormalized,
            NodeId alongSquared,
            NodeId acrossSquared,
            NodeId lobeDirectional,
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
                NodeId id = prefixed(prefix, node.id().value());
                if (node instanceof ConstantNode constant) {
                    nodes.add(new ConstantNode(id, constant.outputType(), constant.value()));
                } else if (node instanceof CoordinateNode coordinate) {
                    nodes.add(new CoordinateNode(id, coordinate.outputType(), coordinate.axis()));
                } else if (node instanceof ArithmeticNode arithmetic) {
                    nodes.add(new ArithmeticNode(
                            id,
                            arithmetic.outputType(),
                            arithmetic.operator(),
                            prefixed(prefix, arithmetic.left().value()),
                            prefixed(prefix, arithmetic.right().value())));
                } else if (node instanceof IntersectionNode intersection) {
                    nodes.add(new IntersectionNode(
                            id,
                            prefixed(prefix, intersection.left().value()),
                            prefixed(prefix, intersection.right().value())));
                } else {
                    throw new IllegalArgumentException(
                            "hybrid primary provider emitted unsupported node kind: " + node.kind());
                }
            }
        }

        private BlendNodes weights(MorphologyBlend blend) {
            return new BlendNodes(
                    constant("hybrid.first-weight", blend.firstWeight()),
                    constant("hybrid.second-weight", blend.secondWeight()));
        }

        private NodeId blend(String id, NodeId first, NodeId second, BlendNodes weights) {
            NodeId firstWeighted = arithmetic(
                    id + ".first-weighted",
                    ArithmeticOperator.MULTIPLY,
                    first,
                    weights.firstWeight());
            NodeId secondWeighted = arithmetic(
                    id + ".second-weighted",
                    ArithmeticOperator.MULTIPLY,
                    second,
                    weights.secondWeight());
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

        private NodeId arithmetic(
                String id, ArithmeticOperator operator, NodeId left, NodeId right) {
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
