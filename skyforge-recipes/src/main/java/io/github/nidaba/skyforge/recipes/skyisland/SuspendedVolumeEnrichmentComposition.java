package io.github.nidaba.skyforge.recipes.skyisland;

import io.github.nidaba.skyforge.kernel.graph.ArithmeticNode;
import io.github.nidaba.skyforge.kernel.graph.ArithmeticOperator;
import io.github.nidaba.skyforge.kernel.graph.ConstantNode;
import io.github.nidaba.skyforge.kernel.graph.GraphNode;
import io.github.nidaba.skyforge.kernel.graph.GraphValueType;
import io.github.nidaba.skyforge.kernel.graph.IntersectionNode;
import io.github.nidaba.skyforge.kernel.graph.NodeId;
import io.github.nidaba.skyforge.kernel.graph.PlanarValueSignalNode;
import io.github.nidaba.skyforge.kernel.graph.ProceduralGraph;
import io.github.nidaba.skyforge.kernel.seed.SeedDerivation;
import io.github.nidaba.skyforge.kernel.serialization.CanonicalGraphJson;
import io.github.nidaba.skyforge.kernel.signal.PlanarValueSignal;
import io.github.nidaba.skyforge.model.skyisland.SkyIslandVolumeDescriptor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Applies the accepted bounded-detail and structured-relief transforms to a compatible primary volume. */
final class SuspendedVolumeEnrichmentComposition {
    private static final NodeId UPPER_SURFACE = new NodeId("upper.surface");
    private static final NodeId UNDERSIDE_SURFACE = new NodeId("underside.surface");
    private static final NodeId UPPER_OFFSET = new NodeId("upper.offset");
    private static final NodeId UNDERSIDE_OFFSET = new NodeId("underside.offset");
    private static final NodeId UPPER_OFFSET_SEEDED = new NodeId("upper.offset.seeded");
    private static final NodeId SUSPENSION = new NodeId("descriptor.suspension-elevation");
    private static final NodeId DENSITY_UPPER_CONSTRAINT = new NodeId("density.upper-constraint");
    private static final NodeId DENSITY_LOWER_CONSTRAINT = new NodeId("density.lower-constraint");
    private static final NodeId DENSITY_INTERSECTION = new NodeId("density.solid-intersection");
    private static final NodeId POSITION_Y = new NodeId("position.y");

    private SuspendedVolumeEnrichmentComposition() {}

    /**
     * Applies the exact SF-IMP-0016 bounded signal factors followed by the exact SF-IMP-0017
     * structured upper-surface factor.
     *
     * <p>The base descriptor must equal the target descriptor with signal amplitude set to zero.
     * This ensures that composition changes only the two already accepted enrichment layers.
     */
    static CompiledSkyIslandVolume apply(
            CompiledSkyIslandVolume base,
            SkyIslandVolumeDescriptor target,
            int recipeVersion) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(target, "target");
        if (recipeVersion <= 0) {
            throw new IllegalArgumentException("recipeVersion must be positive");
        }
        SkyIslandVolumeDescriptor expectedBase = withoutSignalAmplitude(target);
        if (!base.descriptor().equals(expectedBase)) {
            throw new IllegalArgumentException(
                    "base descriptor must equal the target descriptor with zero signal amplitude");
        }
        requireCompatibleGraph(base.upperSurfaceGraph(), UPPER_OFFSET, UPPER_SURFACE, SUSPENSION);
        requireCompatibleGraph(base.undersideSurfaceGraph(), UNDERSIDE_OFFSET, UNDERSIDE_SURFACE, SUSPENSION);
        requireCompatibleGraph(
                base.densityGraph(),
                UPPER_OFFSET,
                UNDERSIDE_OFFSET,
                UPPER_SURFACE,
                UNDERSIDE_SURFACE,
                DENSITY_UPPER_CONSTRAINT,
                DENSITY_LOWER_CONSTRAINT,
                DENSITY_INTERSECTION,
                POSITION_Y,
                SUSPENSION,
                new NodeId("profile.along-normalized"),
                new NodeId("profile.across-normalized"));

        if (target.signalAmplitude() == 0.0) {
            return base;
        }

        ProceduralGraph seededUpper = addBoundedSurfaceDetail(
                base.upperSurfaceGraph(), target, Surface.UPPER);
        ProceduralGraph seededUnderside = addBoundedSurfaceDetail(
                base.undersideSurfaceGraph(), target, Surface.UNDERSIDE);
        ProceduralGraph seededDensity = addBoundedDensityDetail(base.densityGraph(), target);
        Map<String, List<NodeId>> seededProvenance = boundedProvenance(base.provenance());

