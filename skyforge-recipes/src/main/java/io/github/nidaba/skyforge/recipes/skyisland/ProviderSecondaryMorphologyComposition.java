package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.kernel.graph.ArithmeticNode;
import io.github.nidaba.skyforge.kernel.graph.ArithmeticOperator;
import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.CoordinateNode;
import io.github.nidaba.skyforge.kernel.graph.GraphNode;
import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.IntersectionNode;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.PlanarValueSignalNode;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Replaces generic structured relief with provider-supplied secondary factors. */
final class ProviderSecondaryMorphologyComposition {
    static final String FIRST_FACTOR_PREFIX = "provider-secondary.first.";
    static final String SECOND_FACTOR_PREFIX = "provider-secondary.second.";
    static final String SINGLE_FACTOR_PREFIX = "provider-secondary.single.";

    private static final NodeId UPPER_SURFACE = new NodeId("upper.surface");
    private static final NodeId UPPER_OFFSET_SEEDED = new NodeId("upper.offset.seeded");
    private static final NodeId SUSPENSION = new NodeId("descriptor.suspension-elevation");
    private static final NodeId POSITION_Y = new NodeId("position.y");
    private static final NodeId DENSITY_UPPER_CONSTRAINT = new NodeId("density.upper-constraint");
    private static final NodeId DENSITY_LOWER_CONSTRAINT = new NodeId("density.lower-constraint");
    private static final NodeId DENSITY_INTERSECTION = new NodeId("density.solid-intersection");
    private static final NodeId FIRST_WEIGHT = new NodeId("provider-secondary.first-weight");
    private static final NodeId SECOND_WEIGHT = new NodeId("provider-secondary.second-weight");
    private static final NodeId MINIMUM_FACTOR = new NodeId("provider-secondary.minimum-factor");
    private static final NodeId MAXIMUM_FACTOR = new NodeId("provider-secondary.maximum-factor");

    private ProviderSecondaryMorphologyComposition() {}

    static CompiledSkyIslandVolume apply(
            CompiledSkyIslandVolume genericHybrid,
            Optional<SecondaryMorphologyContribution> firstContribution,
            Optional<SecondaryMorphologyContribution> secondContribution,
            MorphologyProviderBlend blend,
            int recipeVersion) {
        Objects.requireNonNull(genericHybrid, "genericHybrid");
        Objects.requireNonNull(firstContribution, "firstContribution");
        Objects.requireNonNull(secondContribution, "secondContribution");
        Objects.requireNonNull(blend, "blend");
        if (recipeVersion <= 0) {
            throw new IllegalArgumentException("recipeVersion must be positive");
        }

        requireCompatibleHybrid(genericHybrid.upperSurfaceGraph());
        requireCompatibleHybrid(genericHybrid.densityGraph());
        Envelope envelope = envelope(firstContribution, secondContribution, blend);

        ProceduralGraph upper = replaceUpper(
                genericHybrid.upperSurfaceGraph(), firstContribution, secondContribution, blend, envelope);
        ProceduralGraph density = replaceDensity(
                genericHybrid.densityGraph(), firstContribution, secondContribution, blend, envelope);
        return new CompiledSkyIslandVolume(
                genericHybrid.descriptor(),
                recipeVersion,
                CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                upper,
                genericHybrid.undersideSurfaceGraph(),
                density,
                provenance(genericHybrid.provenance(), blend));
    }

    /** Applies one provider's optional secondary vocabulary without manufacturing a fake blend. */
    static CompiledSkyIslandVolume applySingle(
            CompiledSkyIslandVolume genericPrimary,
            Optional<SecondaryMorphologyContribution> contribution,
            MorphologyProviderId providerId,
            int recipeVersion) {
        Objects.requireNonNull(genericPrimary, "genericPrimary");
        Objects.requireNonNull(contribution, "contribution");
        Objects.requireNonNull(providerId, "providerId");
        if (recipeVersion <= 0) {
            throw new IllegalArgumentException("recipeVersion must be positive");
        }

        requireCompatibleHybrid(genericPrimary.upperSurfaceGraph());
        requireCompatibleHybrid(genericPrimary.densityGraph());
        Envelope envelope = singleEnvelope(contribution);
        ProceduralGraph upper = replaceUpperSingle(
                genericPrimary.upperSurfaceGraph(), contribution, envelope);
        ProceduralGraph density = replaceDensitySingle(
                genericPrimary.densityGraph(), contribution, envelope);
        return new CompiledSkyIslandVolume(
                genericPrimary.descriptor(),
                recipeVersion,
                CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                upper,
                genericPrimary.undersideSurfaceGraph(),
                density,
                singleProvenance(genericPrimary.provenance(), providerId));
    }

