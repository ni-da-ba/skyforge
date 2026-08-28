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
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Replaces generic structured relief on an enriched hybrid with a convex blend of the two accepted
 * parent family-aware secondary factors.
 */
final class HybridFamilyAwareSecondaryMorphologyComposition {
    static final String FIRST_CARRIER_PREFIX = "hybrid-secondary.first.";
    static final String SECOND_CARRIER_PREFIX = "hybrid-secondary.second.";

    private static final NodeId UPPER_SURFACE = new NodeId("upper.surface");
    private static final NodeId UPPER_OFFSET_SEEDED = new NodeId("upper.offset.seeded");
    private static final NodeId SUSPENSION = new NodeId("descriptor.suspension-elevation");
    private static final NodeId POSITION_Y = new NodeId("position.y");
    private static final NodeId DENSITY_UPPER_CONSTRAINT = new NodeId("density.upper-constraint");
    private static final NodeId DENSITY_LOWER_CONSTRAINT = new NodeId("density.lower-constraint");
    private static final NodeId DENSITY_INTERSECTION = new NodeId("density.solid-intersection");
    private static final NodeId FIRST_WEIGHT = new NodeId("hybrid.first-weight");
    private static final NodeId SECOND_WEIGHT = new NodeId("hybrid.second-weight");

    private HybridFamilyAwareSecondaryMorphologyComposition() {}

    static CompiledSkyIslandVolume apply(
            CompiledSkyIslandVolume genericHybrid,
            CompiledSkyIslandVolume firstCarrier,
            CompiledSkyIslandVolume secondCarrier,
            MorphologyBlend blend,
            int recipeVersion) {
        Objects.requireNonNull(genericHybrid, "genericHybrid");
        Objects.requireNonNull(firstCarrier, "firstCarrier");
        Objects.requireNonNull(secondCarrier, "secondCarrier");
        Objects.requireNonNull(blend, "blend");
        if (recipeVersion <= 0) {
            throw new IllegalArgumentException("recipeVersion must be positive");
        }
        SkyIslandVolumeDescriptor descriptor = genericHybrid.descriptor();
        if (!descriptor.equals(firstCarrier.descriptor())
                || !descriptor.equals(secondCarrier.descriptor())) {
            throw new IllegalArgumentException(
                    "hybrid and parent secondary carriers must share one descriptor");
        }

        requireCompatibleHybrid(genericHybrid.upperSurfaceGraph());
        requireCompatibleHybrid(genericHybrid.densityGraph());

        ProceduralGraph upper = replaceUpper(
                genericHybrid.upperSurfaceGraph(),
                firstCarrier.upperSurfaceGraph(),
                secondCarrier.upperSurfaceGraph(),
                blend);
        ProceduralGraph density = replaceDensity(
                genericHybrid.densityGraph(),
                firstCarrier.densityGraph(),
                secondCarrier.densityGraph(),
                blend);
        return new CompiledSkyIslandVolume(
                descriptor,
                recipeVersion,
                CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                upper,
                genericHybrid.undersideSurfaceGraph(),
                density,
                provenance(genericHybrid.provenance(), blend));
    }

    static NodeId prefixedParentAmplitudeNode(String prefix, MorphologyFamily family) {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(family, "family");
        String identifier = family == MorphologyFamily.MASSIF
                ? "secondary.descriptor-amplitude"
                : "family-aware." + family.identifier() + ".descriptor-amplitude";
        return prefixed(prefix, identifier);
    }

    private static ProceduralGraph replaceUpper(
            ProceduralGraph base,
            ProceduralGraph firstCarrier,
            ProceduralGraph secondCarrier,
            MorphologyBlend blend) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        nodes.removeIf(node -> node.id().equals(UPPER_SURFACE));
        NodeId factor = addBlendedFactor(
                nodes, base.outputType(), firstCarrier, secondCarrier, blend);
        NodeId offset = arithmetic(
                nodes,
                base.outputType(),
                "upper.offset.hybrid-secondary",
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
            ProceduralGraph firstCarrier,
            ProceduralGraph secondCarrier,
            MorphologyBlend blend) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        Set<NodeId> replaced = Set.of(
                UPPER_SURFACE,
                DENSITY_UPPER_CONSTRAINT,
                DENSITY_INTERSECTION);
        nodes.removeIf(node -> replaced.contains(node.id()));

