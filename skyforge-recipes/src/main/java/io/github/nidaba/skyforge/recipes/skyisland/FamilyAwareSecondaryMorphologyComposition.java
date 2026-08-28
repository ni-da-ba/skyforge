package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.kernel.graph.ArithmeticNode;
import io.github.nidaba.skyforge.kernel.graph.ArithmeticOperator;
import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.GraphNode;
import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.IntersectionNode;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Replaces the generic SF-IMP-0017 upper-relief selection in an accepted SF-IMP-0019 composed
 * volume with a positive family-aware secondary factor.
 *
 * <p>The generic secondary subgraph remains present as an inspectable comparison baseline during
 * this proof, but the emitted upper surface and density depend on {@code upper.offset.seeded}
 * multiplied by the selected family-aware factor. The underside remains byte-identical to the
 * accepted SF-IMP-0019 composed underside.
 */
final class FamilyAwareSecondaryMorphologyComposition {
    private static final NodeId UPPER_SURFACE = new NodeId("upper.surface");
    private static final NodeId UPPER_OFFSET_SEEDED = new NodeId("upper.offset.seeded");
    private static final NodeId SUSPENSION = new NodeId("descriptor.suspension-elevation");
    private static final NodeId POSITION_Y = new NodeId("position.y");
    private static final NodeId DENSITY_UPPER_CONSTRAINT = new NodeId("density.upper-constraint");
    private static final NodeId DENSITY_LOWER_CONSTRAINT = new NodeId("density.lower-constraint");
    private static final NodeId DENSITY_INTERSECTION = new NodeId("density.solid-intersection");
    private static final NodeId ALONG = new NodeId("profile.along-normalized");
    private static final NodeId ACROSS = new NodeId("profile.across-normalized");
    private static final NodeId RADIUS_SQUARED = new NodeId("profile.radius-squared");
    private static final NodeId LOBE_DIRECTIONAL = new NodeId("family.lobe-directional");
    private static final NodeId GENERIC_SECONDARY_FACTOR = new NodeId("secondary.upper-factor");

    private FamilyAwareSecondaryMorphologyComposition() {}

    static CompiledSkyIslandVolume apply(
            CompiledSkyIslandVolume genericComposition,
            MorphologyFamily family,
            int recipeVersion) {
        Objects.requireNonNull(genericComposition, "genericComposition");
        Objects.requireNonNull(family, "family");
        if (recipeVersion <= 0) {
            throw new IllegalArgumentException("recipeVersion must be positive");
        }
        SkyIslandVolumeDescriptor descriptor = genericComposition.descriptor();
        if (descriptor.signalAmplitude() == 0.0) {
            return genericComposition;
        }

        requireCompatibleGraph(
                genericComposition.upperSurfaceGraph(),
                UPPER_OFFSET_SEEDED,
                SUSPENSION,
                ALONG,
                ACROSS,
                RADIUS_SQUARED,
                GENERIC_SECONDARY_FACTOR);
        requireCompatibleGraph(
                genericComposition.densityGraph(),
                UPPER_OFFSET_SEEDED,
                SUSPENSION,
                POSITION_Y,
                DENSITY_LOWER_CONSTRAINT,
                ALONG,
                ACROSS,
                RADIUS_SQUARED,
                GENERIC_SECONDARY_FACTOR);
        if (family == MorphologyFamily.LOBED) {
            requireCompatibleGraph(genericComposition.upperSurfaceGraph(), LOBE_DIRECTIONAL);
            requireCompatibleGraph(genericComposition.densityGraph(), LOBE_DIRECTIONAL);
        }

        ProceduralGraph upper = replaceUpper(
                genericComposition.upperSurfaceGraph(), descriptor, family);
        ProceduralGraph density = replaceDensity(
                genericComposition.densityGraph(), descriptor, family);
        return new CompiledSkyIslandVolume(
                descriptor,
                recipeVersion,
                CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                upper,
                genericComposition.undersideSurfaceGraph(),
                density,
                provenance(genericComposition.provenance(), family));
    }

    static double minimumUpperFactor(MorphologyFamily family) {
        return switch (family) {
            case MASSIF -> SecondaryMorphologySkyIslandVolumeRecipe.MINIMUM_UPPER_FACTOR;
            case TABLELAND -> 0.93;
            case SPINE -> 0.86;
            case BASIN -> 0.88;
            case LOBED -> 0.90;
        };
    }