        SecondaryShape shape = SecondaryShape.from(target);
        ProceduralGraph structuredUpper = addStructuredUpper(seededUpper, target, shape);
        ProceduralGraph structuredDensity = addStructuredDensity(seededDensity, target, shape);
        Map<String, List<NodeId>> provenance = structuredProvenance(seededProvenance);

        return new CompiledSkyIslandVolume(
                target,
                recipeVersion,
                CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                structuredUpper,
                seededUnderside,
                structuredDensity,
                provenance);
    }

    private static SkyIslandVolumeDescriptor withoutSignalAmplitude(
            SkyIslandVolumeDescriptor descriptor) {
        return new SkyIslandVolumeDescriptor(
                descriptor.schemaVersion(),
                descriptor.seed(),
                descriptor.centerX(),
                descriptor.centerZ(),
                descriptor.suspensionElevation(),
                descriptor.nominalRadius(),
                descriptor.upperElevation(),
                descriptor.undersideDepth(),
                descriptor.coastalFalloff(),
                descriptor.ridgeAzimuth(),
                descriptor.ridgeStrength(),
                descriptor.undersideTaper(),
                descriptor.undersideAsymmetry(),
                0.0,
                descriptor.signalScale());
    }

    private static void requireCompatibleGraph(ProceduralGraph graph, NodeId... required) {
        for (NodeId id : required) {
            graph.requireNode(id);
        }
    }

    private static ProceduralGraph addBoundedSurfaceDetail(
            ProceduralGraph base,
            SkyIslandVolumeDescriptor descriptor,
            Surface surface) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        NodeId surfaceNode = surface == Surface.UPPER ? UPPER_SURFACE : UNDERSIDE_SURFACE;
        nodes.removeIf(node -> node.id().equals(surfaceNode));

        NodeId factor = addSignalFactor(nodes, base.outputType(), descriptor, surface);
        NodeId baseOffset = surface == Surface.UPPER ? UPPER_OFFSET : UNDERSIDE_OFFSET;
        NodeId seededOffset = arithmetic(
                nodes,
                base.outputType(),
                surface.prefix + ".offset.seeded",
                ArithmeticOperator.MULTIPLY,
                baseOffset,
                factor);
        ArithmeticOperator operator = surface == Surface.UPPER
                ? ArithmeticOperator.ADD
                : ArithmeticOperator.SUBTRACT;
        nodes.add(new ArithmeticNode(
                surfaceNode,
                base.outputType(),
                operator,
                SUSPENSION,
                seededOffset));
        return new ProceduralGraph(nodes, surfaceNode);
    }

    private static ProceduralGraph addBoundedDensityDetail(
            ProceduralGraph base,
            SkyIslandVolumeDescriptor descriptor) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        Set<NodeId> replaced = Set.of(
                UPPER_SURFACE,
                UNDERSIDE_SURFACE,
                DENSITY_UPPER_CONSTRAINT,
                DENSITY_LOWER_CONSTRAINT,
                DENSITY_INTERSECTION);
        nodes.removeIf(node -> replaced.contains(node.id()));

        GraphValueType type = base.outputType();
        NodeId upperFactor = addSignalFactor(nodes, type, descriptor, Surface.UPPER);
        NodeId undersideFactor = addSignalFactor(nodes, type, descriptor, Surface.UNDERSIDE);
        NodeId seededUpperOffset = arithmetic(
                nodes,
                type,
                "upper.offset.seeded",
                ArithmeticOperator.MULTIPLY,
                UPPER_OFFSET,
                upperFactor);
        NodeId seededUndersideOffset = arithmetic(
                nodes,
                type,
                "underside.offset.seeded",
                ArithmeticOperator.MULTIPLY,
                UNDERSIDE_OFFSET,
                undersideFactor);
        nodes.add(new ArithmeticNode(
                UPPER_SURFACE,
                type,
                ArithmeticOperator.ADD,
                SUSPENSION,
                seededUpperOffset));
        nodes.add(new ArithmeticNode(
                UNDERSIDE_SURFACE,
                type,
                ArithmeticOperator.SUBTRACT,
                SUSPENSION,
                seededUndersideOffset));
        nodes.add(new ArithmeticNode(
                DENSITY_UPPER_CONSTRAINT,
                type,
                ArithmeticOperator.SUBTRACT,
                UPPER_SURFACE,
                POSITION_Y));
        nodes.add(new ArithmeticNode(
                DENSITY_LOWER_CONSTRAINT,
                type,
                ArithmeticOperator.SUBTRACT,
                POSITION_Y,
                UNDERSIDE_SURFACE));
        nodes.add(new IntersectionNode(
                DENSITY_INTERSECTION,
                DENSITY_UPPER_CONSTRAINT,
                DENSITY_LOWER_CONSTRAINT));
        return new ProceduralGraph(nodes, DENSITY_INTERSECTION);
    }

    private static NodeId addSignalFactor(
            List<GraphNode> nodes,
            GraphValueType type,
            SkyIslandVolumeDescriptor descriptor,
            Surface surface) {
        String prefix = surface.prefix;
        String namespace = surface == Surface.UPPER
                ? SeededSkyIslandVolumeRecipe.UPPER_SIGNAL_NAMESPACE
                : SeededSkyIslandVolumeRecipe.UNDERSIDE_SIGNAL_NAMESPACE;
        NodeId signal = new NodeId("signal." + prefix + "-detail");
        nodes.add(new PlanarValueSignalNode(
                signal,
                type,
                PlanarValueSignal.VERSION,
                SeedDerivation.VERSION,
                descriptor.seed(),
                namespace,
                descriptor.signalScale()));
        NodeId maximum = constant(
                nodes,
                type,
                "signal." + prefix + ".maximum-relative-displacement",
                SeededSkyIslandVolumeRecipe.MAXIMUM_RELATIVE_DISPLACEMENT);
        NodeId amplitude = constant(
                nodes,
                type,
                "descriptor.signal-amplitude." + prefix,
                descriptor.signalAmplitude());
        NodeId relativeAmplitude = arithmetic(
                nodes,
                type,
                "signal." + prefix + ".relative-amplitude",
                ArithmeticOperator.MULTIPLY,
                maximum,
                amplitude);
        NodeId modulation = arithmetic(
                nodes,
                type,
                "signal." + prefix + ".modulation",
                ArithmeticOperator.MULTIPLY,
                relativeAmplitude,
                signal);
        NodeId one = constant(nodes, type, "signal." + prefix + ".one", 1.0);
        return arithmetic(
                nodes,
                type,
                "signal." + prefix + ".factor",
                ArithmeticOperator.ADD,
                one,
                modulation);
    }

    private static ProceduralGraph addStructuredUpper(
            ProceduralGraph base,
            SkyIslandVolumeDescriptor descriptor,
            SecondaryShape shape) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        nodes.removeIf(node -> node.id().equals(UPPER_SURFACE));
        NodeId factor = addSecondaryFactor(nodes, base.outputType(), descriptor, shape);
        NodeId offset = arithmetic(
                nodes,
                base.outputType(),
                "upper.offset.secondary",
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

    private static ProceduralGraph addStructuredDensity(
            ProceduralGraph base,
            SkyIslandVolumeDescriptor descriptor,
            SecondaryShape shape) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        Set<NodeId> replaced = Set.of(
                UPPER_SURFACE,
                DENSITY_UPPER_CONSTRAINT,
                DENSITY_INTERSECTION);
        nodes.removeIf(node -> replaced.contains(node.id()));

        GraphValueType type = base.outputType();
        NodeId factor = addSecondaryFactor(nodes, type, descriptor, shape);
        NodeId offset = arithmetic(
                nodes,
                type,
                "upper.offset.secondary",
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

    private static NodeId addSecondaryFactor(
            List<GraphNode> nodes,
            GraphValueType type,
            SkyIslandVolumeDescriptor descriptor,
            SecondaryShape shape) {
        NodeId along = new NodeId("profile.along-normalized");
        NodeId across = new NodeId("profile.across-normalized");

        NodeId main = addBasis(nodes, type, "secondary.main-ridge", along, across, shape.main());
        NodeId spur = addBasis(nodes, type, "secondary.spur", along, across, shape.spur());
        NodeId valley = addBasis(nodes, type, "secondary.valley", along, across, shape.valley());

        NodeId amplitude = constant(
                nodes,
                type,
                "secondary.descriptor-amplitude",
                descriptor.signalAmplitude());
        NodeId mainStrength = constant(
                nodes,
                type,
                "secondary.main-ridge.strength",
                SecondaryMorphologySkyIslandVolumeRecipe.MAIN_RIDGE_RELATIVE_AMPLITUDE);
        NodeId spurStrength = constant(
                nodes,
                type,
                "secondary.spur.strength",
                SecondaryMorphologySkyIslandVolumeRecipe.SPUR_RELATIVE_AMPLITUDE);
        NodeId valleyStrength = constant(
                nodes,
                type,
                "secondary.valley.strength",
                SecondaryMorphologySkyIslandVolumeRecipe.VALLEY_RELATIVE_AMPLITUDE);

        NodeId mainWeighted = weighted(
                nodes, type, "secondary.main-ridge.weighted", main, mainStrength, amplitude);
        NodeId spurWeighted = weighted(
                nodes, type, "secondary.spur.weighted", spur, spurStrength, amplitude);
        NodeId valleyWeighted = weighted(
                nodes, type, "secondary.valley.weighted", valley, valleyStrength, amplitude);
        NodeId positive = arithmetic(
                nodes,
                type,
                "secondary.positive-relief",
                ArithmeticOperator.ADD,
                mainWeighted,
                spurWeighted);
        NodeId net = arithmetic(
                nodes,
                type,
                "secondary.net-relief",
                ArithmeticOperator.SUBTRACT,
                positive,
                valleyWeighted);
        NodeId one = constant(nodes, type, "secondary.one", 1.0);
        return arithmetic(
                nodes,
                type,
                "secondary.upper-factor",
                ArithmeticOperator.ADD,
                one,
                net);
    }

    private static NodeId weighted(
            List<GraphNode> nodes,
            GraphValueType type,
            String prefix,
            NodeId basis,
            NodeId strength,
            NodeId amplitude) {
        NodeId scaled = arithmetic(
                nodes,
                type,
                prefix + ".base",
                ArithmeticOperator.MULTIPLY,
                basis,
                strength);
        return arithmetic(
                nodes,
                type,
                prefix,
                ArithmeticOperator.MULTIPLY,
                scaled,
                amplitude);
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

    private static Map<String, List<NodeId>> boundedProvenance(
            Map<String, List<NodeId>> base) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>(base);
        result.put("signal-controls", List.of(
                new NodeId("signal.upper-detail"),
                new NodeId("signal.underside-detail"),
                new NodeId("descriptor.signal-amplitude.upper"),
                new NodeId("descriptor.signal-amplitude.underside"),
                new NodeId("signal.upper.factor"),
                new NodeId("signal.underside.factor")));
        return result;
    }

    private static Map<String, List<NodeId>> structuredProvenance(
            Map<String, List<NodeId>> base) {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>(base);
        result.put("secondary-morphology", List.of(
                new NodeId("secondary.main-ridge.basis"),
                new NodeId("secondary.spur.basis"),
                new NodeId("secondary.valley.basis"),
                new NodeId("secondary.upper-factor"),
                new NodeId("upper.offset.secondary")));
        return result;
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

    private enum Surface {
        UPPER("upper"),
        UNDERSIDE("underside");

        private final String prefix;

        Surface(String prefix) {
            this.prefix = prefix;
        }
    }

    private record BasisShape(
            double angle,
            double centerAlong,
            double centerAcross,
            double length,
            double width) {}

    private record SecondaryShape(BasisShape main, BasisShape spur, BasisShape valley) {
        private static SecondaryShape from(SkyIslandVolumeDescriptor descriptor) {
            double mainAngle = signed(descriptor.seed(), "sky-island.secondary.main-angle") * 0.22;
            double mainAlong = signed(descriptor.seed(), "sky-island.secondary.main-along") * 0.18;
            double mainAcross = signed(descriptor.seed(), "sky-island.secondary.main-across") * 0.10;

            double spurSide = signed(descriptor.seed(), "sky-island.secondary.spur-side") < 0.0
                    ? -1.0
                    : 1.0;
            double spurAngle = spurSide
                    * (0.58 + unit(descriptor.seed(), "sky-island.secondary.spur-angle") * 0.28);
            double spurAlong = signed(descriptor.seed(), "sky-island.secondary.spur-along") * 0.24;
            double spurAcross = signed(descriptor.seed(), "sky-island.secondary.spur-across") * 0.16;

            double valleyAngle = signed(descriptor.seed(), "sky-island.secondary.valley-angle") * 0.38;
            double valleyAlong = signed(descriptor.seed(), "sky-island.secondary.valley-along") * 0.28;
            double valleyAcross = signed(descriptor.seed(), "sky-island.secondary.valley-across") * 0.18;

            return new SecondaryShape(
                    new BasisShape(mainAngle, mainAlong, mainAcross, 0.72, 0.16),
                    new BasisShape(spurAngle, spurAlong, spurAcross, 0.48, 0.11),
                    new BasisShape(valleyAngle, valleyAlong, valleyAcross, 0.62, 0.13));
        }
    }

    private static double signed(long seed, String namespace) {
        return 2.0 * unit(seed, namespace) - 1.0;
    }

    private static double unit(long seed, String namespace) {
        long value = SeedDerivation.derive(seed, namespace);
        return (value >>> 11) * 0x1.0p-53;
    }
}
