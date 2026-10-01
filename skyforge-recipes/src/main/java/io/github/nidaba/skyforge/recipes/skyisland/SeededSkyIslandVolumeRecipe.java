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

/** Compiles the accepted suspended morphology with bounded deterministic seeded enrichment. */
public final class SeededSkyIslandVolumeRecipe {
    /** Version of the seeded suspended-volume recipe defined by this class. */
    public static final int RECIPE_VERSION = 2;

    /** Maximum relative upper or underside displacement at full descriptor amplitude. */
    public static final double MAXIMUM_RELATIVE_DISPLACEMENT = 0.15;

    /** Stable semantic namespace for upper-surface detail. */
    public static final String UPPER_SIGNAL_NAMESPACE = "sky-island.upper-detail";

    /** Stable semantic namespace for underside detail. */
    public static final String UNDERSIDE_SIGNAL_NAMESPACE = "sky-island.underside-detail";

    private static final NodeId UPPER_SURFACE = new NodeId("upper.surface");
    private static final NodeId UNDERSIDE_SURFACE = new NodeId("underside.surface");
    private static final NodeId UPPER_OFFSET = new NodeId("upper.offset");
    private static final NodeId UNDERSIDE_OFFSET = new NodeId("underside.offset");
    private static final NodeId SUSPENSION = new NodeId("descriptor.suspension-elevation");
    private static final NodeId DENSITY_UPPER_CONSTRAINT = new NodeId("density.upper-constraint");
    private static final NodeId DENSITY_LOWER_CONSTRAINT = new NodeId("density.lower-constraint");
    private static final NodeId DENSITY_INTERSECTION = new NodeId("density.solid-intersection");
    private static final NodeId POSITION_Y = new NodeId("position.y");

    private final SignalFreeSkyIslandVolumeRecipe signalFreeRecipe =
            new SignalFreeSkyIslandVolumeRecipe();

    /**
     * Compiles bounded seeded upper and underside detail while preserving the exact signal-free
     * artifact when signal amplitude is zero.
     *
     * <p>Enrichment scales the signed offset of each surface from the suspension plane by a factor
     * in {@code [0.85, 1.15]} at full descriptor amplitude. The factor is therefore strictly
     * positive, which preserves the analytical footprint, rim closure, and surface ordering.
     *
     * @throws NullPointerException if {@code descriptor} is {@code null}
     */
    public CompiledSkyIslandVolume compile(SkyIslandVolumeDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.signalAmplitude() == 0.0) {
            return signalFreeRecipe.compile(descriptor);
        }

        SkyIslandVolumeDescriptor baseDescriptor = new SkyIslandVolumeDescriptor(
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
        CompiledSkyIslandVolume base = signalFreeRecipe.compile(baseDescriptor);

        ProceduralGraph upper = enrichSurface(
                base.upperSurfaceGraph(), descriptor, Surface.UPPER);
        ProceduralGraph underside = enrichSurface(
                base.undersideSurfaceGraph(), descriptor, Surface.UNDERSIDE);
        ProceduralGraph density = enrichDensity(base.densityGraph(), descriptor);

        return new CompiledSkyIslandVolume(
                descriptor,
                RECIPE_VERSION,
                CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                upper,
                underside,
                density,
                provenance(base.provenance()));
    }

    private static ProceduralGraph enrichSurface(
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
        nodes.add(new ArithmeticNode(surfaceNode, base.outputType(), operator, SUSPENSION, seededOffset));
        return new ProceduralGraph(nodes, surfaceNode);
    }

    private static ProceduralGraph enrichDensity(
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
                ? UPPER_SIGNAL_NAMESPACE
                : UNDERSIDE_SIGNAL_NAMESPACE;
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
                MAXIMUM_RELATIVE_DISPLACEMENT);
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

    private static Map<String, List<NodeId>> provenance(Map<String, List<NodeId>> base) {
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
}