        GraphValueType type = base.outputType();
        NodeId factor = addBlendedFactor(nodes, type, firstCarrier, secondCarrier, blend);
        NodeId offset = arithmetic(
                nodes,
                type,
                "upper.offset.hybrid-secondary",
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
            GraphValueType type,
            ProceduralGraph firstCarrier,
            ProceduralGraph secondCarrier,
            MorphologyBlend blend) {
        appendPrefixed(nodes, firstCarrier, FIRST_CARRIER_PREFIX);
        appendPrefixed(nodes, secondCarrier, SECOND_CARRIER_PREFIX);

        NodeId firstFactor = prefixed(
                FIRST_CARRIER_PREFIX, acceptedFactorIdentifier(blend.first()));
        NodeId secondFactor = prefixed(
                SECOND_CARRIER_PREFIX, acceptedFactorIdentifier(blend.second()));
        NodeId firstWeighted = arithmetic(
                nodes,
                type,
                "hybrid-secondary.first-factor-weighted",
                ArithmeticOperator.MULTIPLY,
                firstFactor,
                FIRST_WEIGHT);
        NodeId secondWeighted = arithmetic(
                nodes,
                type,
                "hybrid-secondary.second-factor-weighted",
                ArithmeticOperator.MULTIPLY,
                secondFactor,
                SECOND_WEIGHT);
        return arithmetic(
                nodes,
                type,
                "hybrid-secondary.upper-factor",
                ArithmeticOperator.ADD,
                firstWeighted,
                secondWeighted);
    }

    private static String acceptedFactorIdentifier(MorphologyFamily family) {
        return family == MorphologyFamily.MASSIF
                ? "secondary.upper-factor"
                : "family-aware.upper-factor";
    }

    private static void appendPrefixed(
            List<GraphNode> target, ProceduralGraph source, String prefix) {
        for (GraphNode node : source.nodes()) {
            NodeId id = prefixed(prefix, node.id().value());
            if (node instanceof ConstantNode constant) {
                target.add(new ConstantNode(id, constant.outputType(), constant.value()));
            } else if (node instanceof CoordinateNode coordinate) {
                target.add(new CoordinateNode(id, coordinate.outputType(), coordinate.axis()));
            } else if (node instanceof ArithmeticNode arithmetic) {
                target.add(new ArithmeticNode(
                        id,
                        arithmetic.outputType(),
                        arithmetic.operator(),
                        prefixed(prefix, arithmetic.left().value()),
                        prefixed(prefix, arithmetic.right().value())));
            } else if (node instanceof IntersectionNode intersection) {
                target.add(new IntersectionNode(
                        id,
                        prefixed(prefix, intersection.left().value()),
                        prefixed(prefix, intersection.right().value())));
            } else if (node instanceof PlanarValueSignalNode signal) {
                target.add(new PlanarValueSignalNode(
                        id,
                        signal.outputType(),
                        signal.signalVersion(),
                        signal.seedVersion(),
                        signal.rootSeed(),
                        signal.namespace(),
                        signal.scale()));
            } else {
                throw new IllegalArgumentException(
                        "unsupported parent secondary carrier node kind: " + node.kind());
            }
        }
    }

    private static Map<String, List<NodeId>> provenance(
            Map<String, List<NodeId>> base, MorphologyBlend blend) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>(base);
        result.put("hybrid-secondary-morphology:" + blend.pairIdentifier(), List.of(
                FIRST_WEIGHT,
                SECOND_WEIGHT,
                prefixed(FIRST_CARRIER_PREFIX, acceptedFactorIdentifier(blend.first())),
                prefixed(SECOND_CARRIER_PREFIX, acceptedFactorIdentifier(blend.second())),
                new NodeId("hybrid-secondary.upper-factor"),
                new NodeId("upper.offset.hybrid-secondary")));
        return result;
    }

    private static void requireCompatibleHybrid(ProceduralGraph graph) {
        graph.requireNode(UPPER_OFFSET_SEEDED);
        graph.requireNode(SUSPENSION);
        graph.requireNode(FIRST_WEIGHT);
        graph.requireNode(SECOND_WEIGHT);
        if (graph.outputType() == GraphValueType.SCALAR_FIELD_3) {
            graph.requireNode(POSITION_Y);
            graph.requireNode(DENSITY_LOWER_CONSTRAINT);
        }
    }

    private static NodeId prefixed(String prefix, String identifier) {
        return new NodeId(prefix + identifier);
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
}
