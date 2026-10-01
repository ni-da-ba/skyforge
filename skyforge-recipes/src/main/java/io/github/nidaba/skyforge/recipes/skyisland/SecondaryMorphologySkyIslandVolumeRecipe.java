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
 * Adds deterministic organized ridges, spurs, and valleys above the accepted seeded volume.
 *
 * <p>The secondary morphology scales the already accepted upper offset from the suspension plane by
 * a strictly positive factor. This preserves the exact analytical rim, horizontal footprint, and
 * inside/outside ordering while allowing coherent landforms substantially broader than the detail
 * signal used by {@link SeededSkyIslandVolumeRecipe}.
 */
public final class SecondaryMorphologySkyIslandVolumeRecipe {
    /** Version of the structured secondary-morphology recipe. */
    public static final int RECIPE_VERSION = 3;

    /** Maximum relative contribution of the main secondary ridge. */
    public static final double MAIN_RIDGE_RELATIVE_AMPLITUDE = 0.30;

    /** Maximum relative contribution of the secondary spur. */
    public static final double SPUR_RELATIVE_AMPLITUDE = 0.18;

    /** Maximum relative subtraction of the organized valley. */
    public static final double VALLEY_RELATIVE_AMPLITUDE = 0.24;

    /** Analytical lower bound on the upper-offset scale factor at full amplitude. */
    public static final double MINIMUM_UPPER_FACTOR = 1.0 - VALLEY_RELATIVE_AMPLITUDE;

    /** Analytical upper bound on the upper-offset scale factor at full amplitude. */
    public static final double MAXIMUM_UPPER_FACTOR =
            1.0 + MAIN_RIDGE_RELATIVE_AMPLITUDE + SPUR_RELATIVE_AMPLITUDE;

    private static final NodeId UPPER_SURFACE = new NodeId("upper.surface");
    private static final NodeId UPPER_OFFSET_SEEDED = new NodeId("upper.offset.seeded");
    private static final NodeId SUSPENSION = new NodeId("descriptor.suspension-elevation");
    private static final NodeId DENSITY_UPPER_CONSTRAINT = new NodeId("density.upper-constraint");
    private static final NodeId DENSITY_INTERSECTION = new NodeId("density.solid-intersection");
    private static final NodeId POSITION_Y = new NodeId("position.y");

    private final SeededSkyIslandVolumeRecipe seededRecipe = new SeededSkyIslandVolumeRecipe();

    /**
     * Compiles the accepted seeded volume plus structured upper-surface morphology.
     *
     * <p>Zero signal amplitude remains byte-identical to the accepted signal-free recipe because
     * the seeded recipe returns that artifact directly and this recipe returns it unchanged.
     */
    public CompiledSkyIslandVolume compile(SkyIslandVolumeDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        CompiledSkyIslandVolume base = seededRecipe.compile(descriptor);
        if (descriptor.signalAmplitude() == 0.0) {
            return base;
        }

        SecondaryShape shape = SecondaryShape.from(descriptor);
        ProceduralGraph upper = enrichUpper(base.upperSurfaceGraph(), descriptor, shape);
        ProceduralGraph density = enrichDensity(base.densityGraph(), descriptor, shape);
        return new CompiledSkyIslandVolume(
                descriptor,
                RECIPE_VERSION,
                CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                upper,
                base.undersideSurfaceGraph(),
                density,
                provenance(base.provenance()));
    }

