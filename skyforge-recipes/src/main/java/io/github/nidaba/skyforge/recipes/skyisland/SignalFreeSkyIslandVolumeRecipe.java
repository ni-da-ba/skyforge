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

/** Compiles the deterministic primary suspended volume without seeded enrichment. */
public final class SignalFreeSkyIslandVolumeRecipe {
    /** Version of the signal-free suspended-volume morphology. */
    public static final int RECIPE_VERSION = 1;

    /** Maximum fractional extension of the principal radius at full ridge strength. */
    public static final double MAXIMUM_RIDGE_STRETCH = 0.40;

    /** Maximum signed linear underside bias within the nominal footprint. */
    public static final double MAXIMUM_UNDERSIDE_ASYMMETRY = 0.25;

    /**
     * Compiles upper, underside, and positive-inside density graphs.
     *
     * @throws NullPointerException if {@code descriptor} is {@code null}
     * @throws IllegalArgumentException if seeded enrichment is requested or a derived constant is
     *     outside the finite positive binary64 range
     */
    public CompiledSkyIslandVolume compile(SkyIslandVolumeDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        if (descriptor.signalAmplitude() != 0.0) {
            throw new IllegalArgumentException(
                    "signalAmplitude must be zero for the signal-free volume recipe");
        }

        DerivedShape shape = DerivedShape.from(descriptor);
        ProceduralGraph upper = surfaceGraph(
                descriptor, shape, GraphValueType.SCALAR_FIELD_2, Surface.UPPER);
        ProceduralGraph underside = surfaceGraph(
                descriptor, shape, GraphValueType.SCALAR_FIELD_2, Surface.UNDERSIDE);
        ProceduralGraph density = densityGraph(descriptor, shape);
        return new CompiledSkyIslandVolume(
                descriptor,
                RECIPE_VERSION,
                CanonicalGraphJson.INTERSECTION_SCHEMA_VERSION,
                upper,
                underside,
                density,
                provenance());
    }

    private static ProceduralGraph surfaceGraph(
            SkyIslandVolumeDescriptor descriptor,
            DerivedShape shape,
            GraphValueType type,
            Surface surface) {
        GraphBuilder graph = new GraphBuilder(type);
        ProfileNodes profile = addProfile(graph, descriptor, shape);
        NodeId output = switch (surface) {
            case UPPER -> addUpperSurface(graph, descriptor, shape, profile);
            case UNDERSIDE -> addUndersideSurface(graph, descriptor, profile);
        };
        return graph.build(output);
    }

    private static ProceduralGraph densityGraph(
            SkyIslandVolumeDescriptor descriptor, DerivedShape shape) {
        GraphBuilder graph = new GraphBuilder(GraphValueType.SCALAR_FIELD_3);
        ProfileNodes profile = addProfile(graph, descriptor, shape);
        NodeId upper = addUpperSurface(graph, descriptor, shape, profile);
        NodeId underside = addUndersideSurface(graph, descriptor, profile);
        NodeId y = graph.coordinate("position.y", CoordinateAxis.Y);
        NodeId upperConstraint = graph.arithmetic(
                "density.upper-constraint", ArithmeticOperator.SUBTRACT, upper, y);
        NodeId lowerConstraint = graph.arithmetic(
                "density.lower-constraint", ArithmeticOperator.SUBTRACT, y, underside);
        NodeId density = graph.intersection(
                "density.solid-intersection", upperConstraint, lowerConstraint);
        return graph.build(density);
    }