    static double maximumUpperFactor(MorphologyFamily family) {
        return switch (family) {
            case MASSIF -> SecondaryMorphologySkyIslandVolumeRecipe.MAXIMUM_UPPER_FACTOR;
            case TABLELAND -> 1.15;
            case SPINE -> 1.32;
            case BASIN -> 1.18;
            case LOBED -> 1.24;
        };
    }

    private static ProceduralGraph replaceUpper(
            ProceduralGraph base,
            SkyIslandVolumeDescriptor descriptor,
            MorphologyFamily family) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        nodes.removeIf(node -> node.id().equals(UPPER_SURFACE));
        NodeId factor = addFamilyFactor(nodes, base.outputType(), descriptor, family);
        NodeId offset = arithmetic(
                nodes,
                base.outputType(),
                "upper.offset.family-aware",
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
            SkyIslandVolumeDescriptor descriptor,
            MorphologyFamily family) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        Set<NodeId> replaced = Set.of(
                UPPER_SURFACE,
                DENSITY_UPPER_CONSTRAINT,
                DENSITY_INTERSECTION);
        nodes.removeIf(node -> replaced.contains(node.id()));

        GraphValueType type = base.outputType();
        NodeId factor = addFamilyFactor(nodes, type, descriptor, family);
        NodeId offset = arithmetic(
                nodes,
                type,
                "upper.offset.family-aware",
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

    private static NodeId addFamilyFactor(
            List<GraphNode> nodes,
            GraphValueType type,
            SkyIslandVolumeDescriptor descriptor,
            MorphologyFamily family) {
        if (family == MorphologyFamily.MASSIF) {
            return GENERIC_SECONDARY_FACTOR;
        }
        return switch (family) {
            case MASSIF -> throw new AssertionError("handled above");
            case TABLELAND -> addTablelandFactor(nodes, type, descriptor);
            case SPINE -> addSpineFactor(nodes, type, descriptor);
            case BASIN -> addBasinFactor(nodes, type, descriptor);
            case LOBED -> addLobedFactor(nodes, type, descriptor);
        };
    }

    private static NodeId addTablelandFactor(
            List<GraphNode> nodes,
            GraphValueType type,
            SkyIslandVolumeDescriptor descriptor) {
        String prefix = "family-aware.tableland";
        NodeId gate = addRadialGate(nodes, type, prefix + ".outer-gate", 0.35);
        double angle = signed(descriptor.seed(), "sky-island.family-aware.tableland.ridge-angle") * 0.18;
        NodeId ridge = addBasis(nodes, type, prefix + ".rim-ridge", ALONG, ACROSS,
                new BasisShape(angle, 0.08, 0.0, 0.82, 0.20));
        NodeId shoulder = addBasis(nodes, type, prefix + ".shoulder", ALONG, ACROSS,
                new BasisShape(angle + 0.85, -0.12, 0.08, 0.54, 0.16));
        NodeId valley = addBasis(nodes, type, prefix + ".edge-cut", ALONG, ACROSS,
                new BasisShape(angle - 0.55, 0.20, -0.06, 0.66, 0.13));
        return gatedThreeTermFactor(
                nodes, type, descriptor, prefix, gate, ridge, 0.10, shoulder, 0.05, valley, 0.07);
    }

    private static NodeId addSpineFactor(
            List<GraphNode> nodes,
            GraphValueType type,
            SkyIslandVolumeDescriptor descriptor) {
        String prefix = "family-aware.spine";
        double angle = signed(descriptor.seed(), "sky-island.family-aware.spine.axis-angle") * 0.08;
        double passShift = signed(descriptor.seed(), "sky-island.family-aware.spine.pass-along") * 0.18;
        NodeId ridge = addBasis(nodes, type, prefix + ".axial-ridge", ALONG, ACROSS,
                new BasisShape(angle, 0.0, 0.0, 0.98, 0.10));
        NodeId spur = addBasis(nodes, type, prefix + ".oblique-spur", ALONG, ACROSS,
                new BasisShape(angle + 0.62, -0.18, 0.06, 0.46, 0.12));
        NodeId pass = addBasis(nodes, type, prefix + ".transverse-pass", ALONG, ACROSS,
                new BasisShape(angle + Math.PI / 2.0, passShift, 0.0, 0.28, 0.12));
        return threeTermFactor(
                nodes, type, descriptor, prefix, ridge, 0.24, spur, 0.08, pass, 0.14);
    }

    private static NodeId addBasinFactor(
            List<GraphNode> nodes,
            GraphValueType type,
            SkyIslandVolumeDescriptor descriptor) {
        String prefix = "family-aware.basin";
        double ringCenter = 0.42
                + signed(descriptor.seed(), "sky-island.family-aware.basin.ring-center") * 0.04;
        NodeId gate = addRadialGate(nodes, type, prefix + ".center-preservation-gate", 0.15);
        NodeId ring = addAnnulus(nodes, type, prefix + ".ring", ringCenter, 0.16);
        NodeId gatedRing = arithmetic(
                nodes, type, prefix + ".gated-ring", ArithmeticOperator.MULTIPLY, gate, ring);
        double drainageAngle = signed(
                descriptor.seed(), "sky-island.family-aware.basin.drainage-angle") * 0.45;
        NodeId drainage = addBasis(nodes, type, prefix + ".drainage", ALONG, ACROSS,
                new BasisShape(drainageAngle, 0.12, 0.0, 0.82, 0.10));
        NodeId gatedDrainage = arithmetic(
                nodes, type, prefix + ".gated-drainage", ArithmeticOperator.MULTIPLY, gate, drainage);
        NodeId amplitude = amplitude(nodes, type, prefix, descriptor);
        NodeId ringWeighted = weighted(nodes, type, prefix + ".ring-weighted", gatedRing, 0.18, amplitude);
        NodeId drainageWeighted = weighted(
                nodes, type, prefix + ".drainage-weighted", gatedDrainage, 0.12, amplitude);
        NodeId net = arithmetic(
                nodes, type, prefix + ".net", ArithmeticOperator.SUBTRACT, ringWeighted, drainageWeighted);
        NodeId one = constant(nodes, type, prefix + ".one", 1.0);
        return arithmetic(
                nodes, type, "family-aware.upper-factor", ArithmeticOperator.ADD, one, net);
    }

    private static NodeId addLobedFactor(
            List<GraphNode> nodes,
            GraphValueType type,
            SkyIslandVolumeDescriptor descriptor) {
        String prefix = "family-aware.lobed";
        NodeId amplitude = amplitude(nodes, type, prefix, descriptor);
        NodeId shoulders = weighted(
                nodes, type, prefix + ".shoulders", LOBE_DIRECTIONAL, 0.16, amplitude);
        double saddleAngle = Math.PI / 4.0
                + signed(descriptor.seed(), "sky-island.family-aware.lobed.saddle-angle") * 0.10;
        NodeId saddle = addBasis(nodes, type, prefix + ".inter-lobe-saddle", ALONG, ACROSS,
                new BasisShape(saddleAngle, 0.0, 0.0, 0.82, 0.11));
        NodeId saddleWeighted = weighted(
                nodes, type, prefix + ".saddle-weighted", saddle, 0.10, amplitude);
        NodeId shoulderRidge = addBasis(nodes, type, prefix + ".shoulder-ridge", ALONG, ACROSS,
                new BasisShape(0.0, 0.18, 0.0, 0.56, 0.14));
        NodeId ridgeWeighted = weighted(
                nodes, type, prefix + ".ridge-weighted", shoulderRidge, 0.08, amplitude);
        NodeId positive = arithmetic(
                nodes, type, prefix + ".positive", ArithmeticOperator.ADD, shoulders, ridgeWeighted);
        NodeId net = arithmetic(
                nodes, type, prefix + ".net", ArithmeticOperator.SUBTRACT, positive, saddleWeighted);
        NodeId one = constant(nodes, type, prefix + ".one", 1.0);
        return arithmetic(
                nodes, type, "family-aware.upper-factor", ArithmeticOperator.ADD, one, net);
    }

    private static NodeId threeTermFactor(
            List<GraphNode> nodes,
            GraphValueType type,
            SkyIslandVolumeDescriptor descriptor,
            String prefix,
            NodeId first,
            double firstStrength,
            NodeId second,
            double secondStrength,
            NodeId negative,
            double negativeStrength) {
        NodeId amplitude = amplitude(nodes, type, prefix, descriptor);
        NodeId firstWeighted = weighted(
                nodes, type, prefix + ".first-weighted", first, firstStrength, amplitude);
        NodeId secondWeighted = weighted(
                nodes, type, prefix + ".second-weighted", second, secondStrength, amplitude);
        NodeId negativeWeighted = weighted(
                nodes, type, prefix + ".negative-weighted", negative, negativeStrength, amplitude);
        NodeId positive = arithmetic(
                nodes, type, prefix + ".positive", ArithmeticOperator.ADD, firstWeighted, secondWeighted);
        NodeId net = arithmetic(
                nodes, type, prefix + ".net", ArithmeticOperator.SUBTRACT, positive, negativeWeighted);
        NodeId one = constant(nodes, type, prefix + ".one", 1.0);
        return arithmetic(
                nodes, type, "family-aware.upper-factor", ArithmeticOperator.ADD, one, net);
    }

    private static NodeId gatedThreeTermFactor(
            List<GraphNode> nodes,
            GraphValueType type,
            SkyIslandVolumeDescriptor descriptor,
            String prefix,
            NodeId gate,
            NodeId first,
            double firstStrength,
            NodeId second,
            double secondStrength,
            NodeId negative,
            double negativeStrength) {
        NodeId firstGated = arithmetic(
                nodes, type, prefix + ".first-gated", ArithmeticOperator.MULTIPLY, gate, first);
        NodeId secondGated = arithmetic(
                nodes, type, prefix + ".second-gated", ArithmeticOperator.MULTIPLY, gate, second);
        NodeId negativeGated = arithmetic(
                nodes, type, prefix + ".negative-gated", ArithmeticOperator.MULTIPLY, gate, negative);
        return threeTermFactor(
                nodes,
                type,
                descriptor,
                prefix,
                firstGated,
                firstStrength,
                secondGated,
                secondStrength,
                negativeGated,
                negativeStrength);
    }

    private static NodeId addRadialGate(
            List<GraphNode> nodes,
            GraphValueType type,
            String prefix,
            double offset) {
        NodeId offsetNode = constant(nodes, type, prefix + ".offset", offset);
        NodeId denominator = arithmetic(
                nodes,
                type,
                prefix + ".denominator",
                ArithmeticOperator.ADD,
                offsetNode,
                RADIUS_SQUARED);
        return arithmetic(
                nodes,
                type,
                prefix,
                ArithmeticOperator.DIVIDE,
                RADIUS_SQUARED,
                denominator);
    }

    private static NodeId addAnnulus(
            List<GraphNode> nodes,
            GraphValueType type,
            String prefix,
            double center,
            double width) {
        NodeId centerNode = constant(nodes, type, prefix + ".center", center);
        NodeId widthNode = constant(nodes, type, prefix + ".width", width);
        NodeId local = arithmetic(
                nodes, type, prefix + ".local", ArithmeticOperator.SUBTRACT, RADIUS_SQUARED, centerNode);
        NodeId normalized = arithmetic(
                nodes, type, prefix + ".normalized", ArithmeticOperator.DIVIDE, local, widthNode);
        NodeId squared = arithmetic(
                nodes, type, prefix + ".squared", ArithmeticOperator.MULTIPLY, normalized, normalized);
        NodeId one = constant(nodes, type, prefix + ".one", 1.0);
        NodeId denominator = arithmetic(
                nodes, type, prefix + ".denominator", ArithmeticOperator.ADD, one, squared);
        return arithmetic(
                nodes, type, prefix + ".basis", ArithmeticOperator.DIVIDE, one, denominator);
    }

    private static NodeId amplitude(
            List<GraphNode> nodes,
            GraphValueType type,
            String prefix,
            SkyIslandVolumeDescriptor descriptor) {
        return constant(nodes, type, prefix + ".descriptor-amplitude", descriptor.signalAmplitude());
    }

    private static NodeId weighted(
            List<GraphNode> nodes,
            GraphValueType type,
            String prefix,
            NodeId basis,
            double strength,
            NodeId amplitude) {
        NodeId strengthNode = constant(nodes, type, prefix + ".strength", strength);
        NodeId base = arithmetic(
                nodes, type, prefix + ".base", ArithmeticOperator.MULTIPLY, basis, strengthNode);
        return arithmetic(
                nodes, type, prefix, ArithmeticOperator.MULTIPLY, base, amplitude);
    }

    private static NodeId addBasis(
            List<GraphNode> nodes,
            GraphValueType type,
            String prefix,
            NodeId along,
            NodeId across,
            BasisShape shape) {
        NodeId cosine = constant(nodes, type, prefix + ".cos-angle", Math.cos(shape.angle()));
        NodeId sine = constant(nodes, type, prefix + ".sin-angle", Math.sin(shape.angle()));
        NodeId alongCos = arithmetic(
                nodes, type, prefix + ".along-cos", ArithmeticOperator.MULTIPLY, along, cosine);
        NodeId acrossSin = arithmetic(
                nodes, type, prefix + ".across-sin", ArithmeticOperator.MULTIPLY, across, sine);
        NodeId rotatedAlong = arithmetic(
                nodes, type, prefix + ".rotated-along", ArithmeticOperator.ADD, alongCos, acrossSin);
        NodeId acrossCos = arithmetic(
                nodes, type, prefix + ".across-cos", ArithmeticOperator.MULTIPLY, across, cosine);
        NodeId alongSin = arithmetic(
                nodes, type, prefix + ".along-sin", ArithmeticOperator.MULTIPLY, along, sine);
        NodeId rotatedAcross = arithmetic(
                nodes, type, prefix + ".rotated-across", ArithmeticOperator.SUBTRACT, acrossCos, alongSin);
        NodeId centerAlong = constant(nodes, type, prefix + ".center-along", shape.centerAlong());
        NodeId centerAcross = constant(nodes, type, prefix + ".center-across", shape.centerAcross());
        NodeId localAlong = arithmetic(
                nodes, type, prefix + ".local-along", ArithmeticOperator.SUBTRACT, rotatedAlong, centerAlong);
        NodeId localAcross = arithmetic(
                nodes, type, prefix + ".local-across", ArithmeticOperator.SUBTRACT, rotatedAcross, centerAcross);
        NodeId length = constant(nodes, type, prefix + ".length", shape.length());
        NodeId width = constant(nodes, type, prefix + ".width", shape.width());
        NodeId normalizedAlong = arithmetic(
                nodes, type, prefix + ".normalized-along", ArithmeticOperator.DIVIDE, localAlong, length);
        NodeId normalizedAcross = arithmetic(
                nodes, type, prefix + ".normalized-across", ArithmeticOperator.DIVIDE, localAcross, width);
        NodeId alongSquared = arithmetic(
                nodes, type, prefix + ".along-squared", ArithmeticOperator.MULTIPLY, normalizedAlong, normalizedAlong);
        NodeId alongFourth = arithmetic(
                nodes, type, prefix + ".along-fourth", ArithmeticOperator.MULTIPLY, alongSquared, alongSquared);
        NodeId acrossSquared = arithmetic(
                nodes, type, prefix + ".across-squared", ArithmeticOperator.MULTIPLY, normalizedAcross, normalizedAcross);
        NodeId one = constant(nodes, type, prefix + ".one", 1.0);
        NodeId onePlusAcross = arithmetic(
                nodes, type, prefix + ".one-plus-across", ArithmeticOperator.ADD, one, acrossSquared);
        NodeId denominator = arithmetic(
                nodes, type, prefix + ".denominator", ArithmeticOperator.ADD, onePlusAcross, alongFourth);
        return arithmetic(
                nodes, type, prefix + ".basis", ArithmeticOperator.DIVIDE, one, denominator);
    }

    private static Map<String, List<NodeId>> provenance(
            Map<String, List<NodeId>> base,
            MorphologyFamily family) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>(base);
        List<NodeId> generic = result.remove("secondary-morphology");
        if (generic != null) {
            result.put("secondary-morphology-baseline", generic);
        }
        result.put("family-aware-secondary:" + family.identifier(), family == MorphologyFamily.MASSIF
                ? List.of(GENERIC_SECONDARY_FACTOR, new NodeId("upper.offset.family-aware"))
                : List.of(new NodeId("family-aware.upper-factor"), new NodeId("upper.offset.family-aware")));
        return result;
    }

    private static void requireCompatibleGraph(ProceduralGraph graph, NodeId... required) {
        for (NodeId id : required) {
            graph.requireNode(id);
        }
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

    private record BasisShape(
            double angle,
            double centerAlong,
            double centerAcross,
            double length,
            double width) {}

    private static double signed(long seed, String namespace) {
        return 2.0 * unit(seed, namespace) - 1.0;
    }

    private static double unit(long seed, String namespace) {
        long value = SeedDerivation.derive(seed, namespace);
        return (value >>> 11) * 0x1.0p-53;
    }
}