    private static ProceduralGraph enrichUpper(
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

    private static ProceduralGraph enrichDensity(
            ProceduralGraph base,
            SkyIslandVolumeDescriptor descriptor,
            SecondaryShape shape) {
        List<GraphNode> nodes = new ArrayList<>(base.nodes());
        Set<NodeId> replaced = Set.of(UPPER_SURFACE, DENSITY_UPPER_CONSTRAINT, DENSITY_INTERSECTION);
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
                new NodeId("density.lower-constraint")));
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
                MAIN_RIDGE_RELATIVE_AMPLITUDE);
        NodeId spurStrength = constant(
                nodes,
                type,
                "secondary.spur.strength",
                SPUR_RELATIVE_AMPLITUDE);
        NodeId valleyStrength = constant(
                nodes,
                type,
                "secondary.valley.strength",
                VALLEY_RELATIVE_AMPLITUDE);

        NodeId mainWeighted = weighted(nodes, type, "secondary.main-ridge.weighted", main, mainStrength, amplitude);
        NodeId spurWeighted = weighted(nodes, type, "secondary.spur.weighted", spur, spurStrength, amplitude);
        NodeId valleyWeighted = weighted(nodes, type, "secondary.valley.weighted", valley, valleyStrength, amplitude);
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

    /**
     * Adds a smooth directional basis 1 / (1 + across^2 + along^4).
     *
     * <p>Coordinates are first rotated, translated, and normalized. The basis therefore remains
     * strictly in (0, 1], needs no new kernel primitive, and has an elongated ridge/valley shape.
     */
    private static NodeId addBasis(
            List<GraphNode> nodes,
            GraphValueType type,
            String prefix,
            NodeId along,
            NodeId across,
            BasisShape shape) {
        NodeId cosine = constant(nodes, type, prefix + ".cos-angle", Math.cos(shape.angle()));
        NodeId sine = constant(nodes, type, prefix + ".sin-angle", Math.sin(shape.angle()));
        NodeId alongCos = arithmetic(nodes, type, prefix + ".along-cos", ArithmeticOperator.MULTIPLY, along, cosine);
        NodeId acrossSin = arithmetic(nodes, type, prefix + ".across-sin", ArithmeticOperator.MULTIPLY, across, sine);
        NodeId rotatedAlong = arithmetic(nodes, type, prefix + ".rotated-along", ArithmeticOperator.ADD, alongCos, acrossSin);
        NodeId acrossCos = arithmetic(nodes, type, prefix + ".across-cos", ArithmeticOperator.MULTIPLY, across, cosine);
        NodeId alongSin = arithmetic(nodes, type, prefix + ".along-sin", ArithmeticOperator.MULTIPLY, along, sine);
        NodeId rotatedAcross = arithmetic(nodes, type, prefix + ".rotated-across", ArithmeticOperator.SUBTRACT, acrossCos, alongSin);

        NodeId centerAlong = constant(nodes, type, prefix + ".center-along", shape.centerAlong());
        NodeId centerAcross = constant(nodes, type, prefix + ".center-across", shape.centerAcross());
        NodeId localAlong = arithmetic(nodes, type, prefix + ".local-along", ArithmeticOperator.SUBTRACT, rotatedAlong, centerAlong);
        NodeId localAcross = arithmetic(nodes, type, prefix + ".local-across", ArithmeticOperator.SUBTRACT, rotatedAcross, centerAcross);
        NodeId length = constant(nodes, type, prefix + ".length", shape.length());
        NodeId width = constant(nodes, type, prefix + ".width", shape.width());
        NodeId normalizedAlong = arithmetic(nodes, type, prefix + ".normalized-along", ArithmeticOperator.DIVIDE, localAlong, length);
        NodeId normalizedAcross = arithmetic(nodes, type, prefix + ".normalized-across", ArithmeticOperator.DIVIDE, localAcross, width);
        NodeId alongSquared = arithmetic(nodes, type, prefix + ".along-squared", ArithmeticOperator.MULTIPLY, normalizedAlong, normalizedAlong);
        NodeId alongFourth = arithmetic(nodes, type, prefix + ".along-fourth", ArithmeticOperator.MULTIPLY, alongSquared, alongSquared);
        NodeId acrossSquared = arithmetic(nodes, type, prefix + ".across-squared", ArithmeticOperator.MULTIPLY, normalizedAcross, normalizedAcross);
        NodeId one = constant(nodes, type, prefix + ".one", 1.0);
        NodeId onePlusAcross = arithmetic(nodes, type, prefix + ".one-plus-across", ArithmeticOperator.ADD, one, acrossSquared);
        NodeId denominator = arithmetic(nodes, type, prefix + ".denominator", ArithmeticOperator.ADD, onePlusAcross, alongFourth);
        return arithmetic(nodes, type, prefix + ".basis", ArithmeticOperator.DIVIDE, one, denominator);
    }

    private static Map<String, List<NodeId>> provenance(Map<String, List<NodeId>> base) {
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

            double spurSide = signed(descriptor.seed(), "sky-island.secondary.spur-side") < 0.0 ? -1.0 : 1.0;
            double spurAngle = spurSide * (0.58 + unit(descriptor.seed(), "sky-island.secondary.spur-angle") * 0.28);
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