    private static ProfileNodes addProfile(
            GraphBuilder graph,
            SkyIslandVolumeDescriptor descriptor,
            DerivedShape shape) {
        NodeId x = graph.coordinate("position.x", CoordinateAxis.X);
        NodeId z = graph.coordinate("position.z", CoordinateAxis.Z);
        NodeId centerX = graph.constant("descriptor.center-x", descriptor.centerX());
        NodeId centerZ = graph.constant("descriptor.center-z", descriptor.centerZ());
        NodeId dx = graph.arithmetic(
                "position.relative-x", ArithmeticOperator.SUBTRACT, x, centerX);
        NodeId dz = graph.arithmetic(
                "position.relative-z", ArithmeticOperator.SUBTRACT, z, centerZ);

        NodeId cosine = graph.constant("ridge.cos-azimuth", shape.cosine());
        NodeId sine = graph.constant("ridge.sin-azimuth", shape.sine());
        NodeId dxCosine = graph.arithmetic(
                "ridge.dx-cos", ArithmeticOperator.MULTIPLY, dx, cosine);
        NodeId dzSine = graph.arithmetic(
                "ridge.dz-sin", ArithmeticOperator.MULTIPLY, dz, sine);
        NodeId along = graph.arithmetic(
                "ridge.along-axis", ArithmeticOperator.ADD, dxCosine, dzSine);
        NodeId dzCosine = graph.arithmetic(
                "ridge.dz-cos", ArithmeticOperator.MULTIPLY, dz, cosine);
        NodeId dxSine = graph.arithmetic(
                "ridge.dx-sin", ArithmeticOperator.MULTIPLY, dx, sine);
        NodeId across = graph.arithmetic(
                "ridge.across-axis", ArithmeticOperator.SUBTRACT, dzCosine, dxSine);

        NodeId majorRadius = graph.constant("ridge.major-radius", shape.majorRadius());
        NodeId minorRadius = graph.constant("ridge.minor-radius", shape.minorRadius());
        NodeId alongNormalized = graph.arithmetic(
                "profile.along-normalized", ArithmeticOperator.DIVIDE, along, majorRadius);
        NodeId acrossNormalized = graph.arithmetic(
                "profile.across-normalized", ArithmeticOperator.DIVIDE, across, minorRadius);
        NodeId alongSquared = graph.arithmetic(
                "profile.along-squared",
                ArithmeticOperator.MULTIPLY,
                alongNormalized,
                alongNormalized);
        NodeId acrossSquared = graph.arithmetic(
                "profile.across-squared",
                ArithmeticOperator.MULTIPLY,
                acrossNormalized,
                acrossNormalized);
        NodeId radiusSquared = graph.arithmetic(
                "profile.radius-squared",
                ArithmeticOperator.ADD,
                alongSquared,
                acrossSquared);
        NodeId one = graph.constant("profile.one", 1.0);
        NodeId remaining = graph.arithmetic(
                "profile.remaining", ArithmeticOperator.SUBTRACT, one, radiusSquared);
        NodeId suspension = graph.constant(
                "descriptor.suspension-elevation", descriptor.suspensionElevation());
        return new ProfileNodes(alongNormalized, radiusSquared, remaining, one, suspension);
    }

    private static NodeId addUpperSurface(
            GraphBuilder graph,
            SkyIslandVolumeDescriptor descriptor,
            DerivedShape shape,
            ProfileNodes profile) {
        NodeId coastalShape = graph.constant(
                "upper.coastal-shape", shape.coastalShape());
        NodeId shapedRadius = graph.arithmetic(
                "upper.shaped-radius",
                ArithmeticOperator.MULTIPLY,
                coastalShape,
                profile.radiusSquared());
        NodeId crownFactor = graph.arithmetic(
                "upper.crown-factor",
                ArithmeticOperator.ADD,
                profile.one(),
                shapedRadius);
        NodeId crownProfile = graph.arithmetic(
                "upper.crown-profile",
                ArithmeticOperator.MULTIPLY,
                profile.remaining(),
                crownFactor);
        NodeId elevation = graph.constant(
                "descriptor.upper-elevation", descriptor.upperElevation());
        NodeId offset = graph.arithmetic(
                "upper.offset", ArithmeticOperator.MULTIPLY, elevation, crownProfile);
        return graph.arithmetic(
                "upper.surface", ArithmeticOperator.ADD, profile.suspension(), offset);
    }

    private static NodeId addUndersideSurface(
            GraphBuilder graph,
            SkyIslandVolumeDescriptor descriptor,
            ProfileNodes profile) {
        NodeId taper = graph.constant(
                "descriptor.underside-taper", descriptor.undersideTaper());
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

        double asymmetry = MAXIMUM_UNDERSIDE_ASYMMETRY * descriptor.undersideAsymmetry();
        NodeId asymmetryStrength = graph.constant(
                "descriptor.underside-asymmetry", asymmetry);
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
        NodeId shapedDepth = graph.arithmetic(
                "underside.shaped-depth",
                ArithmeticOperator.MULTIPLY,
                taperedRemaining,
                asymmetryFactor);
        NodeId depth = graph.constant(
                "descriptor.underside-depth", descriptor.undersideDepth());
        NodeId offset = graph.arithmetic(
                "underside.offset", ArithmeticOperator.MULTIPLY, depth, shapedDepth);
        return graph.arithmetic(
                "underside.surface", ArithmeticOperator.SUBTRACT, profile.suspension(), offset);
    }