    private static ProceduralGraph replaceUpper(
            ProceduralGraph base,
            Optional<SecondaryMorphologyContribution> firstContribution,
            Optional<SecondaryMorphologyContribution> secondContribution,
            MorphologyProviderBlend blend,
            Envelope envelope) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        nodes.removeIf(node -> node.id().equals(UPPER_SURFACE));
        NodeId factor = addBlendedFactor(
                nodes, base.outputType(), firstContribution, secondContribution, blend, envelope);
        NodeId offset = arithmetic(
                nodes,
                base.outputType(),
                "upper.offset.provider-secondary",
                ArithmeticOperator.MULTIPLY,
                UPPER_OFFSET_SEEDED,
                factor);
        nodes.add(new ArithmeticNode(
                UPPER_SURFACE,
                base.outputType(),
                ArithmeticOperator.ADD,
                SUSPENSION,
                offset));
        return new ProceduralGraph(nodes, UPPER_SURFACE);
    }

    private static ProceduralGraph replaceDensity(
            ProceduralGraph base,
            Optional<SecondaryMorphologyContribution> firstContribution,
            Optional<SecondaryMorphologyContribution> secondContribution,
            MorphologyProviderBlend blend,
            Envelope envelope) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        Set<NodeId> replaced = Set.of(
                UPPER_SURFACE,
                DENSITY_UPPER_CONSTRAINT,
                DENSITY_INTERSECTION);
        nodes.removeIf(node -> replaced.contains(node.id()));

        GraphValueType type = base.outputType();
        NodeId factor = addBlendedFactor(
                nodes, type, firstContribution, secondContribution, blend, envelope);
        NodeId offset = arithmetic(
                nodes,
                type,
                "upper.offset.provider-secondary",
                ArithmeticOperator.MULTIPLY,
                UPPER_OFFSET_SEEDED,
                factor);
        nodes.add(new ArithmeticNode(
                UPPER_SURFACE,
                type,
                ArithmeticOperator.ADD,
                SUSPENSION,
                offset));
        nodes.add(new ArithmeticNode(
                DENSITY_UPPER_CONSTRAINT,
                type,
                ArithmeticOperator.SUBTRACT,
                UPPER_SURFACE,
                POSITION_Y));
        nodes.add(new IntersectionNode(
                DENSITY_INTERSECTION,
                DENSITY_UPPER_CONSTRAINT,
                DENSITY_LOWER_CONSTRAINT));
        return new ProceduralGraph(nodes, DENSITY_INTERSECTION);
    }

    private static ProceduralGraph replaceUpperSingle(
            ProceduralGraph base,
            Optional<SecondaryMorphologyContribution> contribution,
            Envelope envelope) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        nodes.removeIf(node -> node.id().equals(UPPER_SURFACE));
        NodeId factor = addSingleFactor(nodes, base.outputType(), contribution, envelope);
        NodeId offset = arithmetic(
                nodes,
                base.outputType(),
                "upper.offset.provider-secondary",
                ArithmeticOperator.MULTIPLY,
                UPPER_OFFSET_SEEDED,
                factor);
        nodes.add(new ArithmeticNode(
                UPPER_SURFACE,
                base.outputType(),
                ArithmeticOperator.ADD,
                SUSPENSION,
                offset));
        return new ProceduralGraph(nodes, UPPER_SURFACE);
    }

    private static ProceduralGraph replaceDensitySingle(
            ProceduralGraph base,
            Optional<SecondaryMorphologyContribution> contribution,
            Envelope envelope) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        Set<NodeId> replaced = Set.of(
                UPPER_SURFACE,
                DENSITY_UPPER_CONSTRAINT,
                DENSITY_INTERSECTION);
        nodes.removeIf(node -> replaced.contains(node.id()));

        GraphValueType type = base.outputType();
        NodeId factor = addSingleFactor(nodes, type, contribution, envelope);
        NodeId offset = arithmetic(
                nodes,
                type,
                "upper.offset.provider-secondary",
                ArithmeticOperator.MULTIPLY,
                UPPER_OFFSET_SEEDED,
                factor);
        nodes.add(new ArithmeticNode(
                UPPER_SURFACE,
                type,
                ArithmeticOperator.ADD,
                SUSPENSION,
                offset));
        nodes.add(new ArithmeticNode(
                DENSITY_UPPER_CONSTRAINT,
                type,
                ArithmeticOperator.SUBTRACT,
                UPPER_SURFACE,
                POSITION_Y));
        nodes.add(new IntersectionNode(
                DENSITY_INTERSECTION,
                DENSITY_UPPER_CONSTRAINT,
                DENSITY_LOWER_CONSTRAINT));
        return new ProceduralGraph(nodes, DENSITY_INTERSECTION);
    }

    private static NodeId addBlendedFactor(
            List<GraphNode> nodes,
            GraphValueType targetType,
            Optional<SecondaryMorphologyContribution> firstContribution,
            Optional<SecondaryMorphologyContribution> secondContribution,
            MorphologyProviderBlend blend,
            Envelope envelope) {
        NodeId firstFactor = appendContribution(
                nodes, targetType, firstContribution, FIRST_FACTOR_PREFIX);
        NodeId secondFactor = appendContribution(
                nodes, targetType, secondContribution, SECOND_FACTOR_PREFIX);
        NodeId firstWeight = constant(
                nodes, targetType, FIRST_WEIGHT.value(), blend.firstWeight());
        NodeId secondWeight = constant(
                nodes, targetType, SECOND_WEIGHT.value(), blend.secondWeight());
        constant(nodes, targetType, MINIMUM_FACTOR.value(), envelope.minimum());
        constant(nodes, targetType, MAXIMUM_FACTOR.value(), envelope.maximum());

        NodeId firstWeighted = arithmetic(
                nodes,
                targetType,
                "provider-secondary.first-factor-weighted",
                ArithmeticOperator.MULTIPLY,
                firstFactor,
                firstWeight);
        NodeId secondWeighted = arithmetic(
                nodes,
                targetType,
                "provider-secondary.second-factor-weighted",
                ArithmeticOperator.MULTIPLY,
                secondFactor,
                secondWeight);
        return arithmetic(
                nodes,
                targetType,
                "provider-secondary.upper-factor",
                ArithmeticOperator.ADD,
                firstWeighted,
                secondWeighted);
    }

    private static NodeId addSingleFactor(
            List<GraphNode> nodes,
            GraphValueType targetType,
            Optional<SecondaryMorphologyContribution> contribution,
            Envelope envelope) {
        NodeId factor = appendContribution(nodes, targetType, contribution, SINGLE_FACTOR_PREFIX);
        constant(nodes, targetType, MINIMUM_FACTOR.value(), envelope.minimum());
        constant(nodes, targetType, MAXIMUM_FACTOR.value(), envelope.maximum());
        // Alias under the same final semantic node used by pairwise composition so downstream
        // inspectability does not depend on whether the morphology was single-provider or blended.
        NodeId one = constant(nodes, targetType, "provider-secondary.single-weight", 1.0);
        return arithmetic(
                nodes,
                targetType,
                "provider-secondary.upper-factor",
                ArithmeticOperator.MULTIPLY,
                factor,
                one);
    }

    private static NodeId appendContribution(
            List<GraphNode> target,
            GraphValueType targetType,
            Optional<SecondaryMorphologyContribution> contribution,
            String prefix) {
        if (contribution.isEmpty()) {
            return constant(target, targetType, prefix + "neutral-factor", 1.0);
        }
        ProceduralGraph factor = contribution.orElseThrow().factorGraph();
        appendPromotedPrefixed(target, factor, prefix, targetType);
        return prefixed(prefix, factor.output());
    }

    /**
     * Copies a provider factor graph into either a 2-D surface or 3-D density graph.
     *
     * <p>Only the field dimensionality changes; coordinates, constants, signal identity, operators,
     * and graph topology are preserved exactly. This is the provider-neutral bridge that lets one
     * public 2-D factor contribution participate in the 3-D density proof without provider-specific
     * node knowledge.
     */
    private static void appendPromotedPrefixed(
            List<GraphNode> target,
            ProceduralGraph source,
            String prefix,
            GraphValueType targetType) {
        for (GraphNode node : source.nodes()) {
            NodeId id = prefixed(prefix, node.id());
            if (node instanceof ConstantNode constant) {
                target.add(new ConstantNode(id, targetType, constant.value()));
            } else if (node instanceof CoordinateNode coordinate) {
                target.add(new CoordinateNode(id, targetType, coordinate.axis()));
            } else if (node instanceof ArithmeticNode arithmetic) {
                target.add(new ArithmeticNode(
                        id,
                        targetType,
                        arithmetic.operator(),
                        prefixed(prefix, arithmetic.left()),
                        prefixed(prefix, arithmetic.right())));
            } else if (node instanceof IntersectionNode intersection) {
                target.add(new IntersectionNode(
                        id,
                        prefixed(prefix, intersection.left()),
                        prefixed(prefix, intersection.right())));
            } else if (node instanceof PlanarValueSignalNode signal) {
                target.add(new PlanarValueSignalNode(
                        id,
                        targetType,
                        signal.signalVersion(),
                        signal.seedVersion(),
                        signal.rootSeed(),
                        signal.namespace(),
                        signal.scale()));
            } else {
                throw new IllegalArgumentException(
                        "unsupported provider secondary node kind: " + node.kind());
            }
        }
    }

    private static Envelope envelope(
            Optional<SecondaryMorphologyContribution> firstContribution,
            Optional<SecondaryMorphologyContribution> secondContribution,
            MorphologyProviderBlend blend) {
        double firstMinimum = firstContribution.map(SecondaryMorphologyContribution::minimumFactor).orElse(1.0);
        double firstMaximum = firstContribution.map(SecondaryMorphologyContribution::maximumFactor).orElse(1.0);
        double secondMinimum = secondContribution.map(SecondaryMorphologyContribution::minimumFactor).orElse(1.0);
        double secondMaximum = secondContribution.map(SecondaryMorphologyContribution::maximumFactor).orElse(1.0);
        double minimum = blend.firstWeight() * firstMinimum + blend.secondWeight() * secondMinimum;
        double maximum = blend.firstWeight() * firstMaximum + blend.secondWeight() * secondMaximum;
        requireEnvelope(minimum, maximum, "blended provider secondary envelope");
        return new Envelope(minimum, maximum);
    }

    private static Envelope singleEnvelope(Optional<SecondaryMorphologyContribution> contribution) {
        double minimum = contribution.map(SecondaryMorphologyContribution::minimumFactor).orElse(1.0);
        double maximum = contribution.map(SecondaryMorphologyContribution::maximumFactor).orElse(1.0);
        requireEnvelope(minimum, maximum, "provider secondary envelope");
        return new Envelope(minimum, maximum);
    }

    private static void requireEnvelope(double minimum, double maximum, String label) {
        if (!Double.isFinite(minimum)
                || !Double.isFinite(maximum)
                || minimum <= 0.0
                || maximum < minimum) {
            throw new IllegalArgumentException(label + " must remain finite and positive");
        }
    }

    private static Map<String, List<NodeId>> provenance(
            Map<String, List<NodeId>> base,
            MorphologyProviderBlend blend) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>(base);
        result.put("provider-secondary-morphology:" + blend.pairIdentifier(), List.of(
                FIRST_WEIGHT,
                SECOND_WEIGHT,
                MINIMUM_FACTOR,
                MAXIMUM_FACTOR,
                new NodeId("provider-secondary.upper-factor"),
                new NodeId("upper.offset.provider-secondary")));
        return result;
    }

    private static Map<String, List<NodeId>> singleProvenance(
            Map<String, List<NodeId>> base,
            MorphologyProviderId providerId) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>(base);
        result.put("provider-secondary-morphology:" + providerId, List.of(
                MINIMUM_FACTOR,
                MAXIMUM_FACTOR,
                new NodeId("provider-secondary.upper-factor"),
                new NodeId("upper.offset.provider-secondary")));
        return result;
    }

    private static void requireCompatibleHybrid(ProceduralGraph graph) {
        graph.requireNode(UPPER_OFFSET_SEEDED);
        graph.requireNode(SUSPENSION);
        if (graph.outputType() == GraphValueType.SCALAR_FIELD_3) {
            graph.requireNode(POSITION_Y);
            graph.requireNode(DENSITY_LOWER_CONSTRAINT);
        }
    }

    private static NodeId prefixed(String prefix, NodeId id) {
        return new NodeId(prefix + id.value());
    }

    private static NodeId constant(
            List<GraphNode> nodes,
            GraphValueType type,
            String identifier,
            double value) {
        NodeId id = new NodeId(identifier);
        nodes.add(new ConstantNode(id, type, value));
        return id;
    }

    private static NodeId arithmetic(
            List<GraphNode> nodes,
            GraphValueType type,
            String identifier,
            ArithmeticOperator operator,
            NodeId left,
            NodeId right) {
        NodeId id = new NodeId(identifier);
        nodes.add(new ArithmeticNode(id, type, operator, left, right));
        return id;
    }

    private record Envelope(double minimum, double maximum) {}
}