    private static Map<String, List<NodeId>> provenance() {
        LinkedHashMap<String, List<NodeId>> result = new LinkedHashMap<>();
        result.put("horizontal-center", ids("descriptor.center-x", "descriptor.center-z"));
        result.put("suspension-elevation", ids("descriptor.suspension-elevation"));
        result.put("nominal-radius", ids("ridge.major-radius", "ridge.minor-radius"));
        result.put("upper-elevation", ids("descriptor.upper-elevation", "upper.surface"));
        result.put("underside-depth", ids("descriptor.underside-depth", "underside.surface"));
        result.put("coastal-falloff", ids("upper.coastal-shape", "upper.crown-profile"));
        result.put("primary-ridge", ids("ridge.along-axis", "ridge.across-axis"));
        result.put("underside-taper", ids("descriptor.underside-taper", "underside.tapered-remaining"));
        result.put("underside-asymmetry", ids("descriptor.underside-asymmetry", "underside.asymmetry-factor"));
        result.put("signal-controls", List.of());
        return result;
    }

    private static List<NodeId> ids(String... values) {
        List<NodeId> result = new ArrayList<>(values.length);
        for (String value : values) {
            result.add(new NodeId(value));
        }
        return List.copyOf(result);
    }

    private enum Surface {
        UPPER,
        UNDERSIDE
    }

    private record ProfileNodes(
            NodeId alongNormalized,
            NodeId radiusSquared,
            NodeId remaining,
            NodeId one,
            NodeId suspension) {}

    private record DerivedShape(
            double majorRadius,
            double minorRadius,
            double cosine,
            double sine,
            double coastalShape) {
        private static DerivedShape from(SkyIslandVolumeDescriptor descriptor) {
            double stretch = 1.0 + MAXIMUM_RIDGE_STRETCH * descriptor.ridgeStrength();
            double majorRadius = descriptor.nominalRadius() * stretch;
            double minorRadius = descriptor.nominalRadius() / stretch;
            double cosine = Math.cos(descriptor.ridgeAzimuth());
            double sine = Math.sin(descriptor.ridgeAzimuth());
            double coastalShape = 1.0 - descriptor.coastalFalloff() / descriptor.nominalRadius();
            requireFinitePositive("derived major radius", majorRadius);
            requireFinitePositive("derived minor radius", minorRadius);
            requireFinite("derived ridge cosine", cosine);
            requireFinite("derived ridge sine", sine);
            requireFinite("derived coastal shape", coastalShape);
            return new DerivedShape(majorRadius, minorRadius, cosine, sine, coastalShape);
        }
    }

    private static void requireFinitePositive(String property, double value) {
        requireFinite(property, value);
        if (value <= 0.0) {
            throw new IllegalArgumentException(property + " must be positive");
        }
    }

    private static void requireFinite(String property, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(property + " is outside the representable range");
        }
    }

    private static final class GraphBuilder {
        private final GraphValueType type;
        private final List<GraphNode> nodes = new ArrayList<>();

        private GraphBuilder(GraphValueType type) {
            this.type = type;
        }

        private NodeId coordinate(String id, CoordinateAxis axis) {
            NodeId nodeId = new NodeId(id);
            nodes.add(new CoordinateNode(nodeId, type, axis));
            return nodeId;
        }

        private NodeId constant(String id, double value) {
            NodeId nodeId = new NodeId(id);
            nodes.add(new ConstantNode(nodeId, type, value));
            return nodeId;
        }

        private NodeId arithmetic(
                String id, ArithmeticOperator operator, NodeId left, NodeId right) {
            NodeId nodeId = new NodeId(id);
            nodes.add(new ArithmeticNode(nodeId, type, operator, left, right));
            return nodeId;
        }

        private NodeId intersection(String id, NodeId left, NodeId right) {
            NodeId nodeId = new NodeId(id);
            nodes.add(new IntersectionNode(nodeId, left, right));
            return nodeId;
        }

        private ProceduralGraph build(NodeId output) {
            return new ProceduralGraph(nodes, output);
        }
    }
}
